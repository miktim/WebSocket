/*
 * WebSocket Example1. MIT (c) 2025 miktim@mail.ru
 * Connecting to the echo server.
 */

import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsStatus;

public class Example2 {

    static void log(Object obj) {
        System.out.println(obj);
    }

    public static void main(String[] args) {
        
        WsConnection.Handler handler = new WsConnection.Handler() {

            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                log(conn.getSSLSessionProtocol());
                conn.send("Hi, Server!");
                conn.close("Bye, Server!");
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage is) {
                     log(conn.toString());
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                log(status);
            }
        };
        
        WebSocket webSocket = new WebSocket();
        try {
            webSocket.connect("ws://echo.websocket.org:443", handler);
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
