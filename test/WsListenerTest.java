/*
 * WebSocket listener test. MIT (c) 2020 miktim@mail.ru
 * Created: 2020-03-09
 */

import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import org.samples.java.websocket.WsConnection;
import org.samples.java.websocket.WsHandler;
import org.samples.java.websocket.WsListener;
import org.samples.java.websocket.WebSocket;

public class WsListenerTest {

    public static final int MAX_MESSAGE_LENGTH = 500000;

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
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
                        + " Closure status:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Handle ERROR: "
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " Closure status:"
                        + (con != null ? con.getClosureCode() : null));
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
//                        System.out.println("MsgLen:" + s.length());
                        if (s.getBytes().length > MAX_MESSAGE_LENGTH) {
                            con.close(WsConnection.MESSAGE_TOO_BIG, "");
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
        final WebSocket webSocket = 
                new WebSocket(InetAddress.getByName("localhost"));
        webSocket.setConnectionSoTimeout(1000, true); // ping
        final WsListener listener = webSocket.listen(8080, listenerHandler);
//        WebSocket.setKeystore(new File(path, "localhost.jks"), "password");// java 1.8
//        WebSocket.setKeystore(new File(path, "testkeys"), "passphrase");// java 1.7
        int stopTimeout = 30000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                listener.close();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nWebSocket Listener test"
                + "\r\nIncoming maxMessageLength = " + MAX_MESSAGE_LENGTH
                + "\r\nListener will be closed after "
                + (stopTimeout / 1000) + " seconds"
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
