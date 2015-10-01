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
                if(line.isEmpty()) {
                    replacer.processGorInput(request.toString());
                    request.setLength(0);
                } else {
                    request.append(line);
                    request.append("\n");
                }
            }
            //Send the final request
            replacer.processGorInput(request.toString());

        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }
}
