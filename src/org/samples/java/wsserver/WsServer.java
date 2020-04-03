/*
 * my WebSocket Server java SE 1.8+
 * MIT (c) 2020 miktim@mail.ru
 * RFC-6455: https://tools.ietf.org/html/rfc6455
 *
 * Release notice:
 * - WebSocket extensions not supported
 *
 * Created: 2020-03-09
 */
package org.samples.java.wsserver;

import com.sun.net.httpserver.Headers;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import static org.samples.java.wsserver.WsConnection.GOING_AWAY;

public class WsServer {

    ServerSocket serverSocket;
    boolean isSSL = false;
    private boolean isRunning;
    private int soTimeout = 0;
    int ssoBacklog = 20;
    InetSocketAddress ssoAddress = null;

    public WsServer() {
        this.isSSL = false;
        ssoAddress = new InetSocketAddress(80);
    }

    public void bind(int port) {
        ssoAddress = new InetSocketAddress(port);
    }

    public void bind(InetSocketAddress sa, int bl) {
        ssoAddress = sa;
        ssoBacklog = bl;
    }

// socket timeout for websocket handshaking & ping    
    public void setSoTimeout(int millis) {
        soTimeout = millis;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void start() throws Exception {
        serverSocket = getServerSocketFactory().createServerSocket();
        serverSocket.bind(ssoAddress, ssoBacklog);
        (new WsProcessor(this)).start();
    }

    public void stop() {
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final SortedMap<String, WsHandler> context
            = Collections.synchronizedSortedMap(new TreeMap<>());

    public void createContext(String path, WsHandler handler)
            throws URISyntaxException {
        String cpath = (new URI(path)).getPath(); // check path syntax
        context.put(cpath, handler);
    }

    WsHandler getContext(String path)
            throws URISyntaxException, NoSuchElementException {
        String cpath = (new URI(path)).getPath();
        for (String keyPath : context.keySet()) {
            if (cpath.startsWith(keyPath)) {
                return context.get(keyPath);
            }
        }
        throw new NoSuchElementException();
    }

    private class WsProcessor extends Thread {

        private static final String THREAD_GROUP_NAME = "WSConnections";
        private final WsServer wss;
        private ThreadGroup threadGroup = null;

        WsProcessor(WsServer ws) {
            wss = ws;
        }

        @Override
        public void run() {
            wss.isRunning = true;
            try {
                threadGroup = new ThreadGroup(THREAD_GROUP_NAME);
                Socket socket;
                if (wss.isSSL) {
                    ((SSLServerSocket) wss.serverSocket)
                            .setNeedClientAuth(false);
                }
                while (wss.isRunning) {
                    try {
                        if (wss.isSSL) {
                            socket = ((SSLServerSocket) wss.serverSocket).accept();
                            ((SSLSocket) socket).startHandshake();
                        } else {
                            socket = wss.serverSocket.accept();
                        }

                        socket.setSoTimeout(wss.soTimeout); // seconds for handshake & ping
                        Thread th = new Thread(threadGroup,
                                new WsConnectionProcessor(wss, socket));
//                    th.setDaemon(true);
                        th.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                if (wss.isRunning) {
                    e.printStackTrace();
                }
            }
// close connections            
            if (threadGroup != null) {
                Thread[] threads = new Thread[threadGroup.activeCount()];
                threadGroup.enumerate(threads);
                for (int i = 0; i < threads.length; i++) {
                    threads[i].run();
                }
            }
        }
    }

    private class WsConnectionProcessor implements Runnable {

        private final WsServer server;
        private final Socket socket;
        WsConnection connection = null;

        WsConnectionProcessor(WsServer wss, Socket s) {
            this.server = wss;
            this.socket = s;
        }

        @Override
        public void run() {
            WsHandler handler = null;
            try {
                if (connection == null) {
                    Headers requestHeaders = getRequestHeaders();
                    String[] parts = requestHeaders
                            .getFirst(WsConnection.REQUEST_LINE_HEADER).split(" ");
                    String upgrade = requestHeaders.getFirst("Upgrade");
                    if (parts[0].equals("GET") && parts[2].equals("HTTP/1.1")
                            && upgrade != null && upgrade.equals("websocket")) {
                        handler = server.getContext((new URI(parts[1])).getPath());
                        connection = new WsConnection(socket, requestHeaders, handler);
                        connection.start();
                    } else {
                        connection.sendResponseHeaders(null, "400 Bad request");
                    }
                } else {
                    connection.close(GOING_AWAY); // server stopped
                }
            } catch (Exception e) {
                e.printStackTrace(); // WebSocket handshake exception
            }
            if (!this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (IOException ie) {
                    ie.printStackTrace();
                }
            }
        }

        private Headers getRequestHeaders() throws IOException {
            Headers headers = new Headers();
            BufferedReader br
                    = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            headers.add(WsConnection.REQUEST_LINE_HEADER,
                    br.readLine().replaceAll("  ", " ").trim());
            String[] pair = new String[0];
            while (true) {
                String line = br.readLine();
                if (line == null || line.isEmpty()) {
                    break;
                }
                if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                    headers.add(pair[0], headers.getFirst(pair[0]) + line);
                    continue;
                }
                pair = line.split(":");
                headers.add(pair[0].trim(), pair[1].trim());
            }
            return headers;
        }

    }

    private String certFile = null;
    private String certPassphrase = null;

    public void setCertificate(String certFile, String passphrase) {
        this.certFile = certFile;
        certPassphrase = passphrase;
//        System.setProperty("javax.net.ssl.keyStore", certFile);
//        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private ServerSocketFactory getServerSocketFactory() throws Exception {
        if (isSSL) {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] passphrase = certPassphrase.toCharArray();

            ctx = SSLContext.getInstance("TLS"); 
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS"); 
            ks.load(new FileInputStream(certFile), passphrase);
            kmf.init(ks, passphrase);
            ctx.init(kmf.getKeyManagers(), null, null); 

            ssf = ctx.getServerSocketFactory();
            return ssf;
        } else {
            return ServerSocketFactory.getDefault();
        }
    }

    public static void main(String[] args) throws Exception {
        WsHandler handler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("WebSocket connection open");
                try {
                    con.send("Hello Web Socket Client!");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("WebSocket connection closed");
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("WebSocket Exception. Closure code:"
                        + con.getClosureCode());
                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    con.send(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (Exception e) {
                    e.printStackTrace();
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
        wsServer.createContext("/", handler);
        wsServer.bind(8080);
        wsServer.setCertificate("/home/miktim/Test/localhost.jks", "password");
//        wsServer.setCertificate("/home/miktim/Test/keystore", "password");
//        wsServer.setCertificate("/home/miktim/Test/rsa.keystore", "password");
        wsServer.setSoTimeout(10000);
        wsServer.start();
        /*        
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, 60000);
         */
    }
}
