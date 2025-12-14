/*
 * HttpHead. Read/write/store HTTP message head. MIT (c) 2020-2025 miktim@mail.ru
 *
 * Notes:
 *  - the header names are case-insensitive;
 *  - multiple header values are stored in a comma separated string
 *
 * 2025-11:
 * - setStartLine, getStartLine methods added
 * - fixed excepion's messages
 * - fuxes join
 * 2023-10:
 * - functions join, getValues, setValues added
 *
 * Created: 2020-11-19
 */
package org.miktim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

class HttpHead extends TreeMap<String, String> {

    private String startLine = "";

// Antonym of Java String.split method
    public static String join(Object[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj).append(delimiter);
        }
        return sb.substring(0, sb.length() - delimiter.length());
    }

    public HttpHead() {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    public HttpHead(Map<String, String> map) {
        this();
        putAll(map);
    }
    
    public HttpHead setStartLine(String line) {
        startLine = line;
        return this;
    }
    public String getStartLine() {
        return startLine;
    }
// Create or overwrite header value 
    public HttpHead set(String header, String value) {
        put(header, value);
        return this;
    }
// Create header or add comma separated value
    public HttpHead add(String header, String value) {
        String val = get(header);
        if (val == null || val.trim().isEmpty()) {
            put(header, value);
        } else {
            put(header, val + ", " + value);
        }
        return this;
    }

    public boolean exists(String key) {
        return containsKey(key);
    }

    public HttpHead setValues(String header, String[] values) {
        if (values == null) {
            return this;
        }
        return set(header, join(values, ", "));
    }

    public String[] listValues(String header) {
        if (!exists(header)) {
            return null;
        }
        String[] values = get(header).split(",");
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        return values;
    }

// Returns list of header names    
    public String[] listHeaders() {
        return (new ArrayList<String>(keySet())).toArray(new String[0]);
    }

    String readHeaderLine(InputStream is) throws IOException {
        byte[] bb = new byte[1024];
        int i = 0;
        int b = is.read();
        while (b != '\n' && b != -1 ) {
            bb[i++] = (byte) b;
            b = is.read();
        }

        if (b == '\n' && bb[i - 1] == '\r') {
            return new String(bb, 0, i - 1); // header line MUST ended CRLF
        }
        throw new ProtocolException("Invalid HTTP header");
    }

    public HttpHead read(InputStream is) throws IOException {
        String line = readHeaderLine(is);
        String[] parts = line.split(" ");
        if (!(parts.length > 2
                && (parts[0].startsWith("HTTP/") || parts[2].startsWith("HTTP/")))) {
            throw new ProtocolException("Invalid HTTP header");
        }
        setStartLine(line);
        String key = null;
        while (true) {
            line = readHeaderLine(is);
            if (line == null || line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                put(key, get(key) + line.trim());
                continue;
            }
            key = line.substring(0, line.indexOf(":"));
            add(key, line.substring(key.length() + 1).trim());
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = (new StringBuilder(getStartLine())).append("\r\n");
        for (String hn : listHeaders()) {
            sb.append(hn).append(": ").append(get(hn)).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    public void write(OutputStream os) throws IOException {
        os.write(toString().getBytes());
        os.flush();
    }

}
