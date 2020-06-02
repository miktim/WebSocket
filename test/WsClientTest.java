/*
 * WebSocket client/server test. MIT (c) 2020 miktim@mail.ru
 */
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.samples.java.websocket.WsConnection;
import org.samples.java.websocket.WsHandler;
import org.samples.java.websocket.WsServer;
import org.samples.java.websocket.WssServer;

public class WsClientTest {

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler serverHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Server handle OPEN: " + con.getPath());
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Server handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Server handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Server handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure status:" + con.getClosureStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Server handle TEXT: " + s);
                    }

                } catch (IOException e) {
                    System.out.println("Server handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Server handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
            /*
            @Override
            public void onTextStream(WsConnection con, InputStream is) {
            }
            @Override
            public void onBinaryStream(WsConnection con, InputStream is) {
            }
             */
        };
        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Client handle OPEN: " + con.getPath());
                try {
                    con.send("Hello Server!");
                } catch (IOException e) {
                    System.out.println("Client handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Client handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure status:" + con.getClosureStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                System.out.println(s);
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Client handle TEXT: " + s);
                    }
                } catch (IOException e) {
                    System.out.println("Client handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Client handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
            /*
            @Override
            public void onTextStream(WsConnection con, InputStream is) {
            }
            @Override
            public void onBinaryStream(WsConnection con, InputStream is) {
            }
             */
        };

        int port = 8000 + WssServer.DEFAULT_SERVER_PORT;
        final WsServer wsServer = new WssServer(port, serverHandler);
        wsServer.setConnectionSoTimeout(1000); // handshake & ping
        wsServer.setMaxMessageLength(100000);
/* Android       
        wsServer.setKeystore(new File(getCacheDir(),"localhost.jks"), "password");
*/
// /* Desktop       
        wsServer.setKeystore(new File(path, "localhost.jks"), "password");
//        wsServer.setKeystore(new File(path,"/samplecacerts"), "changeit"); // need client auth
//        wsServer.setKeystore(new File(path,"/testkeys"), "passphrase");
// */
        int stopTimeout = 5000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nTest WebSocket secure client\r\n"
                + "Server will be stopped after "
                + (stopTimeout / 1000) + " seconds");
        wsServer.start();
        WsConnection wsClient = new WsConnection(
                "wss://localhost:" + port + "/test",
                clientHandler);
        wsClient.open();
    }
}