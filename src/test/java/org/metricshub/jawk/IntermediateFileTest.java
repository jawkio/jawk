package org.metricshub.jawk;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Collections;

import org.junit.Test;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AwkSyntaxTree;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptFileSource;
import org.metricshub.jawk.util.ScriptSource;

public class IntermediateFileTest {

    private static File compileToAi(String script) throws Exception {
        AwkParser parser = new AwkParser(false, false, Collections.emptyMap());
        ScriptSource src = new ScriptSource("Body", new StringReader(script), false);
        AwkSyntaxTree ast = parser.parse(Collections.singletonList(src));
        ast.semanticAnalysis();
        ast.semanticAnalysis();
        AwkTuples tuples = new AwkTuples();
        ast.populateTuples(tuples);
        tuples.postProcess();
        parser.populateGlobalVariableNameToOffsetMappings(tuples);
        File f = File.createTempFile("jawk-test", ".ai");
        f.deleteOnExit();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
            oos.writeObject(tuples);
        }
        return f;
    }

    @Test
    public void testMultipleIntermediateFiles() throws Exception {
        File ai1 = compileToAi("BEGIN { print \"A\" }");
        File ai2 = compileToAi("BEGIN { print \"B\" }");

        AwkSettings settings = new AwkSettings();
        settings.setDefaultRS("\n");
        settings.setDefaultORS("\n");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        settings.setOutputStream(new PrintStream(baos));

        settings.addScriptSource(new ScriptFileSource(ai1.getAbsolutePath()));
        settings.addScriptSource(new ScriptFileSource(ai2.getAbsolutePath()));

        Awk awk = new Awk();
        awk.invoke(settings);

        assertEquals("A\nB\n", baos.toString("UTF-8"));
    }
}
