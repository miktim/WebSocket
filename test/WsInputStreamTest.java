
/*
 * WsInputStreamTest. Licence MIT (c) 2020 miktim@mail.ru
 * Created: 2020-10-12
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.samples.java.websocket.WsInputStream;

public class WsInputStreamTest {

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        System.out.println("\r\nWsInputStream Test\r\n");
        FileInputStream fis = new FileInputStream("readme");
        WsInputStream wis = new WsInputStream(1000);
        byte[] fbuf = new byte[20];
        byte[] wbuf = new byte[20];
        int cnt = fis.read(fbuf);
        System.out.println("Wait: \"" + new String(fbuf) + "\"");
        wis.write(fbuf, 0, cnt);
        wis.read(wbuf);
        System.out.println("Get:  \"" + new String(wbuf) + "\"");
        fis.read(fbuf);
        System.out.println("Wait: \"" + new String(fbuf) + "\"");
        wis.write(fbuf);
        Thread.sleep(300);
        wis.read(wbuf);
        System.out.println("Get:  \"" + new String(wbuf) + "\"");
        System.out.println("Wait: EOF(-1)");
        wis.writeEOF();
        wis.read();
        cnt = wis.read(wbuf);
        System.out.println("Get: " + cnt);
        System.out.println("Wait: IOException: Post-EOF Writing");
        try {
            cnt = wis.write(fbuf); 
            System.out.println("Get: " + cnt + " WRONG!");
        } catch (IOException e) {
            System.out.println("Get: " + e.toString());
        }

        wis.close();
        System.out.println("Wait: IOException: Stream Closed");
        try {
            cnt = wis.read(wbuf);
            System.out.println("Get: " + cnt + " WRONG!");
        } catch (IOException e) {
            System.out.println("Get: " + e.toString());
        }

        wis = new WsInputStream(1000);
        System.out.println("Wait: IOException: Stream Closed (Timeout)");
        try {
            cnt = wis.write(fbuf);
            Thread.sleep(2000); // timeout
            cnt = wis.read(wbuf);
            System.out.println("Get: " + cnt + " WRONG!");
        } catch (IOException e) {
            System.out.println("Get: " + e.toString());
        }
    }
}
