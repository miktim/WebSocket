/*
 * Secure WsConnection load test. MIT (c) 2020-2025 miktim@mail.ru
 */
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WssLoadTest {

    static final int MAX_MESSAGE_LENGTH = 1000000; //~1MB
    static final int TEST_SHUTDOWN_TIMEOUT = 10000; //10 sec 
    static final String REMOTE_HOST = "localhost";//"192.168.1.106";

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    static String makePath(WsConnection con) {
        return "" + con.getPath()
                + (con.getQuery() == null ? "" : "?" + con.getQuery());
    }

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        int port = 8443;
        String remoteAddr = REMOTE_HOST + ":" + port;

        WsConnection.Handler handler = new WsConnection.Handler() {
            String makeLogPrefix(WsConnection con) {
                return (con.isClientSide() ? "Client:" : "Server side:")
                        + con.getSubProtocol();
            }

            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log(makeLogPrefix(con) + " onOPEN: " + makePath(con)
                        + " Peer: " + con.getPeerHost()
                        + " SecureProtocol: " + con.getSSLSessionProtocol());
                    con.send(con.isClientSide()
                            ? "Привет, Сервер! " : "Hello, Client! ");
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log(makeLogPrefix(con) + " onCLOSE: " + makePath(con)
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Throwable e) {
                    ws_log(makeLogPrefix(con) + " onERROR: "
                            + makePath(con) + " " + e + " " + con.getStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, WsMessage is) {
                byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];
                int messageLen;
                try {
                    messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                    if (is.read() != -1) {
                        con.close(WsStatus.MESSAGE_TOO_BIG, "Message too big");
                    } else if (is.isText()) {
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
                    con.send(b); //echo binary data
            }
        };

        final WebSocket webSocket = new WebSocket();

//*** Server and client must use the same self-signed certificate
        String storeFileName = "android.jks";
        String password = "qwerty";
        String keyFile = (new File(path, storeFileName)).getCanonicalPath();
        WebSocket.setKeyStore(keyFile, password); // for server
        WebSocket.setTrustStore(keyFile, password); // for client

        WsParameters swsp = new WsParameters(); // server parameters
        swsp.setConnectionSoTimeout(1000, true); // ping
        swsp.setSubProtocols("1,2,3,4,5".split(","));
        final WsServer wsServer = 
                webSocket.startSecureServer(port, handler, swsp);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                ws_log("\r\nTime is over!\r\n");
                webSocket.closeAll("Time is over!");
                timer.cancel();
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException ex) {
                }
            }
        }, TEST_SHUTDOWN_TIMEOUT);
        ws_log("\r\nWssLoadTest "
                + WebSocket.VERSION 
                + "\r\nIncoming maxMessageLength: " + MAX_MESSAGE_LENGTH
                + "\r\nClient try to connect to " + remoteAddr
                + "\r\nWebSocket will be closed after "
                + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        WsParameters cwsp = new WsParameters(); // client parameters
        cwsp.setSubProtocols("1,2,3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/тест", handler, cwsp);
        cwsp.setSubProtocols("2,3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/", handler, cwsp);
        cwsp.setSubProtocols("3,4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "/тест", handler, cwsp);
        cwsp.setSubProtocols("4,5".split(","));
        webSocket.connect("wss://" + remoteAddr + "?параметр=значение", handler, cwsp);
        webSocket.connect("ws://" + remoteAddr + "/must_fail", handler, cwsp);
    }

}
