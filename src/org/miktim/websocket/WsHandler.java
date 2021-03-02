/*
 * WsHandler. WebSocket connection handler, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

public interface WsHandler {
    public void onOpen(WsConnection conn);
    public void onClose(WsConnection conn);
    public void onMessage(WsConnection conn, String s );
    public void onMessage(WsConnection conn, byte[] b);
// onFrame: 
//   - called when the specified maximum message length is exceeded and framing is enabled;
//   - eom (end of message): 1 - for text; 2 - for binary; 0 - continued;
//  public void onFrame(WsConnection conn, byte[] payload, int eom);
// onError:
//   - conn can be null in the listener handler;
//   - check connection is open.
    public void onError(WsConnection conn, Exception e);
}
