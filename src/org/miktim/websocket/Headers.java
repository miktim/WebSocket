/*
 * Headers. Http message headers. MIT (c) 2020-2021 miktim@mail.ru
 *
 * Replaces the core class com.sun.net.httpserver.Headers for Android compatibility
 */
package org.miktim.websocket;

import java.util.HashMap; // key case sensitive
import java.util.Set;

class Headers {

    private final HashMap<String, String> headers = new HashMap<>();

    String get(String key) {
        return headers.get(key);
    }

    void set(String key, String value) {
        headers.put(key, value);
    }

    void add(String key, String value) {
        String val = get(key);
        if (val == null || val.isEmpty()) {
            headers.put(key, value);
        } else {
            headers.put(key, val + "," + value);
        }
    }
    
    Set<String> keySet() {
        return headers.keySet();
    }
}
