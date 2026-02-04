/*
 * WsError. MIT (c) 2026 miktim@mail.ru
 * Indicates serious WebSocket problem.
 */
package org.miktim.websocket;

/**
 * Indicates serious WebSocket problem,
 * contains the error cause.
 * @since 5.0
 */
public class WsError extends RuntimeException {
    WsError(String message, Throwable cause) {
        super(message, cause);
    }
}