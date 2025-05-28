package org.metricshub.jawk;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptFileSource;
import org.metricshub.jawk.util.ScriptSource;

public class AwkTestHelper {

	/** Temporary directory where to store temporary stuff */
	private static String tempDirectory;

	static {
		Path tempDirectoryPath;
		try {
			tempDirectoryPath = Files.createTempDirectory("jawk-test");
			tempDirectoryPath.toFile().deleteOnExit();
			tempDirectory = tempDirectoryPath.toFile().getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the path to a temp directory (as a String)
	 */
	static String getTempDirectory() {
		return tempDirectory;
	}

	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFile Path to the file to be parsed by the AWK script
	 * @return the printed output of the script as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String runAwk(File scriptFile, File inputFile) throws IOException, ExitException, ClassNotFoundException {
		return runAwk(scriptFile, Collections.singletonList(inputFile));
	}

	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFileList List of files that contain the input to be parsed by the AWK script
	 * @return the printed output of the script as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String runAwk(File scriptFile, List<File> inputFileList) throws IOException, ExitException, ClassNotFoundException {
		return runAwk(scriptFile, inputFileList, false);
	}

	/**
	 * Executes the specified AWK script
	 * <p>
	 * @param scriptFile File containing the AWK script to execute
	 * @param inputFileList List of files that contain the input to be parsed by the AWK script
	 * @param setTempDir Whether to set the TEMPDIR variable for the AWK script to play with
	 * @return the printed output of the script as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String runAwk(File scriptFile, List<File> inputFileList, boolean setTempDir) throws IOException, ExitException, ClassNotFoundException {
		AwkSettings settings = new AwkSettings();

		// Default record separator should support both CRLF and LF
		settings.setDefaultRS("\r?\n");
		settings.setDefaultORS("\n");

		// Set the input files
		List<String> files = settings.getNameValueOrFileNames();
		files.addAll(inputFileList.stream().map(File::getAbsolutePath).collect(Collectors.toList()));
		settings.setNameValueOrFileNames(files);

		// Set TEMPDIR so the AWK scripts can "play" with it
		if (setTempDir) {
			Map<String, Object> vars = settings.getVariables();
			vars.put("TEMPDIR", tempDirectory);
			settings.setVariables(vars);
		}

		// Create the OutputStream, to collect the result as a String
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		settings.setOutputStream(new PrintStream(resultBytesStream));

		// Sets the AWK script to execute
		settings.addScriptSource(new ScriptFileSource(scriptFile.getAbsolutePath()));

		// Execute the awk script against the specified input
		Awk awk = new Awk();
		try {
			awk.invoke(settings);
		} catch (ExitException e) {
			if (e.getCode() != 0) {
				throw e;
			}
		}

		// Return the result as a string
		return resultBytesStream.toString("UTF-8");
	}

	/**
	 * Executes the specified script against the specified input
	 * <p>
	 * @param script AWK script to execute (as a String)
	 * @param input Text to process (as a String)
	 * @return result as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String runAwk(String script, String input) throws IOException, ExitException, ClassNotFoundException {
		return runAwk(script, input, false);
	}

	/**
	 * Executes the specified script against the specified input
	 * <p>
	 * @param script AWK script to execute (as a String)
	 * @param input Text to process (as a String)
	 * @param setTempDir Whether to set the TEMPDIR variable for the AWK script to play with
	 * @return result as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String runAwk(String script, String input, boolean setTempDir) throws IOException, ExitException, ClassNotFoundException {
		AwkSettings settings = new AwkSettings();

		// Set the input
		if (input != null) {
			settings.setInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		}

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultRS("\n");
		settings.setDefaultORS("\n");

		// Create the OutputStream, to collect the result as a String
		ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		settings.setOutputStream(new PrintStream(resultBytesStream));

		// Set TEMPDIR so the AWK scripts can "play" with it
		if (setTempDir) {
			Map<String, Object> vars = settings.getVariables();
			vars.put("TEMPDIR", tempDirectory);
			settings.setVariables(vars);
		}

		// Sets the AWK script to execute
		settings.addScriptSource(new ScriptSource("Body", new StringReader(script), false));

		// Execute the awk script against the specified input
		Awk awk = new Awk();
		try {
			awk.invoke(settings);
		} catch (ExitException e) {
			if (e.getCode() != 0) {
				throw e;
			}
		}

		// Return the result as a string
		return resultBytesStream.toString("UTF-8");
	}

	/**
	 * Evaluates the specified AWK expression
	 * <p>
	 * @param expression AWK expression to evaluate (e.g. <code>2 + "3.0"</code>)
	 * @return result as a String
	 * @throws ExitException when the AWK script forces its exit with a specified code
	 * @throws IOException on I/O problems
	 * @throws ClassNotFoundException
	 */
	static String evalAwk(String expression) throws IOException, ExitException, ClassNotFoundException {
		return runAwk("BEGIN { printf " + expression + "}", null);
	}

	/**
	 * Reads the specified file and returns its content as a String
	 *
	 * @param textFile File reference to read
	 * @return The content of the resource file as a String
	 * @throws IOException
	 */
	static String readTextFile(File textFile) throws IOException {
		StringBuilder builder = new StringBuilder();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)))) {
			String l;
			while ((l = reader.readLine()) != null) {
				builder.append(l).append('\n');
			}
		}

		return builder.toString();
	}

	/**
	 * Reads the specified resource file and returns its content as a String
	 *
	 * @param path Path to the file
	 * @return The content of the resource file as a String
	 * @throws IOException
	 */
	static String readResource(String path) throws IOException {
		StringBuilder builder = new StringBuilder();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(AwkTestHelper.class.getResourceAsStream(path)))) {
			String l;
			while ((l = reader.readLine()) != null) {
				builder.append(l).append('\n');
			}
		}

		return builder.toString();
	}
}
