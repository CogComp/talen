#!/usr/bin/env bash
CP="./config/:./target/classes/:./target/dependency/*"

MEMORY="-Xmx30g"
OPTIONS="$MEMORY -Xss40m -ea -cp $CP"
PACKAGE_PREFIX="edu.illinois.cs.cogcomp"

#MAIN="$PACKAGE_PREFIX.lorelei.AddTab"
MAIN="$PACKAGE_PREFIX.lorelei.kb.GeonamesLoader"
time nice java $OPTIONS $MAIN $*
#time mvn exec:java -Dexec.mainClass=$MAIN -Dexec.args="$*"
