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
import io.jawk.backend.AVM;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.jawk.ext.ExtensionRegistry;
import io.jawk.ext.JawkExtension;
import io.jawk.frontend.AstNode;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.OutputStreamAwkSink;
import io.jawk.jrt.StreamInputSource;
import io.jawk.util.AwkSettings;
import io.jawk.util.JawkVersion;
import io.jawk.util.ScriptFileSource;
import io.jawk.util.ScriptSource;

/**
 * Command-line interface for Jawk.
 */
public final class Cli {

	private static final String JAR_NAME;
	private static final String POSIX_LOAD_CONFLICT_MESSAGE = "--posix cannot be combined with -L because -L loads a precompiled program.";
	private static final String PERSISTENT_MEMORY_ENVIRONMENT_VARIABLE = "JAWK_PERSISTENT_MEMORY";
	private static final String PROGRAM_NAME_ENVIRONMENT_VARIABLE = "JAWK_PROGRAM_NAME";

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
	private final Map<String, String> environment;
	private final List<String> nameValueOrFileNames = new ArrayList<String>();

	private final List<ScriptSource> scriptSources = new ArrayList<ScriptSource>();
	private AwkProgram precompiledProgram;
	private final List<String> extensionSpecs = new ArrayList<String>();
	private boolean listExtensions;

	private boolean dumpSyntaxTree;
	private boolean dumpIntermediateCode;
	private File compileOutputFile;
	private boolean printUsage;
	private boolean printVersion;
	private boolean sandbox;
	private boolean processStandardInput;
	private boolean disableOptimize;
	private boolean profiling;
	private File profilingOutputFile;
	private File persistentMemoryFile;

	/**
	 * Creates a CLI instance wired to the standard input and output streams.
	 */
	public Cli() {
		this(System.in, System.out, System.err, System.getenv());
	}

	/**
	 * Creates a CLI instance using the supplied streams.
	 *
	 * @param in stream from which program input is read
	 * @param out stream where program output is written
	 * @param err stream where error messages could be written
	 */
	public Cli(InputStream in, PrintStream out, PrintStream err) {
		this(in, out, err, System.getenv());
	}

	/**
	 * Creates a CLI instance using the supplied streams and environment variables.
	 * <p>
	 * This constructor exists primarily for tests and embedded launchers that need
	 * deterministic control over environment-driven options such as
	 * {@value #PERSISTENT_MEMORY_ENVIRONMENT_VARIABLE}.
	 *
	 * @param in stream from which program input is read
	 * @param out stream where program output is written
	 * @param err stream where error messages could be written
	 * @param environment environment variables visible to this CLI instance
	 */
	Cli(InputStream in, PrintStream out, PrintStream err, Map<String, String> environment) {
		// Keep the caller-provided streams live so the CLI reads and writes directly
		// against the requested endpoints during execution.
		this.out = out;
		this.inputStream = in;
		this.err = err != null ? err : System.err;
		this.environment = environment != null ?
				Collections.unmodifiableMap(new LinkedHashMap<String, String>(environment)) :
				System.getenv();
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
	 * Indicates whether runtime profiling was requested.
	 *
	 * @return {@code true} when profiling should be enabled
	 */
	public boolean isProfiling() {
		return profiling;
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
		boolean posixRequested = false;
		while (argIdx < args.length) {
			String arg = args[argIdx];
			if (arg.length() == 0) {
				throw new IllegalArgumentException("zero-length argument at position " + (argIdx + 1));
			}
			if (isAttachedOptionArgument(arg)) {
				// POSIX attached option-argument, e.g. -fprog.awk: split it into
				// the option and its value so the option branches below see the
				// same shape as the separate form -f prog.awk
				args = splitAttachedOptionArgument(args, argIdx);
				arg = args[argIdx];
			}
			if (arg.charAt(0) != '-') {
				// end of options: remaining args are part of the script execution
				break;
			} else if (arg.equals("-")) {
				// single dash is the standard-input operand (POSIX): end of
				// options, and keep it so it is processed as an input file
				break;
			} else if (arg.equals("--")) {
				// POSIX end-of-options marker: the remaining args are part of
				// the script execution
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
				if (posixRequested) {
					throw new IllegalArgumentException(POSIX_LOAD_CONFLICT_MESSAGE);
				}
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
					throw new IllegalArgumentException("Failed to read program '" + file + "': " + ex.getMessage(), ex);
				}
			} else if (arg.equals("--persist")) {
				// --persist filename : load and save persistent user globals
				checkParameterHasArgument(args, argIdx);
				persistentMemoryFile = new File(args[++argIdx]);
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
			} else if (arg.equals("--posix")) {
				// --posix : enforce POSIX-compatible compile-time behavior
				if (precompiledProgram != null) {
					throw new IllegalArgumentException(POSIX_LOAD_CONFLICT_MESSAGE);
				}
				posixRequested = true;
				settings.setPosix(true);
			} else if (arg.equals("--dump-syntax")) {
// --dump-syntax : dump syntax tree to file
				dumpSyntaxTree = true;
			} else if (arg.equals("-s") || arg.equals("--no-optimize")) {
				// -s/--no-optimize : skip tuple queue optimizations
				disableOptimize = true;
			} else if (arg.equals("--profile")) {
				// --profile : collect and print runtime profiling statistics
				profiling = true;
			} else if (arg.startsWith("--profile=")) {
				// --profile=filename : collect profiling statistics and write them to a file
				String file = arg.substring("--profile=".length());
				if (file.length() == 0) {
					throw new IllegalArgumentException("Need output filename for --profile");
				}
				profiling = true;
				profilingOutputFile = new File(file);
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
			} else if (arg.equals("-V") || arg.equals("--version")) {
				// -V/--version : display version information and exit
				if (argIdx != 0 || args.length != 1) {
					throw new IllegalArgumentException("When printing the version, we do not accept other arguments.");
				}
				printVersion = true;
				return;
			} else {
				// Like gawk: once the program text has been supplied, an
				// unknown option ends option processing and is passed on to
				// the AWK program through ARGV (useful for '#!' scripts)
				if (!scriptSources.isEmpty() || precompiledProgram != null) {
					break;
				}
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
					.add(new ScriptSource(ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT, new StringReader(scriptContent)));
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

	/** Single-letter options that take a value and accept it attached. */
	private static final String VALUE_OPTION_LETTERS = "fvFLKl";

	/**
	 * Tells whether an argument is a value-taking short option with its value
	 * attached, as in {@code -fprog.awk} for {@code -f prog.awk}.
	 *
	 * @param arg the raw command-line argument
	 * @return {@code true} when the argument must be split before dispatch
	 */
	private static boolean isAttachedOptionArgument(String arg) {
		return arg.length() > 2 && arg.charAt(0) == '-' && VALUE_OPTION_LETTERS.indexOf(arg.charAt(1)) >= 0;
	}

	/**
	 * Splits an attached option-argument in two, so that {@code -fprog.awk}
	 * becomes {@code -f prog.awk} in the argument array.
	 *
	 * @param args full array of arguments
	 * @param argIdx index of the attached option-argument to split
	 * @return a copy of the array where the argument at {@code argIdx} is
	 *         replaced by the option and its value as separate elements
	 */
	private static String[] splitAttachedOptionArgument(String[] args, int argIdx) {
		String[] split = new String[args.length + 1];
		System.arraycopy(args, 0, split, 0, argIdx);
		split[argIdx] = args[argIdx].substring(0, 2);
		split[argIdx + 1] = args[argIdx].substring(2);
		System.arraycopy(args, argIdx + 1, split, argIdx + 2, args.length - argIdx - 1);
		return split;
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

	private static final Pattern INITIAL_VAR_PATTERN = Pattern
			.compile("((?:[_a-zA-Z][_0-9a-zA-Z]*::)?[_a-zA-Z][_0-9a-zA-Z]*)=(.*)");

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
			throw new IllegalArgumentException("keyValue \"" + keyValue + "\" must be of the form \"name=value\"");
		}
		String name = m.group(1);
		if (name.startsWith("awk::")) {
			name = name.substring("awk::".length());
		}
		String valueString = m.group(2);
		settings.putVariable(name, valueString);
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
		if (printVersion) {
			version(out);
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
		executeProgram(awk, program, resolvePersistentMemoryFile());
	}

	/**
	 * Resolves the persistent-memory backing file from the command line or
	 * environment.
	 * <p>
	 * The dedicated {@code --persist} option takes precedence over the
	 * {@value #PERSISTENT_MEMORY_ENVIRONMENT_VARIABLE} environment variable.
	 *
	 * @return resolved persistent-memory file, or {@code null} when persistence
	 *         was not requested
	 */
	private File resolvePersistentMemoryFile() {
		if (persistentMemoryFile != null) {
			return persistentMemoryFile;
		}
		String configuredPath = environment.get(PERSISTENT_MEMORY_ENVIRONMENT_VARIABLE);
		if (configuredPath == null || configuredPath.trim().isEmpty()) {
			return null;
		}
		return new File(configuredPath);
	}

	/**
	 * Executes the compiled program through one AVM setup path, optionally loading
	 * and saving persistent user-defined globals when a backing file was
	 * configured.
	 *
	 * @param awk engine used to create the runtime
	 * @param program compiled program to execute
	 * @param memoryFile persistent-memory file, or {@code null} for normal
	 *        execution
	 * @throws Exception if runtime setup, execution, or persistence fails
	 */
	private void executeProgram(Awk awk, AwkProgram program, File memoryFile) throws Exception {
		OutputStreamAwkSink sink = new OutputStreamAwkSink(out, settings.getLocale());
		try (AVM avm = awk.createAvm(profiling)) {
			avm.setAwkSink(sink);
			avm.setErrorStream(err);
			avm.setWarningStream(err);
			avm.getJrt().setSpawnedProcessesInheritStandardInput(processStandardInput);
			if (memoryFile != null) {
				restorePersistentMemoryIfPresent(avm, memoryFile);
			}
			InputStream runtimeInput = inputStream != null ? inputStream : new ByteArrayInputStream(new byte[0]);
			StreamInputSource resolvedSource = new StreamInputSource(runtimeInput, avm, avm.getJrt());
			try {
				if (memoryFile != null) {
					avm.executePersistingGlobals(program, resolvedSource, nameValueOrFileNames, null);
				} else {
					avm.execute(program, resolvedSource, nameValueOrFileNames, null);
				}
			} catch (ExitException ex) {
				if (ex.getCode() != 0) {
					throw ex;
				}
			} finally {
				sink.flush();
				if (memoryFile != null) {
					savePersistentMemory(avm, memoryFile);
				}
				if (profiling) {
					printProfilingReport(avm);
				}
			}
		}
	}

	private void printProfilingReport(AVM avm) {
		if (profilingOutputFile == null) {
			err.println();
			avm.getProfilingReport().print(err);
			return;
		}
		File parent = profilingOutputFile.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
			String message = "Failed to create directory '" + parent + "' for profiling report.";
			throw new UncheckedIOException(message, new IOException(message));
		}
		try (PrintStream profileOut = new PrintStream(profilingOutputFile, "UTF-8")) {
			avm.getProfilingReport().print(profileOut);
			profileOut.flush();
			if (profileOut.checkError()) {
				throw new UncheckedIOException(
						"Failed to write profiling report '" + profilingOutputFile + "'.",
						new IOException("PrintStream reported an error"));
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(
					"Failed to write profiling report '" + profilingOutputFile + "': " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Restores persistent user-defined globals from the specified file when it
	 * already exists.
	 *
	 * @param avm runtime into which the persistent memory should be restored
	 * @param memoryFile file containing the serialized persistent snapshot
	 */
	private static void restorePersistentMemoryIfPresent(AVM avm, File memoryFile) {
		if (!memoryFile.exists()) {
			return;
		}
		if (!memoryFile.isFile()) {
			throw new IllegalArgumentException(
					"Persistent memory path '" + memoryFile + "' exists but is not a regular file.");
		}
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(memoryFile))) {
			@SuppressWarnings("unchecked")
			Map<String, Object> snapshot = (Map<String, Object>) ois.readObject();
			avm.restorePersistentMemory(snapshot);
		} catch (java.io.InvalidClassException ex) {
			throw new IllegalArgumentException(
					"Persistent memory file '" + memoryFile + "' is not compatible with this version (" + ex.getMessage()
							+ "). Please discard it and rerun.",
					ex);
		} catch (ClassCastException ex) {
			throw new IllegalArgumentException(
					"File '" + memoryFile + "' does not contain valid Jawk persistent memory.",
					ex);
		} catch (IOException | ClassNotFoundException ex) {
			throw new IllegalArgumentException(
					"Failed to read persistent memory '" + memoryFile + "': " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Saves the AVM's current persistent user-global bank to the specified file.
	 *
	 * @param avm runtime whose persistent memory should be saved
	 * @param memoryFile destination file
	 * @throws IOException if the snapshot cannot be written
	 */
	private static void savePersistentMemory(AVM avm, File memoryFile) throws IOException {
		File parent = memoryFile.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
			throw new IOException("Failed to create directory '" + parent + "' for persistent memory.");
		}
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(memoryFile))) {
			oos.writeObject(avm.snapshotPersistentMemory());
		}
	}

	/**
	 * Resolves the name to print in usage output for the command Jawk was
	 * invoked as.
	 * <p>
	 * Launchers such as the installed {@code jawk} script pass their own
	 * invocation name through the {@value #PROGRAM_NAME_ENVIRONMENT_VARIABLE}
	 * environment variable; without it, the CLI can only describe the direct
	 * {@code java -jar} invocation of its own jar.
	 *
	 * @return the launcher-provided program name, or the {@code java -jar}
	 *         invocation of this jar when no launcher provided one
	 */
	private String resolveProgramName() {
		String name = environment.get(PROGRAM_NAME_ENVIRONMENT_VARIABLE);
		if (name != null && !name.trim().isEmpty()) {
			return name.trim();
		}
		return "java -jar " + JAR_NAME;
	}

	/**
	 * Prints usage/help information to the provided destination stream.
	 *
	 * @param dest stream to write usage information to
	 */
	private void usage(PrintStream dest) {
		String programName = resolveProgramName();
		dest.println("Usage:");
		dest.println(
				programName + " [-F fs_val]" + " [-f script-filename]" + " [-L program-filename]" + " [--persist memory-file]"
						+ " [-K program-filename]" + " [-S|--sandbox]" + " [--posix]" + " [--dump-syntax]"
						+ " [--dump-intermediate]" + " [-s|--no-optimize]" + " [--profile[=filename]]" + " [--locale locale]"
						+ " [-t]" + " [-l extension]..." + " [-v name=val]..." + " [--]" + " [script]"
						+ " [name=val | input_filename | -]...");
		dest.println();
		dest.println(programName + " --list-ext");
		dest.println();
		dest.println(" -F fs_val = Use fs_val for FS.");
		dest.println(" -f filename = Use contents of filename for script.");
		dest.println(" -L filename = Load precompiled program from filename.");
		dest.println(" --persist filename = Load and save persistent user-defined globals from filename.");
		dest.println(
				"                      When omitted, the " + PERSISTENT_MEMORY_ENVIRONMENT_VARIABLE
						+ " environment variable can provide the backing file path.");
		dest.println(" -l extension = Load an extension by extension name or class name.");
		dest.println(" --load extension = Same as -l.");
		dest.println("                      Extensions must already be on the class path before loading them.");
		dest.println(" -v name=val = Initial awk variable assignments.");
		dest.println(" -- = End of options: the remaining arguments are the script and its operands.");
		dest.println(" - = As an operand, read input from standard input.");
		dest.println();
		dest.println(" -t = (extension) Maintain array keys in sorted order.");
		dest.println(" -K filename = Compile to program file and halt.");
		dest.println(
				" -S, --sandbox = (extension) Enable sandbox mode (no system(), redirection, pipelines, or"
						+ " dynamic extensions).");
		dest.println(
				" --posix = Enforce POSIX-compatible behavior such as disabling nested arrays and"
						+ " BEGINFILE/ENDFILE rules.");
		dest.println(" --dump-syntax = Print the syntax tree.");
		dest.println(" --dump-intermediate = Print the intermediate code.");
		dest.println(" -s, --no-optimize = (extension) Disable optimizations during compilation.");
		dest.println(
				" --profile[=filename] = (extension) Print tuple and function execution statistics to stderr or file.");
		dest.println(" --locale Locale = (extension) Specify a locale to be used instead of US-English");
		dest.println(" --list-ext = (extension) List available extensions.");
		dest.println();
		dest.println(" -h or -? = (extension) This help screen.");
		dest.println(" -V or --version = (extension) Print the Jawk and Java versions.");
	}

	/**
	 * Prints the Jawk version and the Java runtime that executes it to the
	 * provided destination stream.
	 *
	 * @param dest stream to write version information to
	 */
	private static void version(PrintStream dest) {
		dest.println("jawk " + JawkVersion.getVersion());
		dest.println(
				"Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + " "
						+ System.getProperty("java.vm.name") + ")");
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
	 * <p>
	 * This method carries process semantics: because it is what the
	 * {@code jawk} launch of the JVM runs, it declares that the standard input
	 * the CLI reads is the standard input of the process, and the children of
	 * {@code system()} and of command pipes may inherit it, as POSIX requires.
	 * No Java-side check can distinguish the launcher-installed
	 * {@code System.in} from a replacement installed with
	 * {@code System.setIn}, so that declaration is part of this method's
	 * contract: code that replaces {@code System.in} must not call
	 * {@code main} programmatically — embedders and tests go through
	 * {@link #create(String[], InputStream, PrintStream, PrintStream)}, the
	 * constructors, or the {@link Awk} API, where spawned children always get
	 * a closed standard input instead.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		try {
			Cli cli = new Cli();
			// Only the process entry point can vouch that the standard input
			// this CLI reads is the standard input of the JVM itself, the one
			// stream a spawned child can inherit; embedders and tests reach
			// run() through other paths and never set this.
			cli.processStandardInput = true;
			cli.parse(args);
			cli.run();
		} catch (ExitException e) {
			System.exit(e.getCode());
		} catch (AwkRuntimeException e) {
			if (e.getLineNumber() >= 0) {
				System.err.printf("%s (line %d): %s%n", e.getClass().getSimpleName(), e.getLineNumber(), e.getMessage());
			} else {
				System.err.printf("%s: %s%n", e.getClass().getSimpleName(), e.getMessage());
			}
			System.exit(1);
		} catch (IllegalArgumentException e) {
			System.err.println("Failed to parse arguments. Please see the help/usage output (cmd line switch '-h').");
			e.printStackTrace(System.err);
			System.exit(1);
		} catch (UncheckedIOException e) {
			System.err.printf("%s: %s%n", e.getClass().getSimpleName(), e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			System.err.printf("%s: %s%n", e.getClass().getSimpleName(), e.getMessage());
			System.exit(1);
		}
	}
}
