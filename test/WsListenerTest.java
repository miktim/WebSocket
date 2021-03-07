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
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WsListenerTest {

    public static final int MAX_MESSAGE_LENGTH = 1000000;//1MB
    public static final int LISTENER_SHUTDOWN_TIMEOUT = 30000;//30sec
    public static final String WEBSOCKET_SUBPROTOCOLS = "chat,superChat";

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler listenerHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                ws_log("Listener onOPEN: " + con.getPath()
                        + (con.getQuery() == null ? "" : "?" + con.getQuery())
                        + " Peer: " + con.getPeerHost()
                        + " Subprotocol:" + con.getSubProtocol());
                if (con.getPath().startsWith("/test/0")
                        && con.getSubProtocol() == null) {
                    con.close(WsStatus.UNSUPPORTED_DATA, "unknown subprotocol");
                    return;
                }
                try {
                    con.send("Hello Browser!");
                } catch (IOException e) {
                    ws_log("Listener: onOPEN: " + con.getPath()
                            + " send error: " + e);
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                ws_log("Listener onCLOSE: " + con.getPath()
                        + " " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                if (con == null) {
                    ws_log("Listener CRASHED! " + e);
                    e.printStackTrace();
                } else {
                    ws_log("Listener onERROR: "
                            + con.getPath() + " " + e + " " + con.getStatus());
//                e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String testPath = con.getPath();
                    if (testPath.endsWith("2")) { // check handler closure
                        if (s.length() > 128) {
                            con.close(WsStatus.NORMAL_CLOSURE,
                                    "trim close reason longer than 123 bytes: lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ng lo-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-o-ong");
                        } else {
                            con.send(s + s);
                        }

                    } else if (testPath.endsWith("3")) { // check message too big
//                        ws_log("Listener onTEXT: MsgLen=" + s.getBytes("utf-8").length);
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
                    ws_log("Listener onTEXT: " + con.getPath()
                            + " send error: " + e);
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    ws_log("Listener onBINARY: " + con.getPath()
                            + " send error: " + e);
                }
            }
        };

        final WebSocket webSocket
                = new WebSocket(InetAddress.getByName("localhost"));
        WsParameters wsp = webSocket.getWsParameters();
        wsp.setConnectionSoTimeout(1000, true); // ping
        wsp.setMaxMessageLength(MAX_MESSAGE_LENGTH, false);
        wsp.setSubProtocols(WEBSOCKET_SUBPROTOCOLS.split(","));
        webSocket.setWsParameters(wsp);
        final WsListener listener = webSocket.listen(8080, listenerHandler);

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
