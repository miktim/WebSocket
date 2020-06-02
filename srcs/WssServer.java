/*
 * WssServer. Secure WebSocket Server, MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-31
 */
package org.samples.java.websocket;

import java.net.InetSocketAddress;

public class WssServer extends WsServer {

    public static final int DEFAULT_SERVER_PORT = 443;

    public WssServer(int port, WsHandler handler)
            throws NullPointerException {
        super(port, handler);
        this.isSecure = true;
    }

    public WssServer(InetSocketAddress isa, WsHandler handler)
            throws NullPointerException {
        super(isa, handler);
        this.isSecure = true;
    }

}
