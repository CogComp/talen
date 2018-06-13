#!/bin/sh
# generate/update the binary files and dependencies

# Classpath
cpath="target/classes:target/dependency/*:config"

INPATH=$1
OUTPATH=$2

java -classpath  ${cpath} -Xmx16g io.github.mayhewsw.TextFileIndexer -infolder $INPATH -indexfolder $OUTPATH -test
