# Building Laser Tag

Laser Tag is plain Java with no third-party libraries, so the only thing you need to build
and play it is a **JDK (Java Development Kit), version 17 or newer**. Maven is optional. You
only need it to run the automated tests.

- [1. Install a JDK](#1-install-a-jdk)
- [2. Get the code](#2-get-the-code)
- [3. Build and run (easiest)](#3-build-and-run-easiest)
- [4. Build by hand with javac](#4-build-by-hand-with-javac)
- [5. Build and test with Maven](#5-build-and-test-with-maven)
- [6. Share the game with friends](#6-share-the-game-with-friends)
- [7. Troubleshooting](#7-troubleshooting)

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

## 6. Share the game with friends

`lasertag.jar` holds the whole game in one file. Anyone with Java 17 or newer installed
(a JRE is enough) can play it by double-clicking it, or with:

```bash
java -jar lasertag.jar
```

To make a version that doesn't need Java installed, use `jpackage`, which comes with the
JDK. It bundles a Java runtime, so the result is about 150 MB. Build the jar first (step 3
or 4), then put it in a folder by itself.

macOS / Linux:

```bash
mkdir -p dist-input && cp build/lasertag.jar dist-input/
```

```bash
jpackage --input dist-input --main-jar lasertag.jar --main-class lasertag.LaserTag --name LaserTag --type app-image --dest dist
```

Windows (PowerShell):

```powershell
New-Item -ItemType Directory -Force dist-input; Copy-Item build\lasertag.jar dist-input\
```

```powershell
jpackage --input dist-input --main-jar lasertag.jar --main-class lasertag.LaserTag --name LaserTag --type app-image --dest dist
```

This creates `dist/LaserTag` (`LaserTag.app` on macOS) with a launcher you can zip up and
share. `jpackage` builds for the system it runs on, so build on Windows for Windows players,
on macOS for Mac players, and so on. To make an installer instead, change `--type`:
`msi` or `exe` on Windows (needs the [WiX Toolset](https://wixtoolset.org)), `dmg` or
`pkg` on macOS, `deb` or `rpm` on Linux.

## 7. Troubleshooting

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
| The window is tiny or blurry | The game scales by whole numbers for crisp pixels. Make the window at least 960×720 for 2× scale. |
