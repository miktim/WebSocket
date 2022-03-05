/*
 * HttpHead. Read/write/store HTTP message head. MIT (c) 2020-2021 miktim@mail.ru
 *
 * Notes:
 *  - the header names (keys) ara case-insensitive;
 *  - multiple values are stored on a comma separated string;
 *  - the request/status line of the HTTP message head is accessed using the START_LINE constant.
 *
 * Created: 2020-11-19
 */
package org.miktim.websocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HttpHead {

    public static final String START_LINE = "http-message-head-start-line";

    private final TreeMap<String, String> head = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public HttpHead() {
    }

// Create or overwrite header value 
    public HttpHead set(String key, String value) {
        head.put(key, value);
        return this;
    }

// Add comma separated value or create header
    public HttpHead add(String key, String value) {
        String val = head.get(key);
        if (val == null || val.trim().isEmpty()) {
            head.put(key, value);
        } else {
            head.put(key, val + "," + value);
        }
        return this;
    }

    public String remove(String key) {
        return head.remove(key);
    }

    public String get(String key) {
        return head.get(key);
    }

    public boolean containsKey(String key) {
        return head.containsKey(key);
    }

// Returns list of header names    
    public List<String> nameList() {
        List<String> names = new ArrayList<>(head.keySet());
        names.remove(HttpHead.START_LINE);
        return names;
    }

    public Map<String, String> headMap() {
        return head;
    }

    public HttpHead read(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(is));
        String line = br.readLine();
//        if (line.startsWith("\u0016\u0003\u0003")) {
//            throw new javax.net.ssl.SSLHandshakeException("Plain socket");
//        }
        String[] parts = line.split(" ");
        if (!(parts.length > 2
                && (parts[0].startsWith("HTTP/") || parts[2].startsWith("HTTP/")))) {
            throw new ProtocolException("Invalid HTTP request or SSL required");
        }
        set(START_LINE, line);
        String key = null;
        while (true) {
            line = br.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                head.put(key, head.get(key) + line.trim());
                continue;
            }
            key = line.substring(0, line.indexOf(":"));
            add(key, line.substring(key.length() + 1).trim());
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = (new StringBuilder(head.get(START_LINE))).append("\r\n");
        for (String hn : nameList()) {
            sb.append(hn).append(": ").append(head.get(hn)).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    public void write(OutputStream os) throws IOException {
        os.write(toString().getBytes());
        os.flush();
    }

}
