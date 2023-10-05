/*
 * Secure WsConnection test. MIT (c) 2020-2023 miktim@mail.ru
 */
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
    static final int WEBSOCKET_SHUTDOWN_TIMEOUT = 10000; //10 sec 
    static final String REMOTE_HOST = "localhost";//"192.168.1.106";

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    static String makePath(WsConnection con) {
        return con.getPath()
                + (con.getQuery() == null ? "" : "?" + con.getQuery());
    }

    /*
    static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
     */
    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        int port = 8443;
        String remoteAddr = REMOTE_HOST + ":" + port;

        WsHandler handler = new WsHandler() {
            String makeLogPrefix(WsConnection con) {
                return (con.isClientSide() ? "Client:" : "Listener:")
                        + con.getSubProtocol();
            }

            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log(makeLogPrefix(con) + " onOPEN: " + makePath(con)
                        + " Peer: " + con.getPeerHost()
                        + " SecureProtocol: " + con.getSSLSessionProtocol());
                try {
                    con.send(con.isClientSide()
                            ? "Привет, Листенер! " : "Hello, Client! ");
                } catch (IOException e) {
                    ws_log(makeLogPrefix(con) + " onOPEN: " + makePath(con)
                            + " send error: " + e + " " + con.getStatus());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log(makeLogPrefix(con) + " onCLOSE: " + makePath(con)
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Throwable e) {
                if (con == null) {
                    ws_log("Listener CRASHED! " + e);
                    e.printStackTrace();
                } else {
                    ws_log(makeLogPrefix(con) + " onERROR: "
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
                    ws_log(makeLogPrefix(con) + " onMESSAGE: " + con.getPath()
                            + " read error: " + e);
                }
            }

            public void onMessage(WsConnection con, String s) {
                if (!con.isOpen()) {
                    return;
                }
                try {
                    if (s.length() < 128) {
                        ws_log(makeLogPrefix(con) + " onTEXT: " + s);
                    }
                    if (s.getBytes("utf-8").length < (MAX_MESSAGE_LENGTH / 2)) {
                        con.send(s + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    ws_log(makeLogPrefix(con) + " onTEXT: send error: "
                            + e + " " + con.getStatus()
                            + " Msg: \"" + s.substring(0, 12) + "...");
                }
            }

            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b); //echo binary data
                } catch (IOException e) {
                    ws_log(makeLogPrefix(con) + " onBINARY: send error: "
                            + e + " " + con.getStatus());
                }
            }
        };

        final WebSocket webSocket = new WebSocket();

//*** Listener and client must use the same self-signed certificate

// Android:
//        String keyFile = (new File(getCacheDir(),"testkeys")).getCanonicalPath();
// Desktop:
        String keyFile = (new File(path, "testkeys")).getCanonicalPath();
//
        WebSocket.setKeyStore(keyFile, "passphrase"); // for listener
        WebSocket.setTrustStore(keyFile, "passphrase"); // for client

        WsParameters lwsp = new WsParameters(); // listener parameters
        lwsp.setConnectionSoTimeout(1000, true); // ping
        lwsp.setSubProtocols("1,2,3,4,5".split(","));
        final WsListener wsListener = webSocket.listenSafely(port, handler, lwsp);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
//                wsListener.close();
                webSocket.closeAll("Time is over");
                timer.cancel();
            }
        }, WEBSOCKET_SHUTDOWN_TIMEOUT);
        ws_log("\r\nWssConnection (v"
                + WebSocket.VERSION + ") test"
                + "\r\nIncoming maxMessageLength: " + MAX_MESSAGE_LENGTH
                + "\r\nClient try to connect to " + remoteAddr
                + "\r\nWebSocket will be closed after "
                + (WEBSOCKET_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        WsParameters cwsp1 = new WsParameters();
        cwsp1.setSubProtocols("1,2,3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/путь", handler, cwsp1);
        WsParameters cwsp2 = new WsParameters();
        cwsp2.setSubProtocols("2,3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/", handler, cwsp2);
        WsParameters cwsp3 = new WsParameters();
        cwsp3.setSubProtocols("3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/test", handler, cwsp3);
        WsParameters cwsp4 = new WsParameters();
        cwsp4.setSubProtocols("4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "?query=значение", handler, cwsp4);
// The connection listed below should fail (unsecured connection to a secure listener)
        webSocket.connect("ws://" + remoteAddr + "/must_fail", handler, cwsp4);
    }

}
