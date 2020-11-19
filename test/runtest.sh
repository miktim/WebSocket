#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ./WebSocket.jar ]; then
  javac -cp ./WebSocket.jar WsListenerTest.java
  javac -cp ./WebSocket.jar WsConnectionTest.java
  java -cp ./WebSocket.jar:. WsConnectionTest
  java -cp ./WebSocket.jar:. WsListenerTest
  rm -f *.class
else
  echo First make the ./WebSocket.jar file.
fi
echo
echo Completed. Press any key...
read
