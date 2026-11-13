#!/bin/sh
# Minimal Gradle wrapper launcher. Requires gradle/wrapper/gradle-wrapper.jar
# (generate it once with: gradle wrapper --gradle-version 8.10.2)
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "gradle-wrapper.jar missing. Run: gradle wrapper --gradle-version 8.10.2" >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
