/*
 * WsConnection load test. MIT (c) 2020-2026 miktim@mail.ru
 */
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WsLoadTest {

    static final int MESSAGE_LENGTH = 100000; //bytes
    static final int TEST_SHUTDOWN_TIMEOUT = 10000; //10 sec 
    static final int MAX_CONNECTIONS = 10;
    static final String REMOTE_HOST = "localhost";//"192.168.1.106";
    static final int PORT = 8080;
    static String uri = "ws://" + REMOTE_HOST + ":" + PORT;
    static WebSocket webSocket = null;
    static int connections = 0;
    static int messages = 0;
    static int errors = 0;

    static void ws_log(Object msg) {
        System.out.println(msg);
    }

    static String makePath(WsConnection con) {
        return "" + con.getPath()
                + (con.getQuery() == null ? "" : "?" + con.getQuery());
    }
    static void joinAll() {
       try {
       for(WsServer srv : webSocket.listServers())
//           for(WsConnection conn : srv.listConnections())
               srv.join();
       for(WsConnection conn : webSocket.listConnections())
           conn.join();
       } catch(InterruptedException ex) {
       }
    }
    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        int port = 8443;

        WsConnection.Handler handler = new WsConnection.Handler() {
            String side(WsConnection con) {
                return (con.isClientSide() ? "Client " : "Server side ");
            }

            @Override
            public void onOpen(WsConnection con, String subp) {
//                ws_log(side(con) + " onOpen: ");
                try {
//                    con.send(con.isClientSide()
//                            ? "Привет, Сервер! " : "Hello, Client! ");
                    con.send(new byte[MESSAGE_LENGTH]);
                    messages++;
                } catch (WsError err) {
                    errors++;
                    ws_log(side(con) + "onOpen: send error: " + err.getCause());
                }
                if(con.isClientSide()) connections++;
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
//                ws_log(side(con) + "onClose: " + con.getStatus());
                if(con.isClientSide())
                    try {
                        webSocket.connect(uri, this);
                    } catch (Exception ex) {
                        ws_log(side(con) + "onClose connect error: " + ex);
                    }
            }

            @Override
            public void onError(WsConnection con, Throwable e) {
//                    ws_log(side(con) + "onError: " + e );
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, WsMessage is) {
                try {
                    if(con.isOpen())
                        con.send(is, is.isText());
                    messages++;
                    if(con.isClientSide() && Math.random() > 0.5) {
                        con.close();
                    }
                } catch (Exception e) {
                    errors++;
//                    ws_log(side(con) + "onMessage send error: " + e);
                }
            }
        };

        webSocket = new WebSocket();

        WsParameters swsp = new WsParameters(); // server parameters
//        swsp.setConnectionSoTimeout(1000, true); // ping
        final WsServer wsServer = 
                webSocket.startServer(PORT, handler, swsp);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                ws_log("\r\nTime is over! Server is stopped.\r\n");
                webSocket.closeAll("Time is over!");
                timer.cancel();
             }
        }, TEST_SHUTDOWN_TIMEOUT);
        ws_log("\r\nWsLoadTest "
                + WebSocket.VERSION 
                + "\r\nClient try to connect to " + uri
                + "\r\nWebSocket will be closed after "
                + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\nMessage Length: " + MESSAGE_LENGTH
                + "\r\nMax connections: " + MAX_CONNECTIONS
                + "\r\nConnections are closed and opened randomly");

        for (int i = 0; i < MAX_CONNECTIONS; i++)
            webSocket.connect(uri, handler);
        joinAll();
        ws_log(String.format(
                "\r\nConnections: %d Messages sent: %d IOErrors(WebSocket closed): %d",
                connections, messages, errors));
    }

}
