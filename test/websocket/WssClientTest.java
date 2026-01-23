/*
 * WebSocket client test. (c) websocketstest.com
 * Adapted by @miktim, march 2021-2025
 */

import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WssClientTest {

    static final int MAX_MESSAGE_LENGTH = 10000; //
    static final int TEST_SHUTDOWN_TIMEOUT = 10000; //milliseconds 
    static final String REMOTE_URI = "wss://websocketstest.com/service";//

    static String fragmentTest = randomString(512);
    static int counter = 0;
    static final Timer timer = new Timer();
    
    static void ws_log(Object obj) {
        System.out.println(obj);
    }

    static void ws_send(WsConnection con, String msg) {
        ws_log("snd: " + msg);
        try {
            con.send(msg);
        } catch (WsError err) {
            ws_log("snd: " + err.getCause());
        }
    }

    static String randomString(int string_length) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
        StringBuilder randomstring = new StringBuilder();
        for (int i = 0; i < string_length; i++) {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring.append(chars.charAt(rnum));
        }
        return randomstring.toString();
    }

    public static void main(String[] args) throws Exception {

        WsConnection.Handler clientHandler = new WsConnection.Handler() {
            @Override
            public void onOpen(WsConnection con, String subp) {
                ws_log("Connected: " + con.getSSLSessionProtocol()
                + " Peer: " + con.getPeerHost());
//                WsParameters wsp = con.getParameters(); // debug
            }

            @Override
            public void onClose(WsConnection con, WsStatus status) {
                ws_log("Connection closed. " + status);
                timer.cancel();
            }

            @Override
            public void onError(WsConnection con, Throwable e) {
                ws_log("Error: " + e.toString() + " " + con.getStatus());
//                e.printStackTrace();
            }
 
            String tests[] = {
                "version,",
                "echo,test message",
                "ping,",
                "fragments," + fragmentTest,
                "timer,"
            };
            int testId = 0;
            
            @Override
            public void onMessage(WsConnection con, WsMessage msg) {
                if(!msg.isText()) {
                    ws_log("rcv: unexpected binary");
                    con.close("Unexpected binary");
                } else {
                    String packet = "";
                    try {
                        packet = msg.asString();
                    } catch (WsError err) {
                    }
                    String[] arr = packet.split(",", 2);
                    String cmd = arr[0];
                    String response = arr[1];
                    ws_log("rcv: " + packet);
                    if (cmd.equals("connected")) {
                        ws_send(con, tests[testId++]);
                    } else if (cmd.equals("version")) {
                        if (!response.equals("hybi-draft-13")) {
                            ws_log("Something wrong...");
                        } else {
                            ws_log("OK");
                        }
                        ws_send(con, tests[testId++]);
                    } else if (cmd.equals("ping")) {
                        if (!response.equals("success")) {
                            ws_log("Failed!");
                        } else {
                            ws_log("OK");
                        }
                        ws_send(con, tests[testId++]);
                    } else if (cmd.equals("fragments")) {
                        if (!response.equals(fragmentTest)) {
                            ws_log("Failed!");
                        } else {
                            ws_log("OK");
                        }
                        ws_send(con, tests[testId++]);
                    } else if (cmd.equals("echo")) {
                        if (!response.equals("test message")) {
                            ws_log("Failed!");
                        } else {
                            ws_log("OK");
                        }
                        ws_send(con, tests[testId++]);
                    } else if (cmd.equals("time")) {
                        if (++counter > 4) {
                            ws_log("OK");
                            con.close(WsStatus.NORMAL_CLOSURE, "Completed");
                        }
                    } else {
                        ws_log("Unknown command.");
                    }
                }
            }

        };

        final WebSocket webSocket = new WebSocket();
        WsParameters wsp = new WsParameters()
                .setConnectionSoTimeout(10000, true)
                .setMaxMessageLength(MAX_MESSAGE_LENGTH); 
        wsp.getSSLParameters().setProtocols(new String[]{"TLSv1.2"});
// the site does not accept fragmented messages        
//        wsp.setPayloadBufferLength(fragmentTest.length()/2); // code 1005
//        wsp.setPayloadBufferLength(fragmentTest.length()); // code 1005
//        wsp.setPayloadBufferLength(300); // code 1005
        ws_log("\r\nWssClientTest "
                + WebSocket.VERSION
                + "\r\nTrying to connect to " + REMOTE_URI
                + "\r\nTest will be terminated after "
                + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");
        final WsConnection wsConnection
                = webSocket.connect(REMOTE_URI, clientHandler, wsp);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsConnection.close(WsStatus.GOING_AWAY, "Time is over!");
                timer.cancel();
            }
        }, TEST_SHUTDOWN_TIMEOUT);

    }

}
