#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ./WebSocket.jar ]; then
  javac -cp ./WebSocket.jar WsServerTest.java
  javac -cp ./WebSocket.jar WsConnectionTest.java
  javac -cp ./WebSocket.jar WssClientTest.java
  javac -Xlint:deprecation -cp ./WebSocket.jar WsStressTest.java
  java -cp ./WebSocket.jar:. WsConnectionTest
  java -cp ./WebSocket.jar:. WssClientTest
  java -cp ./WebSocket.jar:. WsServerTest
  java -cp ./WebSocket.jar:. WsStressTest
  rm -f *.class
else
  echo First make the ./WebSocket.jar file.
  echo or copy here /dist/websocket-x.x.x.jar as WebSocket.jar
fi
echo
echo Completed. Press Enter to exit...
read
