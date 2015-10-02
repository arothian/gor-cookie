package com.arothian.gorcookie;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CookieReplace {

    public static void main(String[] args) throws FileNotFoundException {

        CookieReplace replacer = new CookieReplace();
        String line;
        Charset utf8 = StandardCharsets.UTF_8;
        Hex hex = new Hex(utf8);

        try (BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, utf8))) {
            while ((line = stdin.readLine()) != null) {
                //Decode the input - its in Hex
                byte[] payload = hex.decode(line.getBytes());

                String output = replacer.processGorInput(new String(payload));
                if(output != null) {
                    //Encode the output
                    String modified = hex.encodeHexString(output.getBytes(utf8));
                    System.out.println(modified);
                } else {
                    System.out.println(line);
                }
            }
        } catch (IOException | DecoderException e) {
            e.printStackTrace(System.err);
        }
    }

    private final HashMap<String, Map<String, String>> requestToOriginalCookies = new HashMap<>();
    private final HashMap<String, String> originalCookieToReplacement = new HashMap<>();

    private static final String COOKIE_VALUE_SEPARATOR = "=";
    private static final String COOKIE_SEPARATOR = ";";

    private static final char ORIGINAL_REQUEST = '1';
    private static final char ORIGINAL_RESPONSE = '2';
    private static final char REPLAY_RESPONSE = '3';

    public CookieReplace() {
    }

    public String processGorInput(String httpStr) throws IOException {
        String[] parts = httpStr.split("\n", -1);
        //Read the custom header that Gor provides
        String originalHeader = parts[0];
        String[] gorHeader = originalHeader.split(" ");
        //Reset parts to just be the raw http request or response
        parts = Arrays.copyOfRange(parts, 1, parts.length);

        String gorRequestID = gorHeader[1];
        Map<String, String> cookies;
        switch(httpStr.charAt(0)) {
            case ORIGINAL_REQUEST:
                return originalHeader+"\n"+processOriginalHttpRequest(parts);
            case ORIGINAL_RESPONSE:
                cookies = CookieReplace.getSetCookieHeaders(parts);
                if (cookies.size() > 0) {
                    requestToOriginalCookies.put(gorRequestID, cookies);
                    System.err.println("Recording Set-Cookie for request " + gorRequestID);
                }
                return null;
            case REPLAY_RESPONSE:
                if (requestToOriginalCookies.containsKey(gorRequestID)) {
                    cookies = CookieReplace.getSetCookieHeaders(parts);
                    Map<String,String> originalCookies = requestToOriginalCookies.remove(gorRequestID);
                    originalCookies.entrySet().parallelStream().filter(originalCookie -> cookies.containsKey(originalCookie.getKey())).forEach(originalCookie -> {
                                String originalVal = originalCookie.getKey() + "=" + originalCookie.getValue();
                                String replacementVal = cookies.get(originalCookie.getKey());
                                System.err.println(String.format("Mapping cookie %s from %s -> %s", originalCookie.getKey(), originalCookie.getValue(), replacementVal));
                                originalCookieToReplacement.put(originalVal, replacementVal);
                            }
                    );
                }
                return null;
            default:
                throw new IOException("Invalid GOR payload - "+httpStr.charAt(0));
        }
    }

    private static Map<String, String> getSetCookieHeaders(String[] parts) {
        HashMap<String, String> headers = new HashMap<>();
        for (String line : parts) {
            if (StringUtils.isBlank(line)) break;
            int splitIndex = line.indexOf(':');
            if (splitIndex < 0 || !"Set-Cookie".equalsIgnoreCase(line.substring(0, splitIndex))) continue;
            int eqIndex = line.indexOf(COOKIE_VALUE_SEPARATOR);
            headers.put(line.substring(splitIndex + 1, eqIndex).trim(), line.substring(eqIndex + 1, line.indexOf(COOKIE_SEPARATOR)).trim());
        }
        return headers;
    }

    private String processOriginalHttpRequest(String[] request) {
        //Log the request being processed
        System.err.println(String.format("Original request - %s", request[0]));
        //For each line, run it through the mapper, which will rewrite any cookies as required then pass the request back
        return Arrays.stream(request).map(line -> rewriteHttpRequest((String) line)).collect(Collectors.joining("\n"));
    }

    private String rewriteHttpRequest(String line) {
        if (StringUtils.isBlank(line)) return line;
        int startIndex = line.indexOf(':');
        if (startIndex < 0 || !"Cookie".equalsIgnoreCase(line.substring(0, startIndex))) return line;

        StringBuilder replacementLine = new StringBuilder(line);
        startIndex++;
        int endIndex = Math.min(replacementLine.indexOf(COOKIE_SEPARATOR, startIndex), replacementLine.length());
        while(endIndex > 0 && endIndex <= replacementLine.length()) {
            //The next cookie is indexes startIndex to endIndex
            int separator = replacementLine.indexOf(COOKIE_VALUE_SEPARATOR, startIndex);

            //Cookie name = startIndex to separator
            String name = replacementLine.substring(startIndex, separator).trim();
            //Cookie value = separator+1 to endIndex
            String value = replacementLine.substring(separator+1, endIndex).trim();

            String replacementValue = originalCookieToReplacement.get(replacementLine.substring(startIndex, endIndex));
            if(replacementValue != null) {
                System.err.println(String.format("Rewriting %s from %s to %s", name, value, replacementValue));
                replacementLine.replace(separator+1, endIndex, replacementValue);
            }
            startIndex = endIndex+2; //If there are additional cookies, they start two indexes later '; NEXTCOOKIE'
            endIndex = Math.min(replacementLine.indexOf(COOKIE_SEPARATOR, startIndex), replacementLine.length());
        }
        return replacementLine.toString();
    }
}
