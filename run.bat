@echo off
rem Builds and runs Laser Tag using only the JDK - no Maven required.
rem Usage: run.bat            start the game
rem        run.bat --server   run a headless dedicated server (see README)
setlocal
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac --release 17 -d build\classes @build\sources.txt || exit /b 1
jar --create --file build\lasertag.jar --main-class lasertag.LaserTag -C build\classes . || exit /b 1
java -jar build\lasertag.jar %*
