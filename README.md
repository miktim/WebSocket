## Java SE 6+/Android 4.1+ WebSocket client and server package, MIT (c) 2020-2025 @miktim<br/>
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

The latest standalone jar and JavaDoc [here](./dist).  
The package was built with debug info using openJDK 1.8 for the target JRE 1.6.  

#### Example1: local echo server for TLS connections:  

```  
public class Example1 {

  int port = 8443; // listening port

  static void log(Object obj) {
    System.out.println(obj);
  }
  
  public static void main(String[] args) {
  
// define server-side connections handler  
    WsConnection.Handler handler = new WsConnection.Handler() {
      int getSession() {
        return Thread.currentThread().hashCode();
      }

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        String msg = "Session open: " + getSession();
        log(msg);
        conn.send(msg);
      }

      @Override
      public void onMessage(WsConnection conn, WsMessage msg) {
        try {
// echoing the WebSocket message as an UTF-8 encoded InputStream        
          conn.send(msg, msg.isText(); 
        } catch (IOException ex) {
// WebSocket message reading errors lead to connection closure.
// No action.
        }
      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
        log(String.format(
          "Session %d closed with status code (%d)",
             getSession(), status.code));
      }
    };
    
    WebSocket webSocket = new WebSocket();
// register your key store file      
    WebSocket.setKeyStore("./keystore.jks", "passphrase");
    try {
      WsServer echoServer = webSocket.startSecureServer(port, handler);
      log("Echo Server listening port: " + port);
    } catch (Exception ex) {
      webSocket.closeAll("Echo Server crashed");
      log(ex);
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
        if(msg.isText()) 
          log(msg.toString());
      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
        log(status);
      }
    };

    WebSocket webSocket = new WebSocket();
    try {
// use the default Java Trust Store    
      webSocket.connect(serverUri, handler);
    } catch (Exception ex) {
      log(ex);
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