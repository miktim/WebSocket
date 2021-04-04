/*
 * WsHandler. WebSocket connection events handler, MIT (c) 2020-2021 miktim@mail.ru
 *
 * There are two scenarios for handling connection events:
 * - onError (if SSL/WebSocket handshake or ServerSocket fails);
 * - onOpen - [onMessage, onMessage, ...] - [onError] - onClose.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.InputStream;

public interface WsHandler {
// onOpen:
//   - the second argument is the negotiated WebSocket subprotocol or null.    
    public void onOpen(WsConnection conn, String subProtocol);

// onMessage:
//   - WebSocket message presented by input stream of binary data or UTF-8 characters;    
//   - exiting the handler closes the stream.
    public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);

// onError:
//   - conn can be null in the listener handler on ServerSocket failure.
    public void onError(WsConnection conn, Exception e);

    public void onClose(WsConnection conn, WsStatus closeStatus);
}
