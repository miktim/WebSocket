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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import static org.samples.java.wsserver.WsConnection.GOING_AWAY;

public class WsServer {
    
    public static final String WSSERVER_VERSION = "0.0.1";
    public static final int DEFAULT_SERVER_PORT = 80;
    public static final int DEFAULT_CONNECTION_SO_TIMEOUT = 0;
    public static final int DEFAULT_MAX_MESSAGE_LENGTH
            = WsConnection.DEFAULT_MAX_MESSAGE_LENGTH;
    public static final String LOG_FILE_NAME = "websocket.log";

    private ServerSocket serverSocket;
    private boolean isRunning;
    private int connectionSoTimeout = DEFAULT_CONNECTION_SO_TIMEOUT;
    private int ssoBacklog = 20;
    private InetSocketAddress ssoAddress = null;
    boolean isSSL = false;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
    private PrintStream logStream;

    public WsServer() {
        this.logStream = System.out;
        this.isSSL = false;
        ssoAddress = new InetSocketAddress(DEFAULT_SERVER_PORT);
    }

    public void setMaxMessageLength(int len) {
        this.maxMessageLength = len;
    }

    public void setLogDirectory(String dir) throws IOException {
        logStream
                = new PrintStream(new FileOutputStream(new File(dir, LOG_FILE_NAME)));
    }

    public void log(String event) {
        String logMsg
                = String.format("%1$tY%1$tm%1$td %1$tH%1$tM%1$tS ", System.currentTimeMillis())
                + getClass().getSimpleName() + " " + event;
        logStream.println(logMsg);
        logStream.flush();
    }

    void logException(String stage, Exception e) {
        log(stage + " " + e.getMessage());
    }

    public void bind(int port) {
        ssoAddress = new InetSocketAddress(port);
    }

    public void bind(InetSocketAddress sa, int bl) {
        ssoAddress = sa;
        ssoBacklog = bl;
    }

// socket timeout for websocket handshaking & ping    
    public void setConnectionSoTimeout(int millis) {
        connectionSoTimeout = millis;
    }

    public void start() throws Exception {
        serverSocket = getServerSocketFactory().createServerSocket();
        serverSocket.bind(ssoAddress, ssoBacklog);
        (new WsProcessor(this)).start();
        this.log("Started");
    }

    public void stop() {
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
        this.log("Stopped");
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

                        socket.setSoTimeout(wss.connectionSoTimeout); // ms for handshake & ping
                        Thread th = new Thread(threadGroup,
                                new WsConnectionProcessor(wss, socket));
//                    th.setDaemon(true);
                        th.start();
                    } catch (Exception e) {
                        wss.logException("socketHandshake", e);
//                        e.printStackTrace(); 
                    }
                }
            } catch (Exception e) {
                if (wss.isRunning) {
                    wss.logException("serverSocket", e);
//                    e.printStackTrace();
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
        WsHandler handler = null;

        WsConnectionProcessor(WsServer wss, Socket s) {
            this.server = wss;
            this.socket = s;
        }

        @Override
        public void run() {
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
                        connection.setMaxMessageLength(server.maxMessageLength);
                        connection.start();
                    } else {
                        connection.sendResponseHeaders(null, "400 Bad request");
                        throw new ProtocolException("bad_request");
                    }
                } else {
                    closeConnection(); // server stopped
                }
            } catch (Exception e) {
                server.logException("wsHandShake", e);
                e.printStackTrace(); // WebSocket handshake exception
            }
            if (!this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (IOException ie) {
//                    ie.printStackTrace();
                }
            }
        }

        void closeConnection() {
            if (connection != null && connection.isOpen() && handler != null) {
                try {
                    connection.close(GOING_AWAY); // server stopped
                } catch (Exception e) {
                    e.printStackTrace();
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

    private String ksFile = null;
    private String ksPassphrase = null;

    public void setKeystore(String ksFile, String passphrase) {
        this.ksFile = ksFile;
        this.ksPassphrase = passphrase;
//        System.setProperty("javax.net.ssl.keyStore", ksFile);
//        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private ServerSocketFactory getServerSocketFactory() throws Exception {
        if (isSSL) {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] passphrase = this.ksPassphrase.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS"); //
            ks.load(new FileInputStream(this.ksFile), passphrase);
            kmf.init(ks, passphrase);
            ctx.init(kmf.getKeyManagers(), null, null);

            ssf = ctx.getServerSocketFactory();
            return ssf;
        } else {
            return ServerSocketFactory.getDefault();
        }
    }

}
