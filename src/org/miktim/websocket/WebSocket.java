/*
 * WebSocket. MIT (c) 2020-2025 miktim@mail.ru
 * Creator of WebSocket servers and client connections.
 */
package org.miktim.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * WebSocket client connections and WebSocket servers factory.
 */
public class WebSocket {

    /**
     * Current package version {@value VERSION}.
     */
    public static final String VERSION = "5.0.3";

    private InetAddress interfaceAddress = null;
    private final List<WsConnection> connections = Collections.synchronizedList(new ArrayList<WsConnection>());
    private final List<WsServer> servers = Collections.synchronizedList(new ArrayList<WsServer>());
    private File storeFile = null;
    private String storePassword = null;

    /**
     * Creates a WebSocket factory.
     */
    public WebSocket() {
    }

    /**
     * Creates a WebSocket factory on the network interface.
     *
     * @param intfAddr network interface address (binding address).
     * @throws WsError with cause {@link IOException} if no such interface.
     */
    public WebSocket(InetAddress intfAddr) {
//            throws IOException {
        try {
            if(intfAddr != null &&
                NetworkInterface.getByInetAddress(intfAddr) == null)
                    throw new SocketException("Not an interface");
            interfaceAddress = intfAddr;
        } catch (Throwable th) {
            throw new WsError("Not an interface", th);
        }
    }

    /**
     * Sets the system properties javax.net.ssl.trustStore/trustStorePassword.
     *
     * @param filePath trust store file path
     * @param passphrase password
     */
    public static void setTrustStore(String filePath, String passphrase) {
        System.setProperty("javax.net.ssl.trustStore", filePath);
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
    }

    /**
     * Sets the system properties link javax.net.ssl.keyStore/keyStorePassword.
     *
     * @param filePath key store file path
     * @param passphrase password
     */
    public static void setKeyStore(String filePath, String passphrase) {
        System.setProperty("javax.net.ssl.keyStore", filePath);
        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

    /**
     * Converts host name to Internationalized Domain Names (IDNs) format and
     * creates URI.
     *
     * @param uri uri String like:
     * scheme://[user-info@]host[:port][/path][?query][#fragment]
     * @return URI object
     * @throws URISyntaxException
     */
    public static URI idnURI(String uri) throws URISyntaxException {
// https://stackoverflow.com/questions/9607903/get-domain-name-from-given-url
        Pattern pattern = Pattern.compile(
                "^(([^:/?#]+):)?(//(([^@]+)@)?([^:/?#]*))?(.*)(\\?([^#]*))?(#(.*))?$");
        Matcher matcher = pattern.matcher(uri);
        matcher.find();
        String host = null;
        if (matcher.groupCount() > 5) {
            host = matcher.group(6); // extract host from uri
        }
        if (host != null) {
            uri = uri.replace(host, java.net.IDN.toASCII(host));
        }
        return new URI(uri);
    }

    /**
     * Sets the keystore or truststore file for TLS connections/servers.
     * @param file keystore (server) or truststore (client) file
     * @param password password
     */
    public void setKeyFile(File file, String password) {
//        if(file == null || password == null) 
//            throw new NullPointerException();
        this.storeFile = file;
        this.storePassword = password;
    }

    /**
     * Clear key file info.
     */
    public void resetKeyFile() {
        storeFile = null;
        storePassword = null;
    }

    /**
     * Returns network interface address of this WebSocket instance.
     *
     * @return network interface address or null
     * @since 4.3
     */
    public InetAddress getInterfaceAddress() {
        return interfaceAddress;
    }

    /**
     * Lists active client connections.
     *
     * @return array of client connections within this WebSocket instance.
     */
    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    /**
     * Lists active servers.
     *
     * @return array of servers within this WebSocket instance.
     */
    public WsServer[] listServers() {
        return servers.toArray(new WsServer[0]);
    }

    /**
     * Closes all servers/connections within this WebSocket instance
     * with specified reason.
     * <p>
     * Connections close status code 1001 (GOING_AWAY).
     * </p>
     *
     * @param reason connection close reason. Max reason length 123 BYTES.
     * @see WsStatus
     * @see WsConnection#close(int,String)
     */
    synchronized public void closeAll(String reason) {
        for (WsServer server : listServers()) {
            server.stopServer(reason);
        }
        for (WsConnection conn : listConnections()) {
            conn.close(WsStatus.GOING_AWAY, reason);
        }
    }

    /**
     * Closes all servers/connections within this WebSocket instance.
     * <p>
     * Connections close status code 1001 (GOING_AWAY).
     * </p>
     *
     * @see WsStatus
     * @see WsConnection#close(int,String)
     */
    public void closeAll() {
        closeAll("Shutdown");
    }

    /**
     * Creates and starts WebSocket server for TLS connections.
     *
     * @param port listening port number.
     * @param handler server side connection handler.<br>
     * If you need to handle server events, use {@link WsServer.Handler} 
     * wich extends {@link WsConnection.Handler}.
     * @param wsp server side connection parameters.
     * @return WebSocket TLS server instance.
     * @throws WsError on server creation error<br>
     * Causes:
     * {@link java.io.IOException},
     * {@link java.security.GeneralSecurityException},
     * {@link java.lang.NullPointerException}
     * @see #setKeyStore(String, String)
     * @see #setKeyFile(File, String)
     * @since 4.3
     */
    public WsServer startSecureServer(int port, WsConnection.Handler handler,
            WsParameters wsp) {
//        throws IOException, GeneralSecurityException {
        return startServer(port, handler, wsp, true);
    }

    /**
     * Creates and starts WebSocket server for TLS connections
     * with default connection parameters
     * <br>
     * See {@link WebSocket#startSecureServer(int, WsConnection.Handler, WsParameters)} for details
     * @throws WsError on server creation error
     * @since 4.3
     */
    public WsServer startSecureServer(int port, WsConnection.Handler handler) {
//            throws IOException, GeneralSecurityException {
        return startServer(port, handler, new WsParameters(), true);
    }

    /**
     * Creates and starts WebSocket server for insecure connections
     * @param port listening port number.
     * @param handler server side connection handler.<br>
     * If you need to handle server events, use {@link WsServer.Handler} 
     * wich extends {@link WsConnection.Handler}.
     * @param wsp server side connection parameters.
     * @return WebSocket cleartext server instance.
     * @throws WsError on server creation error<br>
     * Causes:
     * {@link java.io.IOException},
     * {@link java.security.GeneralSecurityException},
     * {@link java.lang.NullPointerException}
     * @since 4.3
     */
    public WsServer startServer(int port, WsConnection.Handler handler,
            WsParameters wsp) {
//        throws IOException, GeneralSecurityException {
        return startServer(port, handler, wsp, false);
    }

    /**
     * Creates and starts WebSocket server for insecure connections 
     * with default connection parameters
     * <br>
     * See {@link WebSocket#startServer(int, WsConnection.Handler, WsParameters)} for details
     * @return WebSocket cleartext server instance.
     * @throws WsError on server creation error
     * @since 4.3
     */
    public WsServer startServer(int port, WsConnection.Handler handler) { 
//            throws IOException, GeneralSecurityException {
        return startServer(port, handler, new WsParameters(), false);
    }

    WsServer startServer(int port, WsConnection.Handler handler,
            WsParameters wsp, boolean isSecure) {
//            throws IOException, GeneralSecurityException {
        try {
            WsServer server = createServer(port, handler, wsp, isSecure);
            server.start();
            return server;
        } catch(Throwable th) {
            throw new WsError("Server creation error", th);
        }
    }

    WsServer createServer(int port, WsConnection.Handler handler, WsParameters wsp, boolean isSecure)
            throws IOException, GeneralSecurityException {
        if (handler == null || wsp == null) {
            throw new NullPointerException();
        }
        wsp = wsp.deepClone();

        ServerSocket serverSocket;
        if (isSecure) {
            ServerSocketFactory serverSocketFactory;
            if (this.storeFile != null) {
                serverSocketFactory = getSSLContext(false)
                        .getServerSocketFactory();
            } else {
                serverSocketFactory = SSLServerSocketFactory.getDefault();
            }
            serverSocket = serverSocketFactory
                    .createServerSocket(port, wsp.backlog, interfaceAddress);

            SSLParameters sslp = wsp.getSSLParameters();
            if (sslp != null) {
                ((SSLServerSocket) serverSocket).setNeedClientAuth(sslp.getNeedClientAuth());
                ((SSLServerSocket) serverSocket).setEnabledProtocols(sslp.getProtocols());
                ((SSLServerSocket) serverSocket).setWantClientAuth(sslp.getWantClientAuth());
                ((SSLServerSocket) serverSocket).setEnabledCipherSuites(sslp.getCipherSuites());
// TODO: downgrade Android API 24 to API 16
//            ((SSLServerSocket) serverSocket).setSSLParameters(wsp.sslParameters);
            }
        } else {
            serverSocket = new ServerSocket(port, wsp.backlog, interfaceAddress);
        }

        serverSocket.setSoTimeout(0);
        WsServer server
                = new WsServer(serverSocket, handler, isSecure, wsp);
        server.servers = this.servers; // set backlink to the WebSocket server list
        return server;
    }

    /**
     * Creates and starts WebSocket client connection.
     *
     * @param uri connection uri string like:<br>
     * scheme://[user-info@]host[:port][/path][?query][#fragment]<br>
     * - scheme (ws: | wss:) and host are required;<br>
     * - :port (80 | 443), /path and ?query are optional;<br>
     * - user-info@ and #fragment are ignored.
     * @param handler client side connection handler.
     * @param wsp client side connection parameters.
     * @return WebSocket connection instance.
     * @throws WsError on connection creation error.<br>
     * Causes: {@link java.net.URISyntaxException},
     * {@link java.io.IOException},
     * {@link java.security.GeneralSecurityException},
     * {@link java.lang.NullPointerException}
     * @see #setTrustStore(String, String)
     * @see #setKeyFile(File, String)
     */
    synchronized public WsConnection connect(String uri,
            WsConnection.Handler handler, WsParameters wsp) {
//            throws URISyntaxException, IOException, GeneralSecurityException {
        return startConnection(uri, handler, wsp);
    }

    /**
     * Creates and starts WebSocket client connection with default parameters
     * <br>
     * See: {@link WebSocket#connect(String, WsConnection.Handler, WsParameters)} for details 
     * @return WebSocket connection instance.
     * @throws WsError on connection creation error
     * @since 5.0
     */
    public WsConnection connect(String uri, WsConnection.Handler handler) { 
//            throws URISyntaxException, IOException, GeneralSecurityException {
        return connect(uri, handler, new WsParameters());
    }

    WsConnection startConnection (
            String uri, WsConnection.Handler handler, WsParameters wsp) {
//            throws URISyntaxException, GeneralSecurityException, IOException {
        try {
            WsConnection conn = createConnection(uri, handler, wsp);
            conn.start();
            return conn;
        } catch (Throwable th) {
            throw new WsError("Connection creation error", th);
        }
    }

    synchronized WsConnection createConnection(
            String uri, WsConnection.Handler handler, WsParameters wsp)
            throws URISyntaxException, IOException, GeneralSecurityException {
        if (uri == null || handler == null || wsp == null) {
            throw new NullPointerException();
        }
        wsp = wsp.deepClone();
        URI requestURI = idnURI(uri);
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "Scheme and host required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "Unsupported scheme");
        }

        Socket socket;
        boolean isSecure = scheme.equals("wss");
        SSLSocketFactory factory;

        if (isSecure) {
            if (this.storeFile != null) {
                factory = getSSLContext(true).getSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            socket = (SSLSocket) factory.createSocket();
            if (wsp.sslParameters != null) {
                ((SSLSocket) socket).setSSLParameters(wsp.sslParameters);
            }
        } else {
            socket = new Socket();
        }
        socket.setReuseAddress(true); // 
        socket.bind(new InetSocketAddress(interfaceAddress, 0));
        int port = requestURI.getPort();
        if (port < 0) {
            port = isSecure ? 443 : 80;
        }
        socket.connect(
                new InetSocketAddress(
                        requestURI.getHost(), port), wsp.handshakeSoTimeout);

        WsConnection conn = new WsConnection(socket, handler, wsp, requestURI);
        conn.connections = this.connections; // set backlink to the connections list
        return conn;
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private SSLContext getSSLContext(boolean isClient)
            throws IOException, GeneralSecurityException {
//            throws NoSuchAlgorithmException, KeyStoreException,
//            FileNotFoundException, IOException, CertificateException,
//            UnrecoverableKeyException {
        SSLContext ctx;
        KeyManagerFactory kmf;
        KeyStore ks;// = KeyStore.getInstance(KeyStore.getDefaultType());

        String ksPassphrase = this.storePassword;
        File ksFile = this.storeFile;
        char[] passphrase = ksPassphrase.toCharArray();

        ctx = SSLContext.getInstance("TLS");
        kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()); // java:"SunX509", android:"PKIX"
        ks = KeyStore.getInstance(KeyStore.getDefaultType()); // "JKS", "BKS"
        FileInputStream ksFis = new FileInputStream(ksFile);
        ks.load(ksFis, passphrase); // store password
        ksFis.close();
        kmf.init(ks, passphrase); // key password

        if (isClient) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()); // "PKIX", ""
            tmf.init(ks);

            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        } else {
            ctx.init(kmf.getKeyManagers(), null, null);
        }
        return ctx;
    }

}
