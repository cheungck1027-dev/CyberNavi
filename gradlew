#!/bin/sh
#
# CyberNavi Gradle wrapper script
#
# Uses CLASSPATH env var instead of -classpath flag.
# This prevents Gradle 8.14.3 from misreading -classpath via ProcessHandle
# and interpreting it as -c (--settings-file) + lasspath.
#
# JVM opts are passed directly (not via DEFAULT_JVM_OPTS variable)
# to avoid shell quoting issues that cause ClassNotFoundException.

##############################################################################
# Resolve APP_HOME
##############################################################################
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
APP_HOME="`cd "`dirname "$PRG"`" && pwd -P`"

##############################################################################
# Locate Java
##############################################################################
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

##############################################################################
# CLASSPATH via env var (no -classpath flag → no ProcessHandle misread)
##############################################################################
export CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

##############################################################################
# Execute Gradle Wrapper
##############################################################################
exec "$JAVACMD" \
    -Xmx512m \
    -Xms64m \
    -Dorg.gradle.appname="$APP_HOME" \
    -Dfile.encoding=UTF-8 \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
