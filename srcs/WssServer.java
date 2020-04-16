/*
 * my Secure WebSocket Server java SE 1.8+
 * MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-31
 */
package org.samples.java.wsserver;

public class WssServer extends WsServer {

    public static final int DEFAULT_SERVER_PORT = 443;

    public WssServer() {
        super();
        this.isSSL = true;
    }
}