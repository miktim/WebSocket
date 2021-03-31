/*
 * Secure WsConnection test. MIT (c) 2020-2021 miktim@mail.ru
 */
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsListener;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WssConnectionTest {

    static final int MAX_MESSAGE_LENGTH = 1000000; //~1MB
    static final int WEBSOCKET_SHUTDOWN_TIMEOUT = 10000; //10sec 
    static final String REMOTE_HOST = "localhost";//"192.168.1.106";

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    static String makePath(WsConnection con) {
        return con.getPath()
                + (con.getQuery() == null ? "" : "?" + con.getQuery());
    }

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        int port = 8443;
        String remoteAddr = REMOTE_HOST + ":" + port;

        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log("Listener" + con.getId() + " onOPEN: " + makePath(con)
                        + " Peer: " + con.getPeerHost()
                        + " SecureProtocol: " + con.getSSLSessionProtocol());
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    ws_log("Listener onOPEN: " + makePath(con)
                            + " send error: " + e + " " + con.getStatus());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log("Listener" + con.getId() + " onCLOSE: " + makePath(con)
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                if (con == null) {
                    ws_log("Listener CRASHED! " + e);
                    e.printStackTrace();
                } else {
                    ws_log("Listener" + con.getId() + " onERROR: "
                            + makePath(con) + " " + e + " " + con.getStatus());
//                e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WsConnection con, InputStream is, boolean isText) {
                byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
                int messageLen;
                try {
                    messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                    if (is.read() != -1) {
                        con.close(WsStatus.MESSAGE_TOO_BIG, "Message too big");
                    } else if (isText) {
                        onMessage(con, new String(messageBuffer, 0, messageLen, "UTF-8"));
                    } else {
                        onMessage(con, Arrays.copyOfRange(messageBuffer, 0, messageLen));
                    }
                } catch (Exception e) {
                    ws_log("Listener" + con.getId() + " onMESSAGE: " + con.getPath()
                            + " read error: " + e);
                }
            }

//            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        ws_log("Listener" + con.getId() + " onTEXT: " + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    ws_log("Listener" + con.getId() + " onTEXT: send error: "
                            + e + " " + con.getStatus());
                }
            }

//            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    ws_log("Listener" + con.getId() + " onBINARY: send error: "
                            + e + " " + con.getStatus());
                }
            }
        };

        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log("Client" + con.getId()
                        + " onOPEN: " + makePath(con)
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Listener!");
                } catch (IOException e) {
                    ws_log("Client" + con.getId() + " onOPEN: send error: "
                            + e + " " + con.getStatus());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log("Client" + con.getId() + " onCLOSE: " + status);
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                ws_log("Client" + con.getId() + " onERROR: "
                        + e + " " + con.getStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, InputStream is, boolean isText) {
                byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
                int messageLen;
                try {
                    messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                    if (is.read() != -1) {
                        con.close(WsStatus.MESSAGE_TOO_BIG, "Message too big");
                    } else if (isText) {
                        onMessage(con, new String(messageBuffer, 0, messageLen, "UTF-8"));
                    } else {
                        onMessage(con, Arrays.copyOfRange(messageBuffer, 0, messageLen));
                    }
                } catch (Exception e) {
                    ws_log("Listener onMESSAGE: " + con.getPath()
                            + " read error: " + e);
                }
            }

//            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        ws_log("Client" + con.getId() + " onTEXT: " + s);
                    } else {
                        if (s.getBytes("utf-8").length < (MAX_MESSAGE_LENGTH / 2)) {
                            con.send(s + s);
                        } else {
                            con.send(new ByteArrayInputStream(s.getBytes("utf-8")), true);
                        }
                    }
                } catch (IOException e) {
                    ws_log("Client" + con.getId() + " onTEXT: send error: "
                            + e + " " + con.getStatus());
                }
            }

//            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    ws_log("Client" + con.getId() + " onBINARY: send error: " + e);
                }
            }
        };

        final WebSocket webSocket = new WebSocket();
        WsParameters wsp = new WsParameters();
        wsp.setConnectionSoTimeout(1000, true); // ping
        webSocket.setWsParameters(wsp);
// Listener and client must use the same self-signed certificate
        /* Android
        String keyFile = (new File(getCacheDir(),"testkeys")).getCanonicalPath();
         */
// /* Desktop
        String keyFile = (new File(path, "testkeys")).getCanonicalPath();
// */
        WebSocket.setKeyStore(keyFile, "passphrase"); // for listener
        WebSocket.setTrustStore(keyFile, "passphrase"); // for client
        final WsListener wsListener = webSocket.listenSafely(port, listenerHandler);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsListener.close();
//                webSocket.closeAll();
                timer.cancel();
            }
        }, WEBSOCKET_SHUTDOWN_TIMEOUT);
        ws_log("\r\nWssConnection (v"
                + WsConnection.VERSION + ") test"
                + "\r\nClient try to connect to " + remoteAddr
                + "\r\nWebSocket will be closed after "
                + (WEBSOCKET_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");

        webSocket.connect("wss://" + remoteAddr + "", clientHandler);
        webSocket.connect("wss://" + remoteAddr + "/", clientHandler);
        webSocket.connect("wss://" + remoteAddr + "/test", clientHandler);
        webSocket.connect("wss://" + remoteAddr + "?query=фыва", clientHandler);
// Connection below must fail (unsercure connect to secure listener)
        webSocket.connect("ws://" + remoteAddr + "/must_fail", clientHandler);
    }

}
