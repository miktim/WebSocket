#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../srcs/WebSocket.jar ]; then
#  java -cp ../srcs/WebSocket.jar WsServerTest.java # Java 11
#  java -cp ../srcs/WebSocket.jar WsClientTest.java # Java 11
  javac -cp ../srcs/WebSocket.jar WsServerTest.java
  java -cp ../srcs/WebSocket.jar:. WsServerTest
  javac -cp ../srcs/WebSocket.jar WsClientTest.java
  java -cp ../srcs/WebSocket.jar:. WsClientTest
  rm -f *.class 
else
  echo First make the ../srcs/WebSocket.jar file.
fi
