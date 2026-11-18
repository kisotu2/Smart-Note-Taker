#!/usr/bin/env sh
# Lightweight fallback launcher: use an installed Gradle if the standard wrapper JAR is not present.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle -p "$APP_HOME" "$@"
fi
echo "Gradle wrapper JAR is not included. Open the project in Android Studio to sync, or install Gradle and run: gradle wrapper --gradle-version 9.6.0" >&2
exit 1
