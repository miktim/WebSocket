/*
 * WebSocket WsListener test. MIT (c) 2020-2021 miktim@mail.ru
 * Created: 2020-03-09
 */

import java.io.ByteArrayInputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WsListener;
import org.miktim.websocket.WebSocket;

public class WsListenerTest {

    public static final int MAX_MESSAGE_LENGTH = 1000000;//~1MB
    public static final int LISTENER_SHUTDOWN_TIMEOUT = 30000;//30sec
    public static final String WEBSOCKET_SUBPROTOCOL = "chat,superChat";

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Handle OPEN: " + con.getPath()
                        + (con.getQuery() == null ? "" : "?" + con.getQuery())
                        + " Peer: " + con.getPeerHost()
                        + " Subprotocol:" + con.getAgreedSubprotocol());
                if (!con.getPath().startsWith("/test")) {
                    con.close(WsConnection.POLICY_VIOLATION, "path not found");
                    return;
                }
                try {
                    con.send("Hello Browser!");
                } catch (IOException e) {
                    System.out.println("Handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Handle CLOSE: " + con.getPath()
                        + " Close code:" + con.getCloseCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Handle ERROR: "
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " Close code:"
                        + (con != null ? con.getCloseCode() : null));
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String testPath = con.getPath();
                    if (testPath.endsWith("2")) { // check handler closure
                        if (s.length() > 128) {
                            con.close(WsConnection.NORMAL_CLOSURE, "");
                        } else {
                            con.send(s + s);
                        }

                    } else if (testPath.endsWith("3")) { // check message too big
//                        System.out.println("MsgLen:" + s.getBytes("utf-8").length);
                        if (s.length() > 0xFFFF) {
                            con.send(new ByteArrayInputStream(
                                    (s + s).getBytes("utf-8")), true);
                        } else {
                            con.send(s + s);
                        }
                    } else if (testPath.endsWith("4")) { // ping, wait listener shutdown
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    System.out.println("Handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };

        final WebSocket webSocket
                = new WebSocket(InetAddress.getByName("localhost"));
        webSocket.setConnectionSoTimeout(1000, true); // ping
        webSocket.setMaxMessageLength(MAX_MESSAGE_LENGTH, false);
        webSocket.setSubprotocol(WEBSOCKET_SUBPROTOCOL);
        final WsListener listener = webSocket.listen(8080, listenerHandler);
//        WebSocket.setTrustStore((new File(path, "localhost.jks")).getCanonicalPath(), "password");// java 1.8
//        WebSocket.setTrustStore((new File(path, "testkeys")).getCanonicalPath(), "passphrase");// java 1.7

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                listener.close();
                timer.cancel();
            }
        }, LISTENER_SHUTDOWN_TIMEOUT);
        System.out.println("\r\nWebSocket Listener test"
                + "\r\nIncoming maxMessageLength = " + MAX_MESSAGE_LENGTH
                + "\r\nWebSocket Subprotocol = " + WEBSOCKET_SUBPROTOCOL
                + "\r\nListener will be closed after "
                + (LISTENER_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        /* Android
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("file:///android_asset/WsListenerTest.html"));
        startActivity(browserIntent);
         */
// /* Desktop 
        java.awt.Desktop.getDesktop().open(new File(path, "WsListenerTest.html"));
// */
    }

}
