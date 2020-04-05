#!/bin/bash

if [ ! -f ../srcs/WsServer.jar ]; then
  echo First build a WsServer.jar file.
else
java -cp ../srcs/WsServer.jar WsServerTest.java
fi