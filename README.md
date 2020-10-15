# [OpenCompany](https://github.com/open-company) Desktop Wrapper

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

## Background

> Transparency, honesty, kindness, good stewardship, even humor, work in businesses at all times.

> -- [John Gerzema](http://www.johngerzema.com/)

Teams struggle to keep everyone on the same page. People are hyper-connected in the moment with chat and email, but it gets noisy as teams grow, and people miss key information. Everyone needs clear and consistent leadership, and the solution is surprisingly simple and effective - **great leadership updates that build transparency and alignment**.

With that in mind we designed [Carrot](https://carrot.io/), a software-as-a-service application powered by the open source [OpenCompany platform](https://github.com/open-company) and this source-available [web UI](https://github.com/open-company/open-company-web).

With Carrot, important company updates, announcements, stories, and strategic plans create focused, topic-based conversations that keep everyone aligned without interruptions. When information is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for leaders to engage with employees, investors, and customers, creating alignment for everyone.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy teams, investors and customers. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Web Application provides a Web UI for creating and consuming open company content and data.

![OpenCompany Screenshot](https://open-company-assets.s3.amazonaws.com/new_homepage_screenshot.png)

### OS Support

Mac OSX, Windows (Linux coming soon)...


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the Web application.

Most of the dependencies are internal, meaning [Boot](https://github.com/boot-clj/boot) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8 JRE is needed to run Clojure
* [Boot](https://github.com/boot-clj/boot) - A Clojure build and dependency management tool

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

An option we recommend is [OpenJDK](https://openjdk.java.net/). There are [instructions for Linux](https://openjdk.java.net/install/index.html) and [Homebrew](https://brew.sh/) can be used to install OpenJDK on a Mac with:

```
brew tap AdoptOpenJDK/openjdk
brew update
brew cask install adoptopenjdk8
```

#### Boot

Installing Boot is easy, for the most up to date instructions, check out the [Boot README](https://github.com/boot-clj/boot#install).
You can verify your install with:

```console
boot -V
```

## Desktop Application Usage

The Carrot desktop application is built using the [Electron](https://electronjs.org) framework. Via Electron,
we're able to launch a thin application shell (a modified Chromium browser) that loads the Carrot web application,
and provides it with hooks for accessing native desktop features.

The primary source files to be aware of when developing on the desktop application are:

- [main.cljs](./src/oc/electron/main.cljs): _the main electron process is configured and launched here_
- [renderer.js](./resources/electron/renderer.js): _the electron renderer process, which injects native features into the hosted Carrot web page_
- [package.json](./resources/package.json): _node dependency manifest, and home of build/sign/publish configuration_

### Developing locally

Because the desktop application simply loads the Carrot web app, the steps to develop locally are largely the same.
With your local Carrot environment running (i.e. `boot dev`), in a separate terminal, run:

```
boot dev-electron
```

This will compile the main electron process, and place the output in the `target/` directory. From there,
we can launch the application:

```
cd target/
yarn install
yarn start
```

NB: you'll need to install the [yarn](https://yarnpkg.com) package manager for this to work.

If all goes well, the desktop application should open in a new window, and load `localhost:3559`. Hot-reloading
should work, so from here development is identical to the Carrot web app!

### Packaging for deployment

There are two environments against which we can package the Carrot desktop app: staging and production:

```
# staging
boot staging-electron

# production
boot prod-electron
```

Both of these commands result in a production-ready build located in the `target/` directory, and each
will load the respective Carrot web application upon launch. From here, you're free to test locally
if you so wish:

```
cd target/
yarn install
yarn start
```

To actually distribute the application, we first need to package the app (DMG on Mac, EXE installer on Windows),
codesign the resulting artifact, and then publish the signed artifact to GitHub releases. Luckily these steps
are largely automated, but there is a bit of one-time setup.

#### One-time Setup

First, you'll need to have the appropriate Apple certificates installed to your Mac's keychain (ask an admin).
You'll also need the team's provisioning profile to perform development builds. Get this from a team member,
and download it to your local system. Place the file in a well known place (e.g. your `~/code/carrot` directory**.
**Do not put it in the repository**.

Next, we need to configure our environment with a few secrets:

```
cp resources/electron-builder.example.env resources/electron-builder.env
```

Edit this file appropriately. You can generate a GitHub token for yourself [here](https://github.com/settings/tokens). Make
sure to select the `write:packages` scope. Note that this file is ignored by git.

With these in place, use the following to build, sign, and publish a desktop release.

Finally, be sure to log in to [developer.apple.com](https://developer.apple.com) at least one time to accept their terms
of service and fully activate your account.

#### Staging Release (Mac)

This will produce a development build runnable by the devices specified in the supplied provisioning profile.

```
# Bump the version in resources/package.json to X.Y.Z
vim resources/package.json

git add .
git commit -m "Bump desktop version"
git push

boot staging-electron
cd target/
yarn install
npx electron-builder -c.mac.type=development -c.mac.provisioningProfile=/path/to/your/Carrot_MacOS_Development_Profile.provisionprofile --publish always
```

NOTE: `/path/to/your/Carrot_MacOS_Development_Profile.provisionprofile` is the path that you saved the provisioning profile to from the above step
(e.g. `/Users/me/code/carrot/Carrot_MacOS.provisionprofile`). **Make sure to use an absolute path in the command line argument; relative paths will not work!**

Keep in mind that this can take a while (~10 minutes) due to requiring Apple's servers to notarize the application.

This will build, sign, notarize, and publish a tagged draft release to [GitHub Releases](https://github.com/open-company/open-company-desktop/releases).
The tag will match the version number specified in `resources/package.json`.

Because this is a development build in Apple's eyes, _it is only runnable by the devices included in the supplied provisioning profile._ You should
not publish this build in the GitHub Release panel, and instead should distribute it to testers manually.

#### Production Release (Mac)

_Before performing this step, be sure that your changes have been fully merged into `master`. All prod desktop
builds should be made from the `master` branch._

```
boot prod-electron
cd target/
yarn install
npx electron-builder -c.mac.type=distribution -c.mac.identity="OpenCompany, LLC (XXXXXXXXXX) --publish always"
```

You can find the proper value for the `-c.mac.identity` value in your Mac Keychain.

Keep in mind that this can take a while (~10 minutes) due to requiring Apple's servers to notarize the application.

This will build, sign, notarize, and publish a tagged draft release to [GitHub Releases](https://github.com/open-company/open-company-desktop/releases).
Navigate your way there, and if you're ready to roll the release out to customers, you can Publish the draft. Existing client installations
will sense the new update, and automatically install it in the background.

#### Production Release (Windows)

Be sure to follow the same one-time setup that we did above on your Windows machine. Ask an admin for the relevant Windows certs.

To build on windows, you'll need to install a few tools:

- [Java](https://www.java.com/en/download/)
- [Node LTE](https://nodejs.org/en/)
- [boot.exe](https://github.com/boot-clj/boot#windows)
- [Yarn](https://yarnpkg.com)
- [Ruby](rubyinstaller.org/downloads)
- [SASS](https://sass-lang.com/install)

Now you're able to run the following from the Windows PowerShell:

_Before performing this step, be sure that your changes have been fully merged into `master`. All prod desktop
builds should be made from the `master` branch._

```
boot prod-electron-windows
cd target/
yarn install
npx electron-builder --win --publish always
```

This will build, sign, and publish an EXE to GitHub Releases alongside any existing Mac builds with the same version. This EXE
is an installer, and is completely self-contained.

To produce a test build without releasing it replace the last command with:
```
npx electron-builder --win
```

## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-desktop/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Copyright © 2015-2020 OpenCompany, LLC.

This code is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International Public License](https://creativecommons.org/licenses/by-nc-sa/4.0/) (CC BY-NC-SA 4.0).

This means the code is source available to you, but is not open source, due to the following terms of the CC BY-NC-SA 4.0 license:

**Attribution** — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.

**NonCommercial** — You may not use the material for commercial purposes.

**ShareAlike** — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original. 

This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.