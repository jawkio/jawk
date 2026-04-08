package io.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.jawk.ext.ExtensionRegistry;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.StdinExtension;
import io.jawk.frontend.AstNode;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.util.AwkSettings;
import io.jawk.util.ScriptFileSource;
import io.jawk.util.ScriptSource;

/**
 * Command-line interface for Jawk.
 */
public final class Cli {

	private static final String JAR_NAME;

	static {
		String myName;
		try {
			File me = new File(Cli.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			myName = me.getName();
		} catch (Exception e) {
			myName = "Jawk.jar";
		}
		JAR_NAME = myName;
	}

	private final AwkSettings settings = new AwkSettings();
	private final PrintStream out;
	private final PrintStream err;
	private final InputStream inputStream;
	private final List<String> nameValueOrFileNames = new ArrayList<String>();

	private final List<ScriptSource> scriptSources = new ArrayList<ScriptSource>();
	private AwkProgram precompiledProgram;
	private final List<String> extensionSpecs = new ArrayList<String>();
	private boolean listExtensions;

	private boolean dumpSyntaxTree;
	private boolean dumpIntermediateCode;
	private File compileOutputFile;
	private boolean printUsage;
	private boolean sandbox;
	private boolean disableOptimize;

	/**
	 * Creates a CLI instance wired to the standard input and output streams.
	 */
	public Cli() {
		this(System.in, System.out, System.err);
	}

	/**
	 * Creates a CLI instance using the supplied streams.
	 *
	 * @param in stream from which program input is read
	 * @param out stream where program output is written
	 * @param err stream where error messages could be written
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP2")
	public Cli(InputStream in, PrintStream out, PrintStream err) {
		this.out = out;
		this.inputStream = in;
		this.err = err;
	}

	/**
	 * Returns the mutable {@link AwkSettings} configured from the command line.
	 *
	 * @return the settings object populated during argument parsing
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP")
	public AwkSettings getSettings() {
		return settings;
	}

	/**
	 * Indicates whether sandbox mode was requested on the command line.
	 *
	 * @return {@code true} when sandbox mode is enabled
	 */
	public boolean isSandbox() {
		return sandbox;
	}

	/**
	 * Indicates whether tuple optimization was explicitly disabled.
	 *
	 * @return {@code true} when optimization should be skipped
	 */
	public boolean isDisableOptimize() {
		return disableOptimize;
	}

	/**
	 * Returns the list of script sources specified on the command line.
	 *
	 * @return defensive copy of the script sources list
	 */
	public List<ScriptSource> getScriptSources() {
		return new ArrayList<ScriptSource>(scriptSources);
	}

	/**
	 * Returns the precompiled program loaded via the <code>-L</code> option, if any.
	 *
	 * @return the program or {@code null} if none were loaded
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP")
	public AwkProgram getPrecompiledProgram() {
		return precompiledProgram;
	}

	/**
	 * Parses the supplied command-line arguments and configures this instance
	 * accordingly.
	 *
	 * @param args command-line arguments
	 */
	public void parse(String[] args) {

		// Special case: no arguments
		if (args.length == 0) {
			printUsage = true;
			return;
		}

		// Parse the arguments
		int argIdx = 0;
		while (argIdx < args.length) {
			String arg = args[argIdx];
			if (arg.length() == 0) {
				throw new IllegalArgumentException("zero-length argument at position " + (argIdx + 1));
			}
			if (arg.charAt(0) != '-') {
				// end of options: remaining args are part of the script execution
				break;
			} else if (arg.equals("-")) {
				// single dash indicates end of options as well
				++argIdx;
				break;
			} else if (arg.equals("-v")) {
				// -v name=val : assign AWK variable before execution
				checkParameterHasArgument(args, argIdx);
				addVariable(settings, args[++argIdx]);
			} else if (arg.equals("-f")) {
				// -f filename : load script from file
				checkParameterHasArgument(args, argIdx);
				scriptSources.add(new ScriptFileSource(args[++argIdx]));
			} else if (arg.equals("-L")) {
				// -L filename : load precompiled program
				checkParameterHasArgument(args, argIdx);
				String file = args[++argIdx];
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
					precompiledProgram = (AwkProgram) ois.readObject();
				} catch (java.io.InvalidClassException ex) {
					throw new IllegalArgumentException(
							"Precompiled program '" + file + "' is not compatible with this version (" + ex.getMessage()
									+ "). Please recompile.",
							ex);
				} catch (ClassCastException ex) {
					throw new IllegalArgumentException(
							"File '" + file + "' does not contain a valid precompiled AwkProgram. Please recompile.",
							ex);
				} catch (IOException | ClassNotFoundException ex) {
					throw new IllegalArgumentException(
							"Failed to read program '" + file + "': " + ex.getMessage(),
							ex);
				}
			} else if (arg.equals("-l") || arg.equals("--load")) {
				// -l/--load extension : load extension
				checkParameterHasArgument(args, argIdx);
				extensionSpecs.add(args[++argIdx]);
			} else if (arg.equals("--list-ext")) {
				if (argIdx != 0 || args.length != 1) {
					throw new IllegalArgumentException("When listing extensions, we do not accept other arguments.");
				}
				listExtensions = true;
				return;
			} else if (arg.equals("-K")) {
				// -K filename : compile scripts to tuples and exit
				checkParameterHasArgument(args, argIdx);
				compileOutputFile = new File(args[++argIdx]);
			} else if (arg.equals("-S") || arg.equals("--sandbox")) {
// -S/--sandbox : enable sandbox mode
				sandbox = true;
			} else if (arg.equals("--dump-syntax")) {
// --dump-syntax : dump syntax tree to file
				dumpSyntaxTree = true;
			} else if (arg.equals("-s") || arg.equals("--no-optimize")) {
				// -s/--no-optimize : skip tuple queue optimizations
				disableOptimize = true;
			} else if (arg.equals("--dump-intermediate")) {
				// --dump-intermediate : dump intermediate tuples to file
				dumpIntermediateCode = true;
			} else if (arg.equals("-t")) {
				// -t : keep associative array keys sorted
				settings.setUseSortedArrayKeys(true);
			} else if (arg.equals("-F")) {
				// -F fs : set field separator
				checkParameterHasArgument(args, argIdx);
				settings.setFieldSeparator(args[++argIdx]);
			} else if (arg.equals("--locale")) {
				// --locale Locale : specify locale
				checkParameterHasArgument(args, argIdx);
				settings.setLocale(Locale.forLanguageTag(args[++argIdx]));
			} else if (arg.equals("-h") || arg.equals("-?")) {
				// -h/-? : display usage information and exit
				if (argIdx != 0 || args.length != 1) {
					throw new IllegalArgumentException("When printing help/usage output, we do not accept other arguments.");
				}
				printUsage = true;
				return;
			} else {
				throw new IllegalArgumentException("Unknown parameter: " + arg);
			}
			++argIdx;
		}

		if (scriptSources.isEmpty() && precompiledProgram == null) {
			if (argIdx >= args.length) {
				throw new IllegalArgumentException("Awk script not provided.");
			}
			String scriptContent = args[argIdx++];
			scriptSources
					.add(
							new ScriptSource(
									ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
									new StringReader(scriptContent)));
		} else if (!scriptSources.isEmpty()) {
			for (ScriptSource scriptSource : scriptSources) {
				try {
					scriptSource.getReader();
				} catch (IOException ex) {
					throw new IllegalArgumentException(
							"Failed to read script '" + scriptSource.getDescription() + "': " + ex.getMessage(),
							ex);
				}
			}
		}

		while (argIdx < args.length) {
			nameValueOrFileNames.add(args[argIdx++]);
		}
	}

	/**
	 * Ensures that the current command-line option is followed by a value.
	 *
	 * @param args full array of arguments
	 * @param argIdx index of the option that requires a value
	 */
	private static void checkParameterHasArgument(String[] args, int argIdx) {
		if (argIdx + 1 >= args.length) {
			throw new IllegalArgumentException("Need additional argument for " + args[argIdx]);
		}
	}

	private static final Pattern INITIAL_VAR_PATTERN = Pattern.compile("([_a-zA-Z][_0-9a-zA-Z]*)=(.*)");

	/**
	 * Parses a variable assignment passed via <code>-v</code> and stores it in the
	 * provided settings instance.
	 *
	 * @param settings settings to mutate
	 * @param keyValue string of the form {@code name=value}
	 */
	private static void addVariable(AwkSettings settings, String keyValue) {
		Matcher m = INITIAL_VAR_PATTERN.matcher(keyValue);
		if (!m.matches()) {
			throw new IllegalArgumentException(
					"keyValue \"" + keyValue + "\" must be of the form \"name=value\"");
		}
		String name = m.group(1);
		String valueString = m.group(2);
		Object value;
		try {
			value = Integer.parseInt(valueString);
		} catch (NumberFormatException nfe) {
			try {
				value = Double.parseDouble(valueString);
			} catch (NumberFormatException nfe2) {
				value = valueString;
			}
		}
		settings.putVariable(name, value);
	}

	/**
	 * Executes the CLI based on the previously parsed arguments.
	 *
	 * @throws Exception if compilation or execution fails
	 */
	public void run() throws Exception {
		if (printUsage) {
			usage(out);
			return;
		}
		if (listExtensions) {
			Map<String, JawkExtension> available = ExtensionRegistry.listExtensions();
			for (Entry<String, JawkExtension> entry : available.entrySet()) {
				out.println(entry.getKey() + " - " + entry.getValue().getClass().getName());
			}
			return;
		}

		List<JawkExtension> extensions = new ArrayList<JawkExtension>();
		for (String spec : extensionSpecs) {
			JawkExtension extension = ExtensionRegistry.resolve(spec);
			if (extension == null) {
				throw new IllegalArgumentException("Unknown extension '" + spec + "'");
			}
			// Replace the StdinExtension singleton with a fresh instance
			// wired to this CLI's input stream so that StdinGetline()
			// reads from the correct source
			if (extension instanceof StdinExtension) {
				extension = new StdinExtension(inputStream);
			}
			extensions.add(extension);
		}

		Awk awk;
		if (sandbox) {
			awk = extensions.isEmpty() ? new SandboxedAwk(settings) : new SandboxedAwk(extensions, settings);
		} else {
			awk = extensions.isEmpty() ? new Awk(settings) : new Awk(extensions, settings);
		}
		// Use the precompiled program if provided; otherwise compile the scripts now.
		AwkProgram program = precompiledProgram != null ? precompiledProgram : awk.compile(scriptSources, disableOptimize);
		if (dumpSyntaxTree) {
			AstNode ast = awk.getLastAst();
			if (ast != null) {
				ast.dump(out);
			}
		}
		if (dumpIntermediateCode) {
			program.dump(out);
		}
		if (compileOutputFile != null) {
			// Serialize the compiled program to the requested file and exit.
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(compileOutputFile))) {
				oos.writeObject(program);
			}
			return;
		}
		if (dumpSyntaxTree || dumpIntermediateCode) {
			// If only dumping information, no need to execute the script
			return;
		}
		// Finally run the compiled program with the input and arguments.
		awk.script(program).input(inputStream).arguments(nameValueOrFileNames).errorStream(err).execute(out);
	}

	/**
	 * Prints usage/help information to the provided destination stream.
	 *
	 * @param dest stream to write usage information to
	 */
	private static void usage(PrintStream dest) {
		dest.println("Usage:");
		dest
				.println(
						"java -jar " +
								JAR_NAME +
								" [-F fs_val]" +
								" [-f script-filename]" +
								" [-L program-filename]" +
								" [-K program-filename]" +
								" [-o output-filename]" +
								" [-S|--sandbox]" +
								" [--dump-syntax]" +
								" [--dump-intermediate]" +
								" [-s|--no-optimize]" +
								" [--locale locale]" +
								" [-t]" +
								" [-l extension]..." +
								" [-v name=val]..." +
								" [script]" +
								" [name=val | input_filename]...");
		dest.println();
		dest.println("java -jar " + JAR_NAME + " --list-ext");
		dest.println();
		dest.println(" -F fs_val = Use fs_val for FS.");
		dest.println(" -f filename = Use contents of filename for script.");
		dest.println(" -L filename = Load precompiled program from filename.");
		dest.println(" -l extension = Load an extension by extension name or class name.");
		dest.println(" --load extension = Same as -l.");
		dest.println("                      Extensions must already be on the class path before loading them.");
		dest.println(" -v name=val = Initial awk variable assignments.");
		dest.println();
		dest.println(" -t = (extension) Maintain array keys in sorted order.");
		dest.println(" -K filename = Compile to program file and halt.");
		dest.println(" -o = (extension) Specify output file.");
		dest
				.println(
						" -S, --sandbox = (extension) Enable sandbox mode (no system(), redirection, pipelines, or"
								+ " dynamic extensions).");
		dest.println(" --dump-syntax = Print the syntax tree.");
		dest.println(" --dump-intermediate = Print the intermediate code.");
		dest.println(" -s, --no-optimize = (extension) Disable optimizations during compilation.");
		dest.println(" --locale Locale = (extension) Specify a locale to be used instead of US-English");
		dest.println(" --list-ext = (extension) List available extensions.");
		dest.println();
		dest.println(" -h or -? = (extension) This help screen.");
	}

	/**
	 * Parses command-line arguments into a new {@link Cli} instance without
	 * executing it.
	 *
	 * @param args command-line arguments
	 * @return configured CLI instance
	 */
	public static Cli parseCommandLineArguments(String[] args) {
		Cli cli = new Cli();
		cli.parse(args);
		return cli;
	}

	/**
	 * Convenience factory that parses arguments, executes the CLI, and returns the
	 * configured instance.
	 *
	 * @param args command-line arguments
	 * @param is input stream for program input
	 * @param os output stream for program output
	 * @param es error stream for diagnostic messages
	 * @return configured and executed CLI instance
	 * @throws Exception if execution fails
	 */
	public static Cli create(String[] args, InputStream is, PrintStream os, PrintStream es) throws Exception {
		Cli cli = new Cli(is, os, es);
		cli.parse(args);
		cli.run();
		return cli;
	}

	/**
	 * Entry point for the command-line interface.
	 *
	 * @param args command-line arguments
	 */
	@SuppressFBWarnings(value = "VA_FORMAT_STRING_USES_NEWLINE", justification = "let PrintStream decide line separator")
	public static void main(String[] args) {
		try {
			Cli cli = new Cli();
			cli.parse(args);
			cli.run();
		} catch (ExitException e) {
			System.exit(e.getCode());
		} catch (AwkRuntimeException e) {
			if (e.getLineNumber() >= 0) {
				System.err.printf("%s (line %d): %s\n", e.getClass().getSimpleName(), e.getLineNumber(), e.getMessage());
			} else {
				System.err.printf("%s: %s\n", e.getClass().getSimpleName(), e.getMessage());
			}
			System.exit(1);
		} catch (IllegalArgumentException e) {
			System.err.println("Failed to parse arguments. Please see the help/usage output (cmd line switch '-h').");
			e.printStackTrace(System.err);
			System.exit(1);
		} catch (Exception e) {
			System.err.printf("%s: %s\n", e.getClass().getSimpleName(), e.getMessage());
			System.exit(1);
		}
	}
}
