/*
 * WsHandler. WebSocket connection handler, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

public interface WsHandler {
    public void onOpen(WsConnection conn);
    public void onClose(WsConnection conn);
    public void onMessage(WsConnection conn, String message);
    public void onMessage(WsConnection conn, byte[] message);
// onMessage: 
//   - called when the specified maximum message length is exceeded and framing is enabled;
//   - eom (end of message): 1 - end of text; 2 - end of binary; 0 - continued;
//  public void onMessage(WsConnection conn, byte[] data, int eom);
// onError:
//   - conn can be null in the listener handler.
    public void onError(WsConnection conn, Exception e);
}
