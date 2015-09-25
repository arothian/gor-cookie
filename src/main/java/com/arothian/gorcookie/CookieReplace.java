package com.arothian.gorcookie;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

public class CookieReplace {
    
    public static void main(String[] args) {

        File log = new File("cookie.log");
        
        try (PrintStream writer = new PrintStream(log)) {
            String line = null;    
            Charset utf8 = StandardCharsets.UTF_8;
            Hex hex = new Hex(utf8);
            HashMap<String, Map<String, String>> requestToOriginalCookies = new HashMap<String, Map<String, String>>();
            HashMap originalCookieToReplacement = new HashMap();
            writer.println("Starting...");
            try (BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))) {
                while ((line = stdin.readLine()) != null) {
                    
                    //Decode the input - its in Hex
                    byte[] payload = hex.decode(line.getBytes(utf8));
                    String payStr = new String(payload, utf8);
                    String[] parts = payStr.split("\n");
                    String[] gorHeader = parts[0].split(" ");
                    char gorPayload = gorHeader[0].charAt(0);
                    String gorRequestID = gorHeader[1];

                    switch(payload[0]) {
                    case 1:
                        writer.println(parts[1]);
                        //TODO: handle searching and replacing cookies
                        System.out.println(line);
                        break;
                    case 2:
                        Map<String, String> cookies = CookieReplace.getSetCookieHeaders(parts);
                        if (cookies.size() > 0) {
                            requestToOriginalCookies.put(gorRequestID, cookies);
                            writer.println("Recording Set-Cookie for request " + gorRequestID);
                        }
                        System.out.println(line);
                        break;
                    case 3:
                        if (requestToOriginalCookies.containsKey(gorRequestID)) {
                            Map<String, String> cookies = CookieReplace.getSetCookieHeaders(parts);
                            Map originalCookies = (Map)requestToOriginalCookies.remove(gorRequestID);
                            originalCookies.entrySet().parallelStream().filter(originalCookie -> cookies.containsKey(originalCookie.getKey())).forEach(originalCookie -> {
                                String originalVal = (String)originalCookie.getKey() + "=" + (String)originalCookie.getValue();
                                String replacementVal = (String)originalCookie.getKey() + "=" + (String)cookies.get(originalCookie.getKey());
                                writer.println("Mapping " + originalVal + " -> " + replacementVal);
                                originalCookieToReplacement.put(originalVal, replacementVal);
                            }
                            );
                        }
                        System.out.print(line);
                        break;
                    default: 
                        throw new IOException("Invalid GOR payload - "+payload[0]);
                    }
                }
            } catch (IOException | DecoderException e) {
                e.printStackTrace(writer);
            }
        }
    }
    
    public static Map<String, String> getSetCookieHeaders(String[] parts) {
        HashMap<String, String> headers = new HashMap<String, String>();
        for (String line : parts) {
            if (StringUtils.isBlank((CharSequence)line)) break;
            int splitIndex = line.indexOf(58);
            if (splitIndex < 0 || !"Set-Cookie".equals(line.substring(0, splitIndex))) continue;
            int eqIndex = line.indexOf(61);
            headers.put(line.substring(splitIndex + 1, eqIndex), line.substring(eqIndex + 1, line.indexOf(59)));
        }
        return headers;
    }
}
