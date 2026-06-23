#!/usr/bin/env sh
APP_PATH="${0}"
while [ -h "${APP_PATH}" ] ; do
  ls=`ls -ld "${APP_PATH}"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then APP_PATH="$link"
  else APP_PATH="`dirname "${APP_PATH}"`/${link}"; fi
done
APP_HOME="`dirname "${APP_PATH}"`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
DEFAULT_JVM_OPTS=""
exec java $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
