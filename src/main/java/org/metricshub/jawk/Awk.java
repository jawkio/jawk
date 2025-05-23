package org.metricshub.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AwkSyntaxTree;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkLogger;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;
import org.slf4j.Logger;

/**
 * Entry point into the parsing, analysis, and execution
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a library.
 * If you want to use Jawk as a stand-alone application, please use {@see Main}.
 * <p>
 * The overall process to execute a Jawk script is as follows:
 * <ul>
 * <li>Parse the Jawk script, producing an abstract syntax tree.
 * <li>Traverse the abstract syntax tree, producing a list of
 *	 instruction tuples for the interpreter.
 * <li>Traverse the list of tuples, providing a runtime which
 *	 ultimately executes the Jawk script, <strong>or</strong>
 *   Command-line parameters dictate which action is to take place.
 * </ul>
 * Two additional semantic checks on the syntax tree are employed
 * (both to resolve function calls for defined functions).
 * As a result, the syntax tree is traversed three times.
 * And the number of times tuples are traversed is depends
 * on whether interpretation or compilation takes place.
 * <p>
 * By default a minimal set of extensions are automatically
 * included. Please refer to the EXTENSION_PREFIX static field
 * contents for an up-to-date list. As of the initial release
 * of the extension system, the prefix defines the following
 * extensions:
 * <ul>
 * <li>CoreExtension
 * <li>SocketExtension
 * <li>StdinExtension
 * </ul>
 *
 * @see org.metricshub.jawk.backend.AVM
 * @author Danny Daglas
 */
public class Awk {

	private static final String DEFAULT_EXTENSIONS
			= org.metricshub.jawk.ext.CoreExtension.class.getName()
			+ "#" + org.metricshub.jawk.ext.StdinExtension.class.getName();

	private static final Logger LOG = AwkLogger.getLogger(Awk.class);

	/**
	 * Create a new instance of Awk
	 */
	public Awk() {}

	/**
	 * <p>invoke.</p>
	 *
	 * @param settings This tells AWK what to do
	 *   (where to get input from, where to write it to, in what mode to run,
	 *   ...)
	 * @throws java.io.IOException upon an IO error.
	 * @throws java.lang.ClassNotFoundException if intermediate code is specified
	 *           but deserialization fails to load in the JVM
	 * @throws org.metricshub.jawk.ExitException if interpretation is requested,
	 *	 and a specific exit code is requested.
	 */
	public void invoke(AwkSettings settings)
			throws IOException, ClassNotFoundException, ExitException
	{
		AVM avm = null;
		try {
			// key = Keyword, value = JawkExtension
			Map<String, JawkExtension> extensions;
			if (settings.isUserExtensions()) {
				extensions = getJawkExtensions();
				LOG.trace("user extensions = {}", extensions.keySet());
			} else {
				extensions = Collections.emptyMap();
				LOG.trace("user extensions not enabled");
			}

                        AwkTuples tuples = new AwkTuples();

                        List<ScriptSource> notIntermediateScriptSources = new ArrayList<ScriptSource>(settings.getScriptSources().size());
                        for (ScriptSource scriptSource : settings.getScriptSources()) {
                                if (scriptSource.isIntermediate()) {
                                        // read the intermediate file, bypassing frontend processing
                                        AwkTuples partial = (AwkTuples) readObjectFromInputStream(scriptSource.getInputStream());
                                        tuples.mergeFrom(partial);
                                } else {
                                        notIntermediateScriptSources.add(scriptSource);
                                }
                        }
			if (!notIntermediateScriptSources.isEmpty()) {
				AwkParser parser = new AwkParser(
						settings.isAdditionalFunctions(),
						settings.isAdditionalTypeFunctions(),
						extensions);
				// parse the script
				AwkSyntaxTree ast = parser.parse(notIntermediateScriptSources);

				if (settings.isDumpSyntaxTree()) {
					// dump the syntax tree of the script to a file
					String filename = settings.getOutputFilename("syntax_tree.lst");
					LOG.info("writing to '{}'", filename);
					PrintStream ps = new PrintStream(new FileOutputStream(filename));
					if (ast != null) {
						ast.dump(ps);
					}
					ps.close();
					return;
				}
				// otherwise, attempt to traverse the syntax tree and build
				// the intermediate code
				if (ast != null) {
					// 1st pass to tie actual parameters to back-referenced formal parameters
					ast.semanticAnalysis();
					// 2nd pass to tie actual parameters to forward-referenced formal parameters
					ast.semanticAnalysis();
					// build tuples
					int result = ast.populateTuples(tuples);
					// ASSERTION: NOTHING should be left on the operand stack ...
					assert result == 0;
					// Assign queue.next to the next element in the queue.
					// Calls touch(...) per Tuple so that addresses can be normalized/assigned/allocated
					tuples.postProcess();
					// record global_var -> offset mapping into the tuples
					// so that the interpreter/compiler can assign variables
					// on the "file list input" command line
					parser.populateGlobalVariableNameToOffsetMappings(tuples);
				}
				if (settings.isWriteIntermediateFile()) {
					// dump the intermediate code to an intermediate code file
					String filename = settings.getOutputFilename("a.ai");
					LOG.info("writing to '{}'", filename);
					writeObjectToFile(tuples, filename);
					return;
				}
			}
			if (settings.isDumpIntermediateCode()) {
				// dump the intermediate code to a human-readable text file
				String filename = settings.getOutputFilename("avm.lst");
				LOG.info("writing to '{}'", filename);
				PrintStream ps = new PrintStream(new FileOutputStream(filename));
				tuples.dump(ps);
				ps.close();
				return;
			}

			// interpret!
			avm = new AVM(settings, extensions);
			avm.interpret(tuples);

		} finally {
			if (avm != null) {
				avm.waitForIO();
			}
		}
	}

	private static Object readObjectFromInputStream(InputStream is)
			throws IOException, ClassNotFoundException
	{
		ObjectInputStream ois = new ObjectInputStream(is);
		Object retval = ois.readObject();
		ois.close();
		return retval;
	}

	private static void writeObjectToFile(Object object, String filename)
			throws IOException
	{
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
		oos.writeObject(object);
		oos.close();
	}

	private static Map<String, JawkExtension> getJawkExtensions() {
		String extensionsStr = System.getProperty("jawk.extensions", null);
		if (extensionsStr == null) {
			//return Collections.emptyMap();
			extensionsStr = DEFAULT_EXTENSIONS;
		} else {
			extensionsStr = DEFAULT_EXTENSIONS + "#" + extensionsStr;
		}

		// use reflection to obtain extensions

		Set<Class<?>> extensionClasses = new HashSet<Class<?>>();
		Map<String, JawkExtension> retval = new HashMap<String, JawkExtension>();

		StringTokenizer st = new StringTokenizer(extensionsStr, "#");
		while (st.hasMoreTokens()) {
			String cls = st.nextToken();
			LOG.trace("cls = {}", cls);
			try {
				Class<?> c = Class.forName(cls);
				// check if it's a JawkException
				if (!JawkExtension.class.isAssignableFrom(c)) {
					throw new ClassNotFoundException(cls + " does not implement JawkExtension");
				}
				if (extensionClasses.contains(c)) {
					LOG.warn("class {} is multiple times referred in extension class list. Skipping.", cls);
					continue;
				} else {
					extensionClasses.add(c);
				}

				// it is...
				// create a new instance and put it here
				try {
					Constructor<?> constructor = c.getDeclaredConstructor(); // Default constructor
					JawkExtension ji = (JawkExtension) constructor.newInstance();
					String[] keywords = ji.extensionKeywords();
					for (String keyword : keywords) {
						if (retval.get(keyword) != null) {
							throw new IllegalArgumentException("keyword collision : " + keyword
									+ " for both " + retval.get(keyword).getExtensionName()
									+ " and " + ji.getExtensionName());
						}
						retval.put(keyword, ji);
					}
				} catch (InstantiationException |
						IllegalAccessException |
						NoSuchMethodException |
						SecurityException |
						IllegalArgumentException |
						InvocationTargetException e) {
					LOG.warn("Cannot instantiate " + c.getName(), e);
				}
			} catch (ClassNotFoundException cnfe) {
				LOG.warn("Cannot classload {} : {}", new Object[] {cls, cnfe});
			}
		}

		return retval;
	}
}
