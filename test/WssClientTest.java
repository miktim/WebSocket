/*
 * WebSocket client test. (c) https://websocketstest.com
 * Adapted in 2021 by miktim@mail.ru
 */

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsHandler;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WssClientTest {

    static final int MAX_MESSAGE_LENGTH = 1000000; //~1MB
    static final int WEBSOCKET_SHUTDOWN_TIMEOUT = 10000; //10sec 
    static final String REMOTE_CONNECTION = "wss://websocketstest.com:443/service";//

    static String fragmentTest = randomString(512);

    static void ws_log(String msg) {
        System.out.println(msg);
    }

    static void ws_send(WsConnection con, String msg) throws IOException {
        ws_log("snd: " + msg);
        con.send(msg);
    }

    static String randomString(int string_length) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
        String randomstring = "";
        for (int i = 0; i < string_length; i++) {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring += chars.substring(rnum, rnum + 1);
        }
        return randomstring;
    }

    public static void main(String[] args) throws Exception {

        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                ws_log("Connected using " + con.getSSLSessionProtocol()
                        + " to " + REMOTE_CONNECTION);
            }

            @Override
            public void onClose(WsConnection con) {
                ws_log("Connection closed. " + con.getStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                ws_log("Error: " + e.toString()
                        + " " + con.getStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    String packet = s;
                    String[] arr = packet.split(",", 2);
                    String cmd = arr[0];
                    String response = arr[1];
                    ws_log("rcv: " + packet);
                    if (cmd.equals("connected")) {
                        ws_send(con, "version,");
                    } else if (cmd.equals("version")) {
                        if (!response.equals("hybi-draft-13")) {
                            ws_log("Something wrong...");
                        }
                        ws_send(con, "echo,test message");
                    } else if (cmd.equals("ping")) {
                        if (!response.equals("success")) {
                            ws_log("Failed!");
                        }
                        ws_send(con, "fragments," + fragmentTest);
                    } else if (cmd.equals("fragments")) {
                        if (!response.equals(fragmentTest)) {
                            ws_log("Failed!");
                        }
                        ws_send(con, "timer,");
                    } else if (cmd.equals("echo") && response.equals("test message")) {
                        ws_send(con, "ping,");
                    } else if (cmd.equals("time")) {
                    } else {
                        ws_log("Failed!");
                    }
                } catch (IOException e) {
                    ws_log("snd error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                ws_log("rcv: unexpected binary");
            }
        };

        final WebSocket webSocket = new WebSocket();
        WsParameters wsp = new WsParameters();
        wsp.setConnectionSoTimeout(1000, true); // ping every second
        wsp.setMaxMessageLength(MAX_MESSAGE_LENGTH, false);
        webSocket.setWsParameters(wsp);
        ws_log("\r\nWssClient (v"
                + WsConnection.VERSION + ") test"
                + "\r\nTrying to connect to " + REMOTE_CONNECTION
                + "\r\nConnection will be closed after "
                + (WEBSOCKET_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        final WsConnection wsConnection = webSocket.connect(REMOTE_CONNECTION, clientHandler);
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsConnection.close(WsStatus.GOING_AWAY, "Thank you!");
                timer.cancel();
            }
        }, WEBSOCKET_SHUTDOWN_TIMEOUT);

    }

}
