#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../srcs/WebSocket.jar ]; then
  javac -cp ../srcs/WebSocket.jar WsServerTest.java
  javac -cp ../srcs/WebSocket.jar WsClientTest.java
  java -cp ../srcs/WebSocket.jar:. WsClientTest
  java -cp ../srcs/WebSocket.jar:. WsServerTest
  rm -f *.class
else
  echo First make the ../srcs/WebSocket.jar file.
fi
echo
echo Completed. Press any key...
read
