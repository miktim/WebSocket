Java SE 1.6+/Android 4.0+ WebSocket client and server package,  MIT (c) 2020-2025 miktim@mail.ru

Release notes:
  - RFC 6455 compliant package ( https://datatracker.ietf.org/doc/html/rfc6455/ );
  - 100% Java SE 6+/Android 4+ compatible (see WebSocket-Android-Test repo: https://github.com/miktim/WebSocket-Android-Test );
  - supported WebSocket version: 13;
  - WebSocket extensions (Per-Message Deflate, ...) are not supported;
  - supports insecure or TLS connections;
  - client supports Internationalized Domain Names (IDNs);
  - stream-based messaging.
  
The standalone jar ./dist/websocket-... file was generated with debugging info using JDK1.8 for target JDK1.6

package org.miktim.websocket

Overview:

  Class WsException - unchecked exception "hides" real checked cause;
  Class WebSocket - the creator of WebSocket client-side connections or servers;
  Class WsConnection - implements a WebSocket connection on the server side or client side;
  Interface WsConnection.Handler - a connection event handler;  
  Class WsParameters - WebSocket connection creation and execution time parameters;  
  Class WsStatus - WebSocket connection status.
  Class WsServer - implements a WebSocket server for insecure or TLS connections;
  Interface WsServer.Handler - a server event handler;

  Class WsException extends RuntimeException
    Unchecked exception "hides" real checked cause

  Class WebSocket:
    Creator of WebSocket client-side connections or servers.

    Constant:
      static final String VERSION = "4.3.1";

    Constructors:
      WebSocket();
        - creates WebSocket instance
      WebSocket(InetAddress intfAddr);
        - creates WebSocket instance on interface

    Methods:
      static URI idnURI(String uri) throws URISyntaxException;
        - converts the host name to the IDN form as defined in RFC3490 and
          creates a URI
          
      static void setKeyStore(String keyFilePath, String password); 
        - sets system properties javax.net.ssl.keyStore/keyStorePassword
      static void setTrustStore(String keyFilePath, String password);
        - sets system properties javax.net.ssl.trustStore/trustStorePassword

      void setKeyFile(File storeFile, String storePassword);
        - sets a keyStore file or a trustStore file;
      void resetKeyFile();

      WsConnection connect(String uri, WsConnection.Handler handler, WsParameters wsp);
        - creates and starts a client connection;
        - uri like: scheme://[user-info@]host[:port][/paht][?query][#fragment]
            uri's scheme (ws: | wss:) and host are required,
            :port (80 | 443), /path and ?query are optional,
            user-info@ and #fragment - ignored
          handler: is client-side connection handler
          wsp: the connections creation and execution parameters
        - hidden exceptions: URISyntaxException, IOException, GeneralSecurityException;

      WsConnection connect(String uri, WsConnection.Handler handler);
        - creates and starts a client connection with default parameters

      WsServer startServer(int port, WsConnection.Handler handler, WsParameters wsp);
        - creates and starts insecure connections server.
        - port: is listening port number
          handler: is server-side connection handler
          wsp: the connections creation and execution parameters
        - hidden exceptions: IOException, GeneralSecurityException

      WsServer startServer(int port, WsConnection.Handler handler);
        - creates and starts insecure connections server with default
          connection parameters;
        - see above.
 
      WsServer startSecureServer(int port, WsConnection.Handler handler, WsParameters wsp);
        - creates and starts TLS connections server;
        - see above.
        
      WsServer startSecureServer(int port, WsConnection.Handler handler);
        - creates and starts TLS connections server with default
          connection parameters;
        - see above.

    NOTE:
      if you need to handle server events, extend the connection handler
      with a server handler. For example:
        interface MyHandler extends WsConnection.Handler, WsServer.Handler {};

      InetAddress getInterfaceAddress();
        - returns WebSocket instance interface address or null
      WsServer[] listServers();
        - lists active servers.
      WsConnection[] listConnections();
        - lists active client connections.
      
      void closeAll(); 
      void closeAll(String closeReason);
        - closes all active servers/connections, status code 1001 (GOING_AWAY)


  Interface WsConnection.Handler:
    There are several scenarios for handling connection events:
      - [onRequest] - onError - onClose
          when the SSL/WebSocket handshake failed;
      - [onRequest] - onOpen - [onMessage - onMessage - ...] - [onError] - onClose.
    A runtime error in the handler terminates the connection with status
    code 1006 (ABNORMAL_CLOSURE), calls the onError method, and throws
    a RuntimeException.

    Methods:
      Map<String,String> onRequest(Map<String,String> request, Map<String,String> response);
      Since 5.0.0
        - client-side: called BEFORE WebSocket negotiation begins,
          returns additional HTTP request headers or null;
        - server-side: called BEFORE sending response,
          returns additional HTTP response headers or null;
        - request/response Map is HTTP header name, header value pairs.

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
        - sets the buffer length for the outgoing message frames,
          the minimum length is 125 bytes
      int getPayloadBufferLength();
        - default: 32 KiB

      WsParameters setMaxMessageLength(int len);
        - sets incoming messages max length. If exceeded, the connection
          will be terminated with the 1009 (MESSAGE_TOO_BIG) status code
        - len = -1 allows for an "infinite" message
      int getMaxMessageLength();
        - default: 1 MiB

      WsParameters setMaxMessages(int maxMsgs);
        - sets the maximum number of serving incoming messages per
          connection (min value: 1);
        - overflow of this value leads to an error and
          connection closure with status code 1008 (POLICY_VIOLATION)
      int getMaxMessages();
        - default: 3

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


  Class WsConnection extends Thread:
    Client side or server side connection.

    Methods:
      String getSubProtocol();
        - returns null or handshaked WebSocket subprotocol

      void send(InputStream is, boolean isUTF8Text) throws IOException;
        - the input stream is binary data or UTF-8 encoded text
      void send(String message);
        - send text;
        - hidden exception: IOException;
      void send(byte[] message);
        - send binary data
        - hidden exception: IOException;

      byte[] toByteArray(InputStream is);
        - converts the input stream into an array of bytes
        - returns an array of bytes;
        - hidden exception: IOException;
      String toString(InputStream is, String charserName);
        - decoding input stream using the cpecified charset;
        - returns a String
        - hidden exception: IOException;
      String toString(InputStream is);
        - converts the UTF-8 encoded input stream into a String
        - returns a String
        - hidden exception: IOException;

      boolean isOpen();
        - returns true if it is so
      boolean isClientSide();
        - returns true for the client-side connections
      boolean isSecure();
        - is TLS connection
      WsConnection ready()
        - waiting for the WebSocket handshake to complete

      void setHandler(WsConnection.Handler newHandler);
        - sets the secondary connection handler;
        - calls onClose in the old handler (conn.isOpen() returns true),
          then calls onOpen in the new handler.
      boolean isPrimaryHandler();
        - returns true if it is so

      String getSSLSessionProtocol()
        - returns SSL protocol or null for insecure connection
      WsStatus getStatus();
        - returns clone of the connection status
      int getPort();
        - returns the listening port for server side or connecting port for client side
      String getPeerHost();
        - returns remote host name or null
      String getPath();
        - returns http request path or null
      String getQuery();
        - returns http request query or null
      Map<String, String> getRequestHeaders();
      Since 5.0.0
        - returns the HTTP headers of the WebSocket handshake request
      Map<String, String> getResponseHeaders();
      Since 5.0.0
        - returns the HTTP headers of the WebSocket handshake response

      WsParameters getParameters();
        - returns connection parameters

      void close(int statusCode, String reason);
        - the status code outside 1000-4999 will be replaced with
          1005 (NO_STATUS), the reason is ignored;
        - a reason that is longer than 123 BYTES is truncated;
        - the method blocks outgoing messages (sending methods throw IOException);
        - isOpen() returns false;
        - incoming messages are available until the closing handshake completed.
      void close();
        - closes connection with status code 1000 (NORMAL_CLOSURE)
      void close(String reason);
        - closes connection with status code 1000 (NORMAL_CLOSURE)
          and specified reason


  Class WsStatus:
    The status of the WebSocket connection

    Public Fields:
      int code;         // closing code (-1, 0, 1000-4999)
      String reason;    // closing reason (max length 123 BYTES)
      boolean wasClean; // WebSocket closing handshake completed cleanly
      boolean remotely; // closed remotely
      Throwable error;  // connection execution error or null

    Constants:
      Status codes used by package.
      int IS_INACTIVE = -1;
        - the connection yet not open
      int IS_OPEN = 0;
        - the connection is open
      int NORMAL_CLOSURE = 1000;
        - the connection successfully closed
      int GOING_AWAY = 1001;
        - the server shuts down
      int PROTOCOL_ERROR = 1002;
        - TCP,SSL connection error, illegal HTTP head
          or WebSocket HTTP handshake or data exchange violation
      int NO_STATUS = 1005;
        - the connection was closed without code and reason.
      int ABNORMAL_CLOSURE = 1006;
        - the connection not closed properly (opposite side problems or
          the socket timeout expired, unchecked exception in the handler, etc)
      int POLICY_VIOLATION = 1008;
        - the number of served messages has been exceeded
          (see WsConnection.setMaxMessages method)
      int MESSAGE_TOO_BIG = 1009;
        - the length of the message or frame payload size has been exceeded
          (see WsConnection.setMaxMessageLength method)
      int INTERNAL_ERROR = 1011;
        - server crashed


  Class WsServer extends Thread:  
    This class implements a WebSocket server for insecure or TLS connections.
    If the server shuts down or crashes, all associated connections
    are closed with status code 1001 (GOING_AWAY) or 1011 (INTERNAL_ERROR)
    and it is removed from the WebSocket server list.

    Methods:
      boolean isActive();
        - returns the running state
      boolean isSecure();
        - is TLS connections server
      Throwable getError();
        - returns server error or null
      ServerSocket getServerSocket();
        - returns ServerSocket object;
      int getPort();
        - returns the listening port number.
      WsParameters getParameters();
        - returns the server-side connection parameters
      WsConnection[] listConnections();
        - returns the list of active server-side connections

      void stopServer(); 
      void stopServer(String closeReason);
        - stop listening and close all active connections with 
          code 1001 (GOING_AWAY)
        

  Interface WsServer.Handler:
    The default server handler does nothing.
    
    Methods:
      void onStart(WsServer server);
        - called when the server is started

      void onStop(WsServer server, Exception error);
        - called when the server is closed or crashed;
        - error is a ServerSocketException, RuntimeException or null.

 

Usage examples see in:  
  ./test/websocket/WsBasicTest.java  
  ./test/websocket/WsServerTest.java  
  ./test/websocket/WssClientTest.java  
