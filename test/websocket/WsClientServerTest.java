/*
 * Secure WsConnection test. MIT (c) 2020-2023 miktim@mail.ru
 */
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsListener;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WsClientServerTest {

    static final int MAX_MESSAGE_LENGTH = 1000000; // 1MB
    static final int WEBSOCKET_SHUTDOWN_TIMEOUT = 10000; //10 sec 
    static final int PORT = 8080;
    static final String ADDRESS = "ws://localhost:" + PORT;

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

        final WebSocket webSocket = new WebSocket();
        final WsParameters wsp = new WsParameters(); // listener parameters
        wsp.setConnectionSoTimeout(1000, true); // ping
        wsp.setSubProtocols("1,2,3,4,5".split(","));
//        wsp.setBacklog(100);
        
        WsHandler handler = new WsHandler() {
            String makeLogPrefix(WsConnection con) {
                return (con.isClientSide() ? "Client:" : "Listener:")
                        + con.getSubProtocol();
            }

            @Override
            public void onOpen(WsConnection con, String subp) {
                try {
                    con.send("Blah Blah");
                } catch (IOException e) {
                }
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log("["+con.listConnections().length+"] " + status);
//                if (con.isClientSide()) {
                    try {
                        webSocket.connect(ADDRESS, this, wsp);
                    } catch (Exception ex) {
                        ws_log("["+con.listConnections().length+"] Connection refused");
//                        ex.printStackTrace();
                    }
//                }

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
                if (con.isClientSide()) {
                    con.close();
                }
            }

        };

        final WsListener wsListener = webSocket.listen(PORT, handler, wsp);
        webSocket.connect(ADDRESS, handler, wsp);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                webSocket.closeAll("Time is over!");
                timer.cancel();
            }
        }, WEBSOCKET_SHUTDOWN_TIMEOUT);
        ws_log("\r\nWsClientServer (v"
                + WebSocket.VERSION + ") test"
                //                + "\r\nIncoming maxMessageLength: " + MAX_MESSAGE_LENGTH
                + "\r\nClient try to connect to " + ADDRESS
                + "\r\nWebSocket will be closed after "
                + (WEBSOCKET_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
    }

}
