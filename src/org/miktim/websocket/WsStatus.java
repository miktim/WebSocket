/*
 * WsStatus. WebSocket connection status, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Created: 2021-02-08
 */
package org.miktim.websocket;

/**
 * WebSocket connection status.
 * <p>
 * Descriptions of predefined WebSocket closing codes:<br>
 * <a href="https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent/code" target="_blank ">
 * - MDN CloseEvent codes.</a><br>
 * <a href="https://tools.ietf.org/html/rfc6455#section-7.4" target="_blank ">
 * - RFC6455 section-7.4;</a><br>
 * <a href="https://www.iana.org/assignments/websocket/websocket.xml#close-code-number" target="_blank ">
 * - IANA close codes;</a><br>
 * </p>
 */
public final class WsStatus {

        /**
         * WebSocket connection yet not open.
         */
        public static final int IS_INACTIVE = -1; // connection in progress

        /**
         * WebSocket connection is open.
         */
        public static final int IS_OPEN = 0;
        public static final int NORMAL_CLOSURE = 1000; //
        public static final int GOING_AWAY = 1001; //* 
        public static final int PROTOCOL_ERROR = 1002; //* 
        public static final int UNSUPPORTED_DATA = 1003; // 
        public static final int NO_STATUS = 1005; //* 
        public static final int ABNORMAL_CLOSURE = 1006; //* 
        public static final int INVALID_DATA = 1007; // 
        public static final int POLICY_VIOLATION = 1008; //
        public static final int MESSAGE_TOO_BIG = 1009; //*
        public static final int MANDATORY_EXT = 1010; //* 
        public static final int INTERNAL_ERROR = 1011; //*
        public static final int SERVICE_RESTART = 1012; //  
        public static final int TRY_AGAIN_LATER = 1013; //
        public static final int TLS_HANDSHAKE = 1015; //

    /**
     * Closing code (1000-4999).
     */
    public int code = IS_INACTIVE;  // closing code (1000-4999)

    /**
     * Closing reason (max length 123 BYTES).
     */
    public String reason = "";     // closing reason (max length 123 BYTES)

    /**
     * WebSocket closing handshake completed normally.
     */
    public boolean wasClean = false;    // WebSocket closing handshake completed

    /**
     * WebSocket closed remotely.
     */
    public boolean remotely = false;     // closed remotely

    /**
     * WebSocket closed due to error.
     */
    public Throwable error = null; // closed due to exception

    WsStatus() {
    }

    WsStatus deepClone() {
        WsStatus clone = new WsStatus();
        clone.code = code;
        clone.reason = reason;
        clone.wasClean = wasClean;
        clone.remotely = remotely;
        clone.error = error;
        return clone;
    }

    /**
     * Returns a textual representation of the WebSocket status.
     *
     * @return WebSocket status as String.
     */
    @Override
    public String toString() {
        return String.format("WsStatus(%d,\"%s\",%s,%s%s)",
                 code, reason, (wasClean ? "clean" : "dirty"),
                 (remotely ? "remotely" : "locally"),
//                 (error != null ? ",error" : "")
                 (error != null ? "," + error.getClass().getName() : "")
        );
    }

}
