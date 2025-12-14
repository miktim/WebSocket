/*
 * WebSocket Example1. MIT (c) 2025 miktim@mail.ru
 * Connecting to the echo server.
 */

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsStatus;

public class Example2 {
    static String serverURI = "wss://echo.websocket.org";
    static void log(Object obj) {
        System.out.println(obj);
    }

    public static void main(String[] args) {
        
        WsConnection.Handler handler = new WsConnection.Handler() {
            int msgCnt = 2;

            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                log(conn.getSSLSessionProtocol());
                conn.send("Hi, Server!");
                conn.close();
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                log(msg.toString());
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
            webSocket.connect(serverURI, handler);
        } catch (Exception e) {
            log(e);
        }
    }
/* console output like this:
TLSv1.3
Request served by e286e427f61668
Hi, Server!
WsStatus(1000,"Bye, Server!",clean,locally)
*/
}
