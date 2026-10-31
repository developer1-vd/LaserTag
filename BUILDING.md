# Building Laser Tag

Laser Tag is plain Java with no third-party libraries, so the only thing you need to build
and play it is a **JDK (Java Development Kit), version 17 or newer**. Maven is optional. You
only need it to run the automated tests.

- [1. Install a JDK](#1-install-a-jdk)
- [2. Get the code](#2-get-the-code)
- [3. Build and run (easiest)](#3-build-and-run-easiest)
- [4. Build by hand with javac](#4-build-by-hand-with-javac)
- [5. Build and test with Maven](#5-build-and-test-with-maven)
- [6. Build Windows, macOS and Linux apps](#6-build-windows-macos-and-linux-apps)
- [7. Automatic builds and releases (GitHub Actions)](#7-automatic-builds-and-releases-github-actions)
- [8. Troubleshooting](#8-troubleshooting)

## 1. Install a JDK

Check whether you already have one. Open a terminal (Command Prompt or PowerShell on
Windows) and run:

```bash
javac -version
```

If it prints `javac 17` or a higher number, skip to step 2. If the command isn't found, or
the version is lower than 17, install a JDK:

| System | How to install |
| --- | --- |
| Windows | Download the **Temurin JDK 21** `.msi` from [adoptium.net](https://adoptium.net) and run it. On the *Custom Setup* page, enable **Set JAVA_HOME** and **Add to PATH**. |
| macOS | `brew install --cask temurin`, or download the `.pkg` from [adoptium.net](https://adoptium.net). |
| Ubuntu / Debian | `sudo apt install openjdk-21-jdk` |
| Fedora | `sudo dnf install java-21-openjdk-devel` |
| Arch | `sudo pacman -S jdk21-openjdk` |

Then open a **new** terminal and check again with `javac -version`.

A JRE (Java Runtime) without a JDK can *run* a built `lasertag.jar`, but it can't build
the game, because it doesn't include `javac`.

## 2. Get the code

With Git:

```bash
git clone https://github.com/developer1-vd/LaserTag.git
```

```bash
cd LaserTag
```

Without Git, use **Code → Download ZIP** on the GitHub page, unzip it, and open a terminal
in the unzipped folder.

## 3. Build and run (easiest)

The run scripts compile the game into `build/lasertag.jar` and start it.

macOS / Linux:

```bash
./run.sh
```

Windows: double-click `run.bat`, or run it from Command Prompt:

```bat
run.bat
```

`run.sh` only recompiles when a source file has changed, so later starts are quick.
`run.bat` rebuilds every time. Both pass any extra arguments to the game. For example,
this starts a dedicated server:

```bash
./run.sh --server 47777 0 2 normal
```

## 4. Build by hand with javac

This is what the scripts do. Run these from the project folder.

### macOS / Linux

Compile:

```bash
mkdir -p build/classes
```

```bash
javac --release 17 -d build/classes $(find src/main/java -name '*.java')
```

Package into a runnable jar:

```bash
jar --create --file build/lasertag.jar --main-class lasertag.LaserTag -C build/classes .
```

Run:

```bash
java -jar build/lasertag.jar
```

### Windows (PowerShell)

```powershell
New-Item -ItemType Directory -Force build\classes
```

```powershell
javac --release 17 -d build\classes (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName
```

```powershell
jar --create --file build\lasertag.jar --main-class lasertag.LaserTag -C build\classes .
```

```powershell
java -jar build\lasertag.jar
```

You can also run the compiled classes without making a jar:

```bash
java -cp build/classes lasertag.LaserTag
```

## 5. Build and test with Maven

You need [Maven](https://maven.apache.org/install.html) 3.6 or newer, and an internet
connection the first time so Maven can download JUnit and its plugins.

| Command | What it does |
| --- | --- |
| `mvn package` | Compiles, runs all tests, and builds `target/lasertag.jar` |
| `mvn package -DskipTests` | Builds the jar without running the tests |
| `mvn test` | Runs the tests only |
| `mvn test -Dtest=NetworkTest` | Runs one test class |
| `mvn clean` | Deletes the `target` folder |

Then run the game with:

```bash
java -jar target/lasertag.jar
```

The tests run without a display (they use headless mode) and cover the arenas, the game
rules, the bots, online play over real local sockets, internet codes, and router port
mapping against a fake router. They take a few seconds.

The code is in `src/main/java/lasertag` and the tests are in `src/test/java/lasertag`. Any
Java IDE (IntelliJ IDEA, Eclipse, VS Code with the Java extensions) can open the project
folder directly as a Maven project. Run the `lasertag.LaserTag` class to start the game.

## 6. Build Windows, macOS and Linux apps

To make apps that **don't need Java installed**, run the packaging tool on each operating
system you want to support. It's a single Java file, so the same command works everywhere:

```bash
java packaging/Package.java
```

It compiles the game, draws the app icon, bundles a trimmed Java runtime (about 30 MB) with
`jpackage`, and puts everything in `dist/`:

| Built on | You get | Notes |
| --- | --- | --- |
| Windows | `LaserTag-VERSION-windows-x64.zip` (unzip and run `LaserTag.exe`) and `LaserTag-VERSION-windows-x64.msi` (installer with Start-menu and desktop shortcuts) | The `.msi` needs the [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases) on your PATH |
| macOS | `LaserTag-VERSION-macos-ARCH.dmg` (drag to Applications) and `LaserTag-VERSION-macos-ARCH.zip` | ARCH is `arm64` on Apple Silicon and `x64` on Intel Macs. Each only runs on its own type of Mac. |
| Linux | `lasertag_VERSION_amd64.deb` (adds a menu entry) and `LaserTag-VERSION-linux-x64.tar.gz` (extract and run `LaserTag/bin/LaserTag`) | The `.deb` needs `fakeroot` (`sudo apt install fakeroot`). An `.rpm` is also built if `rpmbuild` is installed. |
| any | `LaserTag-VERSION.jar` | Runs anywhere with Java 17+ installed: `java -jar LaserTag-VERSION.jar` |

`jpackage` can only build for the system it runs on, so you need a Windows PC, a Mac and a
Linux machine (or let GitHub build them all, see step 7).

Options:

| Option | What it does |
| --- | --- |
| `--no-installer` | Only build the zip/tar.gz and jar, skipping the `.msi`/`.dmg`/`.deb` |
| `--strict` | Fail instead of warning when an installer can't be built |
| `--version 1.2.3` | Use this version instead of the one in `pom.xml` |

## 7. Automatic builds and releases (GitHub Actions)

The workflow in `.github/workflows/build.yml` runs on GitHub's Windows, macOS and Linux
machines:

- **Every push and pull request:** runs the tests on all three systems, then builds the apps
  for Windows (x64), macOS (Apple Silicon and Intel) and Linux (x64). Download them from the
  run's **Artifacts** section on the repository's *Actions* tab.
- **Pushing a version tag:** also publishes everything as a GitHub Release that players can
  download from the repository's *Releases* page:

  ```bash
  git tag v1.0.0
  ```

  ```bash
  git push origin v1.0.0
  ```

  The tag (without the `v`) becomes the version shown in the installers.

The Linux packages are built on Ubuntu 22.04 so the `.deb` installs on that release and
newer ones (and on Debian 12 or later).

### Signing

The apps aren't code-signed, which costs money on Windows and macOS. Players get a one-time
warning when they first open the game. The README explains how to get past it. To sign
releases, add your certificate to the workflow and pass the matching `jpackage` options in
`packaging/Package.java`: `--mac-sign` with `--mac-signing-key-user-name` on macOS. On
Windows, sign the `.msi` with `signtool` after it is built.

## 8. Troubleshooting

| Problem | Fix |
| --- | --- |
| `javac: command not found` / `'javac' is not recognized` | No JDK on your PATH. Install one (step 1) and open a new terminal. On Windows, rerun the installer with **Add to PATH** enabled. |
| `invalid target release: 17` or `class file has wrong version` | Your JDK is older than 17. Install a newer one, and check that `javac -version` and `java -version` both show 17+. |
| `./run.sh: Permission denied` | Run `chmod +x run.sh` once, or start it with `sh run.sh`. |
| Double-clicking the jar opens an archive tool | Run `java -jar lasertag.jar` from a terminal instead, or reinstall the JDK with file associations enabled. |
| `HeadlessException` or `No X11 DISPLAY` | You're on a machine without a screen (such as a server). Use `--server` there, and play from a computer with a display. |
| Maven fails to download dependencies | Check your internet connection or proxy settings. You can still build and play without Maven (step 3 or 4). |
| `Address already in use` when hosting | Another program, or another copy of the game, is using the port. Pick a different *Port* in the menu. |
| No sound | The game runs silently if Java can't open an audio device. Check your system's sound output, or press `M` in case sound is muted. |
| The window is tiny or blurry | The game scales by whole numbers for crisp pixels, including on high-DPI screens. Make the window bigger (or maximise it) for a larger whole-number scale. |
| `Could not build the msi installer` | Install the [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases) and add its `bin` folder to your PATH, or use `--no-installer` and share the zip. |
| `Can not find fakeroot` when building on Linux | `sudo apt install fakeroot` (or use `--no-installer` and share the tar.gz). |
| `.deb` install fails with `No writable system menu directory found` | The machine has no desktop environment (for example, a server). Use the tar.gz there instead. |
