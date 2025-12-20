@echo off
set "JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-25-Full"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ========================================
echo   FxShield - EXE Packaging Script (FORCED FULL JDK)
echo ========================================
echo Using JDK at: %JAVA_HOME%
java --version
echo:

echo Checking for JavaFX modules...
if not exist "%JAVA_HOME%\jmods\javafx.base.jmod" (
    echo ERROR: The JDK at %JAVA_HOME% is NOT the 'Full' version.
    echo It is missing JavaFX modules.
    echo:
    echo Please download and install the EXACT 'Full' version from here:
    echo https://download.bell-sw.com/java/25+9/bellsoft-jdk25+9-windows-amd64-full.msi
    echo:
    echo After installing, make sure the folder contains 'javafx' files in the 'jmods' subfolder.
    pause
    exit /b
)
echo JavaFX modules found!
echo:

set APP_NAME=FxShield
set MAIN_CLASS=fxShield.Launcher
set JAR_FILE=SoftwareEngAQU.jar
set JAR_DIR=out\artifacts\SoftwareEngAQU_jar
set VERSION=1.0.0

echo ========================================
echo   FxShield - EXE Packaging Script
echo ========================================

echo [1/4] Verifying environment...
echo:
echo IMPORTANT: Make sure you have rebuilt the JAR artifact in your IDE 
echo Build - Build Artifacts - SoftwareEngAQU:jar - Rebuild 
echo before running this script!
echo:
where jpackage >nul 2>nul
if errorlevel 1 goto NO_JPACKAGE
goto CHECK_JAR

:NO_JPACKAGE
echo ERROR: jpackage not found in PATH. Please use JDK 14 or newer.
pause
exit /b

:CHECK_JAR
if not exist "%JAR_DIR%\%JAR_FILE%" goto NO_JAR
goto PREPARE

:NO_JAR
echo ERROR: JAR file not found at: %JAR_DIR%\%JAR_FILE%
echo Please build the JAR artifact in your IDE first - Build - Build Artifacts.
pause
exit /b

:PREPARE
echo [2/4] Preparing destination...
REM First, try to remove the temp_input if it exists
rd /s /q "temp_input" 2>nul
mkdir "temp_input"

REM Try to delete dist folder, ignore errors if it doesn't exist
rd /s /q "dist" 2>nul
REM If it still exists, it's likely locked. Try renaming it as a workaround.
if exist "dist" (
    ren "dist" "dist_old_%RANDOM%" 2>nul
)
mkdir "dist"

echo [2.5/4] Bundling libraries...
copy "%JAR_DIR%\%JAR_FILE%" "temp_input\" >nul
if errorlevel 1 (
    echo ERROR: Could not copy JAR file. 
    echo Please close any running FxShield app and try again.
    pause
    exit /b
)
copy "libs\*.jar" "temp_input\" >nul

echo [3/4] Checking for WiX Toolset...
set HAS_WIX=1
where candle >nul 2>nul
if errorlevel 1 set HAS_WIX=0

if %HAS_WIX% == 0 (
    echo:
    echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    echo WARNING: WiX Toolset not found in PATH.
    echo:
    echo To fix this and get a REAL installer:
    echo 1. Download WiX Toolset v3.11 - not v4/v5/v6 - from:
    echo    https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm
    echo 2. Install wix311.exe.
    echo 3. Add C:\Program Files x86\WiX Toolset v3.11\bin to your System PATH.
    echo 4. Restart your IDE or Terminal, then run this script again.
    echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    echo:
)

echo [4/4] Packaging...
set PKG_TYPE=exe
if %HAS_WIX% == 0 set PKG_TYPE=app-image

set UPGRADE_UUID=93c5fd00-1234-5678-9abc-def012345678

jpackage --name "%APP_NAME%" --app-version "%VERSION%" --input "temp_input" --main-jar "%JAR_FILE%" --main-class %MAIN_CLASS% --type %PKG_TYPE% --dest dist --win-dir-chooser --win-menu --win-shortcut --win-per-user-install --win-upgrade-uuid %UPGRADE_UUID% --description "Fx Shield - System Monitor and Optimizer" --vendor "AQU" --copyright "Copyright 2025 AQU" --about-url "https://github.com/SoftwareEngAQU" --add-modules javafx.controls,javafx.base,javafx.graphics,java.net.http,java.management,jdk.charsets --jlink-options "--bind-services" --java-options "--enable-native-access=ALL-UNNAMED --enable-native-access=javafx.graphics"

if exist "temp_input" rd /s /q "temp_input"

if errorlevel 1 goto FAIL
goto SUCCESS

:SUCCESS
echo:
echo ========================================
if %HAS_WIX% == 1 echo SUCCESS: Installer created in dist folder.
if %HAS_WIX% == 0 echo SUCCESS: App Image created in dist\%APP_NAME%.
echo ========================================
goto END

:FAIL
echo:
echo ERROR: jpackage failed.

:END
pause
