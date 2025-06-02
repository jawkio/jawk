package org.metricshub.jawk.frontend;

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

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.metricshub.jawk.NotImplementedError;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.intermediate.HasFunctionAddress;
import org.metricshub.jawk.jrt.KeyList;
import org.metricshub.jawk.util.AwkLogger;
import org.metricshub.jawk.util.ScriptSource;
import org.slf4j.Logger;

/**
 * Converts the AWK script into a syntax tree,
 * which is useful the backend that either compiles or interprets the script.
 * <p>
 * It contains the internal state of the parser and the lexer.
 *
 * @author Danny Daglas
 */
public class AwkParser {

	private static final Logger LOG = AwkLogger.getLogger(AwkParser.class);

	/**
	 * Interface for statement AST nodes that can be interrupted
	 * with a break statement.
	 */
	private interface Breakable {
		Address breakAddress();
	}

	/**
	 * Interface for statement AST nodes that can be entered
	 * via a next statement.
	 */
	private interface Nextable {
		Address nextAddress();
	}

	/**
	 * Interface for statement AST nodes that can be re-entered
	 * with a continue statement.
	 */
	private interface Continueable {
		Address continueAddress();
	}

	/** Lexer token values, similar to yytok values in lex/yacc. */
	private static int sIdx = 257;

	// Lexable tokens...

	private static final int _EOF_ = sIdx++;
	private static final int _NEWLINE_ = sIdx++;
	private static final int _SEMICOLON_ = sIdx++;
	private static final int _ID_ = sIdx++;
	private static final int _FUNC_ID_ = sIdx++;
	private static final int _INTEGER_ = sIdx++;
	private static final int _DOUBLE_ = sIdx++;
	private static final int _STRING_ = sIdx++;

	private static final int _EQUALS_ = sIdx++;

	private static final int _AND_ = sIdx++;
	private static final int _OR_ = sIdx++;

	private static final int _EQ_ = sIdx++;
	private static final int _GT_ = sIdx++;
	private static final int _GE_ = sIdx++;
	private static final int _LT_ = sIdx++;
	private static final int _LE_ = sIdx++;
	private static final int _NE_ = sIdx++;
	private static final int _NOT_ = sIdx++;
	private static final int _PIPE_ = sIdx++;
	private static final int _QUESTION_MARK_ = sIdx++;
	private static final int _COLON_ = sIdx++;
	private static final int _APPEND_ = sIdx++;

	private static final int _PLUS_ = sIdx++;
	private static final int _MINUS_ = sIdx++;
	private static final int _MULT_ = sIdx++;
	private static final int _DIVIDE_ = sIdx++;
	private static final int _MOD_ = sIdx++;
	private static final int _POW_ = sIdx++;
	private static final int _COMMA_ = sIdx++;
	private static final int _MATCHES_ = sIdx++;
	private static final int _NOT_MATCHES_ = sIdx++;
	private static final int _DOLLAR_ = sIdx++;

	private static final int _INC_ = sIdx++;
	private static final int _DEC_ = sIdx++;

	private static final int _PLUS_EQ_ = sIdx++;
	private static final int _MINUS_EQ_ = sIdx++;
	private static final int _MULT_EQ_ = sIdx++;
	private static final int _DIV_EQ_ = sIdx++;
	private static final int _MOD_EQ_ = sIdx++;
	private static final int _POW_EQ_ = sIdx++;

	private static final int _OPEN_PAREN_ = sIdx++;
	private static final int _CLOSE_PAREN_ = sIdx++;
	private static final int _OPEN_BRACE_ = sIdx++;
	private static final int _CLOSE_BRACE_ = sIdx++;
	private static final int _OPEN_BRACKET_ = sIdx++;
	private static final int _CLOSE_BRACKET_ = sIdx++;

	private static final int _BUILTIN_FUNC_NAME_ = sIdx++;

	private static final int _EXTENSION_ = sIdx++;

	private static final int _KW_SLEEP_ = sIdx++;
	private static final int _KW_DUMP_ = sIdx++;
	private static final int _KW_INTEGER_ = sIdx++;
	private static final int _KW_DOUBLE_ = sIdx++;
	private static final int _KW_STRING_ = sIdx++;

	/**
	 * Contains a mapping of Jawk keywords to their
	 * token values.
	 * They closely correspond to AWK keywords, but with
	 * a few added extensions.
	 * <p>
	 * Keys are the keywords themselves, and values are the
	 * token values (equivalent to yytok values in lex/yacc).
	 * <p>
	 * <strong>Note:</strong> whether built-in AWK function names
	 * and special AWK variable names are formally keywords or not,
	 * they are not stored in this map. They are separated
	 * into other maps.
	 */
	private static final Map<String, Integer> KEYWORDS = new HashMap<String, Integer>();

	static {
		// special keywords
		KEYWORDS.put("function", sIdx++);
		KEYWORDS.put("BEGIN", sIdx++);
		KEYWORDS.put("END", sIdx++);
		KEYWORDS.put("in", sIdx++);

		// statements
		KEYWORDS.put("if", sIdx++);
		KEYWORDS.put("else", sIdx++);
		KEYWORDS.put("while", sIdx++);
		KEYWORDS.put("for", sIdx++);
		KEYWORDS.put("do", sIdx++);
		KEYWORDS.put("return", sIdx++);
		KEYWORDS.put("exit", sIdx++);
		KEYWORDS.put("next", sIdx++);
		KEYWORDS.put("continue", sIdx++);
		KEYWORDS.put("delete", sIdx++);
		KEYWORDS.put("break", sIdx++);

		// special-form functions
		KEYWORDS.put("print", sIdx++);
		KEYWORDS.put("printf", sIdx++);
		KEYWORDS.put("getline", sIdx++);

		KEYWORDS.put("_sleep", _KW_SLEEP_);
		KEYWORDS.put("_dump", _KW_DUMP_);
		KEYWORDS.put("_INTEGER", _KW_INTEGER_);
		KEYWORDS.put("_DOUBLE", _KW_DOUBLE_);
		KEYWORDS.put("_STRING", _KW_STRING_);
	}

	/**
	 * Built-in function token values.
	 * Built-in function token values are distinguished
	 * from lexer token values.
	 */
	private static int fIdx = 257;
	private static final int F_EXEC = fIdx++;
	/**
	 * A mapping of built-in function names to their
	 * function token values.
	 * <p>
	 * <strong>Note:</strong> these are not lexer token
	 * values. Lexer token values are for keywords and
	 * operators.
	 */
	private static final Map<String, Integer> BUILTIN_FUNC_NAMES = new HashMap<String, Integer>();

	static {
		BUILTIN_FUNC_NAMES.put("atan2", fIdx++);
		BUILTIN_FUNC_NAMES.put("close", fIdx++);
		BUILTIN_FUNC_NAMES.put("cos", fIdx++);
		BUILTIN_FUNC_NAMES.put("exp", fIdx++);
		BUILTIN_FUNC_NAMES.put("index", fIdx++);
		BUILTIN_FUNC_NAMES.put("int", fIdx++);
		BUILTIN_FUNC_NAMES.put("length", fIdx++);
		BUILTIN_FUNC_NAMES.put("log", fIdx++);
		BUILTIN_FUNC_NAMES.put("match", fIdx++);
		BUILTIN_FUNC_NAMES.put("rand", fIdx++);
		BUILTIN_FUNC_NAMES.put("sin", fIdx++);
		BUILTIN_FUNC_NAMES.put("split", fIdx++);
		BUILTIN_FUNC_NAMES.put("sprintf", fIdx++);
		BUILTIN_FUNC_NAMES.put("sqrt", fIdx++);
		BUILTIN_FUNC_NAMES.put("srand", fIdx++);
		BUILTIN_FUNC_NAMES.put("sub", fIdx++);
		BUILTIN_FUNC_NAMES.put("gsub", fIdx++);
		BUILTIN_FUNC_NAMES.put("substr", fIdx++);
		BUILTIN_FUNC_NAMES.put("system", fIdx++);
		BUILTIN_FUNC_NAMES.put("tolower", fIdx++);
		BUILTIN_FUNC_NAMES.put("toupper", fIdx++);
		BUILTIN_FUNC_NAMES.put("exec", F_EXEC);
	}

	private static final int spIdx = 257;
	/**
	 * Contains a mapping of Jawk special variables to their
	 * variable token values.
	 * As of this writing, they correspond exactly to
	 * standard AWK variables, no more, no less.
	 * <p>
	 * Keys are the variable names themselves, and values are the
	 * variable token values.
	 */
	private static final Map<String, Integer> SPECIAL_VAR_NAMES = new HashMap<String, Integer>();

	static {
		SPECIAL_VAR_NAMES.put("NR", spIdx);
		SPECIAL_VAR_NAMES.put("FNR", spIdx);
		SPECIAL_VAR_NAMES.put("NF", spIdx);
		SPECIAL_VAR_NAMES.put("FS", spIdx);
		SPECIAL_VAR_NAMES.put("RS", spIdx);
		SPECIAL_VAR_NAMES.put("OFS", spIdx);
		SPECIAL_VAR_NAMES.put("ORS", spIdx);
		SPECIAL_VAR_NAMES.put("RSTART", spIdx);
		SPECIAL_VAR_NAMES.put("RLENGTH", spIdx);
		SPECIAL_VAR_NAMES.put("FILENAME", spIdx);
		SPECIAL_VAR_NAMES.put("SUBSEP", spIdx);
		SPECIAL_VAR_NAMES.put("CONVFMT", spIdx);
		SPECIAL_VAR_NAMES.put("OFMT", spIdx);
		SPECIAL_VAR_NAMES.put("ENVIRON", spIdx);
		SPECIAL_VAR_NAMES.put("ARGC", spIdx);
		SPECIAL_VAR_NAMES.put("ARGV", spIdx);
	}

	/**
	 * Defined as concrete implementation class (not an
	 * interface reference) as to not clutter the interface
	 * with methods appropriate for private access, only.
	 */
	private final AwkSymbolTableImpl symbol_table = new AwkSymbolTableImpl();

	private final boolean additional_functions;
	private final boolean additional_type_functions;
	private final Map<String, JawkExtension> extensions;

	/**
	 * <p>
	 * Constructor for AwkParser.
	 * </p>
	 *
	 * @param additional_functions a boolean
	 * @param additional_type_functions a boolean
	 * @param extensions a {@link java.util.Map} object
	 */
	public AwkParser(boolean additional_functions, boolean additional_type_functions,
			Map<String, JawkExtension> extensions) {
		this.additional_functions = additional_functions;
		this.additional_type_functions = additional_type_functions;
		this.extensions = extensions == null ? Collections.emptyMap() : new HashMap<>(extensions);
	}

	private List<ScriptSource> scriptSources;
	private int scriptSourcesCurrentIndex;
	private LineNumberReader reader;
	private int c;
	private int token;

	private StringBuffer text = new StringBuffer();
	private StringBuffer string = new StringBuffer();
	private StringBuffer regexp = new StringBuffer();

	private void read() throws IOException {
		text.append((char) c);
		c = reader.read();
		// completely bypass \r's
		while (c == '\r') {
			c = reader.read();
		}
		if (c < 0 && (scriptSourcesCurrentIndex + 1) < scriptSources.size()) {
			scriptSourcesCurrentIndex++;
			reader = new LineNumberReader(scriptSources.get(scriptSourcesCurrentIndex).getReader());
			read();
		}
	}

	/**
	 * Skip all whitespaces and comments
	 *
	 * @throws IOException
	 */
	private void skipWhitespaces() throws IOException {
		while (c == ' ' || c == '\t' || c == '#' || c == '\n') {
			if (c == '#') {
				while (c >= 0 && c != '\n') {
					read();
				}
			}
			read();
		}
	}

	/**
	 * Logs a warning about the syntax of the script (at which line, of which file)
	 *
	 * @param message
	 */
	private void warn(String message) {
		LOG
				.warn(
						"%s (%s:%d)",
						message,
						scriptSources.get(scriptSourcesCurrentIndex).getDescription(),
						reader.getLineNumber());
	}

	/**
	 * Parse the script streamed by script_reader. Build and return the
	 * root of the abstract syntax tree which represents the Jawk script.
	 *
	 * @param scriptSources List of script sources
	 * @return The abstract syntax tree of this script.
	 * @throws java.io.IOException upon an IO error.
	 */
	public AwkSyntaxTree parse(List<ScriptSource> scriptSources) throws IOException {
		if (scriptSources == null || scriptSources.isEmpty()) {
			throw new IOException("No script sources supplied");
		}
		this.scriptSources = Collections.unmodifiableList(new ArrayList<>(scriptSources));
		scriptSourcesCurrentIndex = 0;
		reader = new LineNumberReader(this.scriptSources.get(scriptSourcesCurrentIndex).getReader());
		read();
		lexer();
		return SCRIPT();
	}

	/**
	 * Exception indicating a syntax problem in the AWK script
	 */
	public class LexerException extends IOException {

		private static final long serialVersionUID = 1L;

		/**
		 * Create a new LexerException
		 *
		 * @param msg Problem description (without the position, which will be added)
		 */
		LexerException(String msg) {
			super(
					msg
							+ " ("
							+ scriptSources.get(scriptSourcesCurrentIndex).getDescription()
							+ ":"
							+ reader.getLineNumber()
							+ ")");
		}
	}

	/**
	 * Reads the string and handle all escape codes.
	 *
	 * @throws IOException
	 */
	private void readString() throws IOException {
		string.setLength(0);

		while (token != _EOF_ && c > 0 && c != '"' && c != '\n') {
			if (c == '\\') {
				read();
				switch (c) {
				case 'n':
					string.append('\n');
					break;
				case 't':
					string.append('\t');
					break;
				case 'r':
					string.append('\r');
					break;
				case 'a':
					string.append('\007');
					break; // BEL 0x07
				case 'b':
					string.append('\010');
					break; // BS 0x08
				case 'f':
					string.append('\014');
					break; // FF 0x0C
				case 'v':
					string.append('\013');
					break; // VT 0x0B
				// Octal notation: \N \NN \NNN
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7': {
					int octalChar = c - '0';
					read();
					if (c >= '0' && c <= '7') {
						octalChar = (octalChar << 3) + c - '0';
						read();
						if (c >= '0' && c <= '7') {
							octalChar = (octalChar << 3) + c - '0';
							read();
						}
					}
					string.append((char) octalChar);
					continue;
				}
				// Hexadecimal notation: \xN \xNN
				case 'x': {
					int hexChar = 0;
					read();
					if (c >= '0' && c <= '9') {
						hexChar = c - '0';
					} else if (c >= 'A' && c <= 'F') {
						hexChar = c - 'A' + 10;
					} else if (c >= 'a' && c <= 'f') {
						hexChar = c - 'a' + 10;
					} else {
						warn("no hex digits in `\\x' sequence");
						string.append('x');
						continue;
					}
					read();
					if (c >= '0' && c <= '9') {
						hexChar = (hexChar << 4) + c - '0';
					} else if (c >= 'A' && c <= 'F') {
						hexChar = (hexChar << 4) + c - 'A' + 10;
					} else if (c >= 'a' && c <= 'f') {
						hexChar = (hexChar << 4) + c - 'a' + 10;
					} else {
						// Append what we already have, and continue directly, because we already have read the next char
						string.append((char) hexChar);
						continue;
					}
					string.append((char) hexChar);
					break;
				}
				default:
					string.append((char) c);
					break; // Remove the backslash
				}
			} else {
				string.append((char) c);
			}
			read();
		}
		if (token == _EOF_ || c == '\n' || c <= 0) {
			throw new LexerException("Unterminated string: " + text);
		}
		read();
	}

	/**
	 * Reads the regular expression (between slashes '/') and handle '\/'.
	 *
	 * @throws IOException
	 */
	private void readRegexp() throws IOException {
		regexp.setLength(0);

		while (token != _EOF_ && c > 0 && c != '/' && c != '\n') {
			if (c == '\\') {
				read();
				if (c != '/') {
					regexp.append('\\');
				}
			}
			regexp.append((char) c);
			read();
		}
		if (token == _EOF_ || c == '\n' || c <= 0) {
			throw new LexerException("Unterminated string: " + text);
		}
		read();
	}

	private static String toTokenString(int token) {
		Class<AwkParser> c = AwkParser.class;
		Field[] fields = c.getDeclaredFields();
		try {
			for (Field field : fields) {
				if ((field.getModifiers() & Modifier.STATIC) > 0
						&& field.getType() == Integer.TYPE
						&& field.getInt(null) == token) {
					return field.getName();
				}
			}
		} catch (IllegalAccessException iac) {
			LOG.error("Failed to create token string", iac);
			return "[" + token + ": " + iac + "]";
		}
		return "{" + token + "}";
	}

	private int lexer(int expected_token) throws IOException {
		if (token != expected_token) {
			throw new ParserException(
					"Expecting " + toTokenString(expected_token) + ". Found: " + toTokenString(token) + " (" + text + ")");
		}
		return lexer();
	}

	private int lexer() throws IOException {
		// clear whitespace
		while (c >= 0 && (c == ' ' || c == '\t' || c == '#' || c == '\\')) {
			if (c == '\\') {
				read();
				if (c == '\n') {
					read();
				}
				continue;
			}
			if (c == '#') {
				// kill comment
				while (c >= 0 && c != '\n') {
					read();
				}
			} else {
				read();
			}
		}
		text.setLength(0);
		if (c < 0) {
			token = _EOF_;
			return token;
		}
		if (c == ',') {
			read();
			skipWhitespaces();
			token = _COMMA_;
			return token;
		}
		if (c == '(') {
			read();
			token = _OPEN_PAREN_;
			return token;
		}
		if (c == ')') {
			read();
			token = _CLOSE_PAREN_;
			return token;
		}
		if (c == '{') {
			read();
			skipWhitespaces();
			token = _OPEN_BRACE_;
			return token;
		}
		if (c == '}') {
			read();
			token = _CLOSE_BRACE_;
			return token;
		}
		if (c == '[') {
			read();
			token = _OPEN_BRACKET_;
			return token;
		}
		if (c == ']') {
			read();
			token = _CLOSE_BRACKET_;
			return token;
		}
		if (c == '$') {
			read();
			token = _DOLLAR_;
			return token;
		}
		if (c == '~') {
			read();
			token = _MATCHES_;
			return token;
		}
		if (c == '?') {
			read();
			skipWhitespaces();
			token = _QUESTION_MARK_;
			return token;
		}
		if (c == ':') {
			read();
			skipWhitespaces();
			token = _COLON_;
			return token;
		}
		if (c == '&') {
			read();
			if (c == '&') {
				read();
				skipWhitespaces();
				token = _AND_;
				return token;
			}
			throw new LexerException("use && for logical and");
		}
		if (c == '|') {
			read();
			if (c == '|') {
				read();
				skipWhitespaces();
				token = _OR_;
				return token;
			}
			token = _PIPE_;
			return token;
		}
		if (c == '=') {
			read();
			if (c == '=') {
				read();
				token = _EQ_;
				return token;
			}
			token = _EQUALS_;
			return token;
		}
		if (c == '+') {
			read();
			if (c == '=') {
				read();
				token = _PLUS_EQ_;
				return token;
			} else if (c == '+') {
				read();
				token = _INC_;
				return token;
			}
			token = _PLUS_;
			return token;
		}
		if (c == '-') {
			read();
			if (c == '=') {
				read();
				token = _MINUS_EQ_;
				return token;
			} else if (c == '-') {
				read();
				token = _DEC_;
				return token;
			}
			token = _MINUS_;
			return token;
		}
		if (c == '*') {
			read();
			if (c == '=') {
				read();
				token = _MULT_EQ_;
				return token;
			} else if (c == '*') {
				read();
				token = _POW_;
				return token;
			}
			token = _MULT_;
			return token;
		}
		if (c == '/') {
			read();
			if (c == '=') {
				read();
				token = _DIV_EQ_;
				return token;
			}
			token = _DIVIDE_;
			return token;
		}
		if (c == '%') {
			read();
			if (c == '=') {
				read();
				token = _MOD_EQ_;
				return token;
			}
			token = _MOD_;
			return token;
		}
		if (c == '^') {
			read();
			if (c == '=') {
				read();
				token = _POW_EQ_;
				return token;
			}
			token = _POW_;
			return token;
		}
		if (c == '>') {
			read();
			if (c == '=') {
				read();
				token = _GE_;
				return token;
			} else if (c == '>') {
				read();
				token = _APPEND_;
				return token;
			}
			token = _GT_;
			return token;
		}
		if (c == '<') {
			read();
			if (c == '=') {
				read();
				token = _LE_;
				return token;
			}
			token = _LT_;
			return token;
		}
		if (c == '!') {
			read();
			if (c == '=') {
				read();
				token = _NE_;
				return token;
			} else if (c == '~') {
				read();
				token = _NOT_MATCHES_;
				return token;
			}
			token = _NOT_;
			return token;
		}

		if (c == '.') {
			// double!
			read();
			boolean hit = false;
			while (c > 0 && Character.isDigit(c)) {
				hit = true;
				read();
			}
			if (!hit) {
				throw new LexerException("Decimal point encountered with no values on either side.");
			}
			token = _DOUBLE_;
			return token;
		}

		if (Character.isDigit(c)) {
			// integer or double.
			read();
			while (c > 0) {
				if (c == '.') {
					// double!
					read();
					while (c > 0 && Character.isDigit(c)) {
						read();
					}
					token = _DOUBLE_;
					return token;
				} else if (Character.isDigit(c)) {
					// integer or double.
					read();
				} else {
					break;
				}
			}
			// integer, only
			token = _INTEGER_;
			return token;
		}

		if (Character.isJavaIdentifierStart(c)) {
			read();
			while (Character.isJavaIdentifierPart(c)) {
				read();
			}
			// check for certain keywords
			// extensions override built-in stuff
			if (extensions.get(text.toString()) != null) {
				token = _EXTENSION_;
				return token;
			}
			Integer kwToken = KEYWORDS.get(text.toString());
			if (kwToken != null) {
				int kw = kwToken.intValue();
				boolean treatAsIdentifier = (!additional_functions && (kw == _KW_SLEEP_ || kw == _KW_DUMP_))
						||
						(!additional_type_functions && (kw == _KW_INTEGER_ || kw == _KW_DOUBLE_ || kw == _KW_STRING_));
				if (!treatAsIdentifier) {
					token = kw;
					return token;
				}
				// treat as identifier
			}
			Integer builtinIdx = BUILTIN_FUNC_NAMES.get(text.toString());
			if (builtinIdx != null) {
				int idx = builtinIdx.intValue();
				if (additional_functions || idx != F_EXEC) {
					token = _BUILTIN_FUNC_NAME_;
					return token;
				}
				// treat as identifier
			}
			if (c == '(') {
				token = _FUNC_ID_;
				return token;
			} else {
				token = _ID_;
				return token;
			}
		}

		if (c == ';') {
			read();
			while (c == ' ' || c == '\t' || c == '\n' || c == '#') {
				if (c == '\n') {
					break;
				}
				if (c == '#') {
					while (c >= 0 && c != '\n') {
						read();
					}
					if (c == '\n') {
						read();
					}
				} else {
					read();
				}
			}
			token = _SEMICOLON_;
			return token;
		}

		if (c == '\n') {
			read();
			while (c == ' ' || c == '\t' || c == '#' || c == '\n') {
				if (c == '#') {
					while (c >= 0 && c != '\n') {
						read();
					}
				}
				read();
			}
			token = _NEWLINE_;
			return token;
		}

		if (c == '"') {
			// string
			read();
			readString();
			token = _STRING_;
			return token;
		}

		/*
		 * if (c == '\\') {
		 * c = reader.read();
		 * // completely bypass \r's
		 * while(c == '\r') c = reader.read();
		 * if (c<0)
		 * chr=0; // eof
		 * else
		 * chr=c;
		 * }
		 */

		throw new LexerException("Invalid character (" + c + "): " + ((char) c));
	}

	// SUPPORTING FUNCTIONS/METHODS
	private void terminator() throws IOException {
		// like opt_terminator, except error if no terminator was found
		if (!opt_terminator()) {
			throw new ParserException("Expecting statement terminator. Got " + toTokenString(token) + ": " + text);
		}
	}

	private boolean opt_terminator() throws IOException {
		if (opt_newline()) {
			return true;
		} else if (token == _EOF_ || token == _CLOSE_BRACE_) {
			return true; // do nothing
		} else if (token == _SEMICOLON_) {
			lexer();
			return true;
		} else {
			// no terminator consumed
			return false;
		}
	}

	private boolean opt_newline() throws IOException {
		if (token == _NEWLINE_) {
			lexer();
			return true;
		} else {
			return false;
		}
	}

	// RECURSIVE DECENT PARSER:
	// CHECKSTYLE.OFF: MethodName
	// SCRIPT : \n [RULE_LIST] EOF
	AST SCRIPT() throws IOException {
		AST rl;
		if (token != _EOF_) {
			rl = RULE_LIST();
		} else {
			rl = null;
		}
		lexer(_EOF_);
		return rl;
	}

	// RULE_LIST : \n [ ( RULE | FUNCTION terminator ) opt_terminator RULE_LIST ]
	AST RULE_LIST() throws IOException {
		opt_newline();
		AST rule_or_function = null;
		if (token == KEYWORDS.get("function")) {
			rule_or_function = FUNCTION();
		} else if (token != _EOF_) {
			rule_or_function = RULE();
		} else {
			return null;
		}
		opt_terminator(); // newline or ; (maybe)
		return new RuleListAst(rule_or_function, RULE_LIST());
	}

	AST FUNCTION() throws IOException {
		expectKeyword("function");
		String functionName;
		if (token == _FUNC_ID_ || token == _ID_) {
			functionName = text.toString();
			lexer();
		} else {
			throw new ParserException("Expecting function name. Got " + toTokenString(token) + ": " + text);
		}
		symbol_table.setFunctionName(functionName);
		lexer(_OPEN_PAREN_);
		AST formal_param_list;
		if (token == _CLOSE_PAREN_) {
			formal_param_list = null;
		} else {
			formal_param_list = FORMAL_PARAM_LIST(functionName);
		}
		lexer(_CLOSE_PAREN_);
		opt_newline();

		lexer(_OPEN_BRACE_);
		AST function_block = STATEMENT_LIST();
		lexer(_CLOSE_BRACE_);
		symbol_table.clearFunctionName(functionName);
		return symbol_table.addFunctionDef(functionName, formal_param_list, function_block);
	}

	AST FORMAL_PARAM_LIST(String functionName) throws IOException {
		if (token == _ID_) {
			String id = text.toString();
			symbol_table.addFunctionParameter(functionName, id);
			lexer();
			if (token == _COMMA_) {
				lexer();
				opt_newline();
				AST rest = FORMAL_PARAM_LIST(functionName);
				if (rest == null) {
					throw new ParserException("Cannot terminate a formal parameter list with a comma.");
				} else {
					return new FunctionDefParamListAst(id, rest);
				}
			} else {
				return new FunctionDefParamListAst(id, null);
			}
		} else {
			return null;
		}
	}

	// RULE : [ASSIGNMENT_EXPRESSION] [ { STATEMENT_LIST } ]
	AST RULE() throws IOException {
		AST opt_expr;
		AST opt_stmts;
		if (token == KEYWORDS.get("BEGIN")) {
			lexer();
			opt_expr = symbol_table.addBEGIN();
		} else if (token == KEYWORDS.get("END")) {
			lexer();
			opt_expr = symbol_table.addEND();
		} else if (token != _OPEN_BRACE_ && token != _SEMICOLON_ && token != _NEWLINE_ && token != _EOF_) {
			// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
			opt_expr = ASSIGNMENT_EXPRESSION(true, true, false);
			// for ranges, like conditionStart, conditionEnd
			if (token == _COMMA_) {
				lexer();
				opt_newline();
				// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
				opt_expr = new ConditionPairAst(opt_expr, ASSIGNMENT_EXPRESSION(true, true, false));
			}
		} else {
			opt_expr = null;
		}
		if (token == _OPEN_BRACE_) {
			lexer();
			opt_stmts = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
		} else {
			opt_stmts = null;
		}
		return new RuleAst(opt_expr, opt_stmts);
	}

	// STATEMENT_LIST : [ STATEMENT_BLOCK|STATEMENT STATEMENT_LIST ]
	private AST STATEMENT_LIST() throws IOException {
		// statement lists can only live within curly brackets (braces)
		opt_newline();
		if (token == _CLOSE_BRACE_ || token == _EOF_) {
			return null;
		}
		AST stmt;
		if (token == _OPEN_BRACE_) {
			lexer();
			stmt = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
		} else {
			if (token == _SEMICOLON_) {
				// an empty statement (;)
				// do not polute the syntax tree with nulls in this case
				// just return the next statement (recursively)
				lexer();
				return STATEMENT_LIST();
			} else {
				stmt = STATEMENT();
			}
		}

		AST rest = STATEMENT_LIST();
		if (rest == null) {
			return stmt;
		} else if (stmt == null) {
			return rest;
		} else {
			return new StatementListAst(stmt, rest);
		}
	}

	// EXPRESSION_LIST : ASSIGNMENT_EXPRESSION [, EXPRESSION_LIST]
	AST EXPRESSION_LIST(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		AST expr = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false); // do NOT allow multidim indices
																																									// expressions
		if (token == _COMMA_) {
			lexer();
			opt_newline();
			return new FunctionCallParamListAst(expr, EXPRESSION_LIST(not_in_print_root, allow_in_keyword));
		} else {
			return new FunctionCallParamListAst(expr, null);
		}
	}

	// ASSIGNMENT_EXPRESSION = COMMA_EXPRESSION [ (=,+=,-=,*=) ASSIGNMENT_EXPRESSION ]
	AST ASSIGNMENT_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST comma_expression = COMMA_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST assignment_expression = null;
		if (token == _EQUALS_
				|| token == _PLUS_EQ_
				|| token == _MINUS_EQ_
				|| token == _MULT_EQ_
				|| token == _DIV_EQ_
				|| token == _MOD_EQ_
				|| token == _POW_EQ_) {
			op = token;
			txt = text.toString();
			lexer();
			assignment_expression = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new AssignmentExpressionAst(comma_expression, op, txt, assignment_expression);
		}
		return comma_expression;
	}

	// COMMA_EXPRESSION = TERNARY_EXPRESSION [, COMMA_EXPRESSION] !!!ONLY IF!!! allow_multidim_indices is true
	// allow_multidim_indices is set to true when we need (1,2,3,4) expressions to collapse into an array index expression
	// (converts 1,2,3,4 to 1 SUBSEP 2 SUBSEP 3 SUBSEP 4) after an open parenthesis (grouping) expression starter
	AST COMMA_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST concat_expression = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (allow_multidim_indices && token == _COMMA_) {
			// consume the comma
			lexer();
			opt_newline();
			AST rest = COMMA_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			if (rest instanceof ArrayIndexAst) {
				return new ArrayIndexAst(concat_expression, rest);
			} else {
				return new ArrayIndexAst(concat_expression, new ArrayIndexAst(rest, null));
			}
		} else {
			return concat_expression;
		}
	}

	// TERNARY_EXPRESSION = LOGICAL_OR_EXPRESSION [ ? TERNARY_EXPRESSION : TERNARY_EXPRESSION ]
	AST TERNARY_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST le1 = LOGICAL_OR_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == _QUESTION_MARK_) {
			lexer();
			AST true_block = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			lexer(_COLON_);
			AST false_block = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new TernaryExpressionAst(le1, true_block, false_block);
		} else {
			return le1;
		}
	}

	// LOGICAL_OR_EXPRESSION = LOGICAL_AND_EXPRESSION [ || LOGICAL_OR_EXPRESSION ]
	AST LOGICAL_OR_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST le2 = LOGICAL_AND_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le1 = null;
		if (token == _OR_) {
			op = token;
			txt = text.toString();
			lexer();
			le1 = LOGICAL_OR_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpressionAst(le2, op, txt, le1);
		}
		return le2;
	}

	// LOGICAL_AND_EXPRESSION = IN_EXPRESSION [ && LOGICAL_AND_EXPRESSION ]
	AST LOGICAL_AND_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST comparison_expression = IN_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le2 = null;
		if (token == _AND_) {
			op = token;
			txt = text.toString();
			lexer();
			le2 = LOGICAL_AND_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpressionAst(comparison_expression, op, txt, le2);
		}
		return comparison_expression;
	}

	// IN_EXPRESSION = MATCHING_EXPRESSION [ IN_EXPRESSION ]
	// allow_in_keyword is set false while parsing the first expression within
	// a for() statement (because it could be "for (key in arr)", and this
	// production will consume and the for statement will never have a chance
	// of processing it
	// all other times, it is true
	AST IN_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		// true = allow post_inc/dec operators
		AST comparison = MATCHING_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (allow_in_keyword && token == KEYWORDS.get("in")) {
			lexer();
			return new InExpressionAst(
					comparison,
					IN_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		}
		return comparison;
	}

	// MATCHING_EXPRESSION = COMPARISON_EXPRESSION [ (~,!~) MATCHING_EXPRESSION ]
	AST MATCHING_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST expression = COMPARISON_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST comparison_expression = null;
		if (token == _MATCHES_ || token == _NOT_MATCHES_) {
			op = token;
			txt = text.toString();
			lexer();
			comparison_expression = MATCHING_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new ComparisonExpressionAst(expression, op, txt, comparison_expression);
		}
		return expression;
	}

	// COMPARISON_EXPRESSION = CONCAT_EXPRESSION [ (==,>,>=,<,<=,!=,|) COMPARISON_EXPRESSION ]
	// not_in_print_root is set false when within a print/printf statement;
	// all other times it is set true
	AST COMPARISON_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST expression = CONCAT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST comparison_expression = null;
		// Allow < <= == != >=, and only > if comparators are allowed
		if (token == _EQ_
				|| token == _GE_
				|| token == _LT_
				|| token == _LE_
				|| token == _NE_
				|| (token == _GT_ && not_in_print_root)) {
			op = token;
			txt = text.toString();
			lexer();
			comparison_expression = COMPARISON_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new ComparisonExpressionAst(expression, op, txt, comparison_expression);
		} else if (not_in_print_root && token == _PIPE_) {
			lexer();
			return GETLINE_EXPRESSION(expression, not_in_print_root, allow_in_keyword);
		}

		return expression;
	}

	// CONCAT_EXPRESSION = EXPRESSION [ CONCAT_EXPRESSION ]
	AST CONCAT_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST te = EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == _INTEGER_
				||
				token == _DOUBLE_
				||
				token == _OPEN_PAREN_
				||
				token == _FUNC_ID_
				||
				token == _INC_
				||
				token == _DEC_
				||
				token == _ID_
				||
				token == _STRING_
				||
				token == _DOLLAR_
				||
				token == _BUILTIN_FUNC_NAME_
				||
				token == _EXTENSION_) {
			// allow concatination here only when certain tokens follow
			return new ConcatExpressionAst(
					te,
					CONCAT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else
			if (additional_type_functions
					&& (token == KEYWORDS.get("_INTEGER")
							|| token == KEYWORDS.get("_DOUBLE")
							|| token == KEYWORDS.get("_STRING"))) {
								// allow concatenation here only when certain tokens follow
								return new ConcatExpressionAst(
										te,
										CONCAT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices));
							} else {
								return te;
							}
	}

	// EXPRESSION : TERM [ (+|-) EXPRESSION ]
	AST EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST term = TERM(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		while (token == _PLUS_ || token == _MINUS_) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST nextTerm = TERM(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			// Build the tree in left-associative manner
			term = new BinaryExpressionAst(term, op, txt, nextTerm);
		}
		return term;
	}

	// TERM : UNARY_FACTOR [ (*|/|%) TERM ]
	AST TERM(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices) throws IOException {
		AST unaryFactor = UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		while (token == _MULT_ || token == _DIVIDE_ || token == _MOD_) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST nextUnaryFactor = UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			// Build the tree in left-associative manner
			unaryFactor = new BinaryExpressionAst(unaryFactor, op, txt, nextUnaryFactor);
		}
		return unaryFactor;
	}

	// UNARY_FACTOR : [ ! | - | + ] POWER_FACTOR
	AST UNARY_FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		if (token == _NOT_) {
			lexer();
			return new NotExpressionAst(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else if (token == _MINUS_) {
			lexer();
			return new NegativeExpressionAst(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else if (token == _PLUS_) {
			lexer();
			return new UnaryPlusExpressionAst(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else {
			return POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		}
	}

	// POWER_FACTOR : FACTOR_FOR_INCDEC [ ^ POWER_FACTOR ]
	AST POWER_FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST incdec_ast = FACTOR_FOR_INCDEC(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == _POW_) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST term = POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			return new BinaryExpressionAst(incdec_ast, op, txt, term);
		}
		return incdec_ast;
	}

	// according to the spec, pre/post inc can occur
	// only on lvalues, which are NAMES (IDs), array,
	// or field references
	private boolean isLvalue(AST ast) {
		return (ast instanceof IdAst) || (ast instanceof ArrayReferenceAst) || (ast instanceof DollarExpressionAst);
	}

	AST FACTOR_FOR_INCDEC(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean pre_inc = false;
		boolean pre_dec = false;
		boolean post_inc = false;
		boolean post_dec = false;
		if (token == _INC_) {
			pre_inc = true;
			lexer();
		} else if (token == _DEC_) {
			pre_dec = true;
			lexer();
		}

		AST factor_ast = FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);

		if ((pre_inc || pre_dec) && !isLvalue(factor_ast)) {
			throw new ParserException("Cannot pre inc/dec a non-lvalue");
		}

		// only do post ops if:
		// - factor_ast is an lvalue
		// - pre ops were not encountered
		if (isLvalue(factor_ast) && !pre_inc && !pre_dec) {
			if (token == _INC_) {
				post_inc = true;
				lexer();
			} else if (token == _DEC_) {
				post_dec = true;
				lexer();
			}
		}

		if ((pre_inc || pre_dec) && (post_inc || post_dec)) {
			throw new ParserException("Cannot do pre inc/dec AND post inc/dec.");
		}

		if (pre_inc) {
			return new PreIncAst(factor_ast);
		} else if (pre_dec) {
			return new PreDecAst(factor_ast);
		} else if (post_inc) {
			return new PostIncAst(factor_ast);
		} else if (post_dec) {
			return new PostDecAst(factor_ast);
		} else {
			return factor_ast;
		}
	}

	// FACTOR : '(' ASSIGNMENT_EXPRESSION ')' | _INTEGER_ | _DOUBLE_ | _STRING_ | GETLINE [ID-or-array-or-$val] | /[=].../
	// | [++|--] SYMBOL [++|--]
	// AST FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_post_incdec_operators)
	AST FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices) throws IOException {
		if (token == _OPEN_PAREN_) {
			lexer();
			// true = allow multi-dimensional array indices (i.e., commas for 1,2,3,4)
			AST assignment_expression = ASSIGNMENT_EXPRESSION(true, allow_in_keyword, true);
			if (allow_multidim_indices && (assignment_expression instanceof ArrayIndexAst)) {
				throw new ParserException("Cannot nest multi-dimensional array index expressions.");
			}
			lexer(_CLOSE_PAREN_);
			return assignment_expression;
		} else if (token == _INTEGER_) {
			AST integer = symbol_table.addINTEGER(text.toString());
			lexer();
			return integer;
		} else if (token == _DOUBLE_) {
			AST dbl = symbol_table.addDOUBLE(text.toString());
			lexer();
			return dbl;
		} else if (token == _STRING_) {
			AST str = symbol_table.addSTRING(string.toString());
			lexer();
			return str;
		} else if (token == KEYWORDS.get("getline")) {
			return GETLINE_EXPRESSION(null, not_in_print_root, allow_in_keyword);
		} else if (token == _DIVIDE_ || token == _DIV_EQ_) {
			readRegexp();
			if (token == _DIV_EQ_) {
				regexp.insert(0, '=');
			}
			AST regexp_ast = symbol_table.addREGEXP(regexp.toString());
			lexer();
			return regexp_ast;
		} else if (additional_type_functions && token == KEYWORDS.get("_INTEGER")) {
			return INTEGER_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		} else if (additional_type_functions && token == KEYWORDS.get("_DOUBLE")) {
			return DOUBLE_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		} else if (additional_type_functions && token == KEYWORDS.get("_STRING")) {
			return STRING_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		} else {
			if (token == _DOLLAR_) {
				lexer();
				if (token == _INC_ || token == _DEC_) {
					return new DollarExpressionAst(
							FACTOR_FOR_INCDEC(not_in_print_root, allow_in_keyword, allow_multidim_indices));
				}
				if (token == _NOT_ || token == _MINUS_ || token == _PLUS_) {
					return new DollarExpressionAst(UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
				}
				return new DollarExpressionAst(FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
			}
			return SYMBOL(not_in_print_root, allow_in_keyword);
		}
	}

	AST INTEGER_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_INTEGER");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST int_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				int_expr_ast = new IntegerExpressionAst(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return int_expr_ast;
		}
	}

	AST DOUBLE_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_DOUBLE");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST double_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				double_expr_ast = new DoubleExpressionAst(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return double_expr_ast;
		}
	}

	AST STRING_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_STRING");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(_OPEN_PAREN_);
			}
			AST string_expr_ast;
			if (token == _CLOSE_PAREN_) {
				throw new ParserException("expression expected");
			} else {
				string_expr_ast = new StringExpressionAst(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return string_expr_ast;
		}
	}

	// SYMBOL : _ID_ [ '(' params ')' | '[' ASSIGNMENT_EXPRESSION ']' ]
	AST SYMBOL(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		if (token != _ID_ && token != _FUNC_ID_ && token != _BUILTIN_FUNC_NAME_ && token != _EXTENSION_) {
			throw new ParserException("Expecting an ID. Got " + toTokenString(token) + ": " + text);
		}
		int idToken = token;
		String id = text.toString();
		boolean parens = c == '(';
		lexer();

		if (idToken == _EXTENSION_) {
			String extensionKeyword = id;
			// JawkExtension extension = extensions.get(extensionKeyword);
			AST params;

			/*
			 * if (extension.requiresParen()) {
			 * lexer(_OPEN_PAREN_);
			 * if (token == _CLOSE_PAREN_)
			 * params = null;
			 * else
			 * params = EXPRESSION_LIST(not_in_print_root, allow_in_keyword);
			 * lexer(_CLOSE_PAREN_);
			 * } else {
			 * boolean parens = c == '(';
			 * //expectKeyword("delete");
			 * if (parens) {
			 * assert token == _OPEN_PAREN_;
			 * lexer();
			 * }
			 * //AST symbol_ast = SYMBOL(true,true); // allow comparators
			 * params = EXPRESSION_LIST(not_in_print_root, allow_in_keyword);
			 * if (parens)
			 * lexer(_CLOSE_PAREN_);
			 * }
			 */

			// if (extension.requiresParens() || parens)
			if (parens) {
				lexer();
				if (token == _CLOSE_PAREN_) {
					params = null;
				} else { // ?//params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
					params = EXPRESSION_LIST(true, allow_in_keyword); // NO comparators allowed, allow in expression
				}
				lexer(_CLOSE_PAREN_);
			} else {
				/*
				 * if (token == _NEWLINE_ || token == _SEMICOLON_ || token == _CLOSE_BRACE_ || token == _CLOSE_PAREN_
				 * || (token == _GT_ || token == _APPEND_ || token == _PIPE_) )
				 * params = null;
				 * else
				 * params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
				 */
				params = null;
			}

			return new ExtensionAst(extensionKeyword, params);
		} else if (idToken == _FUNC_ID_ || idToken == _BUILTIN_FUNC_NAME_) {
			AST params;
			// length can take on the special form of no parens
			if (id.equals("length")) {
				if (token == _OPEN_PAREN_) {
					lexer();
					if (token == _CLOSE_PAREN_) {
						params = null;
					} else {
						params = EXPRESSION_LIST(true, allow_in_keyword);
					}
					lexer(_CLOSE_PAREN_);
				} else {
					params = null;
				}
			} else {
				lexer(_OPEN_PAREN_);
				if (token == _CLOSE_PAREN_) {
					params = null;
				} else {
					params = EXPRESSION_LIST(true, allow_in_keyword);
				}
				lexer(_CLOSE_PAREN_);
			}
			if (idToken == _BUILTIN_FUNC_NAME_) {
				return new BuiltinFunctionCallAst(id, params);
			} else {
				return symbol_table.addFunctionCall(id, params);
			}
		}
		if (token == _OPEN_BRACKET_) {
			lexer();
			AST idx_ast = ARRAY_INDEX(true, allow_in_keyword);
			lexer(_CLOSE_BRACKET_);
			if (token == _OPEN_BRACKET_) {
				throw new ParserException("Use [a,b,c,...] instead of [a][b][c]... for multi-dimensional arrays.");
			}
			return symbol_table.addArrayReference(id, idx_ast);
		}
		return symbol_table.addID(id);
	}

	// ARRAY_INDEX : ASSIGNMENT_EXPRESSION [, ARRAY_INDEX]
	AST ARRAY_INDEX(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		AST expr_ast = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false);
		if (token == _COMMA_) {
			opt_newline();
			lexer();
			return new ArrayIndexAst(expr_ast, ARRAY_INDEX(not_in_print_root, allow_in_keyword));
		} else {
			return new ArrayIndexAst(expr_ast, null);
		}
	}

	// STATEMENT :
	// IF_STATEMENT
	// | WHILE_STATEMENT
	// | FOR_STATEMENT
	// | DO_STATEMENT
	// | RETURN_STATEMENT
	// | ASSIGNMENT_EXPRESSION
	AST STATEMENT() throws IOException {
		if (token == _OPEN_BRACE_) {
			lexer();
			AST lst = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
			return lst;
		}
		AST stmt;
		if (token == KEYWORDS.get("if")) {
			stmt = IF_STATEMENT();
		} else if (token == KEYWORDS.get("while")) {
			stmt = WHILE_STATEMENT();
		} else if (token == KEYWORDS.get("for")) {
			stmt = FOR_STATEMENT();
		} else {
			if (token == KEYWORDS.get("do")) {
				stmt = DO_STATEMENT();
			} else if (token == KEYWORDS.get("return")) {
				stmt = RETURN_STATEMENT();
			} else if (token == KEYWORDS.get("exit")) {
				stmt = EXIT_STATEMENT();
			} else if (token == KEYWORDS.get("delete")) {
				stmt = DELETE_STATEMENT();
			} else if (token == KEYWORDS.get("print")) {
				stmt = PRINT_STATEMENT();
			} else if (token == KEYWORDS.get("printf")) {
				stmt = PRINTF_STATEMENT();
			} else if (token == KEYWORDS.get("next")) {
				stmt = NEXT_STATEMENT();
			} else if (token == KEYWORDS.get("continue")) {
				stmt = CONTINUE_STATEMENT();
			} else if (token == KEYWORDS.get("break")) {
				stmt = BREAK_STATEMENT();
			} else if (additional_functions && token == KEYWORDS.get("_sleep")) {
				stmt = SLEEP_STATEMENT();
			} else if (additional_functions && token == KEYWORDS.get("_dump")) {
				stmt = DUMP_STATEMENT();
			} else {
				stmt = EXPRESSION_STATEMENT(true, false); // allow in keyword, do NOT allow NonStatement_ASTs
			}
			terminator();
			return stmt;
		}
		// NO TERMINATOR FOR IF, WHILE, AND FOR
		// (leave it for absorption by the callee)
		return stmt;
	}

	AST EXPRESSION_STATEMENT(boolean allow_in_keyword, boolean allow_non_statement_asts) throws IOException {
		// true = allow comparators
		// false = do NOT allow multi-dimensional array indices
		// return new ExpressionStatementAst(ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false));

		AST expr_ast = ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false);
		if (!allow_non_statement_asts && expr_ast instanceof NonStatement_AST) {
			throw new ParserException("Not a valid statement.");
		}
		return new ExpressionStatementAst(expr_ast);
	}

	AST IF_STATEMENT() throws IOException {
		expectKeyword("if");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow in keyword, do NOT allow multidim
																													// indices expressions
		lexer(_CLOSE_PAREN_);

		//// Was:
		//// AST b1 = BLOCK_OR_STMT();
		//// But it didn't handle
		//// if ; else ...
		//// properly
		opt_newline();
		AST b1;
		if (token == _SEMICOLON_) {
			lexer();
			// consume the newline after the semicolon
			opt_newline();
			b1 = null;
		} else {
			b1 = BLOCK_OR_STMT();
		}

		// The OPT_NEWLINE() above causes issues with the following form:
		// if (...) {
		// }
		// else { ... }
		// The \n before the else disassociates subsequent statements
		// if an "else" does not immediately follow.
		// To accommodate, the if_statement will continue to manage
		// statements, causing the original OPT_STATEMENT_LIST to relinquish
		// processing statements to this OPT_STATEMENT_LIST.

		opt_newline();
		if (token == KEYWORDS.get("else")) {
			lexer();
			opt_newline();
			AST b2 = BLOCK_OR_STMT();
			return new IfStatementAst(expr, b1, b2);
		} else {
			AST if_ast = new IfStatementAst(expr, b1, null);
			return if_ast;
		}
	}

	AST BREAK_STATEMENT() throws IOException {
		expectKeyword("break");
		return new BreakStatementAst();
	}

	AST BLOCK_OR_STMT() throws IOException {
		// default case, does NOT consume (require) a terminator
		return BLOCK_OR_STMT(false);
	}

	AST BLOCK_OR_STMT(boolean require_terminator) throws IOException {
		opt_newline();
		AST block;
		// HIJACK BRACES HERE SINCE WE MAY NOT HAVE A TERMINATOR AFTER THE CLOSING BRACE
		if (token == _OPEN_BRACE_) {
			lexer();
			block = STATEMENT_LIST();
			lexer(_CLOSE_BRACE_);
			return block;
		} else if (token == _SEMICOLON_) {
			block = null;
		} else {
			block = STATEMENT();
			// NO TERMINATOR HERE!
		}
		if (require_terminator) {
			terminator();
		}
		return block;
	}

	AST WHILE_STATEMENT() throws IOException {
		expectKeyword("while");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do NOT allow multidim
																													// indices expressions
		lexer(_CLOSE_PAREN_);
		AST block = BLOCK_OR_STMT();
		return new WhileStatementAst(expr, block);
	}

	AST FOR_STATEMENT() throws IOException {
		expectKeyword("for");
		AST expr1 = null;
		AST expr2 = null;
		AST expr3 = null;
		lexer(_OPEN_PAREN_);
		expr1 = OPT_SIMPLE_STATEMENT(false); // false = "no in keyword allowed"

		// branch here if we expect a for(... in ...) statement
		if (token == KEYWORDS.get("in")) {
			if (expr1.ast1 == null || expr1.ast2 != null) {
				throw new ParserException("Invalid expression prior to 'in' statement. Got : " + expr1);
			}
			expr1 = expr1.ast1;
			// analyze expr1 to make sure it's a singleton IdAst
			if (!(expr1 instanceof IdAst)) {
				throw new ParserException("Expecting an ID for 'in' statement. Got : " + expr1);
			}
			// in
			lexer();
			// id
			if (token != _ID_) {
				throw new ParserException(
						"Expecting an ARRAY ID for 'in' statement. Got " + toTokenString(token) + ": " + text);
			}
			String arr_id = text.toString();

			// not an indexed array reference!
			AST array_id_ast = symbol_table.addArrayID(arr_id);

			lexer();
			// close paren ...
			lexer(_CLOSE_PAREN_);
			AST block = BLOCK_OR_STMT();
			return new ForInStatementAst(expr1, array_id_ast, block);
		}

		if (token == _SEMICOLON_) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got " + toTokenString(token) + ": " + text);
		}
		if (token != _SEMICOLON_) {
			expr2 = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do NOT allow multidim
																												// indices expressions
		}
		if (token == _SEMICOLON_) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got " + toTokenString(token) + ": " + text);
		}
		if (token != _CLOSE_PAREN_) {
			expr3 = OPT_SIMPLE_STATEMENT(true); // true = "allow the in keyword"
		}
		lexer(_CLOSE_PAREN_);
		AST block = BLOCK_OR_STMT();
		return new ForStatementAst(expr1, expr2, expr3, block);
	}

	AST OPT_SIMPLE_STATEMENT(boolean allow_in_keyword) throws IOException {
		if (token == _SEMICOLON_) {
			return null;
		} else if (token == KEYWORDS.get("delete")) {
			return DELETE_STATEMENT();
		} else if (token == KEYWORDS.get("print")) {
			return PRINT_STATEMENT();
		} else if (token == KEYWORDS.get("printf")) {
			return PRINTF_STATEMENT();
		} else {
			// allow NonStatement_ASTs
			return EXPRESSION_STATEMENT(allow_in_keyword, true);
		}
	}

	AST DELETE_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("delete");
		if (parens) {
			assert token == _OPEN_PAREN_;
			lexer();
		}
		AST symbol_ast = SYMBOL(true, true); // allow comparators
		if (parens) {
			lexer(_CLOSE_PAREN_);
		}

		return new DeleteStatementAst(symbol_ast);
	}

	private static final class ParsedPrintStatement {

		private AST funcParams;
		private int outputToken;
		private AST outputExpr;

		ParsedPrintStatement(AST funcParams, int outputToken, AST outputExpr) {
			this.funcParams = funcParams;
			this.outputToken = outputToken;
			this.outputExpr = outputExpr;
		}

		public AST getFuncParams() {
			return funcParams;
		}

		public int getOutputToken() {
			return outputToken;
		}

		public AST getOutputExpr() {
			return outputExpr;
		}
	}

	private ParsedPrintStatement parsePrintStatement(boolean parens) throws IOException {
		AST funcParams;
		int outputToken;
		AST outputExpr;
		if (parens) {
			lexer();
			if (token == _CLOSE_PAREN_) {
				funcParams = null;
			} else {
				funcParams = EXPRESSION_LIST(true, true); // comparators are allowed, and also in expression
			}
			lexer(_CLOSE_PAREN_);
		} else {
			if (token == _NEWLINE_
					|| token == _SEMICOLON_
					|| token == _CLOSE_BRACE_
					|| token == _CLOSE_PAREN_
					|| token == _GT_
					|| token == _APPEND_
					|| token == _PIPE_) {
				funcParams = null;
			} else {
				funcParams = EXPRESSION_LIST(false, true); // NO comparators allowed, allow in expression
			}
		}
		if (token == _GT_ || token == _APPEND_ || token == _PIPE_) {
			outputToken = token;
			lexer();
			outputExpr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do NOT
			// allow multidim indices expressions
		} else {
			outputToken = -1;
			outputExpr = null;
		}

		return new ParsedPrintStatement(funcParams, outputToken, outputExpr);
	}

	AST PRINT_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("print");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new PrintAst(
				parsedPrintStatement.getFuncParams(),
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST PRINTF_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("printf");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new PrintfAst(
				parsedPrintStatement.getFuncParams(),
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST GETLINE_EXPRESSION(AST pipe_expr, boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		expectKeyword("getline");
		AST lvalue = LVALUE(not_in_print_root, allow_in_keyword);
		if (lvalue == null) {
			throw new ParserException("Missing lvalue in getline expression");
		}
		if (token == _LT_) {
			lexer();
			AST assignment_expr = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false); // do NOT allow multidim
																																																// indices expressions
			return pipe_expr == null ?
					new GetlineAst(null, lvalue, assignment_expr) : new GetlineAst(pipe_expr, lvalue, assignment_expr);
		} else {
			return pipe_expr == null ? new GetlineAst(null, lvalue, null) : new GetlineAst(pipe_expr, lvalue, null);
		}
	}

	AST LVALUE(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		// false = do NOT allow multi dimension indices expressions
		if (token == _DOLLAR_) {
			return FACTOR(not_in_print_root, allow_in_keyword, false);
		}
		if (token == _ID_) {
			return FACTOR(not_in_print_root, allow_in_keyword, false);
		}
		return null;
	}

	AST DO_STATEMENT() throws IOException {
		expectKeyword("do");
		opt_newline();
		AST block = BLOCK_OR_STMT();
		if (token == _SEMICOLON_) {
			lexer();
		}
		opt_newline();
		expectKeyword("while");
		lexer(_OPEN_PAREN_);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do NOT allow
																													// multidim indices expressions
		lexer(_CLOSE_PAREN_);
		return new DoStatementAst(block, expr);
	}

	AST RETURN_STATEMENT() throws IOException {
		expectKeyword("return");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new ReturnStatementAst(null);
		} else {
			return new ReturnStatementAst(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																								// keyword, do NOT allow multidim
																																								// indices expressions
		}
	}

	AST EXIT_STATEMENT() throws IOException {
		expectKeyword("exit");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new ExitStatementAst(null);
		} else {
			return new ExitStatementAst(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																							// keyword, do NOT allow multidim indices
																																							// expressions
		}
	}

	AST SLEEP_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("_sleep");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new SleepStatementAst(null);
		} else {
			// allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer();
			}
			AST sleep_ast;
			if (token == _CLOSE_PAREN_) {
				sleep_ast = new SleepStatementAst(null);
			} else {
				sleep_ast = new SleepStatementAst(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow
																																											// IN keyword, do NOT allow
																																											// multidim indices expressions
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return sleep_ast;
		}
	}

	AST DUMP_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("_dump");
		if (token == _SEMICOLON_ || token == _NEWLINE_ || token == _CLOSE_BRACE_) {
			return new DumpStatementAst(null);
		} else {
			if (parens) {
				lexer();
			}
			AST dump_ast;
			if (token == _CLOSE_PAREN_) {
				dump_ast = new DumpStatementAst(null);
			} else {
				dump_ast = new DumpStatementAst(EXPRESSION_LIST(true, true)); // true = allow comparators, allow IN keyword
			}
			if (parens) {
				lexer(_CLOSE_PAREN_);
			}
			return dump_ast;
		}
	}

	AST NEXT_STATEMENT() throws IOException {
		expectKeyword("next");
		return new NextStatementAst();
	}

	AST CONTINUE_STATEMENT() throws IOException {
		expectKeyword("continue");
		return new ContinueStatementAst();
	}

	// CHECKSTYLE.ON MethodName

	private void expectKeyword(String keyword) throws IOException {
		if (token == KEYWORDS.get(keyword)) {
			lexer();
		} else {
			throw new ParserException("Expecting " + keyword + ". Got " + toTokenString(token) + ": " + text);
		}
	}

	// parser
	// ===============================================================================
	// AST class defs
	private abstract class AST implements AwkSyntaxTree {

		private final String sourceDescription = scriptSources.get(scriptSourcesCurrentIndex).getDescription();
		private final int lineNo = reader.getLineNumber() + 1;
		protected AST parent;
		protected AST ast1, ast2, ast3, ast4;

		protected final AST searchFor(Class<?> cls) {
			AST ptr = this;
			while (ptr != null) {
				if (cls.isInstance(ptr)) {
					return ptr;
				}
				ptr = ptr.parent;
			}
			return null;
		}

		protected AST() {}

		protected AST(AST ast1) {
			this.ast1 = ast1;

			if (ast1 != null) {
				ast1.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2) {
			this.ast1 = ast1;
			this.ast2 = ast2;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2, AST ast3) {
			this.ast1 = ast1;
			this.ast2 = ast2;
			this.ast3 = ast3;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
			if (ast3 != null) {
				ast3.parent = this;
			}
		}

		protected AST(AST ast1, AST ast2, AST ast3, AST ast4) {
			this.ast1 = ast1;
			this.ast2 = ast2;
			this.ast3 = ast3;
			this.ast4 = ast4;

			if (ast1 != null) {
				ast1.parent = this;
			}
			if (ast2 != null) {
				ast2.parent = this;
			}
			if (ast3 != null) {
				ast3.parent = this;
			}
			if (ast4 != null) {
				ast4.parent = this;
			}
		}

		/**
		 * Dump a meaningful text representation of this
		 * abstract syntax tree node to the output (print)
		 * stream. Either it is called directly by the
		 * application program, or it is called by the
		 * parent node of this tree node.
		 *
		 * @param ps The print stream to dump the text
		 *        representation.
		 */
		@Override
		public void dump(PrintStream ps) {
			dump(ps, 0);
		}

		private void dump(PrintStream ps, int lvl) {
			StringBuffer spaces = new StringBuffer();
			for (int i = 0; i < lvl; i++) {
				spaces.append(' ');
			}
			ps.println(spaces + toString());
			if (ast1 != null) {
				ast1.dump(ps, lvl + 1);
			}
			if (ast2 != null) {
				ast2.dump(ps, lvl + 1);
			}
			if (ast3 != null) {
				ast3.dump(ps, lvl + 1);
			}
			if (ast4 != null) {
				ast4.dump(ps, lvl + 1);
			}
		}

		/**
		 * Apply semantic checks to this node. The default
		 * implementation is to simply call semanticAnalysis()
		 * on all the children of this abstract syntax tree node.
		 * Therefore, this method must be overridden to provide
		 * meaningful semantic analysis / checks.
		 *
		 * @throws SemanticException upon a semantic error.
		 */
		@Override
		public void semanticAnalysis() {
			if (ast1 != null) {
				ast1.semanticAnalysis();
			}
			if (ast2 != null) {
				ast2.semanticAnalysis();
			}
			if (ast3 != null) {
				ast3.semanticAnalysis();
			}
			if (ast4 != null) {
				ast4.semanticAnalysis();
			}
		}

		/**
		 * Appends tuples to the AwkTuples list
		 * for this abstract syntax tree node. Subclasses
		 * must implement this method.
		 * <p>
		 * This is called either by the main program to generate a full
		 * list of tuples for the abstract syntax tree, or it is called
		 * by other abstract syntax tree nodes in response to their
		 * attempt at populating tuples.
		 *
		 * @param tuples The tuples to populate.
		 * @return The number of items left on the stack after
		 *         these tuples have executed.
		 */
		@Override
		public abstract int populateTuples(AwkTuples tuples);

		protected final void pushSourceLineNumber(AwkTuples tuples) {
			tuples.pushSourceLineNumber(lineNo);
		}

		protected final void popSourceLineNumber(AwkTuples tuples) {
			tuples.popSourceLineNumber(lineNo);
		}

		protected boolean is_begin = isBegin();

		private boolean isBegin() {
			boolean result = is_begin;
			if (!result && ast1 != null) {
				result = ast1.isBegin();
			}
			if (!result && ast2 != null) {
				result = ast2.isBegin();
			}
			if (!result && ast3 != null) {
				result = ast3.isBegin();
			}
			if (!result && ast4 != null) {
				result = ast4.isBegin();
			}
			return result;
		}

		protected boolean is_end = isEnd();

		private boolean isEnd() {
			boolean result = is_end;
			if (!result && ast1 != null) {
				result = ast1.isEnd();
			}
			if (!result && ast2 != null) {
				result = ast2.isEnd();
			}
			if (!result && ast3 != null) {
				result = ast3.isEnd();
			}
			if (!result && ast4 != null) {
				result = ast4.isEnd();
			}
			return result;
		}

		protected boolean is_function = isFunction();

		private boolean isFunction() {
			boolean result = is_function;
			if (!result && ast1 != null) {
				result = ast1.isFunction();
			}
			if (!result && ast2 != null) {
				result = ast2.isFunction();
			}
			if (!result && ast3 != null) {
				result = ast3.isFunction();
			}
			if (!result && ast4 != null) {
				result = ast4.isFunction();
			}
			return result;
		}

		public boolean isArray() {
			return false;
		}

		public boolean isScalar() {
			return false;
		}

		/**
		 * Made protected so that subclasses can access it.
		 * Package-level access was not necessary.
		 */
		protected class SemanticException extends RuntimeException {

			private static final long serialVersionUID = 1L;

			SemanticException(String msg) {
				super(msg + " (" + sourceDescription + ":" + lineNo + ")");
			}
		}

		protected final void throwSemanticException(String msg) {
			throw new SemanticException(msg);
		}

		@Override
		public String toString() {
			return getClass().getName().replaceFirst(".*[$.]", "");
		}
	}

	private abstract class ScalarExpressionAst extends AST {

		protected ScalarExpressionAst() {
			super();
		}

		protected ScalarExpressionAst(AST a1) {
			super(a1);
		}

		protected ScalarExpressionAst(AST a1, AST a2) {
			super(a1, a2);
		}

		protected ScalarExpressionAst(AST a1, AST a2, AST a3) {
			super(a1, a2, a3);
		}

		@Override
		public boolean isArray() {
			return false;
		}

		@Override
		public boolean isScalar() {
			return true;
		}
	}

	private static boolean isRule(AST ast) {
		return ast != null && !ast.isBegin() && !ast.isEnd() && !ast.isFunction();
	}

	/**
	 * Inspects the action rule condition whether it contains
	 * extensions. It does a superficial check of
	 * the abstract syntax tree of the action rule.
	 * In other words, it will not examine whether user-defined
	 * functions within the action rule contain extensions.
	 *
	 * @param ast The action rule expression to examine.
	 * @return true if the action rule condition contains
	 *         an extension; false otherwise.
	 */
	@SuppressWarnings("unused")
	private static boolean isExtensionConditionRule(AST ast) {
		if (!isRule(ast)) {
			return false;
		}
		if (ast.ast1 == null) {
			return false;
		}

		if (!containsASTType(ast.ast1, ExtensionAst.class)) {
			return false;
		}

		if (containsASTType(ast.ast1, new Class[] { FunctionCallAst.class, DollarExpressionAst.class })) {
			return false;
		}

		return true;
	}

	private static boolean containsASTType(AST ast, Class<?> cls) {
		return containsASTType(ast, new Class[] { cls });
	}

	private static boolean containsASTType(AST ast, Class<?>[] cls_array) {
		if (ast == null) {
			return false;
		}
		for (Class<?> cls : cls_array) {
			if (cls.isInstance(ast)) {
				return true;
			}
		}
		// prettier-ignore
		return containsASTType(ast.ast1, cls_array)
				|| containsASTType(ast.ast2, cls_array)
				|| containsASTType(ast.ast3, cls_array)
				|| containsASTType(ast.ast4, cls_array);
	}

	private Address next_address;

	private final class RuleListAst extends AST {

		private RuleListAst(AST rule, AST rest) {
			super(rule, rest);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			next_address = tuples.createAddress("next_address");

			Address exit_addr = tuples.createAddress("end blocks start address");

			// goto start address
			Address start_address = tuples.createAddress("start address");

			tuples.setExitAddress(exit_addr);

			tuples.gotoAddress(start_address);

			AST ptr;

			// compile functions

			ptr = this;
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isFunction()) {
					assert ptr.ast1 != null;
					int ast1_count = ptr.ast1.populateTuples(tuples);
					assert ast1_count == 0;
				}

				ptr = ptr.ast2;
			}

			// START OF MAIN BLOCK

			tuples.address(start_address);

			// initialze special variables
			IdAst nr_ast = symbol_table.getID("NR");
			IdAst fnr_ast = symbol_table.getID("FNR");
			IdAst nf_ast = symbol_table.getID("NF");
			IdAst fs_ast = symbol_table.getID("FS");
			IdAst rs_ast = symbol_table.getID("RS");
			IdAst ofs_ast = symbol_table.getID("OFS");
			IdAst ors_ast = symbol_table.getID("ORS");
			IdAst rstart_ast = symbol_table.getID("RSTART");
			IdAst rlength_ast = symbol_table.getID("RLENGTH");
			IdAst filename_ast = symbol_table.getID("FILENAME");
			IdAst subsep_ast = symbol_table.getID("SUBSEP");
			IdAst convfmt_ast = symbol_table.getID("CONVFMT");
			IdAst ofmt_ast = symbol_table.getID("OFMT");
			IdAst environ_ast = symbol_table.getID("ENVIRON");
			IdAst argc_ast = symbol_table.getID("ARGC");
			IdAst argv_ast = symbol_table.getID("ARGV");

			// MUST BE DONE AFTER FUNCTIONS ARE COMPILED,
			// and after special variables are made known to the symbol table
			// (see above)!
			tuples.setNumGlobals(symbol_table.numGlobals());

			tuples.nfOffset(nf_ast.offset);
			tuples.nrOffset(nr_ast.offset);
			tuples.fnrOffset(fnr_ast.offset);
			tuples.fsOffset(fs_ast.offset);
			tuples.rsOffset(rs_ast.offset);
			tuples.ofsOffset(ofs_ast.offset);
			tuples.orsOffset(ors_ast.offset);
			tuples.rstartOffset(rstart_ast.offset);
			tuples.rlengthOffset(rlength_ast.offset);
			tuples.filenameOffset(filename_ast.offset);
			tuples.subsepOffset(subsep_ast.offset);
			tuples.convfmtOffset(convfmt_ast.offset);
			tuples.ofmtOffset(ofmt_ast.offset);
			tuples.environOffset(environ_ast.offset);
			tuples.argcOffset(argc_ast.offset);
			tuples.argvOffset(argv_ast.offset);

			// grab all BEGINs
			ptr = this;
			// ptr.ast1 == blank rule condition (i.e.: { print })
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isBegin()) {
					ptr.ast1.populateTuples(tuples);
				}

				ptr = ptr.ast2;
			}

			// Do we have rules? (apart from BEGIN)
			// If we have rules or END, we need to parse the input
			boolean req_input = false;

			// Check for "normal" rules
			ptr = this;
			while (!req_input && (ptr != null)) {
				if (isRule(ptr.ast1)) {
					req_input = true;
				}
				ptr = ptr.ast2;
			}

			// Now check for "END" rules
			ptr = this;
			while (!req_input && (ptr != null)) {
				if (ptr.ast1 != null && ptr.ast1.isEnd()) {
					req_input = true;
				}
				ptr = ptr.ast2;
			}

			if (req_input) {
				Address input_loop_address = null;
				Address no_more_input = null;

				input_loop_address = tuples.createAddress("input_loop_address");
				tuples.address(input_loop_address);

				ptr = this;

				no_more_input = tuples.createAddress("no_more_input");
				tuples.consumeInput(no_more_input);

				// grab all INPUT RULES
				while (ptr != null) {
					// the first one of these is an input rule
					if (isRule(ptr.ast1)) {
						ptr.ast1.populateTuples(tuples);
					}
					ptr = ptr.ast2;
				}
				tuples.address(next_address);

				tuples.gotoAddress(input_loop_address);

				if (req_input) {
					tuples.address(no_more_input);
					// compiler has issue with missing nop here
					tuples.nop();
				}
			}

			// indicate where the first end block resides
			// in the event of an exit statement
			tuples.address(exit_addr);
			tuples.setWithinEndBlocks(true);

			// grab all ENDs
			ptr = this;
			while (ptr != null) {
				if (ptr.ast1 != null && ptr.ast1.isEnd()) {
					ptr.ast1.populateTuples(tuples);
				}
				ptr = ptr.ast2;
			}

			// force a nop here to resolve any addresses that haven't been resolved yet
			// (i.e., no_more_input wouldn't be resolved if there are no END{} blocks)
			tuples.nop();

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// made non-static to access the "next_address" field of the frontend
	private final class RuleAst extends AST implements Nextable {

		private RuleAst(AST opt_expression, AST opt_rule) {
			super(opt_expression, opt_rule);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				// just indicate to execute the rule
				tuples.push(1); // 1 == true
			} else {
				int result = ast1.populateTuples(tuples);
				assert result == 1;
			}
			// result of whether to execute or not is on the stack
			Address bypass_rule = tuples.createAddress("bypass_rule");
			tuples.ifFalse(bypass_rule);
			// execute the opt_rule here!
			if (ast2 == null) {
				if (ast1 == null || (!ast1.isBegin() && !ast1.isEnd())) {
					// display $0
					tuples.print(0);
				}
				// else, don't populate it with anything
				// (i.e., blank BEGIN/END rule)
			} else {
				// execute it, and leave nothing on the stack
				int ast2_count = ast2.populateTuples(tuples);
				assert ast2_count == 0;
			}
			tuples.address(bypass_rule).nop();
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public Address nextAddress() {
			if (!isRule(this)) {
				throw new SemanticException("Must call next within an input rule.");
			}
			if (next_address == null) {
				throw new SemanticException("Cannot call next here.");
			}
			return next_address;
		}
	}

	private final class IfStatementAst extends AST {

		private IfStatementAst(AST expr, AST b1, AST b2) {
			super(expr, b1, b2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;

			Address elseblock = tuples.createAddress("elseblock");

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(elseblock);
			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0;
			}
			if (ast3 == null) {
				tuples.address(elseblock);
			} else {
				Address end = tuples.createAddress("end");
				tuples.gotoAddress(end);
				tuples.address(elseblock);
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 0;
				tuples.address(end);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class TernaryExpressionAst extends ScalarExpressionAst {

		private TernaryExpressionAst(AST a1, AST a2, AST a3) {
			super(a1, a2, a3);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			assert ast3 != null;

			Address elseexpr = tuples.createAddress("elseexpr");
			Address end_tertiary = tuples.createAddress("end_tertiary");

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(elseexpr);
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.gotoAddress(end_tertiary);

			tuples.address(elseexpr);
			int ast3_result = ast3.populateTuples(tuples);
			assert ast3_result == 1;

			tuples.address(end_tertiary);

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class WhileStatementAst extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private WhileStatementAst(AST expr, AST block) {
			super(expr, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for while statements, the start-of-loop is the continue jump address
			continue_address = loop;

			// condition
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.ifFalse(break_address);

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0;
			}

			tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DoStatementAst extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private DoStatementAst(AST block, AST expr) {
			super(block, expr);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");
			continue_address = tuples.createAddress("continue_address");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 0;
			}

			// for do-while statements, the continue jump address is the loop condition
			tuples.address(continue_address);

			// condition
			assert ast2 != null;
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.ifTrue(loop);

			// tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForStatementAst extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForStatementAst(AST expr1, AST expr2, AST expr3, AST block) {
			super(expr1, expr2, expr3, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			break_address = tuples.createAddress("break_address");
			continue_address = tuples.createAddress("continue_address");

			// initial actions
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				for (int i = 0; i < ast1_result; i++) {
					tuples.pop();
				}
			}
			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (ast2 != null) {
				// condition
				// assert(ast2 != null);
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
				tuples.ifFalse(break_address);
			}

			if (ast4 != null) {
				// post loop action
				int ast4_result = ast4.populateTuples(tuples);
				assert ast4_result == 0;
			}

			// for for-loops, the continue jump address is the post-loop-action
			tuples.address(continue_address);

			// post-loop action
			if (ast3 != null) {
				int ast3_result = ast3.populateTuples(tuples);
				for (int i = 0; i < ast3_result; i++) {
					tuples.pop();
				}
			}

			tuples.gotoAddress(loop);

			tuples.address(break_address);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForInStatementAst extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForInStatementAst(AST key_id_ast, AST array_id_ast, AST block) {
			super(key_id_ast, array_id_ast, block);
		}

		@Override
		public Address breakAddress() {
			assert break_address != null;
			return break_address;
		}

		@Override
		public Address continueAddress() {
			assert continue_address != null;
			return continue_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			assert ast2 != null;

			IdAst array_id_ast = (IdAst) ast2;
			if (array_id_ast.isScalar()) {
				throw new SemanticException(array_id_ast + " is not an array");
			}
			array_id_ast.setArray(true);

			break_address = tuples.createAddress("break_address");

			ast2.populateTuples(tuples);
			// pops the array and pushes the keyset
			tuples.keylist();

			// stack now contains:
			// keylist

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for for-in loops, the continue jump address is the start-of-loop address
			continue_address = loop;

			assert tuples.checkClass(KeyList.class);

			// condition
			tuples.dup();
			tuples.isEmptyList(break_address);

			assert tuples.checkClass(KeyList.class);

			// take an element off the set
			tuples.dup();
			tuples.getFirstAndRemoveFromList();
			// assign it to the id
			tuples.assign(((IdAst) ast1).offset, ((IdAst) ast1).is_global);
			tuples.pop(); // remove the assignment result

			if (ast3 != null) {
				// execute the block
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 0;
			}
			// otherwise, there is no block to execute

			assert tuples.checkClass(KeyList.class);

			tuples.gotoAddress(loop);

			tuples.address(break_address);
			tuples.pop(); // keylist

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	@SuppressWarnings("unused")
	private final class EmptyStatementAst extends AST {

		private EmptyStatementAst() {
			super();
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// nothing to populate!
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	/**
	 * The AST for an expression used as a statement.
	 * If the expression returns a value, the value is popped
	 * off the stack and discarded.
	 */
	private final class ExpressionStatementAst extends AST {

		private ExpressionStatementAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int expr_count = ast1.populateTuples(tuples);
			if (expr_count == 1) {
				tuples.pop();
			} else if (expr_count != 0) {
				assert false : "expr_count = " + expr_count;
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class AssignmentExpressionAst extends ScalarExpressionAst {

		/** operand / operator */
		private int op;
		private String text;

		private AssignmentExpressionAst(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast2 != null;
			int ast2_count = ast2.populateTuples(tuples);
			assert ast2_count == 1;
			// here, stack contains one value
			if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				if (id_ast.isArray()) {
					throw new SemanticException("Cannot use " + id_ast + " as a scalar. It is an array.");
				}
				id_ast.setScalar(true);
				if (op == _EQUALS_) {
					// Expected side effect:
					// Upon assignment, if the var is RS, reapply RS to input streams.
					tuples.assign(id_ast.offset, id_ast.is_global);
				} else if (op == _PLUS_EQ_) {
					tuples.plusEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MINUS_EQ_) {
					tuples.minusEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MULT_EQ_) {
					tuples.multEq(id_ast.offset, id_ast.is_global);
				} else if (op == _DIV_EQ_) {
					tuples.divEq(id_ast.offset, id_ast.is_global);
				} else if (op == _MOD_EQ_) {
					tuples.modEq(id_ast.offset, id_ast.is_global);
				} else if (op == _POW_EQ_) {
					tuples.powEq(id_ast.offset, id_ast.is_global);
				} else {
					throw new Error("Unhandled op: " + op + " / " + text);
				}
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast1 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr = (ArrayReferenceAst) ast1;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				IdAst id_ast = (IdAst) arr.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("Cannot use " + id_ast + " as an array. It is a scalar.");
				}
				id_ast.setArray(true);
				if (op == _EQUALS_) {
					tuples.assignArray(id_ast.offset, id_ast.is_global);
				} else if (op == _PLUS_EQ_) {
					tuples.plusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MINUS_EQ_) {
					tuples.minusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MULT_EQ_) {
					tuples.multEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _DIV_EQ_) {
					tuples.divEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _MOD_EQ_) {
					tuples.modEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == _POW_EQ_) {
					tuples.powEqArray(id_ast.offset, id_ast.is_global);
				} else {
					throw new NotImplementedError("Unhandled op: " + op + " / " + text + " for arrays.");
				}
			} else if (ast1 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// stack contains eval of dollar arg

				if (op == _EQUALS_) {
					tuples.assignAsInputField();
				} else if (op == _PLUS_EQ_) {
					tuples.plusEqInputField();
				} else if (op == _MINUS_EQ_) {
					tuples.minusEqInputField();
				} else if (op == _MULT_EQ_) {
					tuples.multEqInputField();
				} else if (op == _DIV_EQ_) {
					tuples.divEqInputField();
				} else if (op == _MOD_EQ_) {
					tuples.modEqInputField();
				} else if (op == _POW_EQ_) {
					tuples.powEqInputField();
				} else {
					throw new NotImplementedError("Unhandled op: " + op + " / " + text + " for dollar expressions.");
				}
			} else {
				throw new SemanticException("Cannot perform an assignment on: " + ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class InExpressionAst extends ScalarExpressionAst {

		private InExpressionAst(AST arg, AST arr) {
			super(arg, arr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			if (!(ast2 instanceof IdAst)) {
				throw new SemanticException("Expecting an array for rhs of IN. Got an expression.");
			}
			IdAst arr_ast = (IdAst) ast2;
			if (arr_ast.isScalar()) {
				throw new SemanticException("Expecting an array for rhs of IN. Got a scalar.");
			}
			arr_ast.setArray(true);

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;

			int ast2_result = arr_ast.populateTuples(tuples);
			assert ast2_result == 1;

			tuples.isIn();

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ComparisonExpressionAst extends ScalarExpressionAst {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private ComparisonExpressionAst(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;

			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;

			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;

			// 2 values on the stack

			if (op == _EQ_) {
				tuples.cmpEq();
			} else if (op == _NE_) {
				tuples.cmpEq();
				tuples.not();
			} else if (op == _LT_) {
				tuples.cmpLt();
			} else if (op == _GT_) {
				tuples.cmpGt();
			} else if (op == _LE_) {
				tuples.cmpGt();
				tuples.not();
			} else if (op == _GE_) {
				tuples.cmpLt();
				tuples.not();
			} else if (op == _MATCHES_) {
				tuples.matches();
			} else if (op == _NOT_MATCHES_) {
				tuples.matches();
				tuples.not();
			} else {
				throw new Error("Unhandled op: " + op + " / " + text);
			}

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class LogicalExpressionAst extends ScalarExpressionAst {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private LogicalExpressionAst(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// exhibit short-circuit behavior
			Address end = tuples.createAddress("end");
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.dup();
			if (op == _OR_) {
				// short_circuit when op is OR and 1st arg is true
				tuples.ifTrue(end);
			} else if (op == _AND_) {
				tuples.ifFalse(end);
			} else {
				assert false : "Invalid op: " + op + " / " + text;
			}
			tuples.pop();
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;

			tuples.address(end);

			// turn the result into boolean one or zero
			tuples.toNumber();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class BinaryExpressionAst extends ScalarExpressionAst {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private BinaryExpressionAst(AST lhs, int op, String text, AST rhs) {
			super(lhs, rhs);
			this.op = op;
			this.text = text;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + op + "/" + text + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			if (op == _PLUS_) {
				tuples.add();
			} else if (op == _MINUS_) {
				tuples.subtract();
			} else if (op == _MULT_) {
				tuples.multiply();
			} else if (op == _DIVIDE_) {
				tuples.divide();
			} else if (op == _MOD_) {
				tuples.mod();
			} else if (op == _POW_) {
				tuples.pow();
			} else {
				throw new Error("Unhandled op: " + op + " / " + this);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ConcatExpressionAst extends ScalarExpressionAst {

		private ConcatExpressionAst(AST lhs, AST rhs) {
			super(lhs, rhs);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int lhs_count = ast1.populateTuples(tuples);
			assert lhs_count == 1;
			assert ast2 != null;
			int rhs_count = ast2.populateTuples(tuples);
			assert rhs_count == 1;
			tuples.concat();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class NegativeExpressionAst extends ScalarExpressionAst {

		private NegativeExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.negate();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class UnaryPlusExpressionAst extends ScalarExpressionAst {

		private UnaryPlusExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.unaryPlus();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class NotExpressionAst extends ScalarExpressionAst {

		private NotExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.not();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class DollarExpressionAst extends ScalarExpressionAst {

		private DollarExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.getInputField();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ArrayIndexAst extends ScalarExpressionAst {

		private ArrayIndexAst(AST expr_ast, AST next) {
			super(expr_ast, next);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			AST ptr = this;
			int cnt = 0;
			while (ptr != null) {
				assert ptr.ast1 != null;
				int ptr_ast1_result = ptr.ast1.populateTuples(tuples);
				assert ptr_ast1_result == 1;
				++cnt;
				ptr = ptr.ast2;
			}
			assert cnt >= 1;
			if (cnt > 1) {
				tuples.applySubsep(cnt);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	// made classname all capitals to stand out in a syntax tree dump
	private final class StatementListAst extends AST {

		private StatementListAst(AST statement_ast, AST rest) {
			super(statement_ast, rest);
		}

		/**
		 * Recursively process statements within this statement list.
		 * <p>
		 * It originally was done linearly. However, quirks in the grammar required
		 * a more general, recursive approach to processing this "list".
		 * <p>
		 * Note: this should be reevaluated periodically in case the grammar
		 * becomes linear again.
		 */
		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			// typical recursive processing of a list
			assert ast1 != null;
			int ast1_count = ast1.populateTuples(tuples);
			assert ast1_count == 0;
			if (ast2 != null) {
				int ast2_count = ast2.populateTuples(tuples);
				assert ast2_count == 0;
			}
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public String toString() {
			return super.toString() + " <" + ast1 + ">";
		}
	}

	private interface Returnable {
		Address returnAddress();
	}

	// made non-static to access the symbol table
	private final class FunctionDefAst extends AST implements Returnable {

		private String id;
		private Address function_address;
		private Address return_address;

		// to satisfy the Returnable interface

		@Override
		public Address returnAddress() {
			assert return_address != null;
			return return_address;
		}

		private FunctionDefAst(String id, AST params, AST func_body) {
			super(params, func_body);
			this.id = id;
			is_function = true;
		}

		public Address getAddress() {
			assert function_address != null;
			return function_address;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			function_address = tuples.createAddress("function: " + id);
			return_address = tuples.createAddress("return_address for " + id);

			// annotate the tuple list
			// (useful for compilation,
			// not necessary for interpretation)
			tuples.function(id, paramCount());

			// function_address refers to first function body statement
			// rather than to function def opcode because during
			// interpretation, the function definition is a nop,
			// and for compilation, the next match of the function
			// name can be used
			tuples.address(function_address);

			// the stack contains the parameters to the function call (in rev order, which is good)

			// execute the body
			// (function body could be empty [no statements])
			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 0 || ast2_result == 1;
			}

			tuples.address(return_address);

			tuples.returnFromFunction();

			/////////////////////////////////////////////

			popSourceLineNumber(tuples);
			return 0;
		}

		int paramCount() {
			AST ptr = ast1;
			int count = 0;
			while (ptr != null) {
				++count;
				ptr = ptr.ast1;
			}
			return count;
		}

		void checkActualToFormalParameters(AST actual_param_list) {
			AST a_ptr = actual_param_list;
			FunctionDefParamListAst f_ptr = (FunctionDefParamListAst) ast1;
			while (a_ptr != null) {
				// actual parameter
				AST aparam = a_ptr.ast1;
				// formal function parameter
				AST fparam = symbol_table.getFunctionParameterIDAST(id, f_ptr.id);

				if (aparam.isArray() && fparam.isScalar()) {
					aparam
							.throwSemanticException(
									id + ": Actual parameter (" + aparam + ") is an array, but formal parameter is used like a scalar.");
				}
				if (aparam.isScalar() && fparam.isArray()) {
					aparam
							.throwSemanticException(
									id + ": Actual parameter (" + aparam + ") is a scalar, but formal parameter is used like an array.");
				}
				// condition parameters appropriately
				// (based on function parameter semantics)
				if (aparam instanceof IdAst) {
					IdAst aparam_id_ast = (IdAst) aparam;
					if (fparam.isScalar()) {
						aparam_id_ast.setScalar(true);
					}
					if (fparam.isArray()) {
						aparam_id_ast.setArray(true);
					}
				}
				// next
				a_ptr = a_ptr.ast2;
				f_ptr = (FunctionDefParamListAst) f_ptr.ast1;
			}
		}
	}

	private final class FunctionCallAst extends ScalarExpressionAst {

		private FunctionProxy function_proxy;

		private FunctionCallAst(FunctionProxy function_proxy, AST params) {
			super(params);
			this.function_proxy = function_proxy;
		}

		/**
		 * Applies several semantic checks with respect
		 * to user-defined-function calls.
		 * <p>
		 * The checks performed are:
		 * <ul>
		 * <li>Make sure the function is defined.
		 * <li>The number of actual parameters does not
		 * exceed the number of formal parameters.
		 * <li>Matches actual parameters to formal parameter
		 * usage with respect to whether they are
		 * scalars, arrays, or either.
		 * (This determination is based on how
		 * the formal parameters are used within
		 * the function block.)
		 * </ul>
		 * A failure of any one of these checks
		 * results in a SemanticException.
		 *
		 * @throws SemanticException upon a failure of
		 *         any of the semantic checks specified above.
		 */
		@Override
		public void semanticAnalysis() throws SemanticException {
			if (!function_proxy.isDefined()) {
				throw new SemanticException("function " + function_proxy + " not defined");
			}
			int actual_param_count;
			if (ast1 == null) {
				actual_param_count = 0;
			} else {
				actual_param_count = actualParamCount();
			}
			int formal_param_count = function_proxy.getFunctionParamCount();
			if (formal_param_count < actual_param_count) {
				throw new SemanticException(
						"the "
								+ function_proxy.getFunctionName()
								+ " function"
								+ " only accepts at most "
								+ formal_param_count
								+ " parameter(s), not "
								+ actual_param_count);
			}
			if (ast1 != null) {
				function_proxy.checkActualToFormalParameters(ast1);
			}
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (!function_proxy.isDefined()) {
				throw new SemanticException("function " + function_proxy + " not defined");
			}
			tuples.scriptThis();
			int actual_param_count;
			if (ast1 == null) {
				actual_param_count = 0;
			} else {
				actual_param_count = ast1.populateTuples(tuples);
			}
			int formal_param_count = function_proxy.getFunctionParamCount();
			if (formal_param_count < actual_param_count) {
				throw new SemanticException(
						"the "
								+ function_proxy.getFunctionName()
								+ " function"
								+ " only accepts at most "
								+ formal_param_count
								+ " parameter(s), not "
								+ actual_param_count);
			}

			function_proxy.checkActualToFormalParameters(ast1);
			tuples.callFunction(function_proxy, function_proxy.getFunctionName(), formal_param_count, actual_param_count);
			popSourceLineNumber(tuples);
			return 1;
		}

		private int actualParamCount() {
			int cnt = 0;
			AST ptr = ast1;
			while (ptr != null) {
				assert ptr.ast1 != null;
				++cnt;
				ptr = ptr.ast2;
			}
			return cnt;
		}
	}

	private final class BuiltinFunctionCallAst extends ScalarExpressionAst {

		private String id;
		private int fIdx;

		private BuiltinFunctionCallAst(String id, AST params) {
			super(params);
			this.id = id;
			assert BUILTIN_FUNC_NAMES.get(id) != null;
			this.fIdx = BUILTIN_FUNC_NAMES.get(id);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (fIdx == BUILTIN_FUNC_NAMES.get("sprintf")) {
				if (ast1 == null) {
					throw new SemanticException("sprintf requires at least 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result == 0) {
					throw new SemanticException("sprintf requires at minimum 1 argument");
				}
				tuples.sprintf(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("close")) {
				if (ast1 == null) {
					throw new SemanticException("close requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("close requires only 1 argument");
				}
				tuples.close();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("length")) {
				if (ast1 == null) {
					tuples.length(0);
				} else {
					int ast1_result = ast1.populateTuples(tuples);
					if (ast1_result != 1) {
						throw new SemanticException("length requires at least one argument");
					}
					tuples.length(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("srand")) {
				if (ast1 == null) {
					tuples.srand(0);
				} else {
					int ast1_result = ast1.populateTuples(tuples);
					if (ast1_result != 1) {
						throw new SemanticException("srand takes either 0 or one argument, not " + ast1_result);
					}
					tuples.srand(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("rand")) {
				if (ast1 != null) {
					throw new SemanticException("rand does not take arguments");
				}
				tuples.rand();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sqrt")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sqrt requires only 1 argument");
				}
				tuples.sqrt();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("int")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.intFunc();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("log")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.log();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("exp")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("exp requires only 1 argument");
				}
				tuples.exp();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sin")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sin requires only 1 argument");
				}
				tuples.sin();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("cos")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("cos requires only 1 argument");
				}
				tuples.cos();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("atan2")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("atan2 requires 2 arguments");
				}
				tuples.atan2();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("match")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("match requires 2 arguments");
				}
				tuples.match();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("index")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("index requires 2 arguments");
				}
				tuples.index();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sub") || fIdx == BUILTIN_FUNC_NAMES.get("gsub")) {
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("sub needs at least 2 arguments");
				}
				boolean isGsub = fIdx == BUILTIN_FUNC_NAMES.get("gsub");

				int numargs = ast1.populateTuples(tuples);

				// stack contains arg1,arg2[,arg3] - in that pop() order

				if (numargs == 2) {
					tuples.subForDollar0(isGsub);
				} else if (numargs == 3) {
					AST ptr = ast1.ast2.ast2.ast1;
					if (ptr instanceof IdAst) {
						IdAst id_ast = (IdAst) ptr;
						if (id_ast.isArray()) {
							throw new SemanticException("sub cannot accept an unindexed array as its 3rd argument");
						}
						id_ast.setScalar(true);
						tuples.subForVariable(id_ast.offset, id_ast.is_global, isGsub);
					} else if (ptr instanceof ArrayReferenceAst) {
						ArrayReferenceAst arr_ast = (ArrayReferenceAst) ptr;
						// push the index
						int ast2_result = arr_ast.ast2.populateTuples(tuples);
						assert ast2_result == 1;
						IdAst id_ast = (IdAst) arr_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException("Cannot use " + id_ast + " as an array.");
						}
						tuples.subForArrayReference(id_ast.offset, id_ast.is_global, isGsub);
					} else if (ptr instanceof DollarExpressionAst) {
						// push the field ref
						DollarExpressionAst dollar_expr = (DollarExpressionAst) ptr;
						assert dollar_expr.ast1 != null;
						int ast1_result = dollar_expr.ast1.populateTuples(tuples);
						assert ast1_result == 1;
						tuples.subForDollarReference(isGsub);
					} else {
						throw new SemanticException(
								"sub's 3rd argument must be either an id, an array reference, or an input field reference");
					}
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("split")) {
				// split can take 2 or 3 args:
				// split (string, array [,fs])
				// the 2nd argument is pass by reference, which is ok (?)

				// funccallparamlist.funccallparamlist.id_ast
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("split needs at least 2 arguments");
				}
				AST ptr = ast1.ast2.ast1;
				if (!(ptr instanceof IdAst)) {
					throw new SemanticException("split needs an array name as its 2nd argument");
				}
				IdAst arr_ast = (IdAst) ptr;
				if (arr_ast.isScalar()) {
					throw new SemanticException("split's 2nd arg cannot be a scalar");
				}
				arr_ast.setArray(true);

				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2 && ast1_result != 3) {
					throw new SemanticException("split requires 2 or 3 arguments, not " + ast1_result);
				}
				tuples.split(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("substr")) {
				if (ast1 == null) {
					throw new SemanticException("substr requires at least 2 arguments");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2 && ast1_result != 3) {
					throw new SemanticException("substr requires 2 or 3 arguments, not " + ast1_result);
				}
				tuples.substr(ast1_result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("tolower")) {
				if (ast1 == null) {
					throw new SemanticException("tolower requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("tolower requires only 1 argument");
				}
				tuples.tolower();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("toupper")) {
				if (ast1 == null) {
					throw new SemanticException("toupper requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("toupper requires only 1 argument");
				}
				tuples.toupper();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("system")) {
				if (ast1 == null) {
					throw new SemanticException("system requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("system requires only 1 argument");
				}
				tuples.system();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("exec")) {
				if (ast1 == null) {
					throw new SemanticException("exec requires 1 argument");
				}
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("exec requires only 1 argument");
				}
				tuples.exec();
				popSourceLineNumber(tuples);
				return 1;
			} else {
				throw new NotImplementedError("builtin: " + id);
			}
		}
	}

	private final class FunctionCallParamListAst extends AST {

		private FunctionCallParamListAst(AST expr, AST rest) {
			super(expr, rest);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int retval;
			if (ast2 == null) {
				retval = ast1.populateTuples(tuples);
			} else {
				retval = ast1.populateTuples(tuples) + ast2.populateTuples(tuples);
			}
			popSourceLineNumber(tuples);
			return retval;
		}
	}

	private final class FunctionDefParamListAst extends AST {

		private String id;

		private FunctionDefParamListAst(String id, AST rest) {
			super(rest);
			this.id = id;
		}

		public int populateTuples(AwkTuples tuples) {
			throw new Error("Cannot 'execute' function definition parameter list (formal parameters) in this manner.");
		}

		/**
		 * According to the spec
		 * (http://www.opengroup.org/onlinepubs/007908799/xcu/awk.html)
		 * formal function parameters cannot be special variables,
		 * such as NF, NR, etc).
		 *
		 * @throws SemanticException upon a semantic error.
		 */
		@Override
		public void semanticAnalysis() throws SemanticException {
			// could do it recursively, but not necessary
			// since all ast1's are FunctionDefParamList's
			// and, thus, terminals (no need to do further
			// semantic analysis)

			FunctionDefParamListAst ptr = this;
			while (ptr != null) {
				if (SPECIAL_VAR_NAMES.get(ptr.id) != null) {
					throw new SemanticException("Special variable " + ptr.id + " cannot be used as a formal parameter");
				}
				ptr = (FunctionDefParamListAst) ptr.ast1;
			}
		}
	}

	/**
	 * A tag interface for non-statement expressions.
	 * Unknown for certain, but I think this is done
	 * to avoid partial variable assignment mistakes.
	 * For example, instead of a=3, the programmer
	 * inadvertently places the a on the line. If ID_ASTs
	 * were not tagged with NonStatement_AST, then the
	 * incomplete assignment would parse properly, and
	 * the developer might remain unaware of this issue.
	 */
	private interface NonStatement_AST {}

	private final class IdAst extends AST implements NonStatement_AST {

		private String id;
		private int offset = AVM.NULL_OFFSET;
		private boolean is_global;

		private IdAst(String id, boolean is_global) {
			this.id = id;
			this.is_global = is_global;
		}

		private boolean is_array = false;
		private boolean is_scalar = false;

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert offset != AVM.NULL_OFFSET : "offset = " + offset + " for " + this;
			tuples.dereference(offset, isArray(), is_global);
			popSourceLineNumber(tuples);
			return 1;
		}

		@Override
		public boolean isArray() {
			return is_array;
		}

		@Override
		public boolean isScalar() {
			return is_scalar;
		}

		private void setArray(boolean b) {
			is_array = b;
		}

		private void setScalar(boolean b) {
			is_scalar = b;
		}
	}

	private final class ArrayReferenceAst extends ScalarExpressionAst {

		private ArrayReferenceAst(AST id_ast, AST idx_ast) {
			super(id_ast, idx_ast);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + ast1 + " [...])";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			// get the array var
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			// get the index
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.dereferenceArray();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class IntegerAst extends ScalarExpressionAst implements NonStatement_AST {

		private Long I;

		private IntegerAst(Long I) {
			this.I = I;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + I + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(I);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * Can either assume the role of a double or an integer
	 * by aggressively normalizing the value to an int if possible.
	 */
	private final class DoubleAst extends ScalarExpressionAst implements NonStatement_AST {

		private Object D;

		private DoubleAst(Double D) {
			double d = D.doubleValue();
			if (d == (int) d) {
				this.D = (int) d;
			} else {
				this.D = d;
			}
		}

		@Override
		public String toString() {
			return super.toString() + " (" + D + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(D);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * A string is a string; Awk doesn't attempt to normalize
	 * it until it is used in an arithmetic operation!
	 */
	private final class StringAst extends ScalarExpressionAst implements NonStatement_AST {

		private String S;

		private StringAst(String str) {
			assert str != null;
			this.S = str;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + S + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(S);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class RegexpAst extends ScalarExpressionAst {

		private String regexp_str;

		private RegexpAst(String regexp_str) {
			assert regexp_str != null;
			this.regexp_str = regexp_str;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + regexp_str + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.regexp(regexp_str);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ConditionPairAst extends ScalarExpressionAst {

		private ConditionPairAst(AST boolean_ast_1, AST boolean_ast_2) {
			super(boolean_ast_1, boolean_ast_2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			assert ast2 != null;
			int ast2_result = ast2.populateTuples(tuples);
			assert ast2_result == 1;
			tuples.conditionPair();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class IntegerExpressionAst extends ScalarExpressionAst {

		private IntegerExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castInt();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class DoubleExpressionAst extends ScalarExpressionAst {

		private DoubleExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castDouble();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class StringExpressionAst extends ScalarExpressionAst {

		private StringExpressionAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			tuples.castString();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class BeginAst extends AST {

		private BeginAst() {
			super();
			is_begin = true;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(1);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class EndAst extends AST {

		private EndAst() {
			super();
			is_end = true;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(1);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PreIncAst extends ScalarExpressionAst {

		private PreIncAst(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				tuples.inc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr_ast = (ArrayReferenceAst) ast1;
				IdAst id_ast = (IdAst) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// OPTIMIATION: duplicate the x in $x here
				// so that it is not evaluated again
				tuples.dup();
				// stack contains eval of dollar arg
				// tuples.assignAsInputField();
				tuples.incDollarRef();
				// OPTIMIATION continued: now evaluate
				// the dollar expression with x (for $x)
				// instead of evaluating the expression again
				tuples.getInputField();
				popSourceLineNumber(tuples);
				return 1; // NOTE, short-circuit return here!
			} else {
				throw new NotImplementedError("unhandled preinc for " + ast1);
			}
			// else
			// assert false : "cannot refer for pre_inc to "+ast1;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PreDecAst extends ScalarExpressionAst {

		private PreDecAst(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				tuples.dec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr_ast = (ArrayReferenceAst) ast1;
				IdAst id_ast = (IdAst) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// OPTIMIATION: duplicate the x in $x here
				// so that it is not evaluated again
				tuples.dup();
				// stack contains eval of dollar arg
				// tuples.assignAsInputField();
				tuples.decDollarRef();
				// OPTIMIATION continued: now evaluate
				// the dollar expression with x (for $x)
				// instead of evaluating the expression again
				tuples.getInputField();
				popSourceLineNumber(tuples);
				return 1; // NOTE, short-circuit return here!
			} else {
				throw new NotImplementedError("unhandled predec for " + ast1);
			}
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostIncAst extends ScalarExpressionAst {

		private PostIncAst(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				tuples.postInc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr_ast = (ArrayReferenceAst) ast1;
				IdAst id_ast = (IdAst) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast1;
				assert dollar_expr.ast1 != null;
				int dollarast_ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert dollarast_ast1_result == 1;
				tuples.incDollarRef();
			} else {
				throw new NotImplementedError("unhandled postinc for " + ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostDecAst extends ScalarExpressionAst {

		private PostDecAst(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				tuples.postDec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr_ast = (ArrayReferenceAst) ast1;
				IdAst id_ast = (IdAst) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast1;
				assert dollar_expr.ast1 != null;
				int dollarast_ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert dollarast_ast1_result == 1;
				tuples.decDollarRef();
			} else {
				throw new NotImplementedError("unhandled postinc for " + ast1);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PrintAst extends ScalarExpressionAst {

		private int outputToken;

		private PrintAst(AST expr_list, int outToken, AST outputExpr) {
			super(expr_list, outputExpr);
			this.outputToken = outToken;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
				if (param_count == 0) {
					throw new SemanticException("Cannot print the result. The expression doesn't return anything.");
				}
			}

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
			}

			if (outputToken == _GT_) {
				tuples.printToFile(param_count, false); // false = no append
			} else if (outputToken == _APPEND_) {
				tuples.printToFile(param_count, true); // false = no append
			} else if (outputToken == _PIPE_) {
				tuples.printToPipe(param_count);
			} else {
				tuples.print(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// we don't know if it is a scalar
	private final class ExtensionAst extends AST {

		private String extensionKeyword;

		private ExtensionAst(String keyword, AST param_ast) {
			super(param_ast);
			this.extensionKeyword = keyword;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				/// Query for the extension.
				JawkExtension extension = extensions.get(extensionKeyword);
				int arg_count = countParams((FunctionCallParamListAst) ast1);
				/// Get all required assoc array parameters:
				int[] req_array_idxs = extension.getAssocArrayParameterPositions(extensionKeyword, arg_count);
				assert req_array_idxs != null;

				for (int idx : req_array_idxs) {
					AST param_ast = getParamAst((FunctionCallParamListAst) ast1, idx);
					assert ast1 instanceof FunctionCallParamListAst;
					// if the parameter is an IdAst...
					if (param_ast.ast1 instanceof IdAst) {
						// then force it to be an array,
						// or complain if it is already tagged as a scalar
						IdAst id_ast = (IdAst) param_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException(
									"Extension '"
											+ extensionKeyword
											+ "' requires parameter position "
											+ idx
											+ " be an associative array, not a scalar.");
						}
						id_ast.setArray(true);
					}
				}

				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
			}
			// is_initial == true ::
			// retval of this extension is not a function parameter
			// of another extension
			// true iff Extension | FunctionCallParam | FunctionCallParam | etc.
			boolean is_initial;
			if (parent instanceof FunctionCallParamListAst) {
				AST ptr = parent;
				while (ptr instanceof FunctionCallParamListAst) {
					ptr = ptr.parent;
				}
				is_initial = !(ptr instanceof ExtensionAst);
			} else {
				is_initial = true;
			}
			tuples.extension(extensionKeyword, param_count, is_initial);
			popSourceLineNumber(tuples);
			// an extension always returns a value, even if it is blank/null
			return 1;
		}

		private AST getParamAst(FunctionCallParamListAst p_ast, int pos) {
			for (int i = 0; i < pos; ++i) {
				p_ast = (FunctionCallParamListAst) p_ast.ast2;
				if (p_ast == null) {
					throw new SemanticException("More arguments required for assoc array parameter position specification.");
				}
			}
			return p_ast;
		}

		private int countParams(FunctionCallParamListAst p_ast) {
			int cnt = 0;
			while (p_ast != null) {
				p_ast = (FunctionCallParamListAst) p_ast.ast2;
				++cnt;
			}
			return cnt;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + extensionKeyword + ")";
		}
	}

	private final class PrintfAst extends ScalarExpressionAst {

		private int outputToken;

		private PrintfAst(AST expr_list, int outToken, AST outputExpr) {
			super(expr_list, outputExpr);
			this.outputToken = outToken;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				param_count = ast1.populateTuples(tuples);
				assert param_count >= 0;
				if (param_count == 0) {
					throw new SemanticException("Cannot printf the result. The expression doesn't return anything.");
				}
			}

			if (ast2 != null) {
				int ast2_result = ast2.populateTuples(tuples);
				assert ast2_result == 1;
			}

			if (outputToken == _GT_) {
				tuples.printfToFile(param_count, false); // false = no append
			} else if (outputToken == _APPEND_) {
				tuples.printfToFile(param_count, true); // false = no append
			} else if (outputToken == _PIPE_) {
				tuples.printfToPipe(param_count);
			} else {
				tuples.printf(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class GetlineAst extends ScalarExpressionAst {

		private GetlineAst(AST pipe_expr, AST lvalue_ast, AST in_redirect) {
			super(pipe_expr, lvalue_ast, in_redirect);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// stack has ast1 (i.e., "command")
				tuples.useAsCommandInput();
			} else if (ast3 != null) {
				// getline ... < ast3
				int ast3_result = ast3.populateTuples(tuples);
				assert ast3_result == 1;
				// stack has ast3 (i.e., "filename")
				tuples.useAsFileInput();
			} else {
				tuples.getlineInput();
			}
			// 2 resultant values on the stack!
			// 2nd - -1/0/1 for io-err,eof,success
			// 1st(top) - the input
			if (ast2 == null) {
				tuples.assignAsInput();
				// stack still has the input, to be popped below...
				// (all assignment results are placed on the stack)
			} else if (ast2 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast2;
				tuples.assign(id_ast.offset, id_ast.is_global);
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast2 instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr = (ArrayReferenceAst) ast2;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				IdAst id_ast = (IdAst) arr.ast1;
				tuples.assignArray(id_ast.offset, id_ast.is_global);
			} else if (ast2 instanceof DollarExpressionAst) {
				DollarExpressionAst dollar_expr = (DollarExpressionAst) ast2;
				if (dollar_expr.ast2 != null) {
					int ast2_result = dollar_expr.ast2.populateTuples(tuples);
					assert ast2_result == 1;
				}
				// stack contains eval of dollar arg
				tuples.assignAsInputField();
			} else {
				throw new SemanticException("Cannot getline into a " + ast2);
			}
			// get rid of value left by the assignment
			tuples.pop();
			// one value is left on the stack
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ReturnStatementAst extends AST {

		private ReturnStatementAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Returnable returnable = (Returnable) searchFor(Returnable.class);
			if (returnable == null) {
				throw new SemanticException("Cannot use return here.");
			}
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				tuples.setReturnResult();
			}
			tuples.gotoAddress(returnable.returnAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ExitStatementAst extends AST {

		private ExitStatementAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 != null) {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				tuples.exitWithCode();
			} else {
				tuples.exitWithoutCode();
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DeleteStatementAst extends AST {

		private DeleteStatementAst(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;

			if (ast1 instanceof ArrayReferenceAst) {
				assert ast1.ast1 != null; // a in a[b]
				assert ast1.ast2 != null; // b in a[b]
				IdAst id_ast = (IdAst) ast1.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("delete: Cannot use a scalar as an array.");
				}
				id_ast.setArray(true);
				int idx_result = ast1.ast2.populateTuples(tuples);
				assert idx_result == 1;
				// idx on the stack
				tuples.deleteArrayElement(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof IdAst) {
				IdAst id_ast = (IdAst) ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("delete: Cannot delete a scalar.");
				}
				id_ast.setArray(true);
				tuples.deleteArray(id_ast.offset, id_ast.is_global);
			} else {
				throw new Error("Should never reach here : delete for " + ast1);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class BreakStatementAst extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Breakable breakable = (Breakable) searchFor(Breakable.class);
			if (breakable == null) {
				throw new SemanticException("cannot break; not within a loop");
			}
			assert breakable != null;
			tuples.gotoAddress(breakable.breakAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class SleepStatementAst extends AST {

		private SleepStatementAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				tuples.sleep(0);
			} else {
				int ast1_result = ast1.populateTuples(tuples);
				assert ast1_result == 1;
				tuples.sleep(ast1_result);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DumpStatementAst extends AST {

		private DumpStatementAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				tuples.dump(0);
			} else {
				assert ast1 instanceof FunctionCallParamListAst;
				AST ptr = ast1;
				while (ptr != null) {
					if (!(ptr.ast1 instanceof IdAst)) {
						throw new SemanticException("ID required for argument(s) to _dump");
					}
					IdAst id_ast = (IdAst) ptr.ast1;
					if (id_ast.isScalar()) {
						throw new SemanticException("_dump: Cannot use a scalar as an argument.");
					}
					id_ast.setArray(true);
					ptr = ptr.ast2;
				}
				int ast1_result = ast1.populateTuples(tuples);
				tuples.dump(ast1_result);
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class NextStatementAst extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Nextable nextable = (Nextable) searchFor(Nextable.class);
			if (nextable == null) {
				throw new SemanticException("cannot next; not within any input rules");
			}
			assert nextable != null;
			tuples.gotoAddress(nextable.nextAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ContinueStatementAst extends AST {

		private ContinueStatementAst() {
			super();
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			Continueable continueable = (Continueable) searchFor(Continueable.class);
			if (continueable == null) {
				throw new SemanticException("cannot issue a continue; not within any loops");
			}
			assert continueable != null;
			tuples.gotoAddress(continueable.continueAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// this was static...
	// made non-static to throw a meaningful ParserException when necessary
	private final class FunctionProxy implements HasFunctionAddress {

		private FunctionDefAst function_def_ast;
		private String id;

		private FunctionProxy(String id) {
			this.id = id;
		}

		private void setFunctionDefinition(FunctionDefAst function_def) {
			if (function_def_ast != null) {
				throw new ParserException("function " + function_def + " already defined");
			} else {
				function_def_ast = function_def;
			}
		}

		private boolean isDefined() {
			return function_def_ast != null;
		}

		@Override
		public Address getFunctionAddress() {
			return function_def_ast.getAddress();
		}

		private String getFunctionName() {
			return id;
		}

		private int getFunctionParamCount() {
			return function_def_ast.paramCount();
		}

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		private void checkActualToFormalParameters(AST actual_params) {
			function_def_ast.checkActualToFormalParameters(actual_params);
		}
	}

	/**
	 * Adds {var_name -&gt; offset} mappings to the tuples so that global variables
	 * can be set by the interpreter while processing filename and name=value
	 * entries from the command-line.
	 * Also sends function names to the tuples, to provide the back end
	 * with names to invalidate if name=value assignments are passed
	 * in via the -v or ARGV arguments.
	 *
	 * @param tuples The tuples to add the mapping to.
	 */
	public void populateGlobalVariableNameToOffsetMappings(AwkTuples tuples) {
		for (String varname : symbol_table.global_ids.keySet()) {
			IdAst id_ast = symbol_table.global_ids.get(varname);
			// The last arg originally was ", id_ast.is_scalar", but this is not set true
			// if the variable use is ambiguous. Therefore, assume it is a scalar
			// if it's NOT used as an array.
			tuples.addGlobalVariableNameToOffsetMapping(varname, id_ast.offset, id_ast.is_array);
		}
		tuples.setFunctionNameSet(symbol_table.function_proxies.keySet());
	}

	private class AwkSymbolTableImpl {

		int numGlobals() {
			return global_ids.size();
		}

		// "constants"
		private BeginAst begin_ast = null;
		private EndAst end_ast = null;

		// functions (proxies)
		private Map<String, FunctionProxy> function_proxies = new HashMap<String, FunctionProxy>();

		// variable management
		private Map<String, IdAst> global_ids = new HashMap<String, IdAst>();
		private Map<String, Map<String, IdAst>> local_ids = new HashMap<String, Map<String, IdAst>>();
		private Map<String, Set<String>> function_parameters = new HashMap<String, Set<String>>();
		private Set<String> ids = new HashSet<String>();

		// current function definition for symbols
		private String currentFunctionName = null;

		// using set/clear rather than push/pop, it is impossible to define functions within functions
		void setFunctionName(String functionName) {
			assert this.currentFunctionName == null;
			this.currentFunctionName = functionName;
		}

		void clearFunctionName(String functionName) {
			assert this.currentFunctionName != null && this.currentFunctionName.length() > 0;
			assert this.currentFunctionName.equals(functionName);
			this.currentFunctionName = null;
		}

		AST addBEGIN() {
			if (begin_ast == null) {
				begin_ast = new BeginAst();
			}
			return begin_ast;
		}

		AST addEND() {
			if (end_ast == null) {
				end_ast = new EndAst();
			}
			return end_ast;
		}

		private IdAst getID(String id) {
			if (function_proxies.get(id) != null) {
				throw new ParserException("cannot use " + id + " as a variable; it is a function");
			}

			// put in the pool of ids to guard against using it as a function name
			ids.add(id);

			Map<String, IdAst> map;
			if (currentFunctionName == null) {
				map = global_ids;
			} else {
				Set<String> set = function_parameters.get(currentFunctionName);
				// we need "set != null && ..." here because if function
				// is defined with no args (i.e., function f() ...),
				// then set is null
				if (set != null && set.contains(id)) {
					map = local_ids.get(currentFunctionName);
					if (map == null) {
						local_ids.put(currentFunctionName, map = new HashMap<String, IdAst>());
					}
				} else {
					map = global_ids;
				}
			}
			assert map != null;
			IdAst id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new IdAst(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}
			return id_ast;
		}

		AST addID(String id) throws ParserException {
			IdAst ret_val = getID(id);
			/// ***
			/// We really don't know if the evaluation is for an array or for a scalar
			/// here, because we can use an array as a function parameter (passed by reference).
			/// ***
			// if (ret_val.is_array)
			// throw new ParserException("Cannot use "+ret_val+" as a scalar.");
			// ret_val.is_scalar = true;
			return ret_val;
		}

		int addFunctionParameter(String functionName, String id) {
			Set<String> set = function_parameters.get(functionName);
			if (set == null) {
				function_parameters.put(functionName, set = new HashSet<String>());
			}
			if (set.contains(id)) {
				throw new ParserException("multiply defined parameter " + id + " in function " + functionName);
			}
			int retval = set.size();
			set.add(id);
			Map<String, IdAst> map = local_ids.get(functionName);
			if (map == null) {
				local_ids.put(functionName, map = new HashMap<String, IdAst>());
			}
			assert map != null;
			IdAst id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new IdAst(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}

			return retval;
		}

		IdAst getFunctionParameterIDAST(String functionName, String fIdString) {
			return local_ids.get(functionName).get(fIdString);
		}

		AST addArrayID(String id) throws ParserException {
			IdAst ret_val = getID(id);
			if (ret_val.isScalar()) {
				throw new ParserException("Cannot use " + ret_val + " as an array.");
			}
			ret_val.setArray(true);
			return ret_val;
		}

		AST addFunctionDef(String functionName, AST param_list, AST block) {
			if (ids.contains(functionName)) {
				throw new ParserException("cannot use " + functionName + " as a function; it is a variable");
			}
			FunctionProxy function_proxy = function_proxies.get(functionName);
			if (function_proxy == null) {
				function_proxies.put(functionName, function_proxy = new FunctionProxy(functionName));
			}
			FunctionDefAst function_def = new FunctionDefAst(functionName, param_list, block);
			function_proxy.setFunctionDefinition(function_def);
			return function_def;
		}

		AST addFunctionCall(String id, AST param_list) {
			FunctionProxy function_proxy = function_proxies.get(id);
			if (function_proxy == null) {
				function_proxies.put(id, function_proxy = new FunctionProxy(id));
			}
			return new FunctionCallAst(function_proxy, param_list);
		}

		AST addArrayReference(String id, AST idx_ast) throws ParserException {
			return new ArrayReferenceAst(addArrayID(id), idx_ast);
		}

		// constants are no longer cached/hashed so that individual ASTs
		// can report accurate line numbers upon errors

		AST addINTEGER(String integer) {
			return new IntegerAst(Long.parseLong(integer));
		}

		AST addDOUBLE(String dbl) {
			return new DoubleAst(Double.valueOf(dbl));
		}

		AST addSTRING(String str) {
			return new StringAst(str);
		}

		AST addREGEXP(String regexp) {
			return new RegexpAst(regexp);
		}
	}

	public class ParserException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		ParserException(String msg) {
			super(
					msg
							+ " ("
							+ scriptSources.get(scriptSourcesCurrentIndex).getDescription()
							+ ":"
							+ reader.getLineNumber()
							+ ")");
		}
	}
}
