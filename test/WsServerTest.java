/*
 * WebSocket server test MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */

import java.io.ByteArrayInputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.IOException;
import org.samples.java.websocket.WsConnection;
import org.samples.java.websocket.WsHandler;
import org.samples.java.websocket.WsServer;

public class WsServerTest {

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler serverHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                if (!con.getPath().startsWith("/test")) {
                    con.close(WsConnection.POLICY_VIOLATION);
                    return;
                }
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Handle ERROR: " 
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " Closure status:"
                        + (con != null ? con.getClosureStatus() : null));
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String testPath = con.getPath();
                    if (testPath.endsWith("2")) { // check handler closure
                        if (s.length() > 128) {
                            con.close(WsConnection.NORMAL_CLOSURE);
                        } else {
                            con.send(s + s);
                        }

                    } else if (testPath.endsWith("3")) { // check message too big
//                        System.out.println("MsgLen:" + s.length());
//                        con.send(s+s);
                        con.streamText(
                                new ByteArrayInputStream((s + s).getBytes("utf-8")));
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

        final WsServer wsServer = new WsServer(8080, serverHandler);
        wsServer.setConnectionSoTimeout(1000, true); // ping
        wsServer.setMaxMessageLength(200000);
//        WsConnection.setKeyFile(new File(path, "localhost.jks"), "password");// java 1.8
//        WsConnection.setKeyFile(new File(path, "testkeys"), "passphrase");// java 1.7
        int stopTimeout = 30000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nTest WebSocket server"
                +"\r\nmaxMessageLength = " + wsServer.getMaxMessageLength()
                +"\r\nServer will be stopped after "
                + (stopTimeout / 1000) + " seconds");
        wsServer.start();
        /* Android
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("file:///android_asset/WsServerTest.html"));
        startActivity(browserIntent);
         */
// /* Desktop 
        java.awt.Desktop.getDesktop().open(new File(path, "WsServerTest.html"));
// */

    }

}
