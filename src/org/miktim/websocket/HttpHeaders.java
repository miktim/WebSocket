/*
 * HttpHeaders. Read/write/store http headers. MIT (c) 2020-2021 miktim@mail.ru
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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class HttpHeaders {

    public static final String REQUEST_LINE = "Request-Or-Status-Line";
    public static final String STATUS_LINE = REQUEST_LINE;
    
    // keys are case-insensitive
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public HttpHeaders() {
    }
 
    public HttpHeaders set(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public HttpHeaders add(String key, String value) {
        String val = headers.get(key);
        if (val == null || val.isEmpty()) {
            headers.put(key, value);
        } else {
            headers.put(key, val + "," + value);
        }
        return this;
    }
    
    public String get(String key) {
        return headers.get(key);
    }
    
    public Set<String> keySet() {
        Set<String> hks = headers.keySet();
        hks.remove(REQUEST_LINE);
        return hks;
    }

    public HttpHeaders read(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(is));
        String line = br.readLine();
        if (line.startsWith("\u0016\u0003\u0003")) {
            throw new javax.net.ssl.SSLHandshakeException("Plain socket");
        }
        String[] parts = line.split(" ");
        if (!(parts.length > 2
                && (parts[0].equals("HTTP/1.1") || parts[2].equals("HTTP/1.1")))) {
            throw new ProtocolException("SSL required or invalid HTTP request");
        }
        set(REQUEST_LINE, line);
        String key = null;
        while (true) {
            line = br.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                headers.put(key, headers.get(key) + line.trim());
                continue;
            }
            key = line.substring(0, line.indexOf(":"));
            add(key, line.substring(key.length() + 1).trim());
        }
        return this;
    }

    public void write(OutputStream os) throws IOException {
        StringBuilder sb = (new StringBuilder(headers.get(STATUS_LINE))).append("\r\n");
        for (String hn : keySet()) {
            sb.append(hn).append(": ").append(headers.get(hn)).append("\r\n");
        }
        sb.append("\r\n");
        os.write(sb.toString().getBytes());
        os.flush();
    }

}
