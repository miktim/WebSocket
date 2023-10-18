/*
 * IDNsExample class.  MIT (c) 2023 miktim@mail.ru
 * International Domain Names (IDNs) support example 
 */
//package org.miktim.lang;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IDNsExample {
//  Supported uri format: [scheme:][//[user-info@]host][:port][/path][?query][#fragment]

    public static URI idnsURI(String uri) throws URISyntaxException {
// https://stackoverflow.com/questions/9607903/get-domain-name-from-given-url
        Pattern pattern = Pattern.compile("^(([^:/?#]+):)?(//(([^@]+)@)?([^:/?#]*))?(.*)?$");
        Matcher matcher = pattern.matcher(uri);
        matcher.find();
        String host = null;
        if (matcher.groupCount() > 5) {
            host = matcher.group(6); // extract host from uri
        }
        if (host != null) {
            uri = uri.replace(host, java.net.IDN.toASCII(host));
        }
        return new URI(uri);
    }

    public static void main(String[] args) throws Exception {
        String[] uris = new String[]{
            "",
            "wss://域名.cn:8080/路径/",
            "ws://域名.cn:8080",
            "wss://域名.cn",
            "//www.домен.ru?параметр=значение#124",
            "//user:password@www.домен.ru#124",
            "wss:/",
            "/путь"
        };
        for (String uri : uris) {
            System.out.println("uri: " + uri
                    + " IDNs uri: " + idnsURI(uri).toString()
                    + " IDNs host: " + idnsURI(uri).getHost());
        }

    }

}
