#!/bin/bash

if [ -f ../srcs/WebSocket.jar ]; then
  java -cp ../srcs/WebSocket.jar WsServerTest.java
  java -cp ../srcs/WebSocket.jar WsClientTest.java
else
  echo First build the ../srcs/WebSocket.jar file.
fi