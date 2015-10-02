package com.arothian.gorcookie.test;

import com.arothian.gorcookie.CookieReplace;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CookieReplaceTest {

    @Test
    public void executeEngine() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResource("sessioninput.gor").openStream()))) {
            CookieReplace replacer = new CookieReplace();

            StringBuilder request = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if(line.length() > 0 && (line.charAt(0) == '1' || line.charAt(0) == '2' || line.charAt(0) == '3') && line.charAt(1) == ' ' && request.length() > 0) {
                    System.err.println("Sending request");
                    replacer.processGorInput(request.toString().getBytes());
                    request.setLength(0);
                }
                request.append(line);
                request.append("\n");
            }

            System.err.println("Sending request");
            replacer.processGorInput(request.toString().getBytes());

        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }
}
