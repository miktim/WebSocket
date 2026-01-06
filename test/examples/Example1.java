/*
 * WebSocket Example1. MIT (c) 2025 miktim@mail.ru
 * Sample echo server.
 */

import java.io.File;
import java.io.IOException;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsStatus;

public class Example1 {

    static final int PORT = 8443;

    static void log(Object obj) {
        System.out.println(obj);
    }

    public static void main(String[] args) {
        WsConnection.Handler handler = new WsConnection.Handler() {

            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                String msg = "Connection open: " + conn.hashCode();
                log(msg);
                try {
                    conn.send(msg);
                } catch (WsError err) {
                }
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                try {
                    conn.send(msg, msg.isText()); // echo message
                } catch (IOException ex) {
                    log(String.format(
                            "Send error: %d %s",
                            conn.hashCode(), ex.toString()));
                }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                log(String.format("Connection closed: %d (%d)",
                        conn.hashCode(), status.code));
            }

            @Override
            public void onError(WsConnection conn, Throwable err) {
                log("Error: " + err);
            }
        };
        
        WebSocket webSocket = new WebSocket();
        String key = "./localhost.jks";
        String pwd = "password";
// register server keystore file       
        WebSocket.setKeyStore(key, pwd);
        try {
            webSocket.startSecureServer(PORT, handler);
            log("Echo Server listening port: " + PORT);
        } catch (WsError err) {
            log("Server creation error: "+ err.getCause());
        }

// Test connection

// define client connection event handler
    WsConnection.Handler connHandler = new WsConnection.Handler() {

      @Override
      public void onOpen(WsConnection conn, String subProtocol) {
        log("Client onOpen: " + conn.getSSLSessionProtocol());
        conn.send("Hi, Server!");
        conn.close("Bye, Server!");
      }

      @Override
      public void onMessage(WsConnection conn, WsMessage msg) {
        if(msg.isText()) 
          log("Client onMessage: " + msg.asString());
        else 
          conn.close(WsStatus.INVALID_DATA, "Unexpected binary");
      }
      
      @Override
      public void onError(WsConnection conn, Throwable err) {
// Do nothing. Log error in onClose method.      
      }

      @Override
      public void onClose(WsConnection conn, WsStatus status) {
        log("Client onClose: " + status);
      }
    };
    
    try {
// register client trust store key file
      webSocket.setKeyFile(new File(key), pwd);
      webSocket.connect("wss://localhost:" + PORT, connHandler);
    } catch (Exception err) {
      log("Connection creation error: " + err.getCause());
    }
  }

}
