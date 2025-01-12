Java SE/Android WebSocket client and server package,  MIT (c) 2020-2025 miktim@mail.ru

Release notes:
  - RFC 6455 compliant package ( https://datatracker.ietf.org/doc/html/rfc6455/ );
  - 100% Java SE 6+/Android 4+ compatible (see WebSocket-Android-Test repo: https://github.com/miktim/WebSocket-Android-Test );
  - supported WebSocket version: 13;
  - WebSocket extensions (Per-Message Deflate, ...) are not supported;
  - supports cleartext/TLS connections (without tunneling);
  - client supports Internationalized Domain Names (IDNs);
  - thread safe connections;
  - stream-based messaging.
  
The jar ./dist/websocket-... file was generated with debugging info using JDK1.8.0 for target JDK1.6

package org.miktim.websocket

Overview:

  Class WebSocket:
    Servers and client connections creator.

    Constant:
      static final String VERSION = "4.2.0";

    Constructors:
      WebSocket() throws NoSuchAlgorithmException;
        - check if SHA1 exists
      WebSocket(InetAddress bindAddr) throws SocketException, NoSuchAlgorithmException;
        - sets servers/connections binding address

    Methods:
      static URI idnURI(String uri) throws URISyntaxException;
        - converts the host name to the IDN form as defined in RFC3490 and creates a URI
          
      static void setKeyStore(String keyFilePath, String password); 
        - sets system properties javax.net.ssl.keyStore/keyStorePassword
      static void setTrustStore(String keyFilePath, String password);
        - sets system properties javax.net.ssl.trustStore/trustStorePassword

      void setKeyFile(File storeFile, String storePassword);
        - use a keyStore file (server) or a trustStore file (client);
      void resetKeyFile();

      WsServer Server(int port, WsConnection.Handler handler, WsParameters wsp) throws IOException, GeneralSecurityException;
        - creates cleartext connections server
 
      WsServer SecureServer(int port, WsConnection.Handler handler, WsParameters wsp) throws IOException, GeneralSecurityException;
        - creates TLS connections server

      Servers are created using a "dumb" server handler. You can set your own handler or start the server immediately.

      WsConnection connect(String uri, WsConnection.Handler handler, WsParameters wsp) throws URISyntaxException, IOException, GeneralSecurityException;
        - creates and starts a client connection;
        - uri's scheme (ws: | wss:) and host are required,
          :port, /path and ?query are optional,
          user-info@ and #fragment - ignored

      InetAddress getBindAddress();
      WsServer[] listServers();
        - lists active/terminated servers.
      WsConnection[] listConnections();
        - lists active connections.
      
      void closeAll(); 
      void closeAll(String closeReason);
        - closes all active servers/connections, code 1001 (GOING_AWAY)
        

  Class WsServer extends Thread:
  
    Methods:
      WsServer setHandler(WsServer.Handler handler);
      void start();
        - inherited, starts server
      WsServer launch();
        - starts server
      
      boolean isOpen();
        - returns the running state
      boolean isSecure();
        - is secure server
      boolean isInterrupted(); 
        - returns true if the server was interrupted by a method or due to a server socket exception
      Exception getError();
        - returns server socket exception or null
      ServerSocket getServerSocket();
      int getPort();
        - returns the port number on which this server socket is listening.
      InetAddress getBindAddress();
        - returns the ServerSocket binding address or null
      WsParameters getParameters();
        - returns server connection parameters
      WsConnection[] listConnections();
        - returns a list of active connections

      void close(); 
      void close(String closeReason);
        - close methods also closes all associated connections (code GOING_AWAY)
      void interrupt();
        - interrupts the server, associated connections stay alive and can be closed in the usual way

        
  Interface WsServer.Handler:
    Leave handler as soon as possible.
    
    Methods:
      void onStart(WsServer server);

      boolean onAccept(WsServer server, WsConnection conn);
        - called BEFORE WebSocket connection handshake;
        - the returned value of true means approval of the connection, the value of false means the closure of the client connection

      void onStop(WsServer server, Exception error);
        - error is a ServerSocket exception or null


  Class WsConnection extends Thread:
    Constant:
      MESSAGE_QUEUE_CAPACITY = 3 
        - overflow of the incoming message queue leads to an error and connection closure with status code 1008 (POLICY_VIOLATION)

    Methods:
      void send(InputStream is, boolean isUTF8Text) throws IOException; 
        - the input stream is binary data or UTF-8 encoded text
      void send(String message) throws IOException;
        - send text
      void send(byte[] message) throws IOException;
        - send binary data
      
      boolean isClientSide();
        - returns true for client connections
      boolean isOpen();
        - WebSocket connection is open
      boolean isSecure();
        - is TLS connection

      void setHandler(WsConnection.Handler newHandler);
        - calls onClose in the old handler (conn.isOpen() returns true), then calls onOpen in the new handler

      WsConnection[] listConnections()
        - the client connection returns only itself
      String getSSLSessionProtocol()
        - returns SSL protocol or null
      WsStatus getStatus();
        - returns the connection status, syncs with a client WebSocket handshake
      String getSubProtocol();
        - returns null or handshaked WebSocket subprotocol
      String getPeerHost();
        - returns remote host name or null
      int getPort();
        - returns connected port
      String getPath();
        - returns http request path or null
      String getQuery();
        - returns http request query or null
      WsParameters getParameters();

      void close();
        - closes connection with status code 1005 (NO_STATUS)
      void close(int statusCode, String reason); 
        - the status code outside 1000-4999 will be replaced with 1005 (NO_STATUS),
          the reason is ignored;
        - a reason that is longer than 123 BYTES is truncated;
        - the method blocks outgoing messages (sending methods throw IOException);
        - isOpen() returns false;
        - incoming messages are available until the closing handshake completed.
        

  Interface WsConnection.Handler:
    There are two scenarios for handling events:
      - onError - onClose, when the SSL/WebSocket handshake failed;
      - onOpen - [onMessage - onMessage - ...] - [onError] - onClose.
    The handler's RuntimeException only calls the printStackTrace() function. The connection interrupted.
      
    Methods:
      void onOpen(WsConnection conn, String subProtocol);
        - the second argument is the negotiated WebSocket sub protocol or null if the client did not requests it or if the server does not agree to any of the client's requested sub protocols
    
      void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);
        - the message is an InputStream of binary data or UTF-8 encoded text;
        - the available() method returns the total number of bytes in the stream.

      void onError(WsConnection conn, Throwable e);
        - any exception closes the WebSocket connection;
        - large incoming messages may throw an OutOfMemoryError

      void onClose(WsConnection conn, WsStatus closeStatus);
      

  Class WsParameters:
    WebSocket connection parameters
    
    Constructor:
      WsParameters();

    Methods:
      WsParameters setSubProtocols(String[] subps); 
        - sets WebSocket subProtocols in preferred order;
      String[] getSubProtocols();
        - null is default
      WsParameters setHandshakeSoTimeout(int millis);
        - sets a timeout for opening/closing a WebSocket connection
      int getHandshakeSoTimeout();
      WsParameters setConnectionSoTimeout(int millis, boolean pingEnabled)
        - sets data exchange timeout;
        - if the timeout is exceeded and ping is disabled, the connection is terminated with status code 1001 (GOING_AWAY)

      int getConnectionSoTimeout();
      boolean isPingEnabled();
        -enabled by default
      WsParameters setPayloadBufferLength(int len); 
        - sets outgoing messages max payload length, min len = 125 bytes
      int getPayloadBufferLength();
      WsParameters setMaxMessageLength(int len); 
        - sets incoming messages max length. If exceeded, the connection will be terminated with the 1009 (MESSAGE_TOO_BIG) status code
      int getMaxMessageLength();
        - default: 1 MiB

      WsParameters setSSLParameters(SSLParameters sslParms);
        - sets javax.net.ssl.SSLParameters;
        - SSLParameters used by server: Protocols, CipherSuites, NeedClientAut, WantClientAuth.
      SSLParameters getSSLParameters();
        - defaults from the SSLContext

      WsParameters setBacklog(int num);
        - maximum number of pending connections on the ServerSocket 
      int getBacklog();
        - default value is -1: system depended
      

  Class WsStatus:
    Connection status
    
    Fields:
      int code;         // closing code (0, 1000-4999)
      String reason;    // closing reason (max length 123 BYTES)
      boolean wasClean; // WebSocket closing handshake completed cleanly
      boolean remotely; // closed remotely
      Throwable error;  // closing exception or null
      

Usage examples see in:
  ./test/websocket/WssConnectionTest.java
  ./test/websocket/WsServerTest.java
  ./test/websocket/WssClientTest.java
