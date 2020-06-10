/*
 * WsHandler. WebSocket client/server handler, MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */
package org.samples.java.websocket;

//import java.io.InputStream;

public interface WsHandler {
    public void onOpen(WsConnection conn);
    public void onClose(WsConnection conn);
    public void onMessage(WsConnection conn, String s );
    public void onMessage(WsConnection conn, byte[] b);
// onError: check connection closure status
//   conn may be null in the server handler (fatal ServerSocket exception)
    public void onError(WsConnection conn, Exception e);
// for the future if the maximum message length is exceeded
//   public void onStream(WsConnection conn, WsConnection.TextInputStream is );
//   public void onStream(WsConnection conn, WsConnection.BinaryInputStream is);
}
