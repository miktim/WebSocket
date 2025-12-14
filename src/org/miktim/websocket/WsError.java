/*
 * WsError. MIT (c) 2020-2025 miktim@mail.ru
 * Unchecked exception "hides" real checked cause
 */
package org.miktim.websocket;

/**
 * Unchecked exception "hides" real checked cause.
 * @since 5.0
 */
public class WsError extends Error {
    WsError(String message, Throwable cause) {
        super(message, cause);
    }
}
