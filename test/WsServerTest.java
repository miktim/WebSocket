/*
 * my WebSocket Server Test
 * MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import org.samples.java.wsserver.WsConnection;
import org.samples.java.wsserver.WsHandler;
import org.samples.java.wsserver.WsServer;
import org.samples.java.wsserver.WssServer;

public class WsServerTest {

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler handler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Handle OPEN: " + con.getPath());
                try {
                    con.send("Hello Client!" + con.getPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Handle CLOSE: " + con.getPath()
                        + " Closure code:" + con.getClosureCode());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Handle ERROR: " + con.getPath()
                        + " Exception: " + e.getMessage()
                        + " Closure code:" + con.getClosureCode());
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    con.send(s + " " + con.getPath());
                    con.send(s.getBytes());
                } catch (Exception e) {
                    System.out.println("Handle TEXT: " + con.getPath()
                            + " send exception: " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (Exception e) {
                    System.out.println("Handle BINARY: " + con.getPath()
                            + " send exception: " + e.getMessage());
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

        WsServer wsServer = new WsServer();
        wsServer.createContext("/test", handler);
        wsServer.bind(8080);
        wsServer.setKeystore(path + "/localhost.jks", "password");
        wsServer.setConnectionSoTimeout(10000);
//        wsServer.setLogFile(path+"wsserver.log");
        int stopTimeout = 30000;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("Server will be stopped after "
                + (stopTimeout / 1000) + " seconds");
        wsServer.start();
        java.awt.Desktop.getDesktop()
                //                .browse(new URI("file://" + path + "/WsServerTest.html"));
                .open(new File(path, "WsServerTest.html"));

    }

}
