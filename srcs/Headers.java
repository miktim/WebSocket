/*
 * Headers. Http message headers. MIT (c) 2020 miktim@mail.ru
 *
 * Replaces the core class com.sun.net.httpserver.Headers for Android compatibility
 */
package org.samples.java.websocket;

import java.util.HashMap;
import java.util.Set;

public class Headers {

    private final HashMap<String, String> headers = new HashMap<String, String>();

    public String getFirst(String key) {
        return headers.get(key);
    }

    public void add(String key, String value) {
        String val = getFirst(key);
        if (val == null || val.isEmpty()) {
            headers.put(key, value);
        } else {
            headers.put(key, val + "," + value);
        }
    }
    
    public Set<String> keySet() {
        return headers.keySet();
    }
}
