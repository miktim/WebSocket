/*
 * WsStatus. WebSocket connection status, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Created: 2021-02-08
 */
package org.miktim.websocket;

public class WsStatus implements Cloneable {
// Predefined WebSocket closure codes:
//  https://tools.ietf.org/html/rfc6455#section-7.4
//  https://www.iana.org/assignments/websocket/websocket.xml#close-code-number 
//  https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent

    public static final int IS_OPEN = 0;
    public static final int NORMAL_CLOSURE = 1000; //
    public static final int GOING_AWAY = 1001; //* 
    public static final int PROTOCOL_ERROR = 1002; //* 
    public static final int UNSUPPORTED_DATA = 1003; // 
    public static final int NO_STATUS = 1005; //* 
    public static final int ABNORMAL_CLOSURE = 1006; //* 
    public static final int INVALID_DATA = 1007; // 
    public static final int POLICY_VIOLATION = 1008; //
    public static final int MESSAGE_TOO_BIG = 1009; //
    public static final int UNSUPPORTED_EXTENSION = 1010; //* 
    public static final int INTERNAL_ERROR = 1011; //*
    public static final int SERVICE_RESTART = 1012; //  
    public static final int TRY_AGAIN_LATER = 1013; //

    public int code = GOING_AWAY;  // closing code (1000-4999)
    public String reason = "";     // closing reason (max length 123 BYTES)
    public boolean clean = false;  // WebSocket closing handshake completed
    public boolean remote = false; // closed remotely
    public Throwable error = null; // closed due to exception

    WsStatus() {
    }

    @Override
    public WsStatus clone() {
        WsStatus clone = this;
        try {
            clone = (WsStatus) super.clone();
        } catch (CloneNotSupportedException ex) {
        }
        return clone;
    }

    @Override
    public String toString() {
        return String.format("WsStatus(%d,\"%s\",%s%s)",
                code, reason, clean, (remote ? ",remotely" : ""));
    }

}
