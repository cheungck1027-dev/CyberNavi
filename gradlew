#!/bin/sh
# CyberNavi gradlew - uses CLASSPATH env var, no -classpath flag
# Avoids Gradle 8.14.3 ProcessHandle bug that misreads -classpath as --settings-file

# Resolve directory of this script
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# Locate java executable
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

# Set CLASSPATH as env var - avoids -classpath flag appearing in process args
export CLASSPATH="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"

# Launch GradleWrapperMain with all user args passed through
exec "$JAVACMD" -Xmx512m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -Dfile.encoding=UTF-8 \
  org.gradle.wrapper.GradleWrapperMain "$@"
