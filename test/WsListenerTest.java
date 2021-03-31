/*
 * WebSocket WsListener test. MIT (c) 2020-2021 miktim@mail.ru
 * Created: 2020-03-09
 */

import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Arrays;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WsListener;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WsListenerTest {

    public static final int MAX_MESSAGE_LENGTH = 10000000;//1MB
    public static final int LISTENER_SHUTDOWN_TIMEOUT = 30000;//30sec
    public static final String WEBSOCKET_SUBPROTOCOLS = "chat,superChat";

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    public static void main(String[] args) throws Exception {
        String path = "."; // WsListenerTest.html file path
        if (args.length > 0) {
            path = args[0];
        }
// create handler
        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log("Listener" + con.getId()+ " onOPEN: " + con.getPath()
                        + (con.getQuery() == null ? "" : "?" + con.getQuery())
                        + " Peer: " + con.getPeerHost()
                        + " Subprotocol:" + subp);
                if (con.getPath().startsWith("/test/0")
                        && subp  == null) { // unsupported  WebSocket subprotocol
                    con.close(WsStatus.UNSUPPORTED_DATA, "Closed by listener: unknown subprotocol");
                    return;
                }
                try {
                    con.send("Hello Browser!");
                } catch (IOException e) {
                    ws_log("Listener" + con.getId()+ " onOPEN: " + con.getPath()
                            + " send error: " + e);
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log("Listener" + con.getId()+ " onCLOSE: " + con.getPath()
                        + " " + status);
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                if (con == null) {
                    ws_log("Listener" + con.getId()+ " CRASHED! " + e);
                    e.printStackTrace();
                } else {
                    ws_log("Listener" + con.getId()+ " onERROR: "
                            + con.getPath() + " " + e + " " + con.getStatus());
//                e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WsConnection con, InputStream is, boolean isText) {
                int messageLen;
                byte[] messageBuffer = new byte[MAX_MESSAGE_LENGTH];

                try {
                    messageLen = is.read(messageBuffer, 0, messageBuffer.length);
                    if (is.read() != -1) {
                        con.close(WsStatus.MESSAGE_TOO_BIG,
                                "Closed by listener: message too big (max "
                                        + MAX_MESSAGE_LENGTH + " bytes)");
                    } else if (isText) {
                        onMessage(con, new String(messageBuffer, 0, messageLen, "UTF-8"));
                    } else {
                        onMessage(con, Arrays.copyOfRange(messageBuffer, 0, messageLen));
                    }
                } catch (Exception e) {
                    ws_log("Listener" + con.getId()+ " onMESSAGE: " + con.getPath()
                            + " read error: " + e);
                }
            }

//            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String testPath = con.getPath();
                    if (testPath.endsWith("1")) { // wait browser closure
                       ws_log(s);
                       con.send(s);
                    } else if (testPath.endsWith("2")) { // close by listener
                        if (s.length() > 128) {
                            con.close(WsStatus.NORMAL_CLOSURE,
                                    "Closed by listener. Trim close reason longer than 123 bytes: lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ng reason lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ng reason");
                        } else {
                            con.send(s + s);
                        }
                    } else if (testPath.endsWith("3")) { // till  message too big
//                        ws_log("Listener onTEXT: MsgLen=" + s.getBytes("utf-8").length);
                        con.send(s + s); // can throw java.lang.OutOfMemoryError
                    } else if (testPath.endsWith("4")) { // ping, wait listener shutdown
                    } else {
                        con.send(s);
                    }
                } catch (Error e) { // large messages can throw java.lang.OutOfMemoryError
                    ws_log("Listener" + con.getId()+ " onTEXT: " + con.getPath()
                            + " send error: " + e);
                    con.close(WsStatus.INTERNAL_ERROR, e.getMessage());
                } catch (IOException e) {
                    ws_log("Listener" + con.getId()+ " onTEXT: " + con.getPath()
                            + " send error: " + e);
                }
            }

//            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    ws_log("Listener" + con.getId()+ " onBINARY: " + con.getPath()
                            + " send error: " + e);
                }
            }
        };
// create WebSocket "binded" to 127.0.0.1
        final WebSocket webSocket
                = new WebSocket(InetAddress.getByName("localhost"));
        WsParameters wsp = webSocket.getWsParameters();
// set WebSocket parameters: ping timeout and supported subProtocols
        wsp.setConnectionSoTimeout(1000, true); // ping on 1 second timeout
        wsp.setSubProtocols(WEBSOCKET_SUBPROTOCOLS.split(",")); 
        webSocket.setWsParameters(wsp);
// create and start listener on 8080 port binded to 127.0.0.1
        final WsListener listener = webSocket.listen(8080, listenerHandler); 
// init shutdown timer
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                listener.close();
                timer.cancel();
            }
        }, LISTENER_SHUTDOWN_TIMEOUT);

        ws_log("\r\nWsListener test"
                + "\r\nIncoming maxMessageLength: " + MAX_MESSAGE_LENGTH
                + "\r\nWebSocket subProtocols: " + WEBSOCKET_SUBPROTOCOLS
                + "\r\nListener will be closed after "
                + (LISTENER_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
// call the default browser
        /* Android
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("file:///android_asset/WsListenerTest.html"));
        startActivity(browserIntent);
         */
// /* Desktop 
        java.awt.Desktop.getDesktop().open(new File(path, "WsListenerTest.html"));
//        java.awt.Desktop.getDesktop().open(new File(path, "WsListenerTest.html"));
//        java.awt.Desktop.getDesktop().open(new File(path, "WsListenerTest.html"));
// */
    }

}
