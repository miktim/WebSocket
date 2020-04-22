
/*
 * WebSocket client test. MIT (c) 2020 miktim@mail.ru
 */
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.samples.java.wsserver.WsConnection;
import org.samples.java.wsserver.WsHandler;
import org.samples.java.wsserver.WsServer;
import org.samples.java.wsserver.WssServer;

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
                        + " Closure code:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Server handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure code:" + con.getClosureCode());
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
                        + " Closure code:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure code:" + con.getClosureCode());
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

        WsServer wsServer = new WssServer();
        wsServer.createContext("/test", serverHandler);
        int port = 8000 + wsServer.DEFAULT_SERVER_PORT;
//        wsServer.bind(8000 + wsServer.DEFAULT_SERVER_PORT);
        wsServer.bind(port);
        wsServer.setConnectionSoTimeout(10000);
        wsServer.setMaxMessageLength(100000);
//        wsServer.setKeystore(path + "/localhost.jks", "password");
//        wsServer.setKeystore(path + "/samplecacerts", "changeit");
        wsServer.setKeystore(path + "/testkeys", "passphrase");
//        wsServer.setLogFile(new File(path,"wsserver.log"), false);
        int stopTimeout = 5000;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("Test WebSocket client\r\nServer will be stopped after "
                + (stopTimeout / 1000) + " seconds");
        wsServer.start();
        WsConnection wsClient = new WsConnection(
                "wss://localhost:" + port + "/test/",
                clientHandler);
        wsClient.open();
    }
}
