#!/bin/sh
exec java -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=2048m -Xmx2048M -Xss32M -Dfile.encoding=UTF8 -jar $( dirname $0 )/sbt-launch.jar "$@"

