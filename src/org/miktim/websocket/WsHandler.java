/*
 * WsHandler. WebSocket connection events handler, MIT (c) 2020-2021 miktim@mail.ru
 *
 * There are two scenarios for handling connection events:
 * - onError (if SSL/WebSocket handshake or ServerSocket fails);
 * - onOpen - [onMessage - onMessage - ...] - [onError] - onClose.
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
//   - the WebSocket message is represented by an input stream of binary data or UTF-8 characters;
//   - exiting the handler closes the stream.
    public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);

// onError (for logging and debugging):
//   - any error closes the WebSocket connection;
//   - allocating large buffers may throw an OutOfMemoryError;
//   - conn can be null in the listener handler on ServerSocket failure.
    public void onError(WsConnection conn, Throwable e);

    public void onClose(WsConnection conn, WsStatus closeStatus);
}
