#!/bin/bash

echo $(javac -version)
jname=WebSocket
cpath=./org/samples/java/websocket
if [ ! -d ${cpath} ]
  then mkdir -p ${cpath}
  else rm -f ${cpath}/*.*
fi
javac -Xstdout ./compile.log -Xlint:unchecked -cp ${cpath} -d ./ \
  WsHandler.java Headers.java WsConnection.java WsServer.java WssServer.java
if [ $? -eq 0 ] ; then
  jar cvf ./${jname}.jar ${cpath}/*.class
#  javadoc -d ./${jname}Doc -nodeprecated -use package-info.java \
#  WsHandler.java WsConnection.java WsServer.java WssServer.java
fi
rm -f -r ./org
#more < ./compile.log
cat compile.log
echo
echo Completed. Press any key...
read