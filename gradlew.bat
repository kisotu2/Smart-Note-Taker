@echo off
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle -p "%APP_HOME%" %*
  exit /b %ERRORLEVEL%
)
echo Gradle wrapper JAR is not included. Open the project in Android Studio or install Gradle and run: gradle wrapper --gradle-version 9.6.0
exit /b 1
