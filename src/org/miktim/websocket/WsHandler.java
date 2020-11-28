/*
 * WsHandler. WebSocket connection handler, MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

public interface WsHandler {
    public void onOpen(WsConnection conn);
    public void onClose(WsConnection conn);
    public void onMessage(WsConnection conn, String s );
    public void onMessage(WsConnection conn, byte[] b);
// streaming message: for the future if the specified maximum message length is exceeded
//   - exiting the handler closes the input stream.
//  public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);
// onError:
//   - conn can be null in the listener handler (ServerSocket accept() exception, InterruptedException);
//   - check connection closure status.
    public void onError(WsConnection conn, Exception e);
}
