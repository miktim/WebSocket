#!/bin/bash

if [ -f ../srcs/WsServer.jar ]; then
  java -cp ../srcs/WsServer.jar WsServerTest.java
else
  echo First build the ../srcs/WsServer.jar file.
fi