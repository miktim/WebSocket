/*
 * WebSocket Example2. MIT (c) 2025 miktim@mail.ru
 * Connecting to the echo server.
 */

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsStatus;

public class Example2 {
    static String serverURI = "wss://echo.websocket.org";
//    static String serverURI = "wss://127.0.0.1:8443"; // local echo server   
    static void log(Object obj) {
        System.out.println(obj);
    }

    public static void main(String[] args) {
        
        WsConnection.Handler handler = new WsConnection.Handler() {
            int msgCnt = 2;

            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                log(conn.getSSLSessionProtocol());
                try {
                    conn.send("Hi, Server!");
                } catch (WsError err) {
                }
                conn.close("Bye, Server!");
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                if(!msg.isText()) 
                    conn.close(WsStatus.INVALID_DATA, "Unexpected binary");
                else
                    try {
                        log(msg.asString());
                    } catch (WsError err) {
                    }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                log(status);
            }

            @Override
            public void onError(WsConnection conn, Throwable err) {
                log("Error: " + err);
            }
        };
        
        WebSocket webSocket = new WebSocket();
        try {
// for local echo server
//            webSocket.setKeyFile(new File("./localhost.jks"), "password");
            webSocket.connect(serverURI, handler);
        } catch (Exception err) {
            log("Connection creation error: " + err);
        }
    }
/* console output like this:
TLSv1.3
Request served by e286e427f61668
Hi, Server!
WsStatus(1000,"Bye, Server!",clean,locally)
*/
}
