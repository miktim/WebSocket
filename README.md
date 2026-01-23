## Java SE 1.6+/Android 4.0+ WebSocket client and server package, MIT (c) 2020-2026 @miktim
### Release notes:

\- RFC 6455 compliant package ( [https://datatracker.ietf.org/doc/html/rfc6455/](https://datatracker.ietf.org/doc/html/rfc6455/) );  
\- no external dependencies;  
\- Java SE 6+/Android 4.1+ compatible (see WebSocket-Android-Test repo:  
  [https://github.com/miktim/WebSocket-Android-Test](https://github.com/miktim/WebSocket-Android-Test) ).  
\- WebSocket extensions ( Per-Message Deflate, ... ) are not supported;  
\- supported WebSocket version: 13;  
\- supports insecure or TLS connections;  
\- client supports Internationalized Domain Names (IDNs);  
\- stream-based messaging.    

The latest standalone jar is here: [./dist](./dist).  
The package was built with debug info using openJDK 1.8 for the target JRE 1.6.  
The latest JavaDoc is in the [./docs](./docs) folder or [online](https://miktim.github.io/WebSocket/).  

#### Example1: local echo server for TLS connections:  

```  
public class Example1 {

  static final int PORT = 8443; // listening port

  static void log(Object obj) {
    System.out.println(obj);
  }
  
  public static void main(String[] args) {
  
// define server-side connections handler  
    WsConnection.Handler handler = new WsConnection.Handler() {

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        String msg = String.format("Connection %d open.",
          conn.hashCode());
        log(msg);
        conn.send(msg);
      }

      @Override
      public void onMessage(WsConnection conn, WsMessage msg) {
// WsMessage is an input stream of binary data or UTF-8 encoded text     
        try {      
// echoing the message as an stream  
          conn.send(msg, msg.isText()); 
        } catch (IOException e) {
// Do nothing. An error when reading/sending WsMessage means that 
// the connection is closed.
        }  
      }
      
      @Override
      public void onError(WsConnection conn, Throwable err) {

      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
 // logs the connection closure and the error that occurred
        log(String.format("Connection %d closed. %s",
             conn.hashCode(), status.toString()));
      }
    };
    
    WebSocket webSocket = new WebSocket();
    try {
// register your key store file      
      WebSocket.setKeyStore("./yourfile.jks", "password");
      webSocket.startSecureServer(PORT, handler);
      log("Echo Server listening port: " + PORT);
    } catch (WsError err) {
      log("Server creation error: " + err.getCause());
    }
  } 
}
```  

#### Example2: TLS connection to a public WebSocket echo server:  

```
public class Example2 {

  static String serverUri = "wss://echo.websocket.org";

  static void log(Object obj) {
    System.out.println(obj);
  }

  public static void main(String[] args) {

// define client connection event handler
    WsConnection.Handler handler = new WsConnection.Handler() {

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        log(conn.getSSLSessionProtocol());
        conn.send("Hi, Server!");
        conn.close("Bye, Server!");
      }

      @Override
      public void onMessage(WsConnection conn, WsMessage msg) {
// WsMessage is an input stream of binary data or UTF-8 encoded text     
        if(msg.isText())
// reads WsMessage as String and logs it        
          log(msg.asString());
        else 
          conn.close(WsStatus.INVALID_DATA, "Unexpected binary");
      }
      
      @Override
      public void onError(WsConnection conn, Throwable err) {
// Do nothing. Log error in onClose method.      
      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
        log(status);
      }
    };

    WebSocket webSocket = new WebSocket();
    try {
// use the default Java TrustStore for TLS connection   
      webSocket.connect(serverUri, handler);
    } catch (WsError err) {
      log("Connection creation error: " + err.getCause());
    }
  }
/* console output like this:
TLSv1.3
Request served by e286e427f61668
Hi, Server!
WsStatus(1000,"Bye, Server!",clean,locally)
*/
}

```

Good luck!