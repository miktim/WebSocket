#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ./WebSocket.jar ]; then
  javac -cp ./WebSocket.jar WsListenerTest.java
  javac -cp ./WebSocket.jar WssConnectionTest.java
  javac -cp ./WebSocket.jar WssClientTest.java
  java -cp ./WebSocket.jar:. WssConnectionTest
  java -cp ./WebSocket.jar:. WssClientTest
  java -cp ./WebSocket.jar:. WsListenerTest
  rm -f *.class
else
  echo First make the ./WebSocket.jar file.
fi
echo
echo Completed. Press Enter to exit...
read
