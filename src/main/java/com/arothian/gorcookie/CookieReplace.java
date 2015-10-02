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
                byte[] payload = hex.decode(line.getBytes(utf8));
                //Process the gor payload
                byte[] output = replacer.processGorInput(payload);
                //Encode the output
                System.out.println(hex.encodeHexString(output));
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

    public byte[] processGorInput(byte[] httpPayload) throws IOException {
        //Get the plain text portion of the http request/response (all byte data prior to the double newline)
        int splitIndex = -1;
        for(int i=0; i<httpPayload.length-1; i++) {
            if(httpPayload[i] == '\n') {
                if(httpPayload[i+1] == '\n') { //Found two newlines in a row
                    splitIndex = i + 2;
                    break;
                } else if(i < httpPayload.length-2 && httpPayload[i+1] == '\r' && httpPayload[i+2] == '\n') { //Found a newline cr newline, ok
                    splitIndex = i + 3;
                    break;
                }
            }
        }
        if(splitIndex == -1) {
            System.err.println(new String(httpPayload, StandardCharsets.UTF_8));
            System.err.println("Unable to process http payload**");
            return httpPayload;
        }

        String httpText = new String(Arrays.copyOfRange(httpPayload, 0, splitIndex), StandardCharsets.UTF_8);
        String[] parts = httpText.split("\n", -1);
        //Read the custom header that Gor provides
        String originalHeader = parts[0];
        String[] gorHeader = originalHeader.split(" ");
        //Reset parts to just be the raw http request or response
        parts = Arrays.copyOfRange(parts, 1, parts.length);

        String gorRequestID = gorHeader[1];
        Map<String, String> cookies;
        switch(httpText.charAt(0)) {
            case ORIGINAL_REQUEST:
                byte[] originalHeaderBytes = originalHeader.getBytes(StandardCharsets.UTF_8);
                byte[] modifiedRequest = processOriginalHttpRequest(parts).getBytes(StandardCharsets.UTF_8);
                //Put all the pieces back together again
                byte[] modifiedPayload = new byte[originalHeaderBytes.length+1+modifiedRequest.length+httpPayload.length-splitIndex];
                //Copy in the gor header line and add a new line
                System.arraycopy(originalHeaderBytes, 0, modifiedPayload, 0, originalHeaderBytes.length);
                modifiedPayload[originalHeaderBytes.length] = '\n';
                //Copy in the modified http request plain-text
                System.arraycopy(modifiedRequest, 0, modifiedPayload, originalHeaderBytes.length+1, modifiedRequest.length);
                //Copy in any remaining http data
                if(httpPayload.length-splitIndex > 0) {
                    System.arraycopy(httpPayload, splitIndex, modifiedPayload, originalHeaderBytes.length + 1 + modifiedRequest.length, httpPayload.length - splitIndex);
                }
                return modifiedPayload;
            case ORIGINAL_RESPONSE:
                cookies = CookieReplace.getSetCookieHeaders(parts);
                if (cookies.size() > 0) {
                    requestToOriginalCookies.put(gorRequestID, cookies);
                    System.err.println("Recording Set-Cookie for request " + gorRequestID);
                }
                return httpPayload;
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
                return httpPayload;
            default:
                throw new IOException("Invalid GOR payload - "+httpText.charAt(0));
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
