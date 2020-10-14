#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../srcs/WebSocket.jar ]; then
  javac -cp ../srcs/WebSocket.jar WsInputStreamTest.java
  java -cp ../srcs/WebSocket.jar:. WsInputStreamTest
  rm -f *.class
else
  echo First make the ../srcs/WebSocket.jar file.
fi
echo
echo Completed. Press any key...
read
