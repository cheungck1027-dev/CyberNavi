#!/bin/sh
#
# CyberNavi gradlew - compatible with Gradle 8.14.3
# Uses CLASSPATH env var (not -classpath flag) to avoid ProcessHandle arg misinterpretation
#
# Copyright 2015-2021 the original author or authors.
# Licensed under the Apache License, Version 2.0

##############################################################################
# Resolve APP_HOME
##############################################################################
APP_NAME="CyberNavi"
APP_BASE_NAME=`basename "$0"`

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

##############################################################################
# Locate Java
##############################################################################
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME not set and java not found in PATH."
fi

##############################################################################
# Set CLASSPATH - use env var to avoid -classpath flag appearing in process args
# (Gradle 8.14.3 reads process args via ProcessHandle and misinterprets -classpath)
##############################################################################
export CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

##############################################################################
# JVM options
##############################################################################
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect JVM opts: DEFAULT_JVM_OPTS, JAVA_OPTS, GRADLE_OPTS
JVM_OPTS=""
for opt in $DEFAULT_JVM_OPTS; do
    JVM_OPTS="$JVM_OPTS $opt"
done
if [ -n "$JAVA_OPTS" ]; then
    JVM_OPTS="$JVM_OPTS $JAVA_OPTS"
fi
if [ -n "$GRADLE_OPTS" ]; then
    JVM_OPTS="$JVM_OPTS $GRADLE_OPTS"
fi

##############################################################################
# Execute
##############################################################################
exec "$JAVACMD" \
  $JVM_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
