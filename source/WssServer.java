/*
 * my SSL WebSocket Server java SE 1.8+
 * MIT (c) 2020 miktim@mail.ru
 * RFC-6455: https://tools.ietf.org/html/rfc6455
 *
 * Release notice:
 * - WebSocket extensions not supported
 *
 * Created: 2020-03-31
 */
package org.samples.java.wsserver;

import java.net.InetSocketAddress;

public class WssServer extends WsServer {

    public WssServer() {
        this.isSSL = true;
        this.ssoAddress = new InetSocketAddress(443);
    }
}
