package org.metricshub.jawk;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.junit.Test;
import org.metricshub.jawk.util.AwkParameters;
import org.metricshub.jawk.util.AwkSettings;

public class OptionParsingTest {

    @Test
    public void doubleDashStopsOptionParsing() throws IOException {
        String[] args = {"-v", "x=1", "--", "BEGIN { print ARGV[1] }", "-v", "y=2"};
        AwkSettings settings = AwkParameters.parseCommandLineArguments(args);

        assertEquals(1, settings.getVariables().get("x"));

        Reader reader = settings.getScriptSources().get(0).getReader();
        try (BufferedReader br = new BufferedReader(reader)) {
            assertEquals("BEGIN { print ARGV[1] }", br.readLine());
        }

        assertEquals(2, settings.getNameValueOrFileNames().size());
        assertEquals("-v", settings.getNameValueOrFileNames().get(0));
        assertEquals("y=2", settings.getNameValueOrFileNames().get(1));
    }

    @Test
    public void singleDashAfterScriptIsFileName() {
        String[] args = {"BEGIN { }", "-"};
        AwkSettings settings = AwkParameters.parseCommandLineArguments(args);
        assertEquals(1, settings.getNameValueOrFileNames().size());
        assertEquals("-", settings.getNameValueOrFileNames().get(0));
    }
}
