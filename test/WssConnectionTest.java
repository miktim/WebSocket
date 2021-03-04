/*
 * Secure WsConnection test. MIT (c) 2020-2021 miktim@mail.ru
 */
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsParameters;

public class WssConnectionTest {

    static final int MAX_MESSAGE_LENGTH = 1000000; //~1MB
    static final int WEBSOCKET_SHUTDOWN_TIMEOUT = 10000; //10sec 
    static final String REMOTE_HOST = "localhost";//"192.168.1.106";

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        int port = 8443;
        String remoteAddr = REMOTE_HOST + ":" + port;

        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Listener: handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost()
                        + " SecureProtocol: " + con.getSSLSessionProtocol());
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
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Listener: handle ERROR: "
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " " + (con != null ? con.getStatus() : null));
//                e.printStackTrace();
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
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client: handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " " + con.getStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Client: handle TEXT: " + s);
                    } else {
                        if (s.getBytes("utf-8").length < (MAX_MESSAGE_LENGTH / 2)) {
                            con.send(s + s);
                        } else {
                            con.send(new ByteArrayInputStream(s.getBytes("utf-8")), true);
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
        WsParameters wsp = new WsParameters();
        wsp.setConnectionSoTimeout(1000, true); // ping
        wsp.setMaxMessageLength(MAX_MESSAGE_LENGTH, false);
        webSocket.setWsParameters(wsp);
// Both sides must use the same self-signed certificate
        /* Android
        WebSocket.setKeyStore(getCacheDir() + "/testkeys"), "passphrase");
        WebSocket.setTrustStore(getCacheDir() + "/testkeys"), "passphrase");
         */
// /* Desktop
        WebSocket.setKeyStore((new File(path, "testkeys")).getCanonicalPath(), "passphrase");
        WebSocket.setTrustStore((new File(path, "testkeys")).getCanonicalPath(), "passphrase");
// */
        webSocket.listenSafely(port, listenerHandler);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                webSocket.closeAll();
                timer.cancel();
            }
        }, WEBSOCKET_SHUTDOWN_TIMEOUT);
        System.out.println("\r\nWssConnection (v"
                + WsConnection.VERSION + ") test"
                + "\r\nClient try to connect to " + remoteAddr
                + "\r\nWebSocket will be closed after "
                + (WEBSOCKET_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        webSocket.connect("wss://" + remoteAddr + "/test", clientHandler);        
        webSocket.connect("wss://" + remoteAddr + "/test", clientHandler);        
        webSocket.connect("wss://" + remoteAddr + "/test", clientHandler); 
// Connection below must fail (unsrcure connection to secure listener)
        webSocket.connect("ws://" + remoteAddr + "/must_fail", clientHandler);        
    }

}
