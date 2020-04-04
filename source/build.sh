#!/bin/bash

echo $(javac --version)
jname=WsServer
cpath=./org/samples/java/wsserver
if [ ! -d ${cpath} ]
  then mkdir -p ${cpath}
  else rm -f ${cpath}/*.*
fi
javac -Xstdout ./compile.log -Xlint:unchecked -cp ${cpath} -d ./ \
  WsHandler.java WsConnection.java WsServer.java WssServer.java
if [ $? -eq 0 ] ; then
  jar cvf ./${jname}.jar ${cpath}/*.class
#  javadoc -d ./${jname}Doc -nodeprecated -use package-info.java \
#  WsHandler.java WsConnection.java WsServer.java WssServer.java
fi
rm -f -r ./org
more < ./compile.log