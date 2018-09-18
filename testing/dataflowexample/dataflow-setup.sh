#!/bin/bash

WORKING_DIR=$(cd $(dirname "$0") && pwd)

JSR308=$(cd $WORKING_DIR/../../../ && pwd)

#default value is opprop. REPO_SITE may be set to other value for travis test purpose.
export REPO_SITE="${REPO_SITE:-opprop}"

## Fetching DLJC
if [ -d $JSR308/do-like-javac ] ; then
    (cd $JSR308/do-like-javac && git pull)
else
    (cd $JSR308 && git clone --depth 1 https://github.com/"$REPO_SITE"/do-like-javac.git)
fi

## Fetch Lingeling solver
LINGELING_ARCHIVE=http://fmv.jku.at/lingeling/lingeling-bcj-78ebb86-180517.tar.gz

if [ ! -f $JSR308/lingeling.tar.gz ] ; then
    (cd $JSR308 && wget -O lingeling.tar.gz $LINGELING_ARCHIVE)
fi

if [ ! -d $JSR308/lingeling ] ; then
    (cd $JSR308 && tar -xf lingeling.tar.gz && mv lingeling-* lingeling)
fi

if [ ! -f $JSR308/lingeling/lingeling ] || ! ($JSR308/lingeling/lingeling --version) ; then
    (cd $JSR308/lingeling && ./configure.sh && make --silent && ./lingeling --version)
fi

## Build libs for test
(cd $WORKING_DIR && ant compile-libs)
