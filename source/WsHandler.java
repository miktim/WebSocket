/*
 * WsHandler 
 * MIT (c) 2020 miktim@mail.ru
 *
 * Created: 2020-03-09
 */
package org.samples.java.wsserver;

import java.io.IOException;
import java.io.InputStream;

public interface WsHandler {
    public void onOpen(WsConnection con);
    public void onClose(WsConnection con);
    public void onMessage(WsConnection con, String s );
    public void onMessage(WsConnection con, byte[] b);
    public void onError(WsConnection con, Exception e);
//    public void onTextStream(WsConnection con, InputStream is );
//    public void onBinaryStream(WsConnection con, InputStream is);
}
