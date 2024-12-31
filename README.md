## Java SE 6+/Android 4.1+ WebSocket client and server package, MIT (c) 2020-2024 @miktim<br/>
### Release notes:

\- small and easy RFC 6455 compliant package ( [https://datatracker.ietf.org/doc/html/rfc6455/](https://datatracker.ietf.org/doc/html/rfc6455/) );  
\- Java SE 6+/Android 4.1+ compatible (see WebSocket-Android-Test repo:  
  [https://github.com/miktim/WebSocket-Android-Test](https://github.com/miktim/WebSocket-Android-Test) ).  
\- no external dependencies;  
\- supported WebSocket version: 13;  
\- WebSocket extensions are not supported;  
\- supports cleartext/TLS connections (without tunneling);  
\- supports Internationalized Domain Names (IDNs);  
\- incoming WebSocket messages are represented by input streams.    

The ./dist/websocket-... jar file was built with debug info using JDK 8 for the target JRE 6.  

Short documentation in ./README file.  

#### Example: creating and running a Java server for TLS connections:  


```  
  //... some code
  // create server side connection handler
  WsConnection.EventHandler handler = new WsConnection.EventHandler() {
    @Override
    onOpen(WsConnection conn, String subProtocol) {
      conn.send("Hello, Client!");
      // ... do something
    }
    @Override
    onMessage(WsConnection conn, InputStream is, boolean isText) {
      if (isText) { // is UTF-8 text stream?
        // ... UTF-8 text do something
      } else {
        // ... binary data do something
      };
    }
    @Override
    onError(WsConnection conn, Throwable e) {
      // ... do something
    }
    @Override
    onClose(WsConnection conn, WsStatus status) {
      // ... do something
    }
  };
  try {
    // create WebSocket factory
    WebSocket webSocket = new WebSocket();
    // set Java style key store file
    webSocket.setKeyFile(new File("myKeyFile.jks"), "password");
    // create and start server with "dumb" server handler and default connection parameters
    WsServer server = webSocket.SecureServer(8443, handler, new WsParameters()).launch();	
  } (Exception e) {
    e.printStackTrace();
  }
  //... some code
```  

#### Example: creating a TLS connection for an Android client:  

```
  //... some code
  // create client connection handler
  WsConnection.EventHandler handler = new WsConnection.EventHandler() {
    @Override
    onOpen(WsConnection conn, String subProtocol) {
      conn.send("Hello, Server!");
      // ... do something
    }
    @Override
    onMessage(WsConnection conn, InputStream is, boolean isText) {
      if (isText) { // is UTF-8 text stream?
        // ... is UTF-8 text. Do something.
      } else {
        // ... is binary data. Do something.
      };
    }
    @Override
    onError(WsConnection conn, Throwable e) {
      // ... do something
    }
    @Override
    onClose(WsConnection conn, WsStatus status) {
      // ... do something
    }
  };
  try {
    // create WebSocket factory
    WebSocket webSocket = new WebSocket();
    // if needed, set the Android styled trusted key store file
    webSocket.setKeyFile(new File("myKeyFile.bks"), "password");
    // create client TLS connection with default parameters
    WsConnection conn = webSocket.connect("wss://hostname:8443", handler, new WsParameters());	
  } (Exception e) {
    e.printStackTrace();
  }
  //... some code

```

Good luck!