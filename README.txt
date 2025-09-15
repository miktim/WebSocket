Java SE/Android WebSocket client and server package,  MIT (c) 2020-2025 miktim@mail.ru

Release notes:
  - RFC 6455 compliant package ( https://datatracker.ietf.org/doc/html/rfc6455/ );
  - 100% Java SE 6+/Android 4+ compatible (see WebSocket-Android-Test repo: https://github.com/miktim/WebSocket-Android-Test );
  - supported WebSocket version: 13;
  - WebSocket extensions (Per-Message Deflate, ...) are not supported;
  - supports cleartext/TLS connections;
  - client supports Internationalized Domain Names (IDNs);
  - stream-based messaging.
  
The jar ./dist/websocket-... file was generated with debugging info using JDK1.8 for target JDK1.6

package org.miktim.websocket

Overview:

  Class WebSocket - creator of WebSocket servers or client connections;  
  Class WsServer - implements a WebSocket server for cleartext or TLS connections;  
  Interface WsServer.Handler - server event handler;  
  Class WsConnection - implements a WebSocket connection on the server or client side;  
  Interface WsConnection.Handler - connection event handler;  
  Class WsParameters - WebSocket connection creation and execution time parameters;  
  Class WsStatus - WebSocket connection status.

  Class WebSocket:  
    Creator of WebSocket servers or client connections.

    Constant:
      static final String VERSION = "4.2.3";

    Constructors:
      WebSocket() throws NoSuchAlgorithmException;
        - check if SHA1 exists
      WebSocket(InetAddress bindAddr) throws SocketException, NoSuchAlgorithmException;
        - sets servers/connections binding address

    Methods:
      static URI idnURI(String uri) throws URISyntaxException;
        - converts the host name to the IDN form as defined in RFC3490 and
          creates a URI
          
      static void setKeyStore(String keyFilePath, String password); 
        - sets system properties javax.net.ssl.keyStore/keyStorePassword
      static void setTrustStore(String keyFilePath, String password);
        - sets system properties javax.net.ssl.trustStore/trustStorePassword

      void setKeyFile(File storeFile, String storePassword);
        - use a keyStore file (server) or a trustStore file (client);
      void resetKeyFile();

      InetAddress getBindAddress();
        - returns binding address

      WsServer Server(int port, WsConnection.Handler handler, WsParameters wsp) throws IOException, GeneralSecurityException;
        - creates cleartext connections server;
        - start the server instance using the start() or launch() methods. 
 
      WsServer SecureServer(int port, WsConnection.Handler handler, WsParameters wsp) throws IOException, GeneralSecurityException;
        - creates TLS connections server;
        - start the server instance using the start() or launch() methods. 

      Servers are created using a default server handler. You can set
      your own handler or start the server immediately (start(), launch()
      methods.
      The only action of the default handler is to throw a RutimeException
      in case of an error.

      WsConnection connect(String uri, WsConnection.Handler handler, WsParameters wsp) throws URISyntaxException, IOException, GeneralSecurityException;
        - creates and starts a client connection;
        - uri's scheme (ws: | wss:) and host are required,
          :port (80 | 443), /path and ?query are optional,
          user-info@ and #fragment - ignored

      WsServer[] listServers();
        - lists active/interrupted servers.
      WsConnection[] listConnections();
        - lists active connections.
      
      void closeAll(); 
      void closeAll(String closeReason);
        - closes all active servers/connections, status code 1001 (GOING_AWAY)
        

  Class WsServer extends Thread:  
    This class implements a WebSocket server for cleartext or TLS connections.
      - Servers are created using a default server handler. You can set your own
        handler or start the server immediately with start() or launch() methods.
        The default handler's only action is to call RuntimeException when
        an error occurs.
      - If a server shut down normally, all connections associated with it
        are closed with status code 1001 (GOING_AWAY), and it is removed
        from the server list.
      - If the server is interrupted or crashed, it remains in the server list.
        Server side connections stay alive and can be closed by usual way.

    Methods:
      WsServer setHandler(WsServer.Handler handler);
        - replaces the default handler;
      void start();
        - inherited, starts server
      WsServer launch();
        - starts server
      
      boolean isOpen();
        - returns the running state
      boolean isSecure();
        - is TLS connections server
      boolean isInterrupted(); 
        - returns true if the server was interrupted by a method
      Exception getError();
        - returns server exception or null
      ServerSocket getServerSocket();
        - returns ServerSocket object;
      int getPort();
        - returns the port number on which this server socket is listening.
      InetAddress getBindAddress();
        - returns the ServerSocket binding address or null
      WsParameters getParameters();
        - returns the server side connection parameters
      WsConnection[] listConnections();
        - returns the list of active connections

      void close(); 
      void close(String closeReason);
        - close methods also closes all active connections with 
          code 1001 (GOING_AWAY)
      void interrupt();
        - interrupts the server, associated connections stay alive
          and can be closed in the usual way
        
  Interface WsServer.Handler:  
    The only action of the default handler is to throw a RutimeException
    in case of an error.
    
    Methods:
      void onStart(WsServer server);
        - called when the server is started

      boolean onAccept(WsServer server, WsConnection conn);
        - called when accepting a connection BEFORE WebSocket handshake;
        - the returned value of true means approval of the connection, 
          the value of false means the closure of the client connection;
        - leave this method as soon as possible!

      void onStop(WsServer server, Exception error);
        - called when the server is closed or interrupted;
        - error is a ServerSocket, RuntimeException or null.


  Class WsConnection extends Thread:  
    Client side or server side connection.

    Constant:
      MESSAGE_QUEUE_CAPACITY = 3 
        - overflow of the incoming message queue leads to an error and
          connection closure with status code 1008 (POLICY_VIOLATION)

    Methods:
      void send(InputStream is, boolean isUTF8Text) throws IOException; 
        - the input stream is binary data or UTF-8 encoded text
      void send(String message) throws IOException;
        - send text
      void send(byte[] message) throws IOException;
        - send binary data
      
      boolean isClientSide();
        - returns true for client side connections
      boolean isOpen();
        - WebSocket connection is open
      boolean isSecure();
        - is TLS connection

      void setHandler(WsConnection.Handler newHandler);
        - sets the secondary connection handler;
        - calls onClose in the old handler (conn.isOpen() returns true),
          then calls onOpen in the new handler.

      WsConnection[] listConnections()
        - the client connection returns only itself
      String getSSLSessionProtocol()
        - returns SSL protocol or null for cleartext connection
      WsStatus getStatus();
        - returns clone of the connection status
      String getSubProtocol();
        - returns null or handshaked WebSocket subprotocol
      String getPeerHost();
        - returns remote host name or null
      Socket getSocket();
        - returns connection socket
      int getPort();
        - returns the connection port
      String getPath();
        - returns http request path or null
      String getQuery();
        - returns http request query or null
      WsParameters getParameters();
        - returns connection parameters

      void close();
        - closes connection with status code 1000 (NORMAL_CLOSURE)
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
    Runtime errors in the handler terminate the connection with status code 1011,
    call the onError method and throw a RuntimeException.     

    Methods:
      void onOpen(WsConnection conn, String subProtocol);
        - the second argument is the negotiated WebSocket sub protocol
          or null if the client did not requests it or if the server
          does not agree to any of the client's requested sub protocols
    
      void onMessage(WsConnection conn, InputStream is, boolean isText);
        - the message is an InputStream of binary data or UTF-8 encoded text;
        - the available() method returns the total number of bytes in the stream.

      void onError(WsConnection conn, Throwable e);
        - any exception closes the WebSocket connection;
        - large incoming messages may throw an OutOfMemoryError

      void onClose(WsConnection conn, WsStatus closeStatus);
        - called when connection closing handshake completed
          or closing time is over (WsParameters HandshakeSoTimeout)
      

  Class WsParameters:  
    WebSocket connection creation and execution time parameters  
    
    Constructor:
      WsParameters();

    Methods:
      WsParameters setSubProtocols(String[] subps); 
        - sets the WebSocket subprotocols required by the client
         (in the preferred order) or supported by the server;
      String[] getSubProtocols();
        - null is default
      WsParameters setHandshakeSoTimeout(int millis);
        - sets a timeout for opening/closing a WebSocket connection
      int getHandshakeSoTimeout();
        - default: 2000 milliseconds

      WsParameters setConnectionSoTimeout(int millis, boolean pingEnabled)
        - sets data exchange timeout;
        - if the timeout is exceeded and ping is disabled, 
          the connection is closed with status code 1001 (GOING_AWAY)
      int getConnectionSoTimeout();
        - default: 4000 milliseconds
      boolean isPingEnabled();
        - enabled by default

      WsParameters setPayloadBufferLength(int len);
        - sets the maximum payload length of the outgoing message frames,
          the minimum length is 125 bytes
      int getPayloadBufferLength();
        - default: 32 KiB
      WsParameters setMaxMessageLength(int len); 
        - sets incoming messages max length. If exceeded, the connection
          will be terminated with the 1009 (MESSAGE_TOO_BIG) status code
      int getMaxMessageLength();
        - default: 1 MiB

      WsParameters setSSLParameters(SSLParameters sslParms);
        - sets javax.net.ssl.SSLParameters;
        - SSLParameters used by server: 
          Protocols, CipherSuites, NeedClientAut, WantClientAuth.
      SSLParameters getSSLParameters();
        - defaults from the SSLContext

      WsParameters setBacklog(int num);
        - maximum number of pending connections on the ServerSocket 
      int getBacklog();
        - default value is -1: system depended
      

  Class WsStatus:  
    The status of the WebSocket connection
    
    Public Fields:
      int code;         // closing code (-1, 0, 1000-4999)
      String reason;    // closing reason (max length 123 BYTES)
      boolean wasClean; // WebSocket closing handshake completed cleanly
      boolean remotely; // closed remotely
      Throwable error;  // connection execution error or null

    Status codes used by package:
      int IS_INACTIVE = -1; // the connection yet not open
      int IS_OPEN = 0; // the connection is open
      int NORMAL_CLOSURE = 1000; // the connection successfully completed 
      int GOING_AWAY = 1001; //
        the connection closed by server or the connection timeout expired
      int PROTOCOL_ERROR = 1002; // 
        WebSocket handshake or data exchange protocol failed
        or attempting an unsecure connection to a SecureServer 
      int NO_STATUS = 1005; // the close status code outside 1000-4999
      int ABNORMAL_CLOSURE = 1006; // the connection not closed properly 
      int POLICY_VIOLATION = 1008; // message queue capacity exceeded
      int MESSAGE_TOO_BIG = 1009; // message or frame length exceeded
        (see WsConnection.setMaxMessageLength method)
      int UNSUPPORTED_EXTENSION = 1010; // data exchange protocol failed
      int INTERNAL_ERROR = 1011; // RuntimeException in the handlers
      int TLS_HANDSHAKE = 1015; //
        attempting an secure connection to a plaintext Server 
 

Usage examples see in:  
  ./test/websocket/WssBasicTest.java  
  ./test/websocket/WsServerTest.java  
  ./test/websocket/WssClientTest.java  
