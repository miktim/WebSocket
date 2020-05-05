/*
 * WssServer. Secure WebSocket Server, MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-31
 */
package org.samples.java.websocket;

public class WssServer extends WsServer {

    public static final int DEFAULT_SERVER_PORT = 443;

    public WssServer() {
        super();
        this.isSecure = true;
    }
}
