#!/bin/bash

cd $(dirname $0)
java -Xmx6G -Dfile.encoding=UTF8 -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json-simple-1.1.1.jar MorphoCRF -train 