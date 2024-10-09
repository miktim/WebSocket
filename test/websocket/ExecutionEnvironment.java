/*
 * SecureInfo. MIT (c) 2024 miktim@mail.ru
 * Created: 2024-03-02
 */

import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

public class ExecutionEnvironment {

    static void ws_log(String msg) {
        System.out.println(String.valueOf(msg));
    }

    public static String join(Object[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj).append(delimiter);
        }
        return sb.delete(sb.length() - delimiter.length(), sb.length()).toString();
    }

    public static void main(String[] args) throws Exception {
        ws_log("Execution environment:");
        String[] sa = new String[]{"os.name","os.version","java.vendor","java.version"};
        for(String s : sa) {
            ws_log(s + ": " + System.getProperty(s));
        }
        
        SSLContext sslContext = SSLContext.getDefault();
        SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
        ws_log("\r\nDefault SSLParameters.\r\nProtocols:");
        sa = sslParameters.getProtocols();
        ws_log("\"" + join(sa, "\", \"") + "\"");
        sa = sslParameters.getCipherSuites();
        ws_log("Chipher suites:");
        ws_log("\"" + join(sa, "\",\r\n\"") + "\"");

        ws_log("\r\nKeyManagerFactory default algorithm:\r\n\""
                + KeyManagerFactory.getDefaultAlgorithm() + "\"");
        ws_log("KeyStore default type:\r\n\""
                + KeyStore.getDefaultType() + "\"");
        ws_log("TrustManagerFactory default algorithm:\r\n\""
                + TrustManagerFactory.getDefaultAlgorithm() + "\"");

    }

}
