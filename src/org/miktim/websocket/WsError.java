/*
 * WsError. MIT (c) 2020-2025 miktim@mail.ru
 * Indicates serious WebSocket problem.
 */
package org.miktim.websocket;

/**
 * Indicates serious WebSocket problem.
 * Contains the error cause.
 * @since 5.0
 */
public class WsError extends Error {
    WsError(String message, Throwable cause) {
        super(message, cause);
    }
}
