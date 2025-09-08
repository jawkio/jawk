package org.metricshub.jawk;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AstNode;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;

public class ExtensionFallbackTest {

	@Test
	public void testExtensionFallback() throws Exception {
		JawkExtension myExtension = new FallbackTestExtension();
		Map<String, JawkExtension> myExtensionMap = Arrays
				.stream(myExtension.extensionKeywords())
				.collect(Collectors.toMap(k -> k, k -> myExtension));

		AwkSettings settings = new AwkSettings();
		settings.setDefaultRS("\n");
		settings.setDefaultORS("\n");
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		settings.setOutputStream(new PrintStream(resultBytesStream));
		settings
				.addScriptSource(
						new ScriptSource(
								"Body",
								new StringReader("BEGIN { ab[1] = \"a\"; ab[2] = \"b\"; printf myExtensionFunction(3, ab) }"),
								false));

		AVM avm = null;
		try {
			AwkParser parser = new AwkParser(myExtensionMap);
			AstNode ast = parser.parse(settings.getScriptSources());
			ast.semanticAnalysis();
			ast.semanticAnalysis();
			AwkTuples tuples = new AwkTuples();
			ast.populateTuples(tuples);
			tuples.postProcess();
			parser.populateGlobalVariableNameToOffsetMappings(tuples);
			avm = new AVM(settings, myExtensionMap);
			avm.interpret(tuples);
		} catch (ExitException e) {
			if (e.getCode() != 0) {
				throw e;
			}
		} finally {
			if (avm != null) {
				avm.waitForIO();
			}
		}
		String resultString = resultBytesStream.toString("UTF-8");
		assertEquals("ababab", resultString);
		assertEquals(1, ((FallbackTestExtension) myExtension).getInvokeCount());
	}
}
