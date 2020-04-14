/*
 * WsServer Test
 * MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */

import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
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
                        + " " + e.toString() 
                        + " Closure code:" + con.getClosureCode());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String testPath = con.getPath();
                    if (testPath.endsWith("2")) { // check handler closure
                        if(s.length() > 128) con.close();
                        else con.send(s + s);
                        
                    } else if (testPath.endsWith("3")) { // check message too big
//                        System.out.println("MsgLen:" + s.length());
                        con.send(s + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    System.out.println("Handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Handle BINARY: " + con.getPath()
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

        WsServer wsServer = new WsServer();
        wsServer.createContext("/test", handler);
        wsServer.bind(8080);
        wsServer.setConnectionSoTimeout(10000);
        wsServer.setMaxMessageLength(100000);
        wsServer.setKeystore(path + "/localhost.jks", "password");
//        wsServer.setLogFile(new File(path,"wsserver.log"), false);
        int stopTimeout = 20000;
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
                .browse(new URI("file://" + path + "/WsServerTest.html"));
//                .open(new File(path, "WsServerTest.html"));

    }

}
