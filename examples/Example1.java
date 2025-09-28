/*
 * WebSocket Example1. MIT (c) 2025 miktim@mail.ru
 * Sample echo server.
 */
package temp;

import java.io.IOException;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsException;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

public class Example1 {
    static final int PORT = 8443;

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
                String msg = "Session open: " + getSession();
                log(msg);
                conn.send(msg);
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                try {
                    conn.send(msg, msg.isText()); // echo message
                } catch (IOException ex) {
                    log(String.format(
                            "Send error: %d %s", getSession(), ex.toString()));
                }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                log(String.format("Session closed: %d (%d)", getSession(), status.code));
            }
        };
        WebSocket webSocket = new WebSocket();
// register your keystore file       
        WebSocket.setKeyStore("./keystore.jks", "passphrase");
        WsServer echoServer = null;
        try {
            echoServer = webSocket.startSecureServer(PORT, handler);
            log("Echo Server listening port: " + PORT);
        } catch (WsException ex) {
            webSocket.closeAll("Echo Server crashed");
            log(ex.getCause());
        }
    }
}
