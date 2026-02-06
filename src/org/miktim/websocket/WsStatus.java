/*
 * WsStatus. WebSocket connection/server status, MIT (c) 2020-2026 miktim@mail.ru
 *
 * Created: 2021-02-08
 */
package org.miktim.websocket;

/**
 * WebSocket connection status.
 * <p>
 * The class contains public properties and WebSocket predefined close codes.
 * <br>
 * <a href="https://tools.ietf.org/html/rfc6455#section-7.4" target="_blank ">
 * - RFC6455 section-7.4;</a><br>
 * <a href="https://www.iana.org/assignments/websocket/websocket.xml#close-code-number" target="_blank ">
 * - IANA close codes;</a><br>
 * <a href="https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent/code" target="_blank ">
 * - MDN CloseEvent codes.</a><br>
 * </p>
 */
public final class WsStatus {

        /**
         * {@value IS_INACTIVE} The WebSocket connection yet not open.
         */
        public static final int IS_INACTIVE = -1; // connection in progress

        /**
         * {@value IS_OPEN} The WebSocket connection is open.
         */
        public static final int IS_OPEN = 0;
        /**
         * {@value NORMAL_CLOSURE} The WebSocket connection successfully completed.
         */
        public static final int NORMAL_CLOSURE = 1000; //*
        /**
         * {@value GOING_AWAY} The WebSocket server shutdown or connection timeout.
         */
        public static final int GOING_AWAY = 1001; //* 
        /**
         * {@value PROTOCOL_ERROR} Socket connection (SSL/cleartext) error or WebSocket protocol violation.
         */
        public static final int PROTOCOL_ERROR = 1002; //* 
        /**
         * {@value UNSUPPORTED_DATA} Unused.
         */
        public static final int UNSUPPORTED_DATA = 1003; //
        /**
         * {@value NO_STATUS} The connection closed without status code and reason.
         */
        public static final int NO_STATUS = 1005; //* 
        /**
         * {@value ABNORMAL_CLOSURE} Network failure or connection handler crashed.
         */
        public static final int ABNORMAL_CLOSURE = 1006; //* 
        /**
         * {@value INVALID_FRAME} Unused.
         */
        public static final int INVALID_FRAME = 1007; // 
        /**
         * {@value POLICY_VIOLATION} The number of pending messages has been exceeded.
         */
        public static final int POLICY_VIOLATION = 1008; //*
        /**
         * {@value MESSAGE_TOO_BIG} The message or data frame size has been exceeded.
         */
        public static final int MESSAGE_TOO_BIG = 1009; //*
        /**
         * {@value MANDATORY_EXT} Unused.
         */
        public static final int MANDATORY_EXT = 1010; // 
        /**
         * {@value INTERNAL_ERROR} The WebSocket server crashed.
         */
        public static final int INTERNAL_ERROR = 1011; //*
//        public static final int SERVICE_RESTART = 1012; //  
//        public static final int TRY_AGAIN_LATER = 1013; //
//        public static final int TLS_HANDSHAKE = 1015; //

    /**
     * Connection closing code (1000-4999).
     */
    volatile public int code = IS_INACTIVE;  // closing code (1000-4999)

    /**
     * Connection closing reason (max length 123 BYTES).
     */
    public String reason = "";     // closing reason (max length 123 BYTES)

    /**
     * WebSocket closing handshake completed normally.
     */
    public boolean wasClean = false;    // WebSocket closing handshake completed

    /**
     * WebSocket connection closed remotely.
     */
    public boolean remotely = false;     // closed remotely

    /**
     * WebSocket closed due to error.
     */
    public Throwable error = null; // closed due to exception

    WsStatus() {
    }

    synchronized WsStatus deepClone() {
        WsStatus clone = new WsStatus();
        clone.code = code;
        clone.reason = reason;
        clone.wasClean = wasClean;
        clone.remotely = remotely;
        clone.error = error;
        return clone;
    }
    
    synchronized void set(int code, String reason, boolean remotely) {
       this.code = code;
       this.reason = reason;
       this.remotely = remotely;
    }

    /**
     * Returns a textual representation of the WebSocket status.
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
