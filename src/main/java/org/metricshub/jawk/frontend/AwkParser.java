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
	private static int s_idx = 257;

	// Lexable tokens...

	private static final int EOF = s_idx++;
	private static final int NEWLINE = s_idx++;
	private static final int SEMICOLON = s_idx++;
	private static final int ID = s_idx++;
	private static final int FUNC_ID = s_idx++;
	private static final int INTEGER = s_idx++;
	private static final int DOUBLE = s_idx++;
	private static final int STRING = s_idx++;

	private static final int EQUALS = s_idx++;

	private static final int AND = s_idx++;
	private static final int OR = s_idx++;

	private static final int EQ = s_idx++;
	private static final int GT = s_idx++;
	private static final int GE = s_idx++;
	private static final int LT = s_idx++;
	private static final int LE = s_idx++;
	private static final int NE = s_idx++;
	private static final int NOT = s_idx++;
	private static final int PIPE = s_idx++;
	private static final int QUESTION_MARK = s_idx++;
	private static final int COLON = s_idx++;
	private static final int APPEND = s_idx++;

	private static final int PLUS = s_idx++;
	private static final int MINUS = s_idx++;
	private static final int MULT = s_idx++;
	private static final int DIVIDE = s_idx++;
	private static final int MOD = s_idx++;
	private static final int POW = s_idx++;
	private static final int COMMA = s_idx++;
	private static final int MATCHES = s_idx++;
	private static final int NOT_MATCHES = s_idx++;
	private static final int DOLLAR = s_idx++;

	private static final int INC = s_idx++;
	private static final int DEC = s_idx++;

	private static final int PLUS_EQ = s_idx++;
	private static final int MINUS_EQ = s_idx++;
	private static final int MULT_EQ = s_idx++;
	private static final int DIV_EQ = s_idx++;
	private static final int MOD_EQ = s_idx++;
	private static final int POW_EQ = s_idx++;

	private static final int OPEN_PAREN = s_idx++;
	private static final int CLOSE_PAREN = s_idx++;
	private static final int OPEN_BRACE = s_idx++;
	private static final int CLOSE_BRACE = s_idx++;
	private static final int OPEN_BRACKET = s_idx++;
	private static final int CLOSE_BRACKET = s_idx++;

	private static final int BUILTIN_FUNC_NAME = s_idx++;

	private static final int EXTENSION = s_idx++;

	private static final int KW_SLEEP = s_idx++;
	private static final int KW_DUMP = s_idx++;
	private static final int KW_INTEGER = s_idx++;
	private static final int KW_DOUBLE = s_idx++;
	private static final int KW_STRING = s_idx++;

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
		KEYWORDS.put("function", s_idx++);
		KEYWORDS.put("BEGIN", s_idx++);
		KEYWORDS.put("END", s_idx++);
		KEYWORDS.put("in", s_idx++);

		// statements
		KEYWORDS.put("if", s_idx++);
		KEYWORDS.put("else", s_idx++);
		KEYWORDS.put("while", s_idx++);
		KEYWORDS.put("for", s_idx++);
		KEYWORDS.put("do", s_idx++);
		KEYWORDS.put("return", s_idx++);
		KEYWORDS.put("exit", s_idx++);
		KEYWORDS.put("next", s_idx++);
		KEYWORDS.put("continue", s_idx++);
		KEYWORDS.put("delete", s_idx++);
		KEYWORDS.put("break", s_idx++);

		// special-form functions
		KEYWORDS.put("print", s_idx++);
		KEYWORDS.put("printf", s_idx++);
		KEYWORDS.put("getline", s_idx++);

		KEYWORDS.put("_sleep", KW_SLEEP);
		KEYWORDS.put("_dump", KW_DUMP);
		KEYWORDS.put("_INTEGER", KW_INTEGER);
		KEYWORDS.put("_DOUBLE", KW_DOUBLE);
		KEYWORDS.put("_STRING", KW_STRING);
	}

	/**
	 * Built-in function token values.
	 * Built-in function token values are distinguished
	 * from lexer token values.
	 */
	private static int f_idx = 257;
	private static final int F_EXEC = f_idx++;
	/**
	 * A mapping of built-in function names to their
	 * function token values.
	 * <p>
	 * <strong>Note:</strong> these are not lexer token
	 * values. Lexer token values are for keywords and
	 * operators.
	 */
	private static final Map<String, Integer> BUILTINFUNCNAMES = new HashMap<String, Integer>();

	static {
		BUILTINFUNCNAMES.put("atan2", f_idx++);
		BUILTINFUNCNAMES.put("close", f_idx++);
		BUILTINFUNCNAMES.put("cos", f_idx++);
		BUILTINFUNCNAMES.put("exp", f_idx++);
		BUILTINFUNCNAMES.put("index", f_idx++);
		BUILTINFUNCNAMES.put("int", f_idx++);
		BUILTINFUNCNAMES.put("length", f_idx++);
		BUILTINFUNCNAMES.put("log", f_idx++);
		BUILTINFUNCNAMES.put("match", f_idx++);
		BUILTINFUNCNAMES.put("rand", f_idx++);
		BUILTINFUNCNAMES.put("sin", f_idx++);
		BUILTINFUNCNAMES.put("split", f_idx++);
		BUILTINFUNCNAMES.put("sprintf", f_idx++);
		BUILTINFUNCNAMES.put("sqrt", f_idx++);
		BUILTINFUNCNAMES.put("srand", f_idx++);
		BUILTINFUNCNAMES.put("sub", f_idx++);
		BUILTINFUNCNAMES.put("gsub", f_idx++);
		BUILTINFUNCNAMES.put("substr", f_idx++);
		BUILTINFUNCNAMES.put("system", f_idx++);
		BUILTINFUNCNAMES.put("tolower", f_idx++);
		BUILTINFUNCNAMES.put("toupper", f_idx++);
		BUILTINFUNCNAMES.put("exec", F_EXEC);
	}

	private static final int sp_idx = 257;
	/**
	 * Contains a mapping of Jawk special variables to their
	 * variable token values.
	 * As of this writing, they correspond exactly to
	 * standard AWK variables, no more, no less.
	 * <p>
	 * Keys are the variable names themselves, and values are the
	 * variable token values.
	 */
	private static final Map<String, Integer> SPECIALVARNAMES = new HashMap<String, Integer>();

	static {
		SPECIALVARNAMES.put("NR", sp_idx);
		SPECIALVARNAMES.put("FNR", sp_idx);
		SPECIALVARNAMES.put("NF", sp_idx);
		SPECIALVARNAMES.put("FS", sp_idx);
		SPECIALVARNAMES.put("RS", sp_idx);
		SPECIALVARNAMES.put("OFS", sp_idx);
		SPECIALVARNAMES.put("ORS", sp_idx);
		SPECIALVARNAMES.put("RSTART", sp_idx);
		SPECIALVARNAMES.put("RLENGTH", sp_idx);
		SPECIALVARNAMES.put("FILENAME", sp_idx);
		SPECIALVARNAMES.put("SUBSEP", sp_idx);
		SPECIALVARNAMES.put("CONVFMT", sp_idx);
		SPECIALVARNAMES.put("OFMT", sp_idx);
		SPECIALVARNAMES.put("ENVIRON", sp_idx);
		SPECIALVARNAMES.put("ARGC", sp_idx);
		SPECIALVARNAMES.put("ARGV", sp_idx);
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

		while (token != EOF && c > 0 && c != '"' && c != '\n') {
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
		if (token == EOF || c == '\n' || c <= 0) {
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

		while (token != EOF && c > 0 && c != '/' && c != '\n') {
			if (c == '\\') {
				read();
				if (c != '/') {
					regexp.append('\\');
				}
			}
			regexp.append((char) c);
			read();
		}
		if (token == EOF || c == '\n' || c <= 0) {
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
			return token = EOF;
		}
		if (c == ',') {
			read();
			skipWhitespaces();
			return token = COMMA;
		}
		if (c == '(') {
			read();
			return token = OPEN_PAREN;
		}
		if (c == ')') {
			read();
			return token = CLOSE_PAREN;
		}
		if (c == '{') {
			read();
			skipWhitespaces();
			return token = OPEN_BRACE;
		}
		if (c == '}') {
			read();
			return token = CLOSE_BRACE;
		}
		if (c == '[') {
			read();
			return token = OPEN_BRACKET;
		}
		if (c == ']') {
			read();
			return token = CLOSE_BRACKET;
		}
		if (c == '$') {
			read();
			return token = DOLLAR;
		}
		if (c == '~') {
			read();
			return token = MATCHES;
		}
		if (c == '?') {
			read();
			skipWhitespaces();
			return token = QUESTION_MARK;
		}
		if (c == ':') {
			read();
			skipWhitespaces();
			return token = COLON;
		}
		if (c == '&') {
			read();
			if (c == '&') {
				read();
				skipWhitespaces();
				return token = AND;
			}
			throw new LexerException("use && for logical and");
		}
		if (c == '|') {
			read();
			if (c == '|') {
				read();
				skipWhitespaces();
				return token = OR;
			}
			return token = PIPE;
		}
		if (c == '=') {
			read();
			if (c == '=') {
				read();
				return token = EQ;
			}
			return token = EQUALS;
		}
		if (c == '+') {
			read();
			if (c == '=') {
				read();
				return token = PLUS_EQ;
			} else if (c == '+') {
				read();
				return token = INC;
			}
			return token = PLUS;
		}
		if (c == '-') {
			read();
			if (c == '=') {
				read();
				return token = MINUS_EQ;
			} else if (c == '-') {
				read();
				return token = DEC;
			}
			return token = MINUS;
		}
		if (c == '*') {
			read();
			if (c == '=') {
				read();
				return token = MULT_EQ;
			} else if (c == '*') {
				read();
				return token = POW;
			}
			return token = MULT;
		}
		if (c == '/') {
			read();
			if (c == '=') {
				read();
				return token = DIV_EQ;
			}
			return token = DIVIDE;
		}
		if (c == '%') {
			read();
			if (c == '=') {
				read();
				return token = MOD_EQ;
			}
			return token = MOD;
		}
		if (c == '^') {
			read();
			if (c == '=') {
				read();
				return token = POW_EQ;
			}
			return token = POW;
		}
		if (c == '>') {
			read();
			if (c == '=') {
				read();
				return token = GE;
			} else if (c == '>') {
				read();
				return token = APPEND;
			}
			return token = GT;
		}
		if (c == '<') {
			read();
			if (c == '=') {
				read();
				return token = LE;
			}
			return token = LT;
		}
		if (c == '!') {
			read();
			if (c == '=') {
				read();
				return token = NE;
			} else if (c == '~') {
				read();
				return token = NOT_MATCHES;
			}
			return token = NOT;
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
			return token = DOUBLE;
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
					return token = DOUBLE;
				} else if (Character.isDigit(c)) {
					// integer or double.
					read();
				} else {
					break;
				}
			}
			// integer, only
			return token = INTEGER;
		}

		if (Character.isJavaIdentifierStart(c)) {
			read();
			while (Character.isJavaIdentifierPart(c)) {
				read();
			}
			// check for certain keywords
			// extensions override built-in stuff
			if (extensions.get(text.toString()) != null) {
				return token = EXTENSION;
			}
			Integer kw_token = KEYWORDS.get(text.toString());
			if (kw_token != null) {
				int kw = kw_token.intValue();
				boolean treatAsIdentifier = (!additional_functions && (kw == KW_SLEEP || kw == KW_DUMP))
						||
						(!additional_type_functions && (kw == KW_INTEGER || kw == KW_DOUBLE || kw == KW_STRING));
				if (!treatAsIdentifier) {
					return token = kw;
				}
				// treat as identifier
			}
			Integer bf_idx = BUILTINFUNCNAMES.get(text.toString());
			if (bf_idx != null) {
				int idx = bf_idx.intValue();
				if (additional_functions || idx != F_EXEC) {
					return token = BUILTIN_FUNC_NAME;
				}
				// treat as identifier
			}
			if (c == '(') {
				return token = FUNC_ID;
			} else {
				return token = ID;
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
			return token = SEMICOLON;
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
			return token = NEWLINE;
		}

		if (c == '"') {
			// string
			read();
			readString();
			return token = STRING;
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
		} else if (token == EOF || token == CLOSE_BRACE) {
			return true; // do nothing
		} else if (token == SEMICOLON) {
			lexer();
			return true;
		} else {
			// no terminator consumed
			return false;
		}
	}

	private boolean opt_newline() throws IOException {
		if (token == NEWLINE) {
			lexer();
			return true;
		} else {
			return false;
		}
	}

	// RECURSIVE DECENT PARSER:
	// SCRIPT : \n [RULE_LIST] EOF
	AST SCRIPT() throws IOException {
		AST rl;
		if (token != EOF) {
			rl = RULE_LIST();
		} else {
			rl = null;
		}
		lexer(EOF);
		return rl;
	}

	// RULE_LIST : \n [ ( RULE | FUNCTION terminator ) opt_terminator RULE_LIST ]
	AST RULE_LIST() throws IOException {
		opt_newline();
		AST rule_or_function = null;
		if (token == KEYWORDS.get("function")) {
			rule_or_function = FUNCTION();
		} else if (token != EOF) {
			rule_or_function = RULE();
		} else {
			return null;
		}
		opt_terminator(); // newline or ; (maybe)
		return new RuleList_AST(rule_or_function, RULE_LIST());
	}

	AST FUNCTION() throws IOException {
		expectKeyword("function");
		String function_name;
		if (token == FUNC_ID || token == ID) {
			function_name = text.toString();
			lexer();
		} else {
			throw new ParserException("Expecting function name. Got " + toTokenString(token) + ": " + text);
		}
		symbol_table.setFunctionName(function_name);
		lexer(OPEN_PAREN);
		AST formal_param_list;
		if (token == CLOSE_PAREN) {
			formal_param_list = null;
		} else {
			formal_param_list = FORMALPARAMLIST(function_name);
		}
		lexer(CLOSE_PAREN);
		opt_newline();

		lexer(OPEN_BRACE);
		AST function_block = STATEMENT_LIST();
		lexer(CLOSE_BRACE);
		symbol_table.clearFunctionName(function_name);
		return symbol_table.addFunctionDef(function_name, formal_param_list, function_block);
	}

	AST FORMALPARAMLIST(String func_name) throws IOException {
		if (token == ID) {
			String id = text.toString();
			symbol_table.addFunctionParameter(func_name, id);
			lexer();
			if (token == COMMA) {
				lexer();
				opt_newline();
				AST rest = FORMALPARAMLIST(func_name);
				if (rest == null) {
					throw new ParserException("Cannot terminate a formal parameter list with a comma.");
				} else {
					return new FunctionDefParamList_AST(id, rest);
				}
			} else {
				return new FunctionDefParamList_AST(id, null);
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
		} else if (token != OPEN_BRACE && token != SEMICOLON && token != NEWLINE && token != EOF) {
			// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
			opt_expr = ASSIGNMENT_EXPRESSION(true, true, false);
			// for ranges, like conditionStart, conditionEnd
			if (token == COMMA) {
				lexer();
				opt_newline();
				// true = allow comparators, allow IN keyword, do NOT allow multidim indices expressions
				opt_expr = new ConditionPair_AST(opt_expr, ASSIGNMENT_EXPRESSION(true, true, false));
			}
		} else {
			opt_expr = null;
		}
		if (token == OPEN_BRACE) {
			lexer();
			opt_stmts = STATEMENT_LIST();
			lexer(CLOSE_BRACE);
		} else {
			opt_stmts = null;
		}
		return new Rule_AST(opt_expr, opt_stmts);
	}

	// STATEMENT_LIST : [ STATEMENT_BLOCK|STATEMENT STATEMENT_LIST ]
	private AST STATEMENT_LIST() throws IOException {
		// statement lists can only live within curly brackets (braces)
		opt_newline();
		if (token == CLOSE_BRACE || token == EOF) {
			return null;
		}
		AST stmt;
		if (token == OPEN_BRACE) {
			lexer();
			stmt = STATEMENT_LIST();
			lexer(CLOSE_BRACE);
		} else {
			if (token == SEMICOLON) {
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
			return new STATEMENTLIST_AST(stmt, rest);
		}
	}

	// EXPRESSION_LIST : ASSIGNMENT_EXPRESSION [, EXPRESSION_LIST]
	AST EXPRESSION_LIST(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		AST expr = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false); // do NOT allow multidim indices
																																									// expressions
		if (token == COMMA) {
			lexer();
			opt_newline();
			return new FunctionCallParamList_AST(expr, EXPRESSION_LIST(not_in_print_root, allow_in_keyword));
		} else {
			return new FunctionCallParamList_AST(expr, null);
		}
	}

	// ASSIGNMENT_EXPRESSION = COMMA_EXPRESSION [ (=,+=,-=,*=) ASSIGNMENT_EXPRESSION ]
	AST ASSIGNMENT_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST comma_expression = COMMA_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST assignment_expression = null;
		if (token == EQUALS
				|| token == PLUS_EQ
				|| token == MINUS_EQ
				|| token == MULT_EQ
				|| token == DIV_EQ
				|| token == MOD_EQ
				|| token == POW_EQ) {
			op = token;
			txt = text.toString();
			lexer();
			assignment_expression = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new AssignmentExpression_AST(comma_expression, op, txt, assignment_expression);
		}
		return comma_expression;
	}

	// COMMA_EXPRESSION = TERNARY_EXPRESSION [, COMMA_EXPRESSION] !!!ONLY IF!!! allow_multidim_indices is true
	// allow_multidim_indices is set to true when we need (1,2,3,4) expressions to collapse into an array index expression
	// (converts 1,2,3,4 to 1 SUBSEP 2 SUBSEP 3 SUBSEP 4) after an open parenthesis (grouping) expression starter
	AST COMMA_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST concat_expression = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (allow_multidim_indices && token == COMMA) {
			// consume the comma
			lexer();
			opt_newline();
			AST rest = COMMA_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			if (rest instanceof ArrayIndex_AST) {
				return new ArrayIndex_AST(concat_expression, rest);
			} else {
				return new ArrayIndex_AST(concat_expression, new ArrayIndex_AST(rest, null));
			}
		} else {
			return concat_expression;
		}
	}

	// TERNARY_EXPRESSION = LOGICALOREXPRESSION [ ? TERNARY_EXPRESSION : TERNARY_EXPRESSION ]
	AST TERNARY_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST le1 = LOGICALOREXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == QUESTION_MARK) {
			lexer();
			AST true_block = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			lexer(COLON);
			AST false_block = TERNARY_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new TernaryExpression_AST(le1, true_block, false_block);
		} else {
			return le1;
		}
	}

	// LOGICALOREXPRESSION = LOGICALANDEXPRESSION [ || LOGICALOREXPRESSION ]
	AST LOGICALOREXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST le2 = LOGICALANDEXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le1 = null;
		if (token == OR) {
			op = token;
			txt = text.toString();
			lexer();
			le1 = LOGICALOREXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpression_AST(le2, op, txt, le1);
		}
		return le2;
	}

	// LOGICALANDEXPRESSION = IN_EXPRESSION [ && LOGICALANDEXPRESSION ]
	AST LOGICALANDEXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST comparison_expression = IN_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		int op = 0;
		String txt = null;
		AST le2 = null;
		if (token == AND) {
			op = token;
			txt = text.toString();
			lexer();
			le2 = LOGICALANDEXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new LogicalExpression_AST(comparison_expression, op, txt, le2);
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
			return new InExpression_AST(
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
		if (token == MATCHES || token == NOT_MATCHES) {
			op = token;
			txt = text.toString();
			lexer();
			comparison_expression = MATCHING_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new ComparisonExpression_AST(expression, op, txt, comparison_expression);
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
		if (token == EQ
				|| token == GE
				|| token == LT
				|| token == LE
				|| token == NE
				|| (token == GT && not_in_print_root)) {
			op = token;
			txt = text.toString();
			lexer();
			comparison_expression = COMPARISON_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
			return new ComparisonExpression_AST(expression, op, txt, comparison_expression);
		} else if (not_in_print_root && token == PIPE) {
			lexer();
			return GETLINE_EXPRESSION(expression, not_in_print_root, allow_in_keyword);
		}

		return expression;
	}

	// CONCAT_EXPRESSION = EXPRESSION [ CONCAT_EXPRESSION ]
	AST CONCAT_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST te = EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == INTEGER
				||
				token == DOUBLE
				||
				token == OPEN_PAREN
				||
				token == FUNC_ID
				||
				token == INC
				||
				token == DEC
				||
				token == ID
				||
				token == STRING
				||
				token == DOLLAR
				||
				token == BUILTIN_FUNC_NAME
				||
				token == EXTENSION) {
			// allow concatination here only when certain tokens follow
			return new ConcatExpression_AST(
					te,
					CONCAT_EXPRESSION(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else
			if (additional_type_functions
					&& (token == KEYWORDS.get("_INTEGER")
							|| token == KEYWORDS.get("_DOUBLE")
							|| token == KEYWORDS.get("_STRING"))) {
								// allow concatenation here only when certain tokens follow
								return new ConcatExpression_AST(
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
		while (token == PLUS || token == MINUS) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST nextTerm = TERM(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			// Build the tree in left-associative manner
			term = new BinaryExpression_AST(term, op, txt, nextTerm);
		}
		return term;
	}

	// TERM : UNARY_FACTOR [ (*|/|%) TERM ]
	AST TERM(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices) throws IOException {
		AST unaryFactor = UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		while (token == MULT || token == DIVIDE || token == MOD) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST nextUnaryFactor = UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			// Build the tree in left-associative manner
			unaryFactor = new BinaryExpression_AST(unaryFactor, op, txt, nextUnaryFactor);
		}
		return unaryFactor;
	}

	// UNARY_FACTOR : [ ! | - | + ] POWER_FACTOR
	AST UNARY_FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		if (token == NOT) {
			lexer();
			return new NotExpression_AST(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else if (token == MINUS) {
			lexer();
			return new NegativeExpression_AST(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else if (token == PLUS) {
			lexer();
			return new UnaryPlusExpression_AST(POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
		} else {
			return POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		}
	}

	// POWER_FACTOR : FACTORFORINCDEC [ ^ POWER_FACTOR ]
	AST POWER_FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		AST incdec_ast = FACTORFORINCDEC(not_in_print_root, allow_in_keyword, allow_multidim_indices);
		if (token == POW) {
			int op = token;
			String txt = text.toString();
			lexer();
			AST term = POWER_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices);

			return new BinaryExpression_AST(incdec_ast, op, txt, term);
		}
		return incdec_ast;
	}

	// according to the spec, pre/post inc can occur
	// only on lvalues, which are NAMES (IDs), array,
	// or field references
	private boolean isLvalue(AST ast) {
		return (ast instanceof ID_AST) || (ast instanceof ArrayReference_AST) || (ast instanceof DollarExpression_AST);
	}

	AST FACTORFORINCDEC(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean pre_inc = false;
		boolean pre_dec = false;
		boolean post_inc = false;
		boolean post_dec = false;
		if (token == INC) {
			pre_inc = true;
			lexer();
		} else if (token == DEC) {
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
			if (token == INC) {
				post_inc = true;
				lexer();
			} else if (token == DEC) {
				post_dec = true;
				lexer();
			}
		}

		if ((pre_inc || pre_dec) && (post_inc || post_dec)) {
			throw new ParserException("Cannot do pre inc/dec AND post inc/dec.");
		}

		if (pre_inc) {
			return new PreInc_AST(factor_ast);
		} else if (pre_dec) {
			return new PreDec_AST(factor_ast);
		} else if (post_inc) {
			return new PostInc_AST(factor_ast);
		} else if (post_dec) {
			return new PostDec_AST(factor_ast);
		} else {
			return factor_ast;
		}
	}

	// FACTOR : '(' ASSIGNMENT_EXPRESSION ')' | INTEGER | DOUBLE | STRING | GETLINE [ID-or-array-or-$val] | /[=].../
	// | [++|--] SYMBOL [++|--]
	// AST FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_post_incdec_operators)
	AST FACTOR(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices) throws IOException {
		if (token == OPEN_PAREN) {
			lexer();
			// true = allow multi-dimensional array indices (i.e., commas for 1,2,3,4)
			AST assignment_expression = ASSIGNMENT_EXPRESSION(true, allow_in_keyword, true);
			if (allow_multidim_indices && (assignment_expression instanceof ArrayIndex_AST)) {
				throw new ParserException("Cannot nest multi-dimensional array index expressions.");
			}
			lexer(CLOSE_PAREN);
			return assignment_expression;
		} else if (token == INTEGER) {
			AST integer = symbol_table.addINTEGER(text.toString());
			lexer();
			return integer;
		} else if (token == DOUBLE) {
			AST dbl = symbol_table.addDOUBLE(text.toString());
			lexer();
			return dbl;
		} else if (token == STRING) {
			AST str = symbol_table.addSTRING(string.toString());
			lexer();
			return str;
		} else if (token == KEYWORDS.get("getline")) {
			return GETLINE_EXPRESSION(null, not_in_print_root, allow_in_keyword);
		} else if (token == DIVIDE || token == DIV_EQ) {
			readRegexp();
			if (token == DIV_EQ) {
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
			if (token == DOLLAR) {
				lexer();
				if (token == INC || token == DEC) {
					return new DollarExpression_AST(
							FACTORFORINCDEC(not_in_print_root, allow_in_keyword, allow_multidim_indices));
				}
				if (token == NOT || token == MINUS || token == PLUS) {
					return new DollarExpression_AST(UNARY_FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
				}
				return new DollarExpression_AST(FACTOR(not_in_print_root, allow_in_keyword, allow_multidim_indices));
			}
			return SYMBOL(not_in_print_root, allow_in_keyword);
		}
	}

	AST INTEGER_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_INTEGER");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(OPEN_PAREN);
			}
			AST int_expr_ast;
			if (token == CLOSE_PAREN) {
				throw new ParserException("expression expected");
			} else {
				int_expr_ast = new IntegerExpression_AST(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(CLOSE_PAREN);
			}
			return int_expr_ast;
		}
	}

	AST DOUBLE_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_DOUBLE");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(OPEN_PAREN);
			}
			AST double_expr_ast;
			if (token == CLOSE_PAREN) {
				throw new ParserException("expression expected");
			} else {
				double_expr_ast = new DoubleExpression_AST(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(CLOSE_PAREN);
			}
			return double_expr_ast;
		}
	}

	AST STRING_EXPRESSION(boolean not_in_print_root, boolean allow_in_keyword, boolean allow_multidim_indices)
			throws IOException {
		boolean parens = c == '(';
		expectKeyword("_STRING");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			throw new ParserException("expression expected");
		} else {
			// do NOT allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer(OPEN_PAREN);
			}
			AST string_expr_ast;
			if (token == CLOSE_PAREN) {
				throw new ParserException("expression expected");
			} else {
				string_expr_ast = new StringExpression_AST(
						ASSIGNMENT_EXPRESSION(not_in_print_root || parens, allow_in_keyword, allow_multidim_indices));
			}
			if (parens) {
				lexer(CLOSE_PAREN);
			}
			return string_expr_ast;
		}
	}

	// SYMBOL : ID [ '(' params ')' | '[' ASSIGNMENT_EXPRESSION ']' ]
	AST SYMBOL(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		if (token != ID && token != FUNC_ID && token != BUILTIN_FUNC_NAME && token != EXTENSION) {
			throw new ParserException("Expecting an ID. Got " + toTokenString(token) + ": " + text);
		}
		int id_token = token;
		String id = text.toString();
		boolean parens = c == '(';
		lexer();

		if (id_token == EXTENSION) {
			String extension_keyword = id;
			// JawkExtension extension = extensions.get(extension_keyword);
			AST params;

			/*
			 * if (extension.requiresParen()) {
			 * lexer(OPEN_PAREN);
			 * if (token == CLOSE_PAREN)
			 * params = null;
			 * else
			 * params = EXPRESSION_LIST(not_in_print_root, allow_in_keyword);
			 * lexer(CLOSE_PAREN);
			 * } else {
			 * boolean parens = c == '(';
			 * //expectKeyword("delete");
			 * if (parens) {
			 * assert token == OPEN_PAREN;
			 * lexer();
			 * }
			 * //AST symbol_ast = SYMBOL(true,true); // allow comparators
			 * params = EXPRESSION_LIST(not_in_print_root, allow_in_keyword);
			 * if (parens)
			 * lexer(CLOSE_PAREN);
			 * }
			 */

			// if (extension.requiresParens() || parens)
			if (parens) {
				lexer();
				if (token == CLOSE_PAREN) {
					params = null;
				} else { // ?//params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
					params = EXPRESSION_LIST(true, allow_in_keyword); // NO comparators allowed, allow in expression
				}
				lexer(CLOSE_PAREN);
			} else {
				/*
				 * if (token == NEWLINE || token == SEMICOLON || token == CLOSE_BRACE || token == CLOSE_PAREN
				 * || (token == GT || token == APPEND || token == PIPE) )
				 * params = null;
				 * else
				 * params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
				 */
				params = null;
			}

			return new Extension_AST(extension_keyword, params);
		} else if (id_token == FUNC_ID || id_token == BUILTIN_FUNC_NAME) {
			AST params;
			// length can take on the special form of no parens
			if (id.equals("length")) {
				if (token == OPEN_PAREN) {
					lexer();
					if (token == CLOSE_PAREN) {
						params = null;
					} else {
						params = EXPRESSION_LIST(true, allow_in_keyword);
					}
					lexer(CLOSE_PAREN);
				} else {
					params = null;
				}
			} else {
				lexer(OPEN_PAREN);
				if (token == CLOSE_PAREN) {
					params = null;
				} else {
					params = EXPRESSION_LIST(true, allow_in_keyword);
				}
				lexer(CLOSE_PAREN);
			}
			if (id_token == BUILTIN_FUNC_NAME) {
				return new BuiltinFunctionCall_AST(id, params);
			} else {
				return symbol_table.addFunctionCall(id, params);
			}
		}
		if (token == OPEN_BRACKET) {
			lexer();
			AST idx_ast = ARRAY_INDEX(true, allow_in_keyword);
			lexer(CLOSE_BRACKET);
			if (token == OPEN_BRACKET) {
				throw new ParserException("Use [a,b,c,...] instead of [a][b][c]... for multi-dimensional arrays.");
			}
			return symbol_table.addArrayReference(id, idx_ast);
		}
		return symbol_table.addID(id);
	}

	// ARRAY_INDEX : ASSIGNMENT_EXPRESSION [, ARRAY_INDEX]
	AST ARRAY_INDEX(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		AST expr_ast = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false);
		if (token == COMMA) {
			opt_newline();
			lexer();
			return new ArrayIndex_AST(expr_ast, ARRAY_INDEX(not_in_print_root, allow_in_keyword));
		} else {
			return new ArrayIndex_AST(expr_ast, null);
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
		if (token == OPEN_BRACE) {
			lexer();
			AST lst = STATEMENT_LIST();
			lexer(CLOSE_BRACE);
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
		// return new ExpressionStatement_AST(ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false));

		AST expr_ast = ASSIGNMENT_EXPRESSION(true, allow_in_keyword, false);
		if (!allow_non_statement_asts && expr_ast instanceof NonStatement_AST) {
			throw new ParserException("Not a valid statement.");
		}
		return new ExpressionStatement_AST(expr_ast);
	}

	AST IF_STATEMENT() throws IOException {
		expectKeyword("if");
		lexer(OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow in keyword, do NOT allow multidim
																													// indices expressions
		lexer(CLOSE_PAREN);

		//// Was:
		//// AST b1 = BLOCKORSTMT();
		//// But it didn't handle
		//// if ; else ...
		//// properly
		opt_newline();
		AST b1;
		if (token == SEMICOLON) {
			lexer();
			// consume the newline after the semicolon
			opt_newline();
			b1 = null;
		} else {
			b1 = BLOCKORSTMT();
		}

		// The OPT_NEWLINE() above causes issues with the following form:
		// if (...) {
		// }
		// else { ... }
		// The \n before the else disassociates subsequent statements
		// if an "else" does not immediately follow.
		// To accommodate, the if_statement will continue to manage
		// statements, causing the original OPTSTATEMENTLIST to relinquish
		// processing statements to this OPTSTATEMENTLIST.

		opt_newline();
		if (token == KEYWORDS.get("else")) {
			lexer();
			opt_newline();
			AST b2 = BLOCKORSTMT();
			return new IfStatement_AST(expr, b1, b2);
		} else {
			AST if_ast = new IfStatement_AST(expr, b1, null);
			return if_ast;
		}
	}

	AST BREAK_STATEMENT() throws IOException {
		expectKeyword("break");
		return new BreakStatement_AST();
	}

	AST BLOCKORSTMT() throws IOException {
		// default case, does NOT consume (require) a terminator
		return BLOCKORSTMT(false);
	}

	AST BLOCKORSTMT(boolean require_terminator) throws IOException {
		opt_newline();
		AST block;
		// HIJACK BRACES HERE SINCE WE MAY NOT HAVE A TERMINATOR AFTER THE CLOSING BRACE
		if (token == OPEN_BRACE) {
			lexer();
			block = STATEMENT_LIST();
			lexer(CLOSE_BRACE);
			return block;
		} else if (token == SEMICOLON) {
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
		lexer(OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do NOT allow multidim
																													// indices expressions
		lexer(CLOSE_PAREN);
		AST block = BLOCKORSTMT();
		return new WhileStatement_AST(expr, block);
	}

	AST FOR_STATEMENT() throws IOException {
		expectKeyword("for");
		AST expr1 = null;
		AST expr2 = null;
		AST expr3 = null;
		lexer(OPEN_PAREN);
		expr1 = OPTSIMPLESTATEMENT(false); // false = "no in keyword allowed"

		// branch here if we expect a for(... in ...) statement
		if (token == KEYWORDS.get("in")) {
			if (expr1.ast1 == null || expr1.ast2 != null) {
				throw new ParserException("Invalid expression prior to 'in' statement. Got : " + expr1);
			}
			expr1 = expr1.ast1;
			// analyze expr1 to make sure it's a singleton ID_AST
			if (!(expr1 instanceof ID_AST)) {
				throw new ParserException("Expecting an ID for 'in' statement. Got : " + expr1);
			}
			// in
			lexer();
			// id
			if (token != ID) {
				throw new ParserException(
						"Expecting an ARRAY ID for 'in' statement. Got " + toTokenString(token) + ": " + text);
			}
			String arr_id = text.toString();

			// not an indexed array reference!
			AST array_id_ast = symbol_table.addArrayID(arr_id);

			lexer();
			// close paren ...
			lexer(CLOSE_PAREN);
			AST block = BLOCKORSTMT();
			return new ForInStatement_AST(expr1, array_id_ast, block);
		}

		if (token == SEMICOLON) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got " + toTokenString(token) + ": " + text);
		}
		if (token != SEMICOLON) {
			expr2 = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do NOT allow multidim
																												// indices expressions
		}
		if (token == SEMICOLON) {
			lexer();
		} else {
			throw new ParserException("Expecting ;. Got " + toTokenString(token) + ": " + text);
		}
		if (token != CLOSE_PAREN) {
			expr3 = OPTSIMPLESTATEMENT(true); // true = "allow the in keyword"
		}
		lexer(CLOSE_PAREN);
		AST block = BLOCKORSTMT();
		return new ForStatement_AST(expr1, expr2, expr3, block);
	}

	AST OPTSIMPLESTATEMENT(boolean allow_in_keyword) throws IOException {
		if (token == SEMICOLON) {
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
			assert token == OPEN_PAREN;
			lexer();
		}
		AST symbol_ast = SYMBOL(true, true); // allow comparators
		if (parens) {
			lexer(CLOSE_PAREN);
		}

		return new DeleteStatement_AST(symbol_ast);
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
		AST func_params;
		int output_token;
		AST output_expr;
		if (parens) {
			lexer();
			if (token == CLOSE_PAREN) {
				func_params = null;
			} else {
				func_params = EXPRESSION_LIST(true, true); // comparators are allowed, and also in expression
			}
			lexer(CLOSE_PAREN);
		} else {
			if (token == NEWLINE
					|| token == SEMICOLON
					|| token == CLOSE_BRACE
					|| token == CLOSE_PAREN
					|| token == GT
					|| token == APPEND
					|| token == PIPE) {
				func_params = null;
			} else {
				func_params = EXPRESSION_LIST(false, true); // NO comparators allowed, allow in expression
			}
		}
		if (token == GT || token == APPEND || token == PIPE) {
			output_token = token;
			lexer();
			output_expr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do NOT
																															// allow multidim indices expressions
		} else {
			output_token = -1;
			output_expr = null;
		}

		return new ParsedPrintStatement(func_params, output_token, output_expr);
	}

	AST PRINT_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("print");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new Print_AST(
				parsedPrintStatement.getFuncParams(),
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST PRINTF_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("printf");
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		return new Printf_AST(
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
		if (token == LT) {
			lexer();
			AST assignment_expr = ASSIGNMENT_EXPRESSION(not_in_print_root, allow_in_keyword, false); // do NOT allow multidim
																																																// indices expressions
			return pipe_expr == null ?
					new Getline_AST(null, lvalue, assignment_expr) : new Getline_AST(pipe_expr, lvalue, assignment_expr);
		} else {
			return pipe_expr == null ? new Getline_AST(null, lvalue, null) : new Getline_AST(pipe_expr, lvalue, null);
		}
	}

	AST LVALUE(boolean not_in_print_root, boolean allow_in_keyword) throws IOException {
		// false = do NOT allow multi dimension indices expressions
		if (token == DOLLAR) {
			return FACTOR(not_in_print_root, allow_in_keyword, false);
		}
		if (token == ID) {
			return FACTOR(not_in_print_root, allow_in_keyword, false);
		}
		return null;
	}

	AST DO_STATEMENT() throws IOException {
		expectKeyword("do");
		opt_newline();
		AST block = BLOCKORSTMT();
		if (token == SEMICOLON) {
			lexer();
		}
		opt_newline();
		expectKeyword("while");
		lexer(OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do NOT allow
																													// multidim indices expressions
		lexer(CLOSE_PAREN);
		return new DoStatement_AST(block, expr);
	}

	AST RETURN_STATEMENT() throws IOException {
		expectKeyword("return");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			return new ReturnStatement_AST(null);
		} else {
			return new ReturnStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																								// keyword, do NOT allow multidim
																																								// indices expressions
		}
	}

	AST EXIT_STATEMENT() throws IOException {
		expectKeyword("exit");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			return new ExitStatement_AST(null);
		} else {
			return new ExitStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																							// keyword, do NOT allow multidim indices
																																							// expressions
		}
	}

	AST SLEEP_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("_sleep");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			return new SleepStatement_AST(null);
		} else {
			// allow for a blank param list: "()" using the parens boolean below
			// otherwise, the parser will complain because assignment_expression cannot be ()
			if (parens) {
				lexer();
			}
			AST sleep_ast;
			if (token == CLOSE_PAREN) {
				sleep_ast = new SleepStatement_AST(null);
			} else {
				sleep_ast = new SleepStatement_AST(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow
																																											// IN keyword, do NOT allow
																																											// multidim indices expressions
			}
			if (parens) {
				lexer(CLOSE_PAREN);
			}
			return sleep_ast;
		}
	}

	AST DUMP_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("_dump");
		if (token == SEMICOLON || token == NEWLINE || token == CLOSE_BRACE) {
			return new DumpStatement_AST(null);
		} else {
			if (parens) {
				lexer();
			}
			AST dump_ast;
			if (token == CLOSE_PAREN) {
				dump_ast = new DumpStatement_AST(null);
			} else {
				dump_ast = new DumpStatement_AST(EXPRESSION_LIST(true, true)); // true = allow comparators, allow IN keyword
			}
			if (parens) {
				lexer(CLOSE_PAREN);
			}
			return dump_ast;
		}
	}

	AST NEXT_STATEMENT() throws IOException {
		expectKeyword("next");
		return new NextStatement_AST();
	}

	AST CONTINUE_STATEMENT() throws IOException {
		expectKeyword("continue");
		return new ContinueStatement_AST();
	}

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

	private abstract class ScalarExpression_AST extends AST {

		protected ScalarExpression_AST() {
			super();
		}

		protected ScalarExpression_AST(AST a1) {
			super(a1);
		}

		protected ScalarExpression_AST(AST a1, AST a2) {
			super(a1, a2);
		}

		protected ScalarExpression_AST(AST a1, AST a2, AST a3) {
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

		if (!containsASTType(ast.ast1, Extension_AST.class)) {
			return false;
		}

		if (containsASTType(ast.ast1, new Class[] { FunctionCall_AST.class, DollarExpression_AST.class })) {
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

	private final class RuleList_AST extends AST {

		private RuleList_AST(AST rule, AST rest) {
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
			ID_AST nr_ast = symbol_table.getID("NR");
			ID_AST fnr_ast = symbol_table.getID("FNR");
			ID_AST nf_ast = symbol_table.getID("NF");
			ID_AST fs_ast = symbol_table.getID("FS");
			ID_AST rs_ast = symbol_table.getID("RS");
			ID_AST ofs_ast = symbol_table.getID("OFS");
			ID_AST ors_ast = symbol_table.getID("ORS");
			ID_AST rstart_ast = symbol_table.getID("RSTART");
			ID_AST rlength_ast = symbol_table.getID("RLENGTH");
			ID_AST filename_ast = symbol_table.getID("FILENAME");
			ID_AST subsep_ast = symbol_table.getID("SUBSEP");
			ID_AST convfmt_ast = symbol_table.getID("CONVFMT");
			ID_AST ofmt_ast = symbol_table.getID("OFMT");
			ID_AST environ_ast = symbol_table.getID("ENVIRON");
			ID_AST argc_ast = symbol_table.getID("ARGC");
			ID_AST argv_ast = symbol_table.getID("ARGV");

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
	private final class Rule_AST extends AST implements Nextable {

		private Rule_AST(AST opt_expression, AST opt_rule) {
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

	private final class IfStatement_AST extends AST {

		private IfStatement_AST(AST expr, AST b1, AST b2) {
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

	private final class TernaryExpression_AST extends ScalarExpression_AST {

		private TernaryExpression_AST(AST a1, AST a2, AST a3) {
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

	private final class WhileStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private WhileStatement_AST(AST expr, AST block) {
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

	private final class DoStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private DoStatement_AST(AST block, AST expr) {
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

	private final class ForStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForStatement_AST(AST expr1, AST expr2, AST expr3, AST block) {
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

	private final class ForInStatement_AST extends AST implements Breakable, Continueable {

		private Address break_address;
		private Address continue_address;

		private ForInStatement_AST(AST key_id_ast, AST array_id_ast, AST block) {
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

			ID_AST array_id_ast = (ID_AST) ast2;
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
			tuples.assign(((ID_AST) ast1).offset, ((ID_AST) ast1).is_global);
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
	private final class EmptyStatement_AST extends AST {

		private EmptyStatement_AST() {
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
	private final class ExpressionStatement_AST extends AST {

		private ExpressionStatement_AST(AST expr) {
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

	private final class AssignmentExpression_AST extends ScalarExpression_AST {

		/** operand / operator */
		private int op;
		private String text;

		private AssignmentExpression_AST(AST lhs, int op, String text, AST rhs) {
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
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				if (id_ast.isArray()) {
					throw new SemanticException("Cannot use " + id_ast + " as a scalar. It is an array.");
				}
				id_ast.setScalar(true);
				if (op == EQUALS) {
					// Expected side effect:
					// Upon assignment, if the var is RS, reapply RS to input streams.
					tuples.assign(id_ast.offset, id_ast.is_global);
				} else if (op == PLUS_EQ) {
					tuples.plusEq(id_ast.offset, id_ast.is_global);
				} else if (op == MINUS_EQ) {
					tuples.minusEq(id_ast.offset, id_ast.is_global);
				} else if (op == MULT_EQ) {
					tuples.multEq(id_ast.offset, id_ast.is_global);
				} else if (op == DIV_EQ) {
					tuples.divEq(id_ast.offset, id_ast.is_global);
				} else if (op == MOD_EQ) {
					tuples.modEq(id_ast.offset, id_ast.is_global);
				} else if (op == POW_EQ) {
					tuples.powEq(id_ast.offset, id_ast.is_global);
				} else {
					throw new Error("Unhandled op: " + op + " / " + text);
				}
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr = (ArrayReference_AST) ast1;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				ID_AST id_ast = (ID_AST) arr.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("Cannot use " + id_ast + " as an array. It is a scalar.");
				}
				id_ast.setArray(true);
				if (op == EQUALS) {
					tuples.assignArray(id_ast.offset, id_ast.is_global);
				} else if (op == PLUS_EQ) {
					tuples.plusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == MINUS_EQ) {
					tuples.minusEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == MULT_EQ) {
					tuples.multEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == DIV_EQ) {
					tuples.divEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == MOD_EQ) {
					tuples.modEqArray(id_ast.offset, id_ast.is_global);
				} else if (op == POW_EQ) {
					tuples.powEqArray(id_ast.offset, id_ast.is_global);
				} else {
					throw new NotImplementedError("Unhandled op: " + op + " / " + text + " for arrays.");
				}
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
				assert dollar_expr.ast1 != null;
				int ast1_result = dollar_expr.ast1.populateTuples(tuples);
				assert ast1_result == 1;
				// stack contains eval of dollar arg

				if (op == EQUALS) {
					tuples.assignAsInputField();
				} else if (op == PLUS_EQ) {
					tuples.plusEqInputField();
				} else if (op == MINUS_EQ) {
					tuples.minusEqInputField();
				} else if (op == MULT_EQ) {
					tuples.multEqInputField();
				} else if (op == DIV_EQ) {
					tuples.divEqInputField();
				} else if (op == MOD_EQ) {
					tuples.modEqInputField();
				} else if (op == POW_EQ) {
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

	private final class InExpression_AST extends ScalarExpression_AST {

		private InExpression_AST(AST arg, AST arr) {
			super(arg, arr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			assert ast2 != null;
			if (!(ast2 instanceof ID_AST)) {
				throw new SemanticException("Expecting an array for rhs of IN. Got an expression.");
			}
			ID_AST arr_ast = (ID_AST) ast2;
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

	private final class ComparisonExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private ComparisonExpression_AST(AST lhs, int op, String text, AST rhs) {
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

			if (op == EQ) {
				tuples.cmpEq();
			} else if (op == NE) {
				tuples.cmpEq();
				tuples.not();
			} else if (op == LT) {
				tuples.cmpLt();
			} else if (op == GT) {
				tuples.cmpGt();
			} else if (op == LE) {
				tuples.cmpGt();
				tuples.not();
			} else if (op == GE) {
				tuples.cmpLt();
				tuples.not();
			} else if (op == MATCHES) {
				tuples.matches();
			} else if (op == NOT_MATCHES) {
				tuples.matches();
				tuples.not();
			} else {
				throw new Error("Unhandled op: " + op + " / " + text);
			}

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class LogicalExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private LogicalExpression_AST(AST lhs, int op, String text, AST rhs) {
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
			if (op == OR) {
				// short_circuit when op is OR and 1st arg is true
				tuples.ifTrue(end);
			} else if (op == AND) {
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

	private final class BinaryExpression_AST extends ScalarExpression_AST {

		/**
		 * operand / operator
		 */
		private int op;
		private String text;

		private BinaryExpression_AST(AST lhs, int op, String text, AST rhs) {
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
			if (op == PLUS) {
				tuples.add();
			} else if (op == MINUS) {
				tuples.subtract();
			} else if (op == MULT) {
				tuples.multiply();
			} else if (op == DIVIDE) {
				tuples.divide();
			} else if (op == MOD) {
				tuples.mod();
			} else if (op == POW) {
				tuples.pow();
			} else {
				throw new Error("Unhandled op: " + op + " / " + this);
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ConcatExpression_AST extends ScalarExpression_AST {

		private ConcatExpression_AST(AST lhs, AST rhs) {
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

	private final class NegativeExpression_AST extends ScalarExpression_AST {

		private NegativeExpression_AST(AST expr) {
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

	private final class UnaryPlusExpression_AST extends ScalarExpression_AST {

		private UnaryPlusExpression_AST(AST expr) {
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

	private final class NotExpression_AST extends ScalarExpression_AST {

		private NotExpression_AST(AST expr) {
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

	private final class DollarExpression_AST extends ScalarExpression_AST {

		private DollarExpression_AST(AST expr) {
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

	private final class ArrayIndex_AST extends ScalarExpression_AST {

		private ArrayIndex_AST(AST expr_ast, AST next) {
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
	private final class STATEMENTLIST_AST extends AST {

		private STATEMENTLIST_AST(AST statement_ast, AST rest) {
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
	private final class FunctionDef_AST extends AST implements Returnable {

		private String id;
		private Address function_address;
		private Address return_address;

		// to satisfy the Returnable interface

		@Override
		public Address returnAddress() {
			assert return_address != null;
			return return_address;
		}

		private FunctionDef_AST(String id, AST params, AST func_body) {
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
			FunctionDefParamList_AST f_ptr = (FunctionDefParamList_AST) ast1;
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
				if (aparam instanceof ID_AST) {
					ID_AST aparam_id_ast = (ID_AST) aparam;
					if (fparam.isScalar()) {
						aparam_id_ast.setScalar(true);
					}
					if (fparam.isArray()) {
						aparam_id_ast.setArray(true);
					}
				}
				// next
				a_ptr = a_ptr.ast2;
				f_ptr = (FunctionDefParamList_AST) f_ptr.ast1;
			}
		}
	}

	private final class FunctionCall_AST extends ScalarExpression_AST {

		private FunctionProxy function_proxy;

		private FunctionCall_AST(FunctionProxy function_proxy, AST params) {
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

	private final class BuiltinFunctionCall_AST extends ScalarExpression_AST {

		private String id;
		private int f_idx;

		private BuiltinFunctionCall_AST(String id, AST params) {
			super(params);
			this.id = id;
			assert BUILTINFUNCNAMES.get(id) != null;
			this.f_idx = BUILTINFUNCNAMES.get(id);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (f_idx == BUILTINFUNCNAMES.get("sprintf")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("close")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("length")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("srand")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("rand")) {
				if (ast1 != null) {
					throw new SemanticException("rand does not take arguments");
				}
				tuples.rand();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("sqrt")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sqrt requires only 1 argument");
				}
				tuples.sqrt();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("int")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.intFunc();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("log")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.log();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("exp")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("exp requires only 1 argument");
				}
				tuples.exp();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("sin")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("sin requires only 1 argument");
				}
				tuples.sin();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("cos")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 1) {
					throw new SemanticException("cos requires only 1 argument");
				}
				tuples.cos();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("atan2")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("atan2 requires 2 arguments");
				}
				tuples.atan2();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("match")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("match requires 2 arguments");
				}
				tuples.match();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("index")) {
				int ast1_result = ast1.populateTuples(tuples);
				if (ast1_result != 2) {
					throw new SemanticException("index requires 2 arguments");
				}
				tuples.index();
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("sub") || f_idx == BUILTINFUNCNAMES.get("gsub")) {
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("sub needs at least 2 arguments");
				}
				boolean is_gsub = f_idx == BUILTINFUNCNAMES.get("gsub");

				int numargs = ast1.populateTuples(tuples);

				// stack contains arg1,arg2[,arg3] - in that pop() order

				if (numargs == 2) {
					tuples.subForDollar0(is_gsub);
				} else if (numargs == 3) {
					AST ptr = ast1.ast2.ast2.ast1;
					if (ptr instanceof ID_AST) {
						ID_AST id_ast = (ID_AST) ptr;
						if (id_ast.isArray()) {
							throw new SemanticException("sub cannot accept an unindexed array as its 3rd argument");
						}
						id_ast.setScalar(true);
						tuples.subForVariable(id_ast.offset, id_ast.is_global, is_gsub);
					} else if (ptr instanceof ArrayReference_AST) {
						ArrayReference_AST arr_ast = (ArrayReference_AST) ptr;
						// push the index
						int ast2_result = arr_ast.ast2.populateTuples(tuples);
						assert ast2_result == 1;
						ID_AST id_ast = (ID_AST) arr_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException("Cannot use " + id_ast + " as an array.");
						}
						tuples.subForArrayReference(id_ast.offset, id_ast.is_global, is_gsub);
					} else if (ptr instanceof DollarExpression_AST) {
						// push the field ref
						DollarExpression_AST dollar_expr = (DollarExpression_AST) ptr;
						assert dollar_expr.ast1 != null;
						int ast1_result = dollar_expr.ast1.populateTuples(tuples);
						assert ast1_result == 1;
						tuples.subForDollarReference(is_gsub);
					} else {
						throw new SemanticException(
								"sub's 3rd argument must be either an id, an array reference, or an input field reference");
					}
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (f_idx == BUILTINFUNCNAMES.get("split")) {
				// split can take 2 or 3 args:
				// split (string, array [,fs])
				// the 2nd argument is pass by reference, which is ok (?)

				// funccallparamlist.funccallparamlist.id_ast
				if (ast1 == null || ast1.ast2 == null || ast1.ast2.ast1 == null) {
					throw new SemanticException("split needs at least 2 arguments");
				}
				AST ptr = ast1.ast2.ast1;
				if (!(ptr instanceof ID_AST)) {
					throw new SemanticException("split needs an array name as its 2nd argument");
				}
				ID_AST arr_ast = (ID_AST) ptr;
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
			} else if (f_idx == BUILTINFUNCNAMES.get("substr")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("tolower")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("toupper")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("system")) {
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
			} else if (f_idx == BUILTINFUNCNAMES.get("exec")) {
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

	private final class FunctionCallParamList_AST extends AST {

		private FunctionCallParamList_AST(AST expr, AST rest) {
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

	private final class FunctionDefParamList_AST extends AST {

		private String id;

		private FunctionDefParamList_AST(String id, AST rest) {
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

			FunctionDefParamList_AST ptr = this;
			while (ptr != null) {
				if (SPECIALVARNAMES.get(ptr.id) != null) {
					throw new SemanticException("Special variable " + ptr.id + " cannot be used as a formal parameter");
				}
				ptr = (FunctionDefParamList_AST) ptr.ast1;
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

	private final class ID_AST extends AST implements NonStatement_AST {

		private String id;
		private int offset = AVM.NULL_OFFSET;
		private boolean is_global;

		private ID_AST(String id, boolean is_global) {
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

	private final class ArrayReference_AST extends ScalarExpression_AST {

		private ArrayReference_AST(AST id_ast, AST idx_ast) {
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

	private final class Integer_AST extends ScalarExpression_AST implements NonStatement_AST {

		private Long I;

		private Integer_AST(Long I) {
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
	private final class Double_AST extends ScalarExpression_AST implements NonStatement_AST {

		private Object D;

		private Double_AST(Double D) {
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
	private final class String_AST extends ScalarExpression_AST implements NonStatement_AST {

		private String S;

		private String_AST(String str) {
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

	private final class Regexp_AST extends ScalarExpression_AST {

		private String regexp_str;

		private Regexp_AST(String regexp_str) {
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

	private final class ConditionPair_AST extends ScalarExpression_AST {

		private ConditionPair_AST(AST boolean_ast_1, AST boolean_ast_2) {
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

	private final class IntegerExpression_AST extends ScalarExpression_AST {

		private IntegerExpression_AST(AST expr) {
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

	private final class DoubleExpression_AST extends ScalarExpression_AST {

		private DoubleExpression_AST(AST expr) {
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

	private final class StringExpression_AST extends ScalarExpression_AST {

		private StringExpression_AST(AST expr) {
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

	private final class Begin_AST extends AST {

		private Begin_AST() {
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

	private final class End_AST extends AST {

		private End_AST() {
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

	private final class PreInc_AST extends ScalarExpression_AST {

		private PreInc_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.inc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
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

	private final class PreDec_AST extends ScalarExpression_AST {

		private PreDec_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.dec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
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

	private final class PostInc_AST extends ScalarExpression_AST {

		private PostInc_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.postInc(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.incArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
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

	private final class PostDec_AST extends ScalarExpression_AST {

		private PostDec_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;
			int ast1_result = ast1.populateTuples(tuples);
			assert ast1_result == 1;
			if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
				tuples.postDec(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ArrayReference_AST) {
				ArrayReference_AST arr_ast = (ArrayReference_AST) ast1;
				ID_AST id_ast = (ID_AST) arr_ast.ast1;
				assert id_ast != null;
				assert arr_ast.ast2 != null;
				int arr_ast2_result = arr_ast.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				tuples.decArrayRef(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast1;
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

	private final class Print_AST extends ScalarExpression_AST {

		private int output_token;

		private Print_AST(AST expr_list, int output_token, AST output_expr) {
			super(expr_list, output_expr);
			this.output_token = output_token;
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

			if (output_token == GT) {
				tuples.printToFile(param_count, false); // false = no append
			} else if (output_token == APPEND) {
				tuples.printToFile(param_count, true); // false = no append
			} else if (output_token == PIPE) {
				tuples.printToPipe(param_count);
			} else {
				tuples.print(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// we don't know if it is a scalar
	private final class Extension_AST extends AST {

		private String extension_keyword;

		private Extension_AST(String extension_keyword, AST param_ast) {
			super(param_ast);
			this.extension_keyword = extension_keyword;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int param_count;
			if (ast1 == null) {
				param_count = 0;
			} else {
				/// Query for the extension.
				JawkExtension extension = extensions.get(extension_keyword);
				int arg_count = countParams((FunctionCallParamList_AST) ast1);
				/// Get all required assoc array parameters:
				int[] req_array_idxs = extension.getAssocArrayParameterPositions(extension_keyword, arg_count);
				assert req_array_idxs != null;

				for (int idx : req_array_idxs) {
					AST param_ast = getParamAst((FunctionCallParamList_AST) ast1, idx);
					assert ast1 instanceof FunctionCallParamList_AST;
					// if the parameter is an ID_AST...
					if (param_ast.ast1 instanceof ID_AST) {
						// then force it to be an array,
						// or complain if it is already tagged as a scalar
						ID_AST id_ast = (ID_AST) param_ast.ast1;
						if (id_ast.isScalar()) {
							throw new SemanticException(
									"Extension '"
											+ extension_keyword
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
			if (parent instanceof FunctionCallParamList_AST) {
				AST ptr = parent;
				while (ptr instanceof FunctionCallParamList_AST) {
					ptr = ptr.parent;
				}
				is_initial = !(ptr instanceof Extension_AST);
			} else {
				is_initial = true;
			}
			tuples.extension(extension_keyword, param_count, is_initial);
			popSourceLineNumber(tuples);
			// an extension always returns a value, even if it is blank/null
			return 1;
		}

		private AST getParamAst(FunctionCallParamList_AST p_ast, int pos) {
			for (int i = 0; i < pos; ++i) {
				p_ast = (FunctionCallParamList_AST) p_ast.ast2;
				if (p_ast == null) {
					throw new SemanticException("More arguments required for assoc array parameter position specification.");
				}
			}
			return p_ast;
		}

		private int countParams(FunctionCallParamList_AST p_ast) {
			int cnt = 0;
			while (p_ast != null) {
				p_ast = (FunctionCallParamList_AST) p_ast.ast2;
				++cnt;
			}
			return cnt;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + extension_keyword + ")";
		}
	}

	private final class Printf_AST extends ScalarExpression_AST {

		private int output_token;

		private Printf_AST(AST expr_list, int output_token, AST output_expr) {
			super(expr_list, output_expr);
			this.output_token = output_token;
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

			if (output_token == GT) {
				tuples.printfToFile(param_count, false); // false = no append
			} else if (output_token == APPEND) {
				tuples.printfToFile(param_count, true); // false = no append
			} else if (output_token == PIPE) {
				tuples.printfToPipe(param_count);
			} else {
				tuples.printf(param_count);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class Getline_AST extends ScalarExpression_AST {

		private Getline_AST(AST pipe_expr, AST lvalue_ast, AST in_redirect) {
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
			} else if (ast2 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast2;
				tuples.assign(id_ast.offset, id_ast.is_global);
				if (id_ast.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (ast2 instanceof ArrayReference_AST) {
				ArrayReference_AST arr = (ArrayReference_AST) ast2;
				// push the index
				assert arr.ast2 != null;
				int arr_ast2_result = arr.ast2.populateTuples(tuples);
				assert arr_ast2_result == 1;
				// push the array ref itself
				ID_AST id_ast = (ID_AST) arr.ast1;
				tuples.assignArray(id_ast.offset, id_ast.is_global);
			} else if (ast2 instanceof DollarExpression_AST) {
				DollarExpression_AST dollar_expr = (DollarExpression_AST) ast2;
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

	private final class ReturnStatement_AST extends AST {

		private ReturnStatement_AST(AST expr) {
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

	private final class ExitStatement_AST extends AST {

		private ExitStatement_AST(AST expr) {
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

	private final class DeleteStatement_AST extends AST {

		private DeleteStatement_AST(AST symbol_ast) {
			super(symbol_ast);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert ast1 != null;

			if (ast1 instanceof ArrayReference_AST) {
				assert ast1.ast1 != null; // a in a[b]
				assert ast1.ast2 != null; // b in a[b]
				ID_AST id_ast = (ID_AST) ast1.ast1;
				if (id_ast.isScalar()) {
					throw new SemanticException("delete: Cannot use a scalar as an array.");
				}
				id_ast.setArray(true);
				int idx_result = ast1.ast2.populateTuples(tuples);
				assert idx_result == 1;
				// idx on the stack
				tuples.deleteArrayElement(id_ast.offset, id_ast.is_global);
			} else if (ast1 instanceof ID_AST) {
				ID_AST id_ast = (ID_AST) ast1;
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

	private class BreakStatement_AST extends AST {

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

	private final class SleepStatement_AST extends AST {

		private SleepStatement_AST(AST expr) {
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

	private final class DumpStatement_AST extends AST {

		private DumpStatement_AST(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (ast1 == null) {
				tuples.dump(0);
			} else {
				assert ast1 instanceof FunctionCallParamList_AST;
				AST ptr = ast1;
				while (ptr != null) {
					if (!(ptr.ast1 instanceof ID_AST)) {
						throw new SemanticException("ID required for argument(s) to _dump");
					}
					ID_AST id_ast = (ID_AST) ptr.ast1;
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

	private class NextStatement_AST extends AST {

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

	private final class ContinueStatement_AST extends AST {

		private ContinueStatement_AST() {
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

		private FunctionDef_AST function_def_ast;
		private String id;

		private FunctionProxy(String id) {
			this.id = id;
		}

		private void setFunctionDefinition(FunctionDef_AST function_def) {
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
			ID_AST id_ast = symbol_table.global_ids.get(varname);
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
		private Begin_AST begin_ast = null;
		private End_AST end_ast = null;

		// functions (proxies)
		private Map<String, FunctionProxy> function_proxies = new HashMap<String, FunctionProxy>();

		// variable management
		private Map<String, ID_AST> global_ids = new HashMap<String, ID_AST>();
		private Map<String, Map<String, ID_AST>> local_ids = new HashMap<String, Map<String, ID_AST>>();
		private Map<String, Set<String>> function_parameters = new HashMap<String, Set<String>>();
		private Set<String> ids = new HashSet<String>();

		// current function definition for symbols
		private String func_name = null;

		// using set/clear rather than push/pop, it is impossible to define functions within functions
		void setFunctionName(String func_name) {
			assert this.func_name == null;
			this.func_name = func_name;
		}

		void clearFunctionName(String func_name) {
			assert this.func_name != null && this.func_name.length() > 0;
			assert this.func_name.equals(func_name);
			this.func_name = null;
		}

		AST addBEGIN() {
			if (begin_ast == null) {
				begin_ast = new Begin_AST();
			}
			return begin_ast;
		}

		AST addEND() {
			if (end_ast == null) {
				end_ast = new End_AST();
			}
			return end_ast;
		}

		private ID_AST getID(String id) {
			if (function_proxies.get(id) != null) {
				throw new ParserException("cannot use " + id + " as a variable; it is a function");
			}

			// put in the pool of ids to guard against using it as a function name
			ids.add(id);

			Map<String, ID_AST> map;
			if (func_name == null) {
				map = global_ids;
			} else {
				Set<String> set = function_parameters.get(func_name);
				// we need "set != null && ..." here because if function
				// is defined with no args (i.e., function f() ...),
				// then set is null
				if (set != null && set.contains(id)) {
					map = local_ids.get(func_name);
					if (map == null) {
						local_ids.put(func_name, map = new HashMap<String, ID_AST>());
					}
				} else {
					map = global_ids;
				}
			}
			assert map != null;
			ID_AST id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new ID_AST(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}
			return id_ast;
		}

		AST addID(String id) throws ParserException {
			ID_AST ret_val = getID(id);
			/// ***
			/// We really don't know if the evaluation is for an array or for a scalar
			/// here, because we can use an array as a function parameter (passed by reference).
			/// ***
			// if (ret_val.is_array)
			// throw new ParserException("Cannot use "+ret_val+" as a scalar.");
			// ret_val.is_scalar = true;
			return ret_val;
		}

		int addFunctionParameter(String func_name, String id) {
			Set<String> set = function_parameters.get(func_name);
			if (set == null) {
				function_parameters.put(func_name, set = new HashSet<String>());
			}
			if (set.contains(id)) {
				throw new ParserException("multiply defined parameter " + id + " in function " + func_name);
			}
			int retval = set.size();
			set.add(id);

			Map<String, ID_AST> map = local_ids.get(func_name);
			if (map == null) {
				local_ids.put(func_name, map = new HashMap<String, ID_AST>());
			}
			assert map != null;
			ID_AST id_ast = map.get(id);
			if (id_ast == null) {
				id_ast = new ID_AST(id, map == global_ids);
				id_ast.offset = map.size();
				assert id_ast.offset != AVM.NULL_OFFSET;
				map.put(id, id_ast);
			}

			return retval;
		}

		ID_AST getFunctionParameterIDAST(String func_name, String f_id_string) {
			return local_ids.get(func_name).get(f_id_string);
		}

		AST addArrayID(String id) throws ParserException {
			ID_AST ret_val = getID(id);
			if (ret_val.isScalar()) {
				throw new ParserException("Cannot use " + ret_val + " as an array.");
			}
			ret_val.setArray(true);
			return ret_val;
		}

		AST addFunctionDef(String func_name, AST param_list, AST block) {
			if (ids.contains(func_name)) {
				throw new ParserException("cannot use " + func_name + " as a function; it is a variable");
			}
			FunctionProxy function_proxy = function_proxies.get(func_name);
			if (function_proxy == null) {
				function_proxies.put(func_name, function_proxy = new FunctionProxy(func_name));
			}
			FunctionDef_AST function_def = new FunctionDef_AST(func_name, param_list, block);
			function_proxy.setFunctionDefinition(function_def);
			return function_def;
		}

		AST addFunctionCall(String id, AST param_list) {
			FunctionProxy function_proxy = function_proxies.get(id);
			if (function_proxy == null) {
				function_proxies.put(id, function_proxy = new FunctionProxy(id));
			}
			return new FunctionCall_AST(function_proxy, param_list);
		}

		AST addArrayReference(String id, AST idx_ast) throws ParserException {
			return new ArrayReference_AST(addArrayID(id), idx_ast);
		}

		// constants are no longer cached/hashed so that individual ASTs
		// can report accurate line numbers upon errors

		AST addINTEGER(String integer) {
			return new Integer_AST(Long.parseLong(integer));
		}

		AST addDOUBLE(String dbl) {
			return new Double_AST(Double.valueOf(dbl));
		}

		AST addSTRING(String str) {
			return new String_AST(str);
		}

		AST addREGEXP(String regexp) {
			return new Regexp_AST(regexp);
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
