(ns oc.electron.main
  (:require [oc.electron.auto-update :as auto-update]
            [oops.core :refer (ocall oset!)]
            [taoensso.timbre :as timbre]
            ["path" :as path]
            ["@sentry/electron" :as sentry]
            ["url" :refer (URL)]
            ["electron" :refer (systemPreferences app nativeTheme ipcMain session shell BrowserWindow)]))

(goog-define dev? true)
(goog-define web-origin "http://localhost:3559")
(goog-define auth-origin "http://localhost:3003")
(goog-define init-path "/login/desktop")
(goog-define sentry-dsn false)
(goog-define sentry-environment "")

;; Setup sentry
(when sentry-dsn
  (.init ^js sentry #js {:dsn sentry-dsn :environment sentry-environment}))

;; Begin checking for updates
(auto-update/start-update-cycle!)

(def main-window (atom nil))
(def quitting? (atom false))

(def init-url (str web-origin init-path))

(def min-win-dims [980 720])
(def init-win-dims [1280 720])

(defn mac?
  []
  (= (.-platform ^js js/process) "darwin"))

(defn win32?
  []
  (= (.-platform ^js js/process) "win32"))

(defn- load-page
  [window]
  (timbre/info "Loading " init-url)
  (ocall window "loadURL" init-url))

(defn- mk-window
  [w h show?]
  (let [mac-frame-settings (when (mac?) {:titleBarStyle "hiddenInset"})
        win-frame-settings (when (win32?) {:frame true})]
    (BrowserWindow. (clj->js
                     (merge
                      mac-frame-settings
                      win-frame-settings
                      {:width w
                       :height h
                       :minWidth (first min-win-dims)
                       :minHeight (second min-win-dims)
                       :show show?
                       ;; Icon of Ubuntu/Linux. Other platforms are configured in package.json
                       :icon (.join ^js path (.getAppPath ^js app) "public" "carrot.iconset" "icon_512x512.png")
                       :webPreferences #js {:enableRemoteModule false
                                            :preload (.join ^js path (.getAppPath ^js app) "public" "electron" "renderer.js")}
                       })))))

(defn- set-csp
  []
  ;; Define Content Security Policy
  ;; https://electronjs.org/docs/tutorial/security#6-define-a-content-security-policy
  (.. session -defaultSession -webRequest
      (onHeadersReceived
       (fn [details callback]
         (let [details-cljs (js->clj details)
               merged-details (merge details-cljs
                                     {"Content-Security-Policy" ["default-src \"none\""]})]
           (callback (clj->js merged-details)))))))

(def slack-origin "https://slack.com")
(def slack-origin-re #"^https://.*\.slack\.com$")
(def google-accounts-origin "https://accounts.google.com")
(def filestack-api-origin "https://www.filestackapi.com")
(def filestack-static-origin "https://static.filestackapi.com")
(def dropbox-origin "https://www.dropbox.com")
(def onedrive-origin "https://login.live.com")
(def box-origin "https://www.box.com")

(defn- allowed-origin?
  [o]
  (or
    (= o web-origin)
    (= o auth-origin)
    (= o slack-origin)
    (= o google-accounts-origin)
    (= o filestack-api-origin)
    (= o filestack-static-origin)
    (= o dropbox-origin)
    (= o onedrive-origin)
    (= o box-origin)
    (re-matches slack-origin-re o)))

(defn- prevent-navigation-external-to-carrot
  []
  (.on ^js app "web-contents-created"
       (fn [event contents]
         (.on ^js contents "will-navigate"
              (fn [event navigation-url]
                (let [parsed-url    (URL. navigation-url)
                      target-origin (.-origin ^js parsed-url)]
                  (timbre/info "Attempting to navigate to origin: " target-origin)
                  (when-not (allowed-origin? target-origin)
                    (timbre/info "Navigation prevented")
                    (ocall event "preventDefault")))))
         (.on ^js contents "new-window"
              (fn [event navigation-url & x]
                (let [parsed-url    (URL. navigation-url)
                      target-origin (.-origin ^js parsed-url)]
                  (timbre/info "Attempting to open new window at: " target-origin)
                  (when-not (allowed-origin? target-origin)
                    (timbre/info "New window not whitelisted, opening in external browser")
                    (ocall event "preventDefault")
                    (.openExternal ^js shell navigation-url))))))))

(defn- init-browser
  []
  (if (some? @main-window)
    (.show ^js @main-window)
    (do (set-csp)
        (prevent-navigation-external-to-carrot)
        (reset! main-window (mk-window (first init-win-dims) (second init-win-dims) true))
        (when (win32?)
          (.setMenuBarVisibility ^js @main-window false))
        (load-page @main-window)
        (when dev? (.openDevTools ^js @main-window))
        ;; -- Main window event handlers --
        (.on ^js @main-window "close" #(if (or @quitting? (win32?))
                                     (reset! main-window nil)
                                     (do (ocall % "preventDefault")
                                         (.hide ^js @main-window))))
        (let [ui-theme-changed #(ocall @main-window "webContents.send" "ui-theme-changed" (.-shouldUseDarkColors ^js nativeTheme))]
          (.on ^js systemPreferences "accent-color-changed" ui-theme-changed)
          (.subscribeNotification ^js systemPreferences "AppleInterfaceThemeChangedNotification" ui-theme-changed)
          (.on ^js systemPreferences "color-changed" ui-theme-changed)))))

(defn init
  []
  (set! *main-cli-fn* (fn [] nil))

  ;; Required for desktop notifications to work on Windows
  ;; https://electronjs.org/docs/tutorial/notifications#windows
  ;; When testing, add `node_modules\electron\dist\electron.exe` to your Start Menu
  (when (win32?)
    (.setAppUserModelId ^js app "io.carrot.desktop"))

  ;; -- App event handlers --
  (.on ^js app "window-all-closed" #(when-not (mac?)
                                      (.quit ^js app)))
  (.on ^js app "activate" #(when-let [w @main-window]
                             (.show ^js w)))
  (.on ^js app "before-quit" #(reset! quitting? true))
  (.on ^js app "ready" init-browser)

  ;; -- Inter-process Communication event handlers --
  ;; see electron/renderer.js
  (.on ^js ipcMain "set-badge-count" (fn [event arg] (.setBadgeCount ^js app arg)))
  (.on ^js ipcMain "show-desktop-window" (fn [event arg]
                                           (when @main-window
                                             (.show ^js @main-window))))
  (.on ^js ipcMain "window-has-focus?" (fn [event]
                                         (let [ret-value (boolean (.getFocusedWindow ^js BrowserWindow))]
                                            (oset! event "returnValue" ret-value))))
  (.on ^js ipcMain "dark-theme-enabled?" (fn [event]
                                           (let [ret-value (boolean (.-shouldUseDarkColors ^js nativeTheme))]
                                             (oset! event "returnValue" ret-value)))))
