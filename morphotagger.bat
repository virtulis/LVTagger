@echo off
cd /d %~dp0
java -Xmx1G -Dfile.encoding=UTF8 -cp dist\CRF.jar;dist\morphology.jar;lib\json-simple-1.1.1.jar lv.lumii.morphotagger.MorphoPipe %*