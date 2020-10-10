#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../srcs/WebSocket.jar ]; then
  javac -cp ../srcs/WebSocket.jar WsListenerTest.java
  javac -cp ../srcs/WebSocket.jar WsConnectionTest.java
  java -cp ../srcs/WebSocket.jar:. WsConnectionTest
  java -cp ../srcs/WebSocket.jar:. WsListenerTest
  rm -f *.class
else
  echo First make the ../srcs/WebSocket.jar file.
fi
echo
echo Completed. Press any key...
read
