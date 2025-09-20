## Java SE 6+/Android 4.1+ WebSocket client and server package, MIT (c) 2020-2025 @miktim<br/>
### Release notes:

\- RFC 6455 compliant package ( [https://datatracker.ietf.org/doc/html/rfc6455/](https://datatracker.ietf.org/doc/html/rfc6455/) );  
\- no external dependencies;  
\- Java SE 6+/Android 4.1+ compatible (see WebSocket-Android-Test repo:  
  [https://github.com/miktim/WebSocket-Android-Test](https://github.com/miktim/WebSocket-Android-Test) ).  
\- WebSocket extensions ( Per-Message Deflate, ... ) are not supported;  
\- supported WebSocket version: 13;  
\- supports cleartext/TLS connections;  
\- client supports Internationalized Domain Names (IDNs);  
\- stream-based messaging.    

The [/dist/websocket-...](./dist) jar file was built with debug info using JDK 1.8 for the target JRE 1.6.  

Overview of the package in the file [./README.txt](./README.txt)  

#### Example1: local echo server for TLS connections:  

```  
public class Example1 {

  static void log(Object obj) {
    System.out.println(obj);
  }
  
  public static void main(String[] args) {
  
    WsConnection.Handler handler = new WsConnection.Handler() {
      int getSession() {
       return Thread.currentThread().hashCode();
      }

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        try {
          String msg = "Session open: " + getSession();
          log(msg);
          conn.send(msg);
        } catch (IOException ex) {
          log("Send error: " + getSession() + ex);
        }
      }

      @Override
      public void onMessage(WsConnection conn, InputStream is, boolean isText) {
        try {
          conn.send(is, isText); // echo message
        } catch (IOException ex) {
          log(String.format(
            "Send error: %d %s", getSession(), ex.toString()));
        }
      }

      @Override
      public void onError(WsConnection conn, Throwable ex) {
        log(String.format(
          "Handler error: %d %s", getSession(), ex.toString() ));
      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
        log(String.format(
          "Session closed: %d (%d)", getSession(), status.code));
      }
    };
    
    WebSocket webSocket = new WebSocket();
// register your key store file       
    WebSocket.setKeyStore("keystore.jks", "passphrase");
    int port = 8443;
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

    WsConnection.Handler handler = new WsConnection.Handler() {

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        log(conn.getSSLSessionProtocol());
          try {
            conn.send("Hi, Server!");
            conn.close("Bye, Server!");
          } catch (IOException ex) {
            log(ex);
          }
      }

      @Override
      public void onMessage(WsConnection conn, InputStream is, boolean isText) {
        try {
          log(conn.toString(is);
        } catch (IOException ex) {
          log(ex);
          conn.close(WsStatus.UNSUPPORTED_DATA, ex.toString());
        }
      }

      @Override
      public void onError(WsConnection conn, Throwable ex) {
        log(ex);
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