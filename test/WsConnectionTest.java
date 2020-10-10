/*
 * Secure WsConnection test. MIT (c) 2020 miktim@mail.ru
 */
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.samples.java.websocket.WsConnection;
import org.samples.java.websocket.WsHandler;
import org.samples.java.websocket.WebSocket;

public class WsConnectionTest {

    static final int MAX_MESSAGE_LENGTH = 500000;

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }

        String remoteHost = "localhost";
        int port = 8443;
        String remoteAddr = remoteHost + ":" + port;

        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Listener: handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Listener: handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Listener: handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Listener: handle ERROR: "
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " Closure status:"
                        + (con != null ? con.getClosureCode() : null));
                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Listener: handle TEXT: " + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    System.out.println("Listener: handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Listener: handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };
        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Client: handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Listener!");
                } catch (IOException e) {
                    System.out.println("Client: handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Client: handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client: handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure status:" + con.getClosureCode());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Client: handle TEXT: " + s);
                    } else {
                        if (s.getBytes("utf-8").length < MAX_MESSAGE_LENGTH) {
                            con.send(s + s);
                        } else {
                            con.send(s);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Client: handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Client: handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };

        final WebSocket webSocket = new WebSocket();
        webSocket.setConnectionSoTimeout(1000, true); // ping
        /* Android       
        WebSocket.setKeystore(new File(getCacheDir(),"testkeys"), "passphrase");
         */
// /* Desktop       
//        WebSocket.setKeystore(new File(path, "localhost.jks"), "password"); // from java 1.8
        WebSocket.setKeystore(new File(path, "testkeys"), "passphrase");
// */
        webSocket.listenSafely(port, listenerHandler);
        int stopTimeout = 10000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                webSocket.closeAll();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nSecure WsConnection test ("
                + WsConnection.VERSION + ")"
                + "\r\nClient connects to " + remoteAddr
                + "\r\nListener will be closed after "
                + (stopTimeout / 1000) + " seconds"
                + "\r\n");
        webSocket.connect("wss://" + remoteAddr + "/test", clientHandler);
    }

}
