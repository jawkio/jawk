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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.NotImplementedError;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;
import java.util.function.Supplier;
import org.metricshub.jawk.util.ScriptSource;
import org.metricshub.jawk.frontend.ast.LexerException;
import org.metricshub.jawk.frontend.ast.ParserException;

/**
 * Converts the AWK script into a syntax tree,
 * which is useful the backend that either compiles or interprets the script.
 * <p>
 * It contains the internal state of the parser and the lexer.
 *
 * @author Danny Daglas
 */
public class AwkParser {

	/**
	 * Flags that describe special behaviours of AST nodes. These replace the
	 * previous marker interfaces such as {@code Breakable} and
	 * {@code NonStatementAst}.
	 */
	private enum AstFlag {
		BREAKABLE,
		NEXTABLE,
		CONTINUEABLE,
		RETURNABLE,
		NON_STATEMENT
	}

	/** Lexer token values. */
	enum Token {
		EOF,
		NEWLINE,
		SEMICOLON,
		ID,
		FUNC_ID,
		INTEGER,
		DOUBLE,
		STRING,

		EQUALS,

		AND,
		OR,

		EQ,
		GT,
		GE,
		LT,
		LE,
		NE,
		NOT,
		PIPE,
		QUESTION_MARK,
		COLON,
		APPEND,

		PLUS,
		MINUS,
		MULT,
		DIVIDE,
		MOD,
		POW,
		COMMA,
		MATCHES,
		NOT_MATCHES,
		DOLLAR,

		INC,
		DEC,

		PLUS_EQ,
		MINUS_EQ,
		MULT_EQ,
		DIV_EQ,
		MOD_EQ,
		POW_EQ,

		OPEN_PAREN,
		CLOSE_PAREN,
		OPEN_BRACE,
		CLOSE_BRACE,
		OPEN_BRACKET,
		CLOSE_BRACKET,

		BUILTIN_FUNC_NAME,

		EXTENSION,

		KW_FUNCTION,
		KW_BEGIN,
		KW_END,
		KW_IN,
		KW_IF,
		KW_ELSE,
		KW_WHILE,
		KW_FOR,
		KW_DO,
		KW_RETURN,
		KW_EXIT,
		KW_NEXT,
		KW_CONTINUE,
		KW_DELETE,
		KW_BREAK,
		KW_PRINT,
		KW_PRINTF,
		KW_GETLINE
	}

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
	private static final Map<String, Token> KEYWORDS = new HashMap<String, Token>();

	static {
		// special keywords
		KEYWORDS.put("function", Token.KW_FUNCTION);
		KEYWORDS.put("BEGIN", Token.KW_BEGIN);
		KEYWORDS.put("END", Token.KW_END);
		KEYWORDS.put("in", Token.KW_IN);

		// statements
		KEYWORDS.put("if", Token.KW_IF);
		KEYWORDS.put("else", Token.KW_ELSE);
		KEYWORDS.put("while", Token.KW_WHILE);
		KEYWORDS.put("for", Token.KW_FOR);
		KEYWORDS.put("do", Token.KW_DO);
		KEYWORDS.put("return", Token.KW_RETURN);
		KEYWORDS.put("exit", Token.KW_EXIT);
		KEYWORDS.put("next", Token.KW_NEXT);
		KEYWORDS.put("continue", Token.KW_CONTINUE);
		KEYWORDS.put("delete", Token.KW_DELETE);
		KEYWORDS.put("break", Token.KW_BREAK);

		// special-form functions
		KEYWORDS.put("print", Token.KW_PRINT);
		KEYWORDS.put("printf", Token.KW_PRINTF);
		KEYWORDS.put("getline", Token.KW_GETLINE);
	}

	/**
	 * Built-in function token values.
	 * Built-in function token values are distinguished
	 * from lexer token values.
	 */
	private static int fIdx = 257;
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
		BUILTIN_FUNC_NAMES.put("exec", fIdx++);
	}

	private static final int SP_IDX = 257;
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
		SPECIAL_VAR_NAMES.put("NR", SP_IDX);
		SPECIAL_VAR_NAMES.put("FNR", SP_IDX);
		SPECIAL_VAR_NAMES.put("NF", SP_IDX);
		SPECIAL_VAR_NAMES.put("FS", SP_IDX);
		SPECIAL_VAR_NAMES.put("RS", SP_IDX);
		SPECIAL_VAR_NAMES.put("OFS", SP_IDX);
		SPECIAL_VAR_NAMES.put("ORS", SP_IDX);
		SPECIAL_VAR_NAMES.put("RSTART", SP_IDX);
		SPECIAL_VAR_NAMES.put("RLENGTH", SP_IDX);
		SPECIAL_VAR_NAMES.put("FILENAME", SP_IDX);
		SPECIAL_VAR_NAMES.put("SUBSEP", SP_IDX);
		SPECIAL_VAR_NAMES.put("CONVFMT", SP_IDX);
		SPECIAL_VAR_NAMES.put("OFMT", SP_IDX);
		SPECIAL_VAR_NAMES.put("ENVIRON", SP_IDX);
		SPECIAL_VAR_NAMES.put("ARGC", SP_IDX);
		SPECIAL_VAR_NAMES.put("ARGV", SP_IDX);
	}

	/**
	 * Defined as concrete implementation class (not an
	 * interface reference) as to not clutter the interface
	 * with methods appropriate for private access, only.
	 */
	private final AwkSymbolTableImpl symbolTable = new AwkSymbolTableImpl();

	private final Map<String, JawkExtension> extensions;

	/**
	 * <p>
	 * Constructor for AwkParser.
	 * </p>
	 *
	 * @param extensions a {@link java.util.Map} object
	 */
	public AwkParser(Map<String, JawkExtension> extensions) {
		this.extensions = extensions == null ? Collections.emptyMap() : new HashMap<>(extensions);
	}

	private List<ScriptSource> scriptSources;
	private int scriptSourcesCurrentIndex;
	private LineNumberReader reader;
	private int c;
	private Token token;

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
	 * Parse the script streamed by script_reader. Build and return the
	 * root of the abstract syntax tree which represents the Jawk script.
	 *
	 * @param localScriptSources List of script sources
	 * @return The abstract syntax tree of this script.
	 * @throws java.io.IOException upon an IO error.
	 */
	public AstNode parse(List<ScriptSource> localScriptSources) throws IOException {
		if (localScriptSources == null || localScriptSources.isEmpty()) {
			throw new IOException("No script sources supplied");
		}
		this.scriptSources = Collections.unmodifiableList(new ArrayList<>(localScriptSources));
		scriptSourcesCurrentIndex = 0;
		reader = new LineNumberReader(this.scriptSources.get(scriptSourcesCurrentIndex).getReader());
		read();
		lexer();
		return SCRIPT();
	}

	/**
	 * Parse a single AWK expression and return the corresponding AST.
	 *
	 * @param expressionSource The expression to parse (not a statement or rule, just an expression)
	 * @return tuples representing the expression
	 * @throws IOException upon an IO error or parsing error
	 */
	public AstNode parseExpression(ScriptSource expressionSource) throws IOException {

		// Sanity check
		if (expressionSource == null) {
			throw new IOException("No source supplied");
		}

		// Reader of the expression
		this.scriptSources = Collections.singletonList(expressionSource);
		scriptSourcesCurrentIndex = 0;
		reader = new LineNumberReader(this.scriptSources.get(scriptSourcesCurrentIndex).getReader());

		// Initialize the lexer
		read();
		lexer();

		// An expression is a TERNARY_EXPRESSION
		return EXPRESSION_TO_EVALUATE();
	}

	private LexerException lexerException(String msg) {
		return new LexerException(
				msg,
				scriptSources.get(scriptSourcesCurrentIndex).getDescription(),
				reader.getLineNumber());
	}

	/**
	 * Reads the string and handle all escape codes.
	 *
	 * @throws IOException
	 */
	@SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "intentional for escape sequence parsing")
	private void readString() throws IOException {
		string.setLength(0);

		while (token != Token.EOF && c > 0 && c != '"' && c != '\n') {
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
		if (token == Token.EOF || c == '\n' || c <= 0) {
			throw lexerException("Unterminated string: " + text);
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

		while (token != Token.EOF && c > 0 && c != '/' && c != '\n') {
			if (c == '\\') {
				read();
				if (c != '/') {
					regexp.append('\\');
				}
			}
			regexp.append((char) c);
			read();
		}
		if (token == Token.EOF || c == '\n' || c <= 0) {
			throw lexerException("Unterminated string: " + text);
		}
		read();
	}

	private Token lexer(Token expectedToken) throws IOException {
		if (token != expectedToken) {
			throw parserException(
					"Expecting " + expectedToken.name() + ". Found: " + token.name() + " (" + text + ")");
		}
		return lexer();
	}

	private Token lexer() throws IOException {
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
			token = Token.EOF;
			return token;
		}
		if (c == ',') {
			read();
			skipWhitespaces();
			token = Token.COMMA;
			return token;
		}
		if (c == '(') {
			read();
			token = Token.OPEN_PAREN;
			return token;
		}
		if (c == ')') {
			read();
			token = Token.CLOSE_PAREN;
			return token;
		}
		if (c == '{') {
			read();
			skipWhitespaces();
			token = Token.OPEN_BRACE;
			return token;
		}
		if (c == '}') {
			read();
			token = Token.CLOSE_BRACE;
			return token;
		}
		if (c == '[') {
			read();
			token = Token.OPEN_BRACKET;
			return token;
		}
		if (c == ']') {
			read();
			token = Token.CLOSE_BRACKET;
			return token;
		}
		if (c == '$') {
			read();
			token = Token.DOLLAR;
			return token;
		}
		if (c == '~') {
			read();
			token = Token.MATCHES;
			return token;
		}
		if (c == '?') {
			read();
			skipWhitespaces();
			token = Token.QUESTION_MARK;
			return token;
		}
		if (c == ':') {
			read();
			skipWhitespaces();
			token = Token.COLON;
			return token;
		}
		if (c == '&') {
			read();
			if (c == '&') {
				read();
				skipWhitespaces();
				token = Token.AND;
				return token;
			}
			throw lexerException("use && for logical and");
		}
		if (c == '|') {
			read();
			if (c == '|') {
				read();
				skipWhitespaces();
				token = Token.OR;
				return token;
			}
			token = Token.PIPE;
			return token;
		}
		if (c == '=') {
			read();
			if (c == '=') {
				read();
				token = Token.EQ;
				return token;
			}
			token = Token.EQUALS;
			return token;
		}
		if (c == '+') {
			read();
			if (c == '=') {
				read();
				token = Token.PLUS_EQ;
				return token;
			} else if (c == '+') {
				read();
				token = Token.INC;
				return token;
			}
			token = Token.PLUS;
			return token;
		}
		if (c == '-') {
			read();
			if (c == '=') {
				read();
				token = Token.MINUS_EQ;
				return token;
			} else if (c == '-') {
				read();
				token = Token.DEC;
				return token;
			}
			token = Token.MINUS;
			return token;
		}
		if (c == '*') {
			read();
			if (c == '=') {
				read();
				token = Token.MULT_EQ;
				return token;
			} else if (c == '*') {
				read();
				if (c == '=') {
					read();
					token = Token.POW_EQ;
					return token;
				}
				token = Token.POW;
				return token;
			}
			token = Token.MULT;
			return token;
		}
		if (c == '/') {
			read();
			if (c == '=') {
				read();
				token = Token.DIV_EQ;
				return token;
			}
			token = Token.DIVIDE;
			return token;
		}
		if (c == '%') {
			read();
			if (c == '=') {
				read();
				token = Token.MOD_EQ;
				return token;
			}
			token = Token.MOD;
			return token;
		}
		if (c == '^') {
			read();
			if (c == '=') {
				read();
				token = Token.POW_EQ;
				return token;
			}
			token = Token.POW;
			return token;
		}
		if (c == '>') {
			read();
			if (c == '=') {
				read();
				token = Token.GE;
				return token;
			} else if (c == '>') {
				read();
				token = Token.APPEND;
				return token;
			}
			token = Token.GT;
			return token;
		}
		if (c == '<') {
			read();
			if (c == '=') {
				read();
				token = Token.LE;
				return token;
			}
			token = Token.LT;
			return token;
		}
		if (c == '!') {
			read();
			if (c == '=') {
				read();
				token = Token.NE;
				return token;
			} else if (c == '~') {
				read();
				token = Token.NOT_MATCHES;
				return token;
			}
			token = Token.NOT;
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
				throw lexerException("Decimal point encountered with no values on either side.");
			}
			token = Token.DOUBLE;
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
					token = Token.DOUBLE;
					return token;
				} else if (Character.isDigit(c)) {
					// integer or double.
					read();
				} else {
					break;
				}
			}
			// integer, only
			token = Token.INTEGER;
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
				token = Token.EXTENSION;
				return token;
			}
			Token kwToken = KEYWORDS.get(text.toString());
			if (kwToken != null) {
				token = kwToken;
				return token;
			}
			Integer builtinIdx = BUILTIN_FUNC_NAMES.get(text.toString());
			if (builtinIdx != null) {
				token = Token.BUILTIN_FUNC_NAME;
				return token;
			}
			if (c == '(') {
				token = Token.FUNC_ID;
				return token;
			} else {
				token = Token.ID;
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
			token = Token.SEMICOLON;
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
			token = Token.NEWLINE;
			return token;
		}

		if (c == '"') {
			// string
			read();
			readString();
			token = Token.STRING;
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

		throw lexerException("Invalid character (" + c + "): " + ((char) c));
	}

	// SUPPORTING FUNCTIONS/METHODS
	private void terminator() throws IOException {
		// like optTerminator, except error if no terminator was found
		if (!optTerminator()) {
			throw parserException("Expecting statement terminator. Got " + token.name() + ": " + text);
		}
	}

	private boolean optTerminator() throws IOException {
		if (optNewline()) {
			return true;
		} else if (token == Token.EOF || token == Token.CLOSE_BRACE) {
			return true; // do nothing
		} else if (token == Token.SEMICOLON) {
			lexer();
			return true;
		} else {
			// no terminator consumed
			return false;
		}
	}

	private boolean optNewline() throws IOException {
		if (token == Token.NEWLINE) {
			lexer();
			return true;
		} else {
			return false;
		}
	}

	// RECURSIVE DECENT PARSER:
	// CHECKSTYLE.OFF: MethodName
	// SCRIPT : \n [RULE_LIST] Token.EOF
	AST SCRIPT() throws IOException {
		AST rl;
		if (token != Token.EOF) {
			rl = RULE_LIST();
		} else {
			rl = null;
		}
		lexer(Token.EOF);
		return rl;
	}

	// EXPRESSION_TO_EVALUATE: [TERNARY_EXPRESSION] Token.EOF
	// Used to parse simple expressions to evaluate instead of full scripts
	AST EXPRESSION_TO_EVALUATE() throws IOException {
		AST exprAst = token != Token.EOF ? TERNARY_EXPRESSION(true, false, true) : null;
		lexer(Token.EOF);
		return new ExpressionToEvaluateAst(exprAst);
	}

	// RULE_LIST : \n [ ( RULE | FUNCTION terminator ) optTerminator RULE_LIST ]
	AST RULE_LIST() throws IOException {
		optNewline();
		AST ruleOrFunction = null;
		if (token == Token.KW_FUNCTION) {
			ruleOrFunction = FUNCTION();
		} else if (token != Token.EOF) {
			ruleOrFunction = RULE();
		} else {
			return null;
		}
		optTerminator(); // newline or ; (maybe)
		return new RuleListAst(ruleOrFunction, RULE_LIST());
	}

	// FUNCTION: function functionName( [FORMAL_PARAM_LIST] ) STATEMENT_LIST
	AST FUNCTION() throws IOException {
		expectKeyword("function");
		String functionName;
		if (token == Token.FUNC_ID || token == Token.ID) {
			functionName = text.toString();
			lexer();
		} else {
			throw parserException("Expecting function name. Got " + token.name() + ": " + text);
		}
		symbolTable.setFunctionName(functionName);
		lexer(Token.OPEN_PAREN);
		AST formalParamList;
		if (token == Token.CLOSE_PAREN) {
			formalParamList = null;
		} else {
			formalParamList = FORMAL_PARAM_LIST(functionName);
		}
		lexer(Token.CLOSE_PAREN);
		optNewline();

		lexer(Token.OPEN_BRACE);
		AST functionBlock = STATEMENT_LIST();
		lexer(Token.CLOSE_BRACE);
		symbolTable.clearFunctionName(functionName);
		return symbolTable.addFunctionDef(functionName, formalParamList, functionBlock);
	}

	// FORMAT_PARAM_LIST:
	AST FORMAL_PARAM_LIST(String functionName) throws IOException {
		if (token == Token.ID) {
			String id = text.toString();
			symbolTable.addFunctionParameter(functionName, id);
			lexer();
			if (token == Token.COMMA) {
				lexer();
				optNewline();
				AST rest = FORMAL_PARAM_LIST(functionName);
				if (rest == null) {
					throw parserException("Cannot terminate a formal parameter list with a comma.");
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
		AST optExpr;
		AST optStmts;
		if (token == Token.KW_BEGIN) {
			lexer();
			optExpr = symbolTable.addBEGIN();
		} else if (token == Token.KW_END) {
			lexer();
			optExpr = symbolTable.addEND();
		} else if (token != Token.OPEN_BRACE && token != Token.SEMICOLON && token != Token.NEWLINE && token != Token.EOF) {
			// true = allow comparators, allow IN keyword, do Token.NOT allow multidim indices expressions
			optExpr = ASSIGNMENT_EXPRESSION(true, true, false);
			// for ranges, like conditionStart, conditionEnd
			if (token == Token.COMMA) {
				lexer();
				optNewline();
				// true = allow comparators, allow IN keyword, do Token.NOT allow multidim indices expressions
				optExpr = new ConditionPairAst(optExpr, ASSIGNMENT_EXPRESSION(true, true, false));
			}
		} else {
			optExpr = null;
		}
		if (token == Token.OPEN_BRACE) {
			lexer();
			optStmts = STATEMENT_LIST();
			lexer(Token.CLOSE_BRACE);
		} else {
			optStmts = null;
		}
		return new RuleAst(optExpr, optStmts);
	}

	// STATEMENT_LIST : [ STATEMENT_BLOCK|STATEMENT STATEMENT_LIST ]
	private AST STATEMENT_LIST() throws IOException {
		// statement lists can only live within curly brackets (braces)
		optNewline();
		if (token == Token.CLOSE_BRACE || token == Token.EOF) {
			return null;
		}
		AST stmt;
		if (token == Token.OPEN_BRACE) {
			lexer();
			stmt = STATEMENT_LIST();
			lexer(Token.CLOSE_BRACE);
		} else {
			if (token == Token.SEMICOLON) {
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

	/**
	 * Parse a (possibly comma‐separated) list of ASSIGNMENT_EXPRESSIONs.
	 *
	 * @param allowComparisons
	 *        – true ⇒ treat ‘>’ and ‘<’ as comparison operators
	 *        – false ⇒ treat ‘>’ and ‘<’ as redirection tokens (break out)
	 * @param allowInKeyword
	 *        – true ⇒ allow the “in” keyword inside expressions
	 *        – false ⇒ disallow “in”
	 * @param commaIsArgSeparator
	 *        – true ⇒ we are in a print(…) or printf(…) context and must treat every top‐level comma as a new argument
	 *        – false ⇒ a normal context (function call, bare print, etc.), so commas simply split as before
	 */
	AST EXPRESSION_LIST(boolean allowComparisons, boolean allowInKeyword, boolean commaIsArgSeparator)
			throws IOException {
		// 1) Parse exactly one assignment expression.
		// Passing `allowComparisons` will decide if ‘>’/’<’ become comparisons or redirectors.
		AST expr = ASSIGNMENT_EXPRESSION(allowComparisons, allowInKeyword, /* allowMultidim= */ false);

		// 2) If the next token is a comma, we must consume it and build the rest of the list
		// regardless of whether forceCommaSplit is true or false. In practice, callers
		// set forceCommaSplit=true when they know “every comma here must spawn a new argument.”
		// But even if forceCommaSplit=false, we still consume commas here so that normal
		// function‐call argument lists and bare “print x, y” continue to work exactly as before.
		if (token == Token.COMMA) {
			lexer(); // consume ','
			optNewline(); // allow newline after comma (AWK style)

			// Recurse with the same flags; the caller’s forceCommaSplit value is simply passed along,
			// but it doesn’t prevent us from splitting on commas. It’s up to the caller to decide
			// which comparisons/redirections are allowed inside ASSIGNMENT_EXPRESSION calls.
			AST rest = EXPRESSION_LIST(allowComparisons, allowInKeyword, commaIsArgSeparator);
			return new FunctionCallParamListAst(expr, rest);
		}

		// 3) No comma ⇒ this single expression is a one‐element list.
		return new FunctionCallParamListAst(expr, null);
	}

	// ASSIGNMENT_EXPRESSION = COMMA_EXPRESSION [ (=,+=,-=,*=) ASSIGNMENT_EXPRESSION ]
	AST ASSIGNMENT_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST commaExpression = COMMA_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		Token op = null;
		String txt = null;
		AST assignmentExpression = null;
		if (token == Token.EQUALS
				|| token == Token.PLUS_EQ
				|| token == Token.MINUS_EQ
				|| token == Token.MULT_EQ
				|| token == Token.DIV_EQ
				|| token == Token.MOD_EQ
				|| token == Token.POW_EQ) {
			op = token;
			txt = text.toString();
			lexer();
			assignmentExpression = ASSIGNMENT_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new AssignmentExpressionAst(commaExpression, op, txt, assignmentExpression);
		}
		return commaExpression;
	}

	// COMMA_EXPRESSION = TERNARY_EXPRESSION [, COMMA_EXPRESSION] !!!ONLY IF!!! allowMultidimIndices is true
	// allowMultidimIndices is set to true when we need (1,2,3,4) expressions to collapse into an array index expression
	// (converts 1,2,3,4 to 1 SUBSEP 2 SUBSEP 3 SUBSEP 4) after an open parenthesis (grouping) expression starter
	AST COMMA_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST concatExpression = TERNARY_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		if (allowMultidimIndices && token == Token.COMMA) {
			// consume the comma
			lexer();
			optNewline();
			AST rest = COMMA_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			if (rest instanceof ArrayIndexAst) {
				return new ArrayIndexAst(concatExpression, rest);
			} else {
				return new ArrayIndexAst(concatExpression, new ArrayIndexAst(rest, null));
			}
		} else {
			return concatExpression;
		}
	}

	// TERNARY_EXPRESSION = LOGICAL_OR_EXPRESSION [ ? TERNARY_EXPRESSION : TERNARY_EXPRESSION ]
	AST TERNARY_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST le1 = LOGICAL_OR_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		if (token == Token.QUESTION_MARK) {
			lexer();
			AST trueBlock = TERNARY_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			lexer(Token.COLON);
			AST falseBlock = TERNARY_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new TernaryExpressionAst(le1, trueBlock, falseBlock);
		} else {
			return le1;
		}
	}

	// LOGICAL_OR_EXPRESSION = LOGICAL_AND_EXPRESSION [ || LOGICAL_OR_EXPRESSION ]
	AST LOGICAL_OR_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST le2 = LOGICAL_AND_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		Token op = null;
		String txt = null;
		AST le1 = null;
		if (token == Token.OR) {
			op = token;
			txt = text.toString();
			lexer();
			le1 = LOGICAL_OR_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new LogicalExpressionAst(le2, op, txt, le1);
		}
		return le2;
	}

	// LOGICAL_AND_EXPRESSION = IN_EXPRESSION [ && LOGICAL_AND_EXPRESSION ]
	AST LOGICAL_AND_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST comparisonExpression = IN_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		Token op = null;
		String txt = null;
		AST le2 = null;
		if (token == Token.AND) {
			op = token;
			txt = text.toString();
			lexer();
			le2 = LOGICAL_AND_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new LogicalExpressionAst(comparisonExpression, op, txt, le2);
		}
		return comparisonExpression;
	}

	// IN_EXPRESSION = MATCHING_EXPRESSION [ IN_EXPRESSION ]
	// allowInKeyword is set false while parsing the first expression within
	// a for() statement (because it could be "for (key in arr)", and this
	// production will consume and the for statement will never have a chance
	// of processing it
	// all other times, it is true
	AST IN_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		// true = allow postInc/dec operators
		AST comparison = MATCHING_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		if (allowInKeyword && token == Token.KW_IN) {
			lexer();
			return new InExpressionAst(
					comparison,
					IN_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices));
		}
		return comparison;
	}

	// MATCHING_EXPRESSION = COMPARISON_EXPRESSION [ (~,!~) MATCHING_EXPRESSION ]
	AST MATCHING_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST expression = COMPARISON_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		Token op = null;
		String txt = null;
		AST comparisonExpression = null;
		if (token == Token.MATCHES || token == Token.NOT_MATCHES) {
			op = token;
			txt = text.toString();
			lexer();
			comparisonExpression = MATCHING_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new ComparisonExpressionAst(expression, op, txt, comparisonExpression);
		}
		return expression;
	}

	// COMPARISON_EXPRESSION = CONCAT_EXPRESSION [ (==,>,>=,<,<=,!=,|) COMPARISON_EXPRESSION ]
	// allowComparison is set false when within a print/printf statement;
	// all other times it is set true
	AST COMPARISON_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST expression = CONCAT_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		Token op = null;
		String txt = null;
		AST comparisonExpression = null;
		// Allow < <= == != >=, and only > if comparators are allowed
		if (token == Token.EQ
				|| token == Token.GE
				|| token == Token.LT
				|| token == Token.LE
				|| token == Token.NE
				|| (token == Token.GT && allowComparison)) {
			op = token;
			txt = text.toString();
			lexer();
			comparisonExpression = COMPARISON_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
			return new ComparisonExpressionAst(expression, op, txt, comparisonExpression);
		} else if (allowComparison && token == Token.PIPE) {
			lexer();
			return GETLINE_EXPRESSION(expression, allowComparison, allowInKeyword);
		}

		return expression;
	}

	// CONCAT_EXPRESSION = EXPRESSION [ CONCAT_EXPRESSION ]
	AST CONCAT_EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST te = EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices);
		if (token == Token.INTEGER
				||
				token == Token.DOUBLE
				||
				token == Token.OPEN_PAREN
				||
				token == Token.FUNC_ID
				||
				token == Token.INC
				||
				token == Token.DEC
				||
				token == Token.ID
				||
				token == Token.STRING
				||
				token == Token.DOLLAR
				||
				token == Token.BUILTIN_FUNC_NAME
				||
				token == Token.EXTENSION) {
			// allow concatination here only when certain tokens follow
			return new ConcatExpressionAst(
					te,
					CONCAT_EXPRESSION(allowComparison, allowInKeyword, allowMultidimIndices));
		} else {
			return te;
		}
	}

	// EXPRESSION : TERM [ (+|-) EXPRESSION ]
	AST EXPRESSION(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST term = TERM(allowComparison, allowInKeyword, allowMultidimIndices);
		while (token == Token.PLUS || token == Token.MINUS) {
			Token op = token;
			String txt = text.toString();
			lexer();
			AST nextTerm = TERM(allowComparison, allowInKeyword, allowMultidimIndices);

			// Build the tree in left-associative manner
			term = new BinaryExpressionAst(term, op, txt, nextTerm);
		}
		return term;
	}

	// TERM : UNARY_FACTOR [ (*|/|%) TERM ]
	AST TERM(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices) throws IOException {
		AST unaryFactor = UNARY_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices);
		while (token == Token.MULT || token == Token.DIVIDE || token == Token.MOD) {
			Token op = token;
			String txt = text.toString();
			lexer();
			AST nextUnaryFactor = UNARY_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices);

			// Build the tree in left-associative manner
			unaryFactor = new BinaryExpressionAst(unaryFactor, op, txt, nextUnaryFactor);
		}
		return unaryFactor;
	}

	// UNARY_FACTOR : [ ! | - | + ] POWER_FACTOR
	AST UNARY_FACTOR(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		if (token == Token.NOT) {
			lexer();
			return new NotExpressionAst(POWER_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices));
		} else if (token == Token.MINUS) {
			lexer();
			return new NegativeExpressionAst(POWER_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices));
		} else if (token == Token.PLUS) {
			lexer();
			return new UnaryPlusExpressionAst(POWER_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices));
		} else {
			return POWER_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices);
		}
	}

	// POWER_FACTOR : FACTOR_FOR_INCDEC [ ^ POWER_FACTOR ]
	AST POWER_FACTOR(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		AST incdecAst = FACTOR_FOR_INCDEC(allowComparison, allowInKeyword, allowMultidimIndices);
		if (token == Token.POW) {
			Token op = token;
			String txt = text.toString();
			lexer();
			AST term = POWER_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices);

			return new BinaryExpressionAst(incdecAst, op, txt, term);
		}
		return incdecAst;
	}

	// according to the spec, pre/post inc can occur
	// only on lvalues, which are NAMES (IDs), array,
	// or field references
	private boolean isLvalue(AST ast) {
		return (ast instanceof IDAst) || (ast instanceof ArrayReferenceAst) || (ast instanceof DollarExpressionAst);
	}

	AST FACTOR_FOR_INCDEC(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices)
			throws IOException {
		boolean preInc = false;
		boolean preDec = false;
		boolean postInc = false;
		boolean postDec = false;
		if (token == Token.INC) {
			preInc = true;
			lexer();
		} else if (token == Token.DEC) {
			preDec = true;
			lexer();
		}

		AST factorAst = FACTOR(allowComparison, allowInKeyword, allowMultidimIndices);

		if ((preInc || preDec) && !isLvalue(factorAst)) {
			throw parserException("Cannot pre inc/dec a non-lvalue");
		}

		// only do post ops if:
		// - factorAst is an lvalue
		// - pre ops were not encountered
		if (isLvalue(factorAst) && !preInc && !preDec) {
			if (token == Token.INC) {
				postInc = true;
				lexer();
			} else if (token == Token.DEC) {
				postDec = true;
				lexer();
			}
		}

		if ((preInc || preDec) && (postInc || postDec)) {
			throw parserException("Cannot do pre inc/dec Token.AND post inc/dec.");
		}

		if (preInc) {
			return new PreIncAst(factorAst);
		} else if (preDec) {
			return new PreDecAst(factorAst);
		} else if (postInc) {
			return new PostIncAst(factorAst);
		} else if (postDec) {
			return new PostDecAst(factorAst);
		} else {
			return factorAst;
		}
	}

	// FACTOR : '(' ASSIGNMENT_EXPRESSION ')' | Token.INTEGER | Token.DOUBLE | Token.STRING | GETLINE
	// [Token.ID-or-array-or-$val] | /[=].../
	// | [++|--] SYMBOL [++|--]
	// AST FACTOR(boolean allowComparison, boolean allowInKeyword, boolean allow_post_incdec_operators)
	AST FACTOR(boolean allowComparison, boolean allowInKeyword, boolean allowMultidimIndices) throws IOException {
		if (token == Token.OPEN_PAREN) {
			lexer();
			// true = allow multi-dimensional array indices (i.e., commas for 1,2,3,4)
			AST assignmentExpression = ASSIGNMENT_EXPRESSION(true, allowInKeyword, true);
			if (allowMultidimIndices && (assignmentExpression instanceof ArrayIndexAst)) {
				throw parserException("Cannot nest multi-dimensional array index expressions.");
			}
			lexer(Token.CLOSE_PAREN);
			return assignmentExpression;
		} else if (token == Token.INTEGER) {
			AST integer = symbolTable.addINTEGER(text.toString());
			lexer();
			return integer;
		} else if (token == Token.DOUBLE) {
			AST dbl = symbolTable.addDOUBLE(text.toString());
			lexer();
			return dbl;
		} else if (token == Token.STRING) {
			AST str = symbolTable.addSTRING(string.toString());
			lexer();
			return str;
		} else if (token == Token.KW_GETLINE) {
			return GETLINE_EXPRESSION(null, allowComparison, allowInKeyword);
		} else if (token == Token.DIVIDE || token == Token.DIV_EQ) {
			readRegexp();
			if (token == Token.DIV_EQ) {
				regexp.insert(0, '=');
			}
			AST regexpAst = symbolTable.addREGEXP(regexp.toString());
			lexer();
			return regexpAst;
		} else {
			if (token == Token.DOLLAR) {
				lexer();
				if (token == Token.INC || token == Token.DEC) {
					return new DollarExpressionAst(
							FACTOR_FOR_INCDEC(allowComparison, allowInKeyword, allowMultidimIndices));
				}
				if (token == Token.NOT || token == Token.MINUS || token == Token.PLUS) {
					return new DollarExpressionAst(UNARY_FACTOR(allowComparison, allowInKeyword, allowMultidimIndices));
				}
				return new DollarExpressionAst(FACTOR(allowComparison, allowInKeyword, allowMultidimIndices));
			}
			return SYMBOL(allowComparison, allowInKeyword);
		}
	}

	// SYMBOL : Token.ID [ '(' params ')' | '[' ASSIGNMENT_EXPRESSION ']' ]
	AST SYMBOL(boolean allowComparison, boolean allowInKeyword) throws IOException {
		if (token != Token.ID && token != Token.FUNC_ID && token != Token.BUILTIN_FUNC_NAME && token != Token.EXTENSION) {
			throw parserException("Expecting an Token.ID. Got " + token.name() + ": " + text);
		}
		Token idToken = token;
		String id = text.toString();
		boolean parens = c == '(';
		lexer();

		if (idToken == Token.EXTENSION) {
			String extensionKeyword = id;
			// JawkExtension extension = extensions.get(extensionKeyword);
			AST params;

			/*
			 * if (extension.requiresParen()) {
			 * lexer(Token.OPEN_PAREN);
			 * if (token == Token.CLOSE_PAREN)
			 * params = null;
			 * else
			 * params = EXPRESSION_LIST(allowComparison, allowInKeyword);
			 * lexer(Token.CLOSE_PAREN);
			 * } else {
			 * boolean parens = c == '(';
			 * //expectKeyword("delete");
			 * if (parens) {
			 * assert token == Token.OPEN_PAREN;
			 * lexer();
			 * }
			 * //AST symbolAst = SYMBOL(true,true); // allow comparators
			 * params = EXPRESSION_LIST(allowComparison, allowInKeyword);
			 * if (parens)
			 * lexer(Token.CLOSE_PAREN);
			 * }
			 */

			// if (extension.requiresParens() || parens)
			if (parens) {
				lexer();
				if (token == Token.CLOSE_PAREN) {
					params = null;
				} else { // ?//params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
					params = EXPRESSION_LIST(true, allowInKeyword, false); // comparators allowed, allow in expression
				}
				lexer(Token.CLOSE_PAREN);
			} else {
				/*
				 * if (token == Token.NEWLINE || token == Token.SEMICOLON || token == Token.CLOSE_BRACE || token ==
				 * Token.CLOSE_PAREN
				 * || (token == Token.GT || token == Token.APPEND || token == Token.PIPE) )
				 * params = null;
				 * else
				 * params = EXPRESSION_LIST(false,true); // NO comparators allowed, allow in expression
				 */
				params = null;
			}

			return new ExtensionAst(extensionKeyword, params);
		} else if (idToken == Token.FUNC_ID || idToken == Token.BUILTIN_FUNC_NAME) {
			AST params;
			// length can take on the special form of no parens
			if (id.equals("length")) {
				if (token == Token.OPEN_PAREN) {
					lexer();
					if (token == Token.CLOSE_PAREN) {
						params = null;
					} else {
						params = EXPRESSION_LIST(true, allowInKeyword, false);
					}
					lexer(Token.CLOSE_PAREN);
				} else {
					params = null;
				}
			} else {
				lexer(Token.OPEN_PAREN);
				if (token == Token.CLOSE_PAREN) {
					params = null;
				} else {
					params = EXPRESSION_LIST(true, allowInKeyword, false);
				}
				lexer(Token.CLOSE_PAREN);
			}
			if (idToken == Token.BUILTIN_FUNC_NAME) {
				return new BuiltinFunctionCallAst(id, params);
			} else {
				return symbolTable.addFunctionCall(id, params);
			}
		}
		if (token == Token.OPEN_BRACKET) {
			lexer();
			AST idxAst = ARRAY_INDEX(true, allowInKeyword);
			lexer(Token.CLOSE_BRACKET);
			if (token == Token.OPEN_BRACKET) {
				throw parserException("Use [a,b,c,...] instead of [a][b][c]... for multi-dimensional arrays.");
			}
			return symbolTable.addArrayReference(id, idxAst);
		}
		return symbolTable.addID(id);
	}

	// ARRAY_INDEX : ASSIGNMENT_EXPRESSION [, ARRAY_INDEX]
	AST ARRAY_INDEX(boolean allowComparison, boolean allowInKeyword) throws IOException {
		AST exprAst = ASSIGNMENT_EXPRESSION(allowComparison, allowInKeyword, false);
		if (token == Token.COMMA) {
			optNewline();
			lexer();
			return new ArrayIndexAst(exprAst, ARRAY_INDEX(allowComparison, allowInKeyword));
		} else {
			return new ArrayIndexAst(exprAst, null);
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
		if (token == Token.OPEN_BRACE) {
			lexer();
			AST lst = STATEMENT_LIST();
			lexer(Token.CLOSE_BRACE);
			return lst;
		}
		AST stmt;
		if (token == Token.KW_IF) {
			stmt = IF_STATEMENT();
		} else if (token == Token.KW_WHILE) {
			stmt = WHILE_STATEMENT();
		} else if (token == Token.KW_FOR) {
			stmt = FOR_STATEMENT();
		} else {
			if (token == Token.KW_DO) {
				stmt = DO_STATEMENT();
			} else if (token == Token.KW_RETURN) {
				stmt = RETURN_STATEMENT();
			} else if (token == Token.KW_EXIT) {
				stmt = EXIT_STATEMENT();
			} else if (token == Token.KW_DELETE) {
				stmt = DELETE_STATEMENT();
			} else if (token == Token.KW_PRINT) {
				stmt = PRINT_STATEMENT();
			} else if (token == Token.KW_PRINTF) {
				stmt = PRINTF_STATEMENT();
			} else if (token == Token.KW_NEXT) {
				stmt = NEXT_STATEMENT();
			} else if (token == Token.KW_CONTINUE) {
				stmt = CONTINUE_STATEMENT();
			} else if (token == Token.KW_BREAK) {
				stmt = BREAK_STATEMENT();
			} else {
				stmt = EXPRESSION_STATEMENT(true, false); // allow in keyword, do Token.NOT allow non-statement ASTs
			}
			terminator();
			return stmt;
		}
		// NO TERMINATOR FOR IF, WHILE, Token.AND FOR
		// (leave it for absorption by the callee)
		return stmt;
	}

	AST EXPRESSION_STATEMENT(boolean allowInKeyword, boolean allowNonStatementAsts) throws IOException {
		// true = allow comparators
		// false = do Token.NOT allow multi-dimensional array indices
		// return new ExpressionStatementAst(ASSIGNMENT_EXPRESSION(true, allowInKeyword, false));

		AST exprAst = ASSIGNMENT_EXPRESSION(true, allowInKeyword, false);
		if (!allowNonStatementAsts && exprAst.hasFlag(AstFlag.NON_STATEMENT)) {
			throw parserException("Not a valid statement.");
		}
		return new ExpressionStatementAst(exprAst);
	}

	AST IF_STATEMENT() throws IOException {
		expectKeyword("if");
		lexer(Token.OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow in keyword, do Token.NOT allow
																													// multidim
																													// indices expressions
		lexer(Token.CLOSE_PAREN);

		//// Was:
		//// AST b1 = BLOCK_OR_STMT();
		//// But it didn't handle
		//// if ; else ...
		//// properly
		optNewline();
		AST b1;
		if (token == Token.SEMICOLON) {
			lexer();
			// consume the newline after the semicolon
			optNewline();
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
		// To accommodate, the ifStatement will continue to manage
		// statements, causing the original OPT_STATEMENT_LIST to relinquish
		// processing statements to this OPT_STATEMENT_LIST.

		optNewline();
		if (token == Token.KW_ELSE) {
			lexer();
			optNewline();
			AST b2 = BLOCK_OR_STMT();
			return new IfStatementAst(expr, b1, b2);
		} else {
			AST ifAst = new IfStatementAst(expr, b1, null);
			return ifAst;
		}
	}

	AST BREAK_STATEMENT() throws IOException {
		expectKeyword("break");
		return new BreakStatementAst();
	}

	AST BLOCK_OR_STMT() throws IOException {
		// default case, does Token.NOT consume (require) a terminator
		return BLOCK_OR_STMT(false);
	}

	AST BLOCK_OR_STMT(boolean requireTerminator) throws IOException {
		optNewline();
		AST block;
		// HIJACK BRACES HERE SINCE WE MAY Token.NOT HAVE A TERMINATOR AFTER THE CLOSING BRACE
		if (token == Token.OPEN_BRACE) {
			lexer();
			block = STATEMENT_LIST();
			lexer(Token.CLOSE_BRACE);
			return block;
		} else if (token == Token.SEMICOLON) {
			block = null;
		} else {
			block = STATEMENT();
			// NO TERMINATOR HERE!
		}
		if (requireTerminator) {
			terminator();
		}
		return block;
	}

	AST WHILE_STATEMENT() throws IOException {
		expectKeyword("while");
		lexer(Token.OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do Token.NOT allow
																													// multidim
																													// indices expressions
		lexer(Token.CLOSE_PAREN);
		AST block = BLOCK_OR_STMT();
		return new WhileStatementAst(expr, block);
	}

	AST FOR_STATEMENT() throws IOException {
		expectKeyword("for");
		AST expr1 = null;
		AST expr2 = null;
		AST expr3 = null;
		lexer(Token.OPEN_PAREN);
		expr1 = OPT_SIMPLE_STATEMENT(false); // false = "no in keyword allowed"

		// branch here if we expect a for(... in ...) statement
		if (token == Token.KW_IN) {
			if (expr1.ast1 == null || expr1.ast2 != null) {
				throw parserException("Invalid expression prior to 'in' statement. Got : " + expr1);
			}
			expr1 = expr1.ast1;
			// analyze expr1 to make sure it's a singleton IDAst
			if (!(expr1 instanceof IDAst)) {
				throw parserException("Expecting an Token.ID for 'in' statement. Got : " + expr1);
			}
			// in
			lexer();
			// id
			if (token != Token.ID) {
				throw parserException(
						"Expecting an ARRAY Token.ID for 'in' statement. Got " + token.name() + ": " + text);
			}
			String arrId = text.toString();

			// not an indexed array reference!
			AST arrayIdAst = symbolTable.addArrayID(arrId);

			lexer();
			// close paren ...
			lexer(Token.CLOSE_PAREN);
			AST block = BLOCK_OR_STMT();
			return new ForInStatementAst(expr1, arrayIdAst, block);
		}

		if (token == Token.SEMICOLON) {
			lexer();
			optNewline();
		} else {
			throw parserException("Expecting ;. Got " + token.name() + ": " + text);
		}
		if (token != Token.SEMICOLON) {
			expr2 = ASSIGNMENT_EXPRESSION(true, true, false); // allow comparators, allow IN keyword, do Token.NOT allow
																												// multidim
																												// indices expressions
		}
		if (token == Token.SEMICOLON) {
			lexer();
			optNewline();
		} else {
			throw parserException("Expecting ;. Got " + token.name() + ": " + text);
		}
		if (token != Token.CLOSE_PAREN) {
			expr3 = OPT_SIMPLE_STATEMENT(true); // true = "allow the in keyword"
		}
		lexer(Token.CLOSE_PAREN);
		AST block = BLOCK_OR_STMT();
		return new ForStatementAst(expr1, expr2, expr3, block);
	}

	AST OPT_SIMPLE_STATEMENT(boolean allowInKeyword) throws IOException {
		if (token == Token.SEMICOLON) {
			return null;
		} else if (token == Token.KW_DELETE) {
			return DELETE_STATEMENT();
		} else if (token == Token.KW_PRINT) {
			return PRINT_STATEMENT();
		} else if (token == Token.KW_PRINTF) {
			return PRINTF_STATEMENT();
		} else {
			// allow non-statement ASTs
			return EXPRESSION_STATEMENT(allowInKeyword, true);
		}
	}

	AST DELETE_STATEMENT() throws IOException {
		boolean parens = c == '(';
		expectKeyword("delete");
		if (parens) {
			assert token == Token.OPEN_PAREN;
			lexer();
		}
		AST symbolAst = SYMBOL(true, true); // allow comparators
		if (parens) {
			lexer(Token.CLOSE_PAREN);
		}

		return new DeleteStatementAst(symbolAst);
	}

	private static final class ParsedPrintStatement {

		private AST funcParams;
		private Token outputToken;
		private AST outputExpr;

		ParsedPrintStatement(AST funcParams, Token outputToken, AST outputExpr) {
			this.funcParams = funcParams;
			this.outputToken = outputToken;
			this.outputExpr = outputExpr;
		}

		public AST getFuncParams() {
			return funcParams;
		}

		public Token getOutputToken() {
			return outputToken;
		}

		public AST getOutputExpr() {
			return outputExpr;
		}
	}

	private ParsedPrintStatement parsePrintStatement(boolean parens) throws IOException {
		AST funcParams;
		Token outputToken;
		AST outputExpr;
		if (parens) {
			lexer();
			if (token == Token.CLOSE_PAREN) {
				funcParams = null;
			} else {
				funcParams = EXPRESSION_LIST(true, true, true); // '>' and 'in' allowed, but ',' forces new args
			}
			lexer(Token.CLOSE_PAREN);
		} else {
			if (token == Token.NEWLINE
					|| token == Token.SEMICOLON
					|| token == Token.CLOSE_BRACE
					|| token == Token.CLOSE_PAREN
					|| token == Token.GT
					|| token == Token.APPEND
					|| token == Token.PIPE) {
				funcParams = null;
			} else {
				funcParams = EXPRESSION_LIST(false, true, false); // NO comparators allowed, allow in expression
			}
		}
		if (token == Token.GT || token == Token.APPEND || token == Token.PIPE) {
			outputToken = token;
			lexer();
			outputExpr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do Token.NOT
			// allow multidim indices expressions
		} else {
			outputToken = null;
			outputExpr = null;
		}

		return new ParsedPrintStatement(funcParams, outputToken, outputExpr);
	}

	AST PRINT_STATEMENT() throws IOException {
		expectKeyword("print");
		boolean parens = token == Token.OPEN_PAREN;
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		AST params = parsedPrintStatement.getFuncParams();
		if (parens
				&& token == Token.QUESTION_MARK
				&& params instanceof FunctionCallParamListAst
				&& ((FunctionCallParamListAst) params).getAst2() == null) {
			AST condExpr = ((FunctionCallParamListAst) params).getAst1();
			lexer();
			AST trueBlock = TERNARY_EXPRESSION(true, true, true);
			lexer(Token.COLON);
			AST falseBlock = TERNARY_EXPRESSION(true, true, true);
			params = new FunctionCallParamListAst(
					new TernaryExpressionAst(condExpr, trueBlock, falseBlock),
					null);
		}

		return new PrintAst(
				params,
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST PRINTF_STATEMENT() throws IOException {
		expectKeyword("printf");
		boolean parens = token == Token.OPEN_PAREN;
		ParsedPrintStatement parsedPrintStatement = parsePrintStatement(parens);

		AST params = parsedPrintStatement.getFuncParams();
		if (parens
				&& token == Token.QUESTION_MARK
				&& params instanceof FunctionCallParamListAst
				&& ((FunctionCallParamListAst) params).getAst2() == null) {
			AST condExpr = ((FunctionCallParamListAst) params).getAst1();
			lexer();
			AST trueBlock = TERNARY_EXPRESSION(true, true, true);
			lexer(Token.COLON);
			AST falseBlock = TERNARY_EXPRESSION(true, true, true);
			params = new FunctionCallParamListAst(
					new TernaryExpressionAst(condExpr, trueBlock, falseBlock),
					null);
		}

		return new PrintfAst(
				params,
				parsedPrintStatement.getOutputToken(),
				parsedPrintStatement.getOutputExpr());
	}

	AST GETLINE_EXPRESSION(AST pipeExpr, boolean allowComparison, boolean allowInKeyword) throws IOException {
		expectKeyword("getline");
		AST lvalue = LVALUE(allowComparison, allowInKeyword);
		if (token == Token.LT) {
			lexer();
			AST assignmentExpr = ASSIGNMENT_EXPRESSION(allowComparison, allowInKeyword, false); // do Token.NOT allow multidim
																																													// indices expressions
			return pipeExpr == null ?
					new GetlineAst(null, lvalue, assignmentExpr) : new GetlineAst(pipeExpr, lvalue, assignmentExpr);
		} else {
			return pipeExpr == null ? new GetlineAst(null, lvalue, null) : new GetlineAst(pipeExpr, lvalue, null);
		}
	}

	AST LVALUE(boolean allowComparison, boolean allowInKeyword) throws IOException {
		// false = do Token.NOT allow multi dimension indices expressions
		if (token == Token.DOLLAR) {
			return FACTOR(allowComparison, allowInKeyword, false);
		}
		if (token == Token.ID) {
			return FACTOR(allowComparison, allowInKeyword, false);
		}
		return null;
	}

	AST DO_STATEMENT() throws IOException {
		expectKeyword("do");
		optNewline();
		AST block = BLOCK_OR_STMT();
		if (token == Token.SEMICOLON) {
			lexer();
		}
		optNewline();
		expectKeyword("while");
		lexer(Token.OPEN_PAREN);
		AST expr = ASSIGNMENT_EXPRESSION(true, true, false); // true = allow comparators, allow IN keyword, do Token.NOT
																													// allow
																													// multidim indices expressions
		lexer(Token.CLOSE_PAREN);
		return new DoStatementAst(block, expr);
	}

	AST RETURN_STATEMENT() throws IOException {
		expectKeyword("return");
		if (token == Token.SEMICOLON || token == Token.NEWLINE || token == Token.CLOSE_BRACE) {
			return new ReturnStatementAst(null);
		} else {
			return new ReturnStatementAst(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																								// keyword, do Token.NOT allow multidim
																																								// indices expressions
		}
	}

	AST EXIT_STATEMENT() throws IOException {
		expectKeyword("exit");
		if (token == Token.SEMICOLON || token == Token.NEWLINE || token == Token.CLOSE_BRACE) {
			return new ExitStatementAst(null);
		} else {
			return new ExitStatementAst(ASSIGNMENT_EXPRESSION(true, true, false)); // true = allow comparators, allow IN
																																							// keyword, do Token.NOT allow multidim
																																							// indices
																																							// expressions
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
			throw parserException("Expecting " + keyword + ". Got " + token.name() + ": " + text);
		}
	}

	// parser
	// ===============================================================================
	// AST class defs
	private abstract class AST extends AstNode {

		private final String sourceDescription = scriptSources.get(scriptSourcesCurrentIndex).getDescription();
		private final int lineNo = reader.getLineNumber() + 1;
		private AST parent;
		private AST ast1, ast2, ast3, ast4;
		private final EnumSet<AstFlag> flags = EnumSet.noneOf(AstFlag.class);

		protected final void addFlag(AstFlag flag) {
			flags.add(flag);
		}

		protected final boolean hasFlag(AstFlag flag) {
			return flags.contains(flag);
		}

		protected Address breakAddress() {
			return null;
		}

		protected Address continueAddress() {
			return null;
		}

		protected Address nextAddress() {
			return null;
		}

		protected Address returnAddress() {
			return null;
		}

		protected final AST getParent() {
			return parent;
		}

		@SuppressWarnings("unused")
		protected final void setParent(AST p) {
			parent = p;
		}

		protected final AST getAst1() {
			return ast1;
		}

		@SuppressWarnings("unused")
		protected final void setAst1(AST a1) {
			ast1 = a1;
		}

		protected final AST getAst2() {
			return ast2;
		}

		@SuppressWarnings("unused")
		protected final void setAst2(AST a2) {
			ast2 = a2;
		}

		protected final AST getAst3() {
			return ast3;
		}

		@SuppressWarnings("unused")
		protected final void setAst3(AST a3) {
			ast3 = a3;
		}

		protected final AST getAst4() {
			return ast4;
		}

		@SuppressWarnings("unused")
		protected final void setAst4(AST a4) {
			ast4 = a4;
		}

		protected final AST searchFor(AstFlag flag) {
			AST ptr = this;
			while (ptr != null) {
				if (ptr.hasFlag(flag)) {
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

		private boolean isBegin = isBegin();

		@SuppressWarnings("unused")
		protected final boolean isBeginFlag() {
			return isBegin;
		}

		protected final void setBeginFlag(boolean flag) {
			isBegin = flag;
		}

		private boolean isBegin() {
			boolean result = isBegin;
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

		private boolean isEnd = isEnd();

		@SuppressWarnings("unused")
		protected final boolean isEndFlag() {
			return isEnd;
		}

		protected final void setEndFlag(boolean flag) {
			isEnd = flag;
		}

		private boolean isEnd() {
			boolean result = isEnd;
			if (!result && ast1 != null) {
				result = ast1.isEnd();
			}
			if (!result && ast2 != null) {
				result = ast2.isEnd();
			}
			if (!result && ast3 != null) {
				result = ast3.isEnd();
			}
			if (!result && getAst4() != null) {
				result = getAst4().isEnd();
			}
			return result;
		}

		private boolean isFunction = isFunction();

		@SuppressWarnings("unused")
		protected final boolean isFunctionFlag() {
			return isFunction;
		}

		protected final void setFunctionFlag(boolean flag) {
			isFunction = flag;
		}

		private boolean isFunction() {
			boolean result = isFunction;
			if (!result && getAst1() != null) {
				result = getAst1().isFunction();
			}
			if (!result && getAst2() != null) {
				result = getAst2().isFunction();
			}
			if (!result && getAst3() != null) {
				result = getAst3().isFunction();
			}
			if (!result && getAst4() != null) {
				result = getAst4().isFunction();
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
		if (ast.getAst1() == null) {
			return false;
		}

		if (!containsASTType(ast.getAst1(), ExtensionAst.class)) {
			return false;
		}

		if (containsASTType(ast.getAst1(), new Class[] { FunctionCallAst.class, DollarExpressionAst.class })) {
			return false;
		}

		return true;
	}

	private static boolean containsASTType(AST ast, Class<?> cls) {
		return containsASTType(ast, new Class[] { cls });
	}

	private static boolean containsASTType(AST ast, Class<?>[] clsArray) {
		if (ast == null) {
			return false;
		}
		for (Class<?> cls : clsArray) {
			if (cls.isInstance(ast)) {
				return true;
			}
		}
		// prettier-ignore
		return containsASTType(ast.getAst1(), clsArray)
				|| containsASTType(ast.getAst2(), clsArray)
				|| containsASTType(ast.getAst3(), clsArray)
				|| containsASTType(ast.getAst4(), clsArray);
	}

	private Address nextAddress;

	private final class RuleListAst extends AST {

		private RuleListAst(AST rule, AST rest) {
			super(rule, rest);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {

			pushSourceLineNumber(tuples);

			nextAddress = tuples.createAddress("nextAddress");

			// goto start address
			Address startAddress = tuples.createAddress("start address");
			tuples.gotoAddress(startAddress);

			AST ptr;

			// compile functions
			ptr = this;
			while (ptr != null) {
				if (ptr.getAst1() != null && ptr.getAst1().isFunction()) {
					assert ptr.getAst1() != null;
					int ast1Count = ptr.getAst1().populateTuples(tuples);
					assert ast1Count == 0;
				}

				ptr = ptr.getAst2();
			}

			// START OF MAIN BLOCK
			tuples.address(startAddress);

			// initialize special variables
			IDAst nrAst = symbolTable.getID("NR");
			IDAst fnrAst = symbolTable.getID("FNR");
			IDAst nfAst = symbolTable.getID("NF");
			IDAst fsAst = symbolTable.getID("FS");
			IDAst rsAst = symbolTable.getID("RS");
			IDAst ofsAst = symbolTable.getID("OFS");
			IDAst orsAst = symbolTable.getID("ORS");
			IDAst rstartAst = symbolTable.getID("RSTART");
			IDAst rlengthAst = symbolTable.getID("RLENGTH");
			IDAst filenameAst = symbolTable.getID("FILENAME");
			IDAst subsepAst = symbolTable.getID("SUBSEP");
			IDAst convfmtAst = symbolTable.getID("CONVFMT");
			IDAst ofmtAst = symbolTable.getID("OFMT");
			IDAst environAst = symbolTable.getID("ENVIRON");
			IDAst argcAst = symbolTable.getID("ARGC");
			IDAst argvAst = symbolTable.getID("ARGV");

			// MUST BE DONE AFTER FUNCTIONS ARE COMPILED,
			// and after special variables are made known to the symbol table
			// (see above)!
			tuples.setNumGlobals(symbolTable.numGlobals());

			tuples.nfOffset(nfAst.offset);
			tuples.nrOffset(nrAst.offset);
			tuples.fnrOffset(fnrAst.offset);
			tuples.fsOffset(fsAst.offset);
			tuples.rsOffset(rsAst.offset);
			tuples.ofsOffset(ofsAst.offset);
			tuples.orsOffset(orsAst.offset);
			tuples.rstartOffset(rstartAst.offset);
			tuples.rlengthOffset(rlengthAst.offset);
			tuples.filenameOffset(filenameAst.offset);
			tuples.subsepOffset(subsepAst.offset);
			tuples.convfmtOffset(convfmtAst.offset);
			tuples.ofmtOffset(ofmtAst.offset);
			tuples.environOffset(environAst.offset);
			tuples.argcOffset(argcAst.offset);
			tuples.argvOffset(argvAst.offset);

			Address exitAddr = tuples.createAddress("end blocks start address");
			tuples.setExitAddress(exitAddr);

			// grab all BEGINs
			ptr = this;
			// ptr.getAst1() == blank rule condition (i.e.: { print })
			while (ptr != null) {
				if (ptr.getAst1() != null && ptr.getAst1().isBegin()) {
					ptr.getAst1().populateTuples(tuples);
				}

				ptr = ptr.getAst2();
			}

			// Do we have rules? (apart from BEGIN)
			// If we have rules or END, we need to parse the input
			boolean reqInput = false;

			// Check for "normal" rules
			ptr = this;
			while (!reqInput && (ptr != null)) {
				if (isRule(ptr.getAst1())) {
					reqInput = true;
				}
				ptr = ptr.getAst2();
			}

			// Now check for "END" rules
			ptr = this;
			while (!reqInput && (ptr != null)) {
				if (ptr.getAst1() != null && ptr.getAst1().isEnd()) {
					reqInput = true;
				}
				ptr = ptr.getAst2();
			}

			if (reqInput) {
				Address inputLoopAddress = null;
				Address noMoreInput = null;

				inputLoopAddress = tuples.createAddress("input_loop_address");
				tuples.address(inputLoopAddress);

				ptr = this;

				noMoreInput = tuples.createAddress("no_more_input");
				tuples.consumeInput(noMoreInput);

				// grab all INPUT RULES
				while (ptr != null) {
					// the first one of these is an input rule
					if (isRule(ptr.getAst1())) {
						ptr.getAst1().populateTuples(tuples);
					}
					ptr = ptr.getAst2();
				}
				tuples.address(nextAddress);

				tuples.gotoAddress(inputLoopAddress);

				if (reqInput) {
					tuples.address(noMoreInput);
					// compiler has issue with missing nop here
					tuples.nop();
				}
			}

			// indicate where the first end block resides
			// in the event of an exit statement
			tuples.address(exitAddr);
			tuples.setWithinEndBlocks(true);

			// grab all ENDs
			ptr = this;
			while (ptr != null) {
				if (ptr.getAst1() != null && ptr.getAst1().isEnd()) {
					ptr.getAst1().populateTuples(tuples);
				}
				ptr = ptr.getAst2();
			}

			// force a nop here to resolve any addresses that haven't been resolved yet
			// (i.e., no_more_input wouldn't be resolved if there are no END{} blocks)
			tuples.nop();

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ExpressionToEvaluateAst extends AST {

		private ExpressionToEvaluateAst(AST expr) {
			super(expr);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {

			pushSourceLineNumber(tuples);
			nextAddress = tuples.createAddress("nextAddress");

			// goto start address
			Address startAddress = tuples.createAddress("start address");
			tuples.gotoAddress(startAddress);

			// START OF MAIN BLOCK
			tuples.address(startAddress);

			// initialize special variables
			IDAst nrAst = symbolTable.getID("NR");
			IDAst fnrAst = symbolTable.getID("FNR");
			IDAst nfAst = symbolTable.getID("NF");
			IDAst fsAst = symbolTable.getID("FS");
			IDAst rsAst = symbolTable.getID("RS");
			IDAst subsepAst = symbolTable.getID("SUBSEP");
			IDAst convfmtAst = symbolTable.getID("CONVFMT");
			IDAst environAst = symbolTable.getID("ENVIRON");

			// MUST BE DONE AFTER FUNCTIONS ARE COMPILED,
			// and after special variables are made known to the symbol table
			// (see above)!
			tuples.setNumGlobals(symbolTable.numGlobals());

			tuples.nfOffset(nfAst.offset);
			tuples.nrOffset(nrAst.offset);
			tuples.fnrOffset(fnrAst.offset);
			tuples.fsOffset(fsAst.offset);
			tuples.rsOffset(rsAst.offset);
			tuples.subsepOffset(subsepAst.offset);
			tuples.convfmtOffset(convfmtAst.offset);
			tuples.environOffset(environAst.offset);

			tuples.setInputForEval();

			if (getAst1() != null) {
				getAst1().populateTuples(tuples);
			}
			tuples.nop();

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// made non-static to access the "nextAddress" field of the frontend
	private final class RuleAst extends AST {

		private RuleAst(AST optExpression, AST optRule) {
			super(optExpression, optRule);
			addFlag(AstFlag.NEXTABLE);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (getAst1() == null) {
				// just indicate to execute the rule
				tuples.push(1); // 1 == true
			} else {
				int result = getAst1().populateTuples(tuples);
				assert result == 1;
			}
			// result of whether to execute or not is on the stack
			Address bypassRule = tuples.createAddress("bypassRule");
			tuples.ifFalse(bypassRule);
			// execute the optRule here!
			if (getAst2() == null) {
				if (getAst1() == null || (!getAst1().isBegin() && !getAst1().isEnd())) {
					// display $0
					tuples.print(0);
				}
				// else, don't populate it with anything
				// (i.e., blank BEGIN/END rule)
			} else {
				// execute it, and leave nothing on the stack
				int ast2Count = getAst2().populateTuples(tuples);
				assert ast2Count == 0;
			}
			tuples.address(bypassRule).nop();
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public Address nextAddress() {
			if (!isRule(this)) {
				throw new SemanticException("Must call next within an input rule.");
			}
			if (nextAddress == null) {
				throw new SemanticException("Cannot call next here.");
			}
			return nextAddress;
		}
	}

	private final class IfStatementAst extends AST {

		private IfStatementAst(AST expr, AST b1, AST b2) {
			super(expr, b1, b2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;

			Address elseblock = tuples.createAddress("elseblock");

			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			tuples.ifFalse(elseblock);
			if (getAst2() != null) {
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 0;
			}
			if (getAst3() == null) {
				tuples.address(elseblock);
			} else {
				Address end = tuples.createAddress("end");
				tuples.gotoAddress(end);
				tuples.address(elseblock);
				int ast3Result = getAst3().populateTuples(tuples);
				assert ast3Result == 0;
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
			assert getAst1() != null;
			assert getAst2() != null;
			assert getAst3() != null;

			Address elseexpr = tuples.createAddress("elseexpr");
			Address endTertiary = tuples.createAddress("endTertiary");

			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			tuples.ifFalse(elseexpr);
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;
			tuples.gotoAddress(endTertiary);

			tuples.address(elseexpr);
			int ast3Result = getAst3().populateTuples(tuples);
			assert ast3Result == 1;

			tuples.address(endTertiary);

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class WhileStatementAst extends AST {

		private Address breakAddress;
		private Address continueAddress;

		private WhileStatementAst(AST expr, AST block) {
			super(expr, block);
			addFlag(AstFlag.BREAKABLE);
			addFlag(AstFlag.CONTINUEABLE);
		}

		@Override
		public Address breakAddress() {
			assert breakAddress != null;
			return breakAddress;
		}

		@Override
		public Address continueAddress() {
			assert continueAddress != null;
			return continueAddress;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			breakAddress = tuples.createAddress("breakAddress");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for while statements, the start-of-loop is the continue jump address
			continueAddress = loop;

			// condition
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			tuples.ifFalse(breakAddress);

			if (getAst2() != null) {
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 0;
			}

			tuples.gotoAddress(loop);

			tuples.address(breakAddress);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DoStatementAst extends AST {

		private Address breakAddress;
		private Address continueAddress;

		private DoStatementAst(AST block, AST expr) {
			super(block, expr);
			addFlag(AstFlag.BREAKABLE);
			addFlag(AstFlag.CONTINUEABLE);
		}

		@Override
		public Address breakAddress() {
			assert breakAddress != null;
			return breakAddress;
		}

		@Override
		public Address continueAddress() {
			assert continueAddress != null;
			return continueAddress;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			breakAddress = tuples.createAddress("breakAddress");
			continueAddress = tuples.createAddress("continueAddress");

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (getAst1() != null) {
				int ast1Result = getAst1().populateTuples(tuples);
				assert ast1Result == 0;
			}

			// for do-while statements, the continue jump address is the loop condition
			tuples.address(continueAddress);

			// condition
			assert getAst2() != null;
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;
			tuples.ifTrue(loop);

			// tuples.gotoAddress(loop);

			tuples.address(breakAddress);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForStatementAst extends AST {

		private Address breakAddress;
		private Address continueAddress;

		private ForStatementAst(AST expr1, AST expr2, AST expr3, AST block) {
			super(expr1, expr2, expr3, block);
			addFlag(AstFlag.BREAKABLE);
			addFlag(AstFlag.CONTINUEABLE);
		}

		@Override
		public Address breakAddress() {
			assert breakAddress != null;
			return breakAddress;
		}

		@Override
		public Address continueAddress() {
			assert continueAddress != null;
			return continueAddress;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			breakAddress = tuples.createAddress("breakAddress");
			continueAddress = tuples.createAddress("continueAddress");

			// initial actions
			if (getAst1() != null) {
				int ast1Result = getAst1().populateTuples(tuples);
				for (int i = 0; i < ast1Result; i++) {
					tuples.pop();
				}
			}
			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			if (getAst2() != null) {
				// condition
				// assert(getAst2() != null);
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 1;
				tuples.ifFalse(breakAddress);
			}

			if (getAst4() != null) {
				// post loop action
				int ast4Result = getAst4().populateTuples(tuples);
				assert ast4Result == 0;
			}

			// for for-loops, the continue jump address is the post-loop-action
			tuples.address(continueAddress);

			// post-loop action
			if (getAst3() != null) {
				int ast3Result = getAst3().populateTuples(tuples);
				for (int i = 0; i < ast3Result; i++) {
					tuples.pop();
				}
			}

			tuples.gotoAddress(loop);

			tuples.address(breakAddress);

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class ForInStatementAst extends AST {

		private Address breakAddress;
		private Address continueAddress;

		private ForInStatementAst(AST keyIdAst, AST arrayIdAst, AST block) {
			super(keyIdAst, arrayIdAst, block);
			addFlag(AstFlag.BREAKABLE);
			addFlag(AstFlag.CONTINUEABLE);
		}

		@Override
		public Address breakAddress() {
			assert breakAddress != null;
			return breakAddress;
		}

		@Override
		public Address continueAddress() {
			assert continueAddress != null;
			return continueAddress;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			assert getAst2() != null;

			IDAst arrayIdAst = (IDAst) getAst2();
			if (arrayIdAst.isScalar()) {
				throw new SemanticException(arrayIdAst + " is not an array");
			}
			arrayIdAst.setArray(true);

			breakAddress = tuples.createAddress("breakAddress");

			getAst2().populateTuples(tuples);
			// pops the array and pushes the keyset
			tuples.keylist();

			// stack now contains:
			// keylist

			// LOOP
			Address loop = tuples.createAddress("loop");
			tuples.address(loop);

			// for for-in loops, the continue jump address is the start-of-loop address
			continueAddress = loop;

			assert tuples.checkClass(Deque.class);

			// condition
			tuples.dup();
			tuples.isEmptyList(breakAddress);

			assert tuples.checkClass(Deque.class);

			// take an element off the set
			tuples.dup();
			tuples.getFirstAndRemoveFromList();
			// assign it to the id
			tuples.assign(((IDAst) getAst1()).offset, ((IDAst) getAst1()).isGlobal);
			tuples.pop(); // remove the assignment result

			if (getAst3() != null) {
				// execute the block
				int ast3Result = getAst3().populateTuples(tuples);
				assert ast3Result == 0;
			}
			// otherwise, there is no block to execute

			assert tuples.checkClass(Deque.class);

			tuples.gotoAddress(loop);

			tuples.address(breakAddress);
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
			int exprCount = getAst1().populateTuples(tuples);
			if (exprCount == 1) {
				tuples.pop();
			} else if (exprCount != 0) {
				assert false : "exprCount = " + exprCount;
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class AssignmentExpressionAst extends ScalarExpressionAst {

		/** operand / operator */
		private Token op;
		private String text;

		private AssignmentExpressionAst(AST lhs, Token op, String text, AST rhs) {
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
			assert getAst2() != null;
			int ast2Count = getAst2().populateTuples(tuples);
			assert ast2Count == 1;
			// here, stack contains one value
			if (getAst1() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst1();
				if (idAst.isArray()) {
					throw new SemanticException("Cannot use " + idAst + " as a scalar. It is an array.");
				}
				idAst.setScalar(true);
				if (op == Token.EQUALS) {
					// Expected side effect:
					// Upon assignment, if the var is RS, reapply RS to input streams.
					tuples.assign(idAst.offset, idAst.isGlobal);
				} else if (op == Token.PLUS_EQ) {
					tuples.plusEq(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MINUS_EQ) {
					tuples.minusEq(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MULT_EQ) {
					tuples.multEq(idAst.offset, idAst.isGlobal);
				} else if (op == Token.DIV_EQ) {
					tuples.divEq(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MOD_EQ) {
					tuples.modEq(idAst.offset, idAst.isGlobal);
				} else if (op == Token.POW_EQ) {
					tuples.powEq(idAst.offset, idAst.isGlobal);
				} else {
					throw new Error("Unhandled op: " + op + " / " + text);
				}
				if (idAst.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (getAst1() instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr = (ArrayReferenceAst) getAst1();
				// push the index
				assert arr.getAst2() != null;
				int arrAst2Result = arr.getAst2().populateTuples(tuples);
				assert arrAst2Result == 1;
				// push the array ref itself
				IDAst idAst = (IDAst) arr.getAst1();
				if (idAst.isScalar()) {
					throw new SemanticException("Cannot use " + idAst + " as an array. It is a scalar.");
				}
				idAst.setArray(true);
				if (op == Token.EQUALS) {
					tuples.assignArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.PLUS_EQ) {
					tuples.plusEqArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MINUS_EQ) {
					tuples.minusEqArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MULT_EQ) {
					tuples.multEqArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.DIV_EQ) {
					tuples.divEqArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.MOD_EQ) {
					tuples.modEqArray(idAst.offset, idAst.isGlobal);
				} else if (op == Token.POW_EQ) {
					tuples.powEqArray(idAst.offset, idAst.isGlobal);
				} else {
					throw new NotImplementedError("Unhandled op: " + op + " / " + text + " for arrays.");
				}
			} else if (getAst1() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst1();
				assert dollarExpr.getAst1() != null;
				int ast1Result = dollarExpr.getAst1().populateTuples(tuples);
				assert ast1Result == 1;
				// stack contains eval of dollar arg

				if (op == Token.EQUALS) {
					tuples.assignAsInputField();
				} else if (op == Token.PLUS_EQ) {
					tuples.plusEqInputField();
				} else if (op == Token.MINUS_EQ) {
					tuples.minusEqInputField();
				} else if (op == Token.MULT_EQ) {
					tuples.multEqInputField();
				} else if (op == Token.DIV_EQ) {
					tuples.divEqInputField();
				} else if (op == Token.MOD_EQ) {
					tuples.modEqInputField();
				} else if (op == Token.POW_EQ) {
					tuples.powEqInputField();
				} else {
					throw new NotImplementedError("Unhandled op: " + op + " / " + text + " for dollar expressions.");
				}
			} else {
				throw new SemanticException("Cannot perform an assignment on: " + getAst1());
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
			assert getAst1() != null;
			assert getAst2() != null;
			if (!(getAst2() instanceof IDAst)) {
				throw new SemanticException("Expecting an array for rhs of IN. Got an expression.");
			}
			IDAst arrAst = (IDAst) getAst2();
			if (arrAst.isScalar()) {
				throw new SemanticException("Expecting an array for rhs of IN. Got a scalar.");
			}
			arrAst.setArray(true);

			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;

			int ast2Result = arrAst.populateTuples(tuples);
			assert ast2Result == 1;

			tuples.isIn();

			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ComparisonExpressionAst extends ScalarExpressionAst {

		/**
		 * operand / operator
		 */
		private Token op;
		private String text;

		private ComparisonExpressionAst(AST lhs, Token op, String text, AST rhs) {
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
			assert getAst1() != null;
			assert getAst2() != null;

			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;

			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;

			// 2 values on the stack

			if (op == Token.EQ) {
				tuples.cmpEq();
			} else if (op == Token.NE) {
				tuples.cmpEq();
				tuples.not();
			} else if (op == Token.LT) {
				tuples.cmpLt();
			} else if (op == Token.GT) {
				tuples.cmpGt();
			} else if (op == Token.LE) {
				tuples.cmpGt();
				tuples.not();
			} else if (op == Token.GE) {
				tuples.cmpLt();
				tuples.not();
			} else if (op == Token.MATCHES) {
				tuples.matches();
			} else if (op == Token.NOT_MATCHES) {
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
		private Token op;
		private String text;

		private LogicalExpressionAst(AST lhs, Token op, String text, AST rhs) {
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
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			tuples.dup();
			if (op == Token.OR) {
				// shortCircuit when op is Token.OR and 1st arg is true
				tuples.ifTrue(end);
			} else if (op == Token.AND) {
				tuples.ifFalse(end);
			} else {
				assert false : "Invalid op: " + op + " / " + text;
			}
			tuples.pop();
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;

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
		private Token op;
		private String text;

		private BinaryExpressionAst(AST lhs, Token op, String text, AST rhs) {
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
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;
			if (op == Token.PLUS) {
				tuples.add();
			} else if (op == Token.MINUS) {
				tuples.subtract();
			} else if (op == Token.MULT) {
				tuples.multiply();
			} else if (op == Token.DIVIDE) {
				tuples.divide();
			} else if (op == Token.MOD) {
				tuples.mod();
			} else if (op == Token.POW) {
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
			assert getAst1() != null;
			int lhsCount = getAst1().populateTuples(tuples);
			assert lhsCount == 1;
			assert getAst2() != null;
			int rhsCount = getAst2().populateTuples(tuples);
			assert rhsCount == 1;
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
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
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
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
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
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
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
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			tuples.getInputField();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ArrayIndexAst extends ScalarExpressionAst {

		private ArrayIndexAst(AST exprAst, AST next) {
			super(exprAst, next);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			AST ptr = this;
			int cnt = 0;
			while (ptr != null) {
				assert ptr.getAst1() != null;
				int ptrAst1Result = ptr.getAst1().populateTuples(tuples);
				assert ptrAst1Result == 1;
				++cnt;
				ptr = ptr.getAst2();
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

		private StatementListAst(AST statementAst, AST rest) {
			super(statementAst, rest);
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
			assert getAst1() != null;
			int ast1Count = getAst1().populateTuples(tuples);
			assert ast1Count == 0;
			if (getAst2() != null) {
				int ast2Count = getAst2().populateTuples(tuples);
				assert ast2Count == 0;
			}
			popSourceLineNumber(tuples);
			return 0;
		}

		@Override
		public String toString() {
			return super.toString() + " <" + getAst1() + ">";
		}
	}

	// made non-static to access the symbol table
	private final class FunctionDefAst extends AST {

		private String id;
		private Address functionAddress;
		private Address returnAddress;

		@Override
		public Address returnAddress() {
			assert returnAddress != null;
			return returnAddress;
		}

		private FunctionDefAst(String id, AST params, AST funcBody) {
			super(params, funcBody);
			this.id = id;
			setFunctionFlag(true);
			addFlag(AstFlag.RETURNABLE);
		}

		public Address getAddress() {
			assert functionAddress != null;
			return functionAddress;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			functionAddress = tuples.createAddress("function: " + id);
			returnAddress = tuples.createAddress("returnAddress for " + id);

			// annotate the tuple list
			// (useful for compilation,
			// not necessary for interpretation)
			tuples.function(id, paramCount());

			// functionAddress refers to first function body statement
			// rather than to function def opcode because during
			// interpretation, the function definition is a nop,
			// and for compilation, the next match of the function
			// name can be used
			tuples.address(functionAddress);

			// the stack contains the parameters to the function call (in rev order, which is good)

			// execute the body
			// (function body could be empty [no statements])
			if (getAst2() != null) {
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 0 || ast2Result == 1;
			}

			tuples.address(returnAddress);

			tuples.returnFromFunction();

			/////////////////////////////////////////////

			popSourceLineNumber(tuples);
			return 0;
		}

		int paramCount() {
			AST ptr = getAst1();
			int count = 0;
			while (ptr != null) {
				++count;
				ptr = ptr.getAst1();
			}
			return count;
		}

		void checkActualToFormalParameters(AST actualParamList) {
			AST aPtr = actualParamList;
			FunctionDefParamListAst fPtr = (FunctionDefParamListAst) getAst1();
			while (aPtr != null) {
				// actual parameter
				AST aparam = aPtr.getAst1();
				// formal function parameter
				AST fparam = symbolTable.getFunctionParameterIDAST(id, fPtr.id);

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
				if (aparam instanceof IDAst) {
					IDAst aparamIdAst = (IDAst) aparam;
					if (fparam.isScalar()) {
						aparamIdAst.setScalar(true);
					}
					if (fparam.isArray()) {
						aparamIdAst.setArray(true);
					}
				}
				// next
				aPtr = aPtr.getAst2();
				fPtr = (FunctionDefParamListAst) fPtr.getAst1();
			}
		}
	}

	private final class FunctionCallAst extends ScalarExpressionAst {

		private FunctionProxy functionProxy;

		private FunctionCallAst(FunctionProxy functionProxy, AST params) {
			super(params);
			this.functionProxy = functionProxy;
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
			if (!functionProxy.isDefined()) {
				throw new SemanticException("function " + functionProxy + " not defined");
			}
			int actualParamCountLocal;
			if (getAst1() == null) {
				actualParamCountLocal = 0;
			} else {
				actualParamCountLocal = actualParamCount();
			}
			int formalParamCount = functionProxy.getFunctionParamCount();
			if (formalParamCount < actualParamCountLocal) {
				throw new SemanticException(
						"the "
								+ functionProxy.getFunctionName()
								+ " function"
								+ " only accepts at most "
								+ formalParamCount
								+ " parameter(s), not "
								+ actualParamCountLocal);
			}
			if (getAst1() != null) {
				functionProxy.checkActualToFormalParameters(getAst1());
			}
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (!functionProxy.isDefined()) {
				throw new SemanticException("function " + functionProxy + " not defined");
			}
			tuples.scriptThis();
			int actualParamCountLocal;
			if (getAst1() == null) {
				actualParamCountLocal = 0;
			} else {
				actualParamCountLocal = getAst1().populateTuples(tuples);
			}
			int formalParamCount = functionProxy.getFunctionParamCount();
			if (formalParamCount < actualParamCountLocal) {
				throw new SemanticException(
						"the "
								+ functionProxy.getFunctionName()
								+ " function"
								+ " only accepts at most "
								+ formalParamCount
								+ " parameter(s), not "
								+ actualParamCountLocal);
			}

			functionProxy.checkActualToFormalParameters(getAst1());
			tuples.callFunction(functionProxy, functionProxy.getFunctionName(), formalParamCount, actualParamCountLocal);
			popSourceLineNumber(tuples);
			return 1;
		}

		private int actualParamCount() {
			int cnt = 0;
			AST ptr = getAst1();
			while (ptr != null) {
				assert ptr.getAst1() != null;
				++cnt;
				ptr = ptr.getAst2();
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
				if (getAst1() == null) {
					throw new SemanticException("sprintf requires at least 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result == 0) {
					throw new SemanticException("sprintf requires at minimum 1 argument");
				}
				tuples.sprintf(ast1Result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("close")) {
				if (getAst1() == null) {
					throw new SemanticException("close requires 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("close requires only 1 argument");
				}
				tuples.close();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("length")) {
				if (getAst1() == null) {
					tuples.length(0);
				} else {
					int ast1Result = getAst1().populateTuples(tuples);
					if (ast1Result != 1) {
						throw new SemanticException("length requires at least one argument");
					}
					tuples.length(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("srand")) {
				if (getAst1() == null) {
					tuples.srand(0);
				} else {
					int ast1Result = getAst1().populateTuples(tuples);
					if (ast1Result != 1) {
						throw new SemanticException("srand takes either 0 or one argument, not " + ast1Result);
					}
					tuples.srand(1);
				}
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("rand")) {
				if (getAst1() != null) {
					throw new SemanticException("rand does not take arguments");
				}
				tuples.rand();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sqrt")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("sqrt requires only 1 argument");
				}
				tuples.sqrt();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("int")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("int requires only 1 argument");
				}
				tuples.intFunc();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("log")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("log requires only 1 argument");
				}
				tuples.log();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("exp")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("exp requires only 1 argument");
				}
				tuples.exp();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sin")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("sin requires only 1 argument");
				}
				tuples.sin();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("cos")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("cos requires only 1 argument");
				}
				tuples.cos();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("atan2")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 2) {
					throw new SemanticException("atan2 requires 2 arguments");
				}
				tuples.atan2();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("match")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 2) {
					throw new SemanticException("match requires 2 arguments");
				}
				tuples.match();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("index")) {
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 2) {
					throw new SemanticException("index requires 2 arguments");
				}
				tuples.index();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("sub") || fIdx == BUILTIN_FUNC_NAMES.get("gsub")) {
				if (getAst1() == null || getAst1().getAst2() == null || getAst1().getAst2().getAst1() == null) {
					throw new SemanticException("sub needs at least 2 arguments");
				}
				boolean isGsub = fIdx == BUILTIN_FUNC_NAMES.get("gsub");

				int numargs = getAst1().populateTuples(tuples);

				// stack contains arg1,arg2[,arg3] - in that pop() order

				if (numargs == 2) {
					tuples.subForDollar0(isGsub);
				} else if (numargs == 3) {
					AST ptr = getAst1().getAst2().getAst2().getAst1();
					if (ptr instanceof IDAst) {
						IDAst idAst = (IDAst) ptr;
						if (idAst.isArray()) {
							throw new SemanticException("sub cannot accept an unindexed array as its 3rd argument");
						}
						idAst.setScalar(true);
						tuples.subForVariable(idAst.offset, idAst.isGlobal, isGsub);
					} else if (ptr instanceof ArrayReferenceAst) {
						ArrayReferenceAst arrAst = (ArrayReferenceAst) ptr;
						// push the index
						int ast2Result = arrAst.getAst2().populateTuples(tuples);
						assert ast2Result == 1;
						IDAst idAst = (IDAst) arrAst.getAst1();
						if (idAst.isScalar()) {
							throw new SemanticException("Cannot use " + idAst + " as an array.");
						}
						tuples.subForArrayReference(idAst.offset, idAst.isGlobal, isGsub);
					} else if (ptr instanceof DollarExpressionAst) {
						// push the field ref
						DollarExpressionAst dollarExpr = (DollarExpressionAst) ptr;
						assert dollarExpr.getAst1() != null;
						int ast1Result = dollarExpr.getAst1().populateTuples(tuples);
						assert ast1Result == 1;
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

				// funccallparamlist.funccallparamlist.idAst
				if (getAst1() == null || getAst1().getAst2() == null || getAst1().getAst2().getAst1() == null) {
					throw new SemanticException("split needs at least 2 arguments");
				}
				AST ptr = getAst1().getAst2().getAst1();
				if (!(ptr instanceof IDAst)) {
					throw new SemanticException("split needs an array name as its 2nd argument");
				}
				IDAst arrAst = (IDAst) ptr;
				if (arrAst.isScalar()) {
					throw new SemanticException("split's 2nd arg cannot be a scalar");
				}
				arrAst.setArray(true);

				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 2 && ast1Result != 3) {
					throw new SemanticException("split requires 2 or 3 arguments, not " + ast1Result);
				}
				tuples.split(ast1Result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("substr")) {
				if (getAst1() == null) {
					throw new SemanticException("substr requires at least 2 arguments");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 2 && ast1Result != 3) {
					throw new SemanticException("substr requires 2 or 3 arguments, not " + ast1Result);
				}
				tuples.substr(ast1Result);
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("tolower")) {
				if (getAst1() == null) {
					throw new SemanticException("tolower requires 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("tolower requires only 1 argument");
				}
				tuples.tolower();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("toupper")) {
				if (getAst1() == null) {
					throw new SemanticException("toupper requires 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("toupper requires only 1 argument");
				}
				tuples.toupper();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("system")) {
				if (getAst1() == null) {
					throw new SemanticException("system requires 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
					throw new SemanticException("system requires only 1 argument");
				}
				tuples.system();
				popSourceLineNumber(tuples);
				return 1;
			} else if (fIdx == BUILTIN_FUNC_NAMES.get("exec")) {
				if (getAst1() == null) {
					throw new SemanticException("exec requires 1 argument");
				}
				int ast1Result = getAst1().populateTuples(tuples);
				if (ast1Result != 1) {
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
			assert getAst1() != null;
			int retval;
			if (getAst2() == null) {
				retval = getAst1().populateTuples(tuples);
			} else {
				retval = getAst1().populateTuples(tuples) + getAst2().populateTuples(tuples);
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
			// since all getAst1()'s are FunctionDefParamList's
			// and, thus, terminals (no need to do further
			// semantic analysis)

			FunctionDefParamListAst ptr = this;
			while (ptr != null) {
				if (SPECIAL_VAR_NAMES.get(ptr.id) != null) {
					throw new SemanticException("Special variable " + ptr.id + " cannot be used as a formal parameter");
				}
				ptr = (FunctionDefParamListAst) ptr.getAst1();
			}
		}
	}

	/**
	 * Flag for non-statement expressions.
	 * Unknown for certain, but I think this is done
	 * to avoid partial variable assignment mistakes.
	 * For example, instead of a=3, the programmer
	 * inadvertently places the a on the line. If IDAsts
	 * were not tagged with AstFlag.NON_STATEMENT, then the
	 * incomplete assignment would parse properly, and
	 * the developer might remain unaware of this issue.
	 */

	private final class IDAst extends AST {

		private String id;
		private int offset = AVM.NULL_OFFSET;
		private boolean isGlobal;

		private IDAst(String id, boolean isGlobal) {
			this.id = id;
			this.isGlobal = isGlobal;
			addFlag(AstFlag.NON_STATEMENT);
		}

		private boolean isArray = false;
		private boolean isScalar = false;

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert offset != AVM.NULL_OFFSET : "offset = " + offset + " for " + this;
			tuples.dereference(offset, isArray(), isGlobal);
			popSourceLineNumber(tuples);
			return 1;
		}

		@Override
		public boolean isArray() {
			return isArray;
		}

		@Override
		public boolean isScalar() {
			return isScalar;
		}

		private void setArray(boolean b) {
			isArray = b;
		}

		private void setScalar(boolean b) {
			isScalar = b;
		}
	}

	private final class ArrayReferenceAst extends ScalarExpressionAst {

		private ArrayReferenceAst(AST idAst, AST idxAst) {
			super(idAst, idxAst);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + getAst1() + " [...])";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			assert getAst2() != null;
			// get the array var
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			// get the index
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;
			tuples.dereferenceArray();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class IntegerAst extends ScalarExpressionAst {

		private Long value;

		private IntegerAst(Long value) {
			this.value = value;
			addFlag(AstFlag.NON_STATEMENT);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + value + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(value);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * Can either assume the role of a double or an integer
	 * by aggressively normalizing the value to an int if possible.
	 */
	private final class DoubleAst extends ScalarExpressionAst {

		private Object value;

		private DoubleAst(Double val) {
			double d = val.doubleValue();
			if (d == (int) d) {
				this.value = (int) d;
			} else {
				this.value = d;
			}
			addFlag(AstFlag.NON_STATEMENT);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + value + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(value);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	/**
	 * A string is a string; Awk doesn't attempt to normalize
	 * it until it is used in an arithmetic operation!
	 */
	private final class StringAst extends ScalarExpressionAst {

		private String value;

		private StringAst(String str) {
			assert str != null;
			this.value = str;
			addFlag(AstFlag.NON_STATEMENT);
		}

		@Override
		public String toString() {
			return super.toString() + " (" + value + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.push(value);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class RegexpAst extends ScalarExpressionAst {

		private String regexpStr;

		private RegexpAst(String regexpStr) {
			assert regexpStr != null;
			this.regexpStr = regexpStr;
		}

		@Override
		public String toString() {
			return super.toString() + " (" + regexpStr + ")";
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			tuples.regexp(regexpStr);
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class ConditionPairAst extends ScalarExpressionAst {

		private ConditionPairAst(AST booleanAst1, AST booleanAst2) {
			super(booleanAst1, booleanAst2);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			assert getAst2() != null;
			int ast2Result = getAst2().populateTuples(tuples);
			assert ast2Result == 1;
			tuples.conditionPair();
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class BeginAst extends AST {

		private BeginAst() {
			super();
			setBeginFlag(true);
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
			setEndFlag(true);
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

		private PreIncAst(AST symbolAst) {
			super(symbolAst);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			if (getAst1() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst1();
				tuples.inc(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof ArrayReferenceAst) {
				ArrayReferenceAst arrAst = (ArrayReferenceAst) getAst1();
				IDAst idAst = (IDAst) arrAst.getAst1();
				assert idAst != null;
				assert arrAst.getAst2() != null;
				int arrAst2Result = arrAst.getAst2().populateTuples(tuples);
				assert arrAst2Result == 1;
				tuples.incArrayRef(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst1();
				assert dollarExpr.getAst1() != null;
				int ast1Result = dollarExpr.getAst1().populateTuples(tuples);
				assert ast1Result == 1;
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
				throw new NotImplementedError("unhandled preinc for " + getAst1());
			}
			// else
			// assert false : "cannot refer for preInc to "+getAst1();
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PreDecAst extends ScalarExpressionAst {

		private PreDecAst(AST symbolAst) {
			super(symbolAst);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			if (getAst1() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst1();
				tuples.dec(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof ArrayReferenceAst) {
				ArrayReferenceAst arrAst = (ArrayReferenceAst) getAst1();
				IDAst idAst = (IDAst) arrAst.getAst1();
				assert idAst != null;
				assert arrAst.getAst2() != null;
				int arrAst2Result = arrAst.getAst2().populateTuples(tuples);
				assert arrAst2Result == 1;
				tuples.decArrayRef(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst1();
				assert dollarExpr.getAst1() != null;
				int ast1Result = dollarExpr.getAst1().populateTuples(tuples);
				assert ast1Result == 1;
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
				throw new NotImplementedError("unhandled predec for " + getAst1());
			}
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostIncAst extends ScalarExpressionAst {

		private PostIncAst(AST symbolAst) {
			super(symbolAst);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			if (getAst1() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst1();
				assert dollarExpr.getAst1() != null;
				int dollarAst1Result = dollarExpr.getAst1().populateTuples(tuples);
				assert dollarAst1Result == 1;
				tuples.incDollarRef();
			} else {
				int ast1Result = getAst1().populateTuples(tuples);
				assert ast1Result == 1;
				if (getAst1() instanceof IDAst) {
					IDAst idAst = (IDAst) getAst1();
					tuples.postInc(idAst.offset, idAst.isGlobal);
				} else if (getAst1() instanceof ArrayReferenceAst) {
					ArrayReferenceAst arrAst = (ArrayReferenceAst) getAst1();
					IDAst idAst = (IDAst) arrAst.getAst1();
					assert idAst != null;
					assert arrAst.getAst2() != null;
					int arrAst2Result = arrAst.getAst2().populateTuples(tuples);
					assert arrAst2Result == 1;
					tuples.incArrayRef(idAst.offset, idAst.isGlobal);
				} else {
					throw new NotImplementedError("unhandled postinc for " + getAst1());
				}
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PostDecAst extends ScalarExpressionAst {

		private PostDecAst(AST symbolAst) {
			super(symbolAst);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;
			int ast1Result = getAst1().populateTuples(tuples);
			assert ast1Result == 1;
			if (getAst1() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst1();
				tuples.postDec(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof ArrayReferenceAst) {
				ArrayReferenceAst arrAst = (ArrayReferenceAst) getAst1();
				IDAst idAst = (IDAst) arrAst.getAst1();
				assert idAst != null;
				assert arrAst.getAst2() != null;
				int arrAst2Result = arrAst.getAst2().populateTuples(tuples);
				assert arrAst2Result == 1;
				tuples.decArrayRef(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst1();
				assert dollarExpr.getAst1() != null;
				int dollarAst1Result = dollarExpr.getAst1().populateTuples(tuples);
				assert dollarAst1Result == 1;
				tuples.decDollarRef();
			} else {
				throw new NotImplementedError("unhandled postinc for " + getAst1());
			}
			popSourceLineNumber(tuples);
			return 1;
		}
	}

	private final class PrintAst extends ScalarExpressionAst {

		private Token outputToken;

		private PrintAst(AST exprList, Token outToken, AST outputExpr) {
			super(exprList, outputExpr);
			this.outputToken = outToken;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int paramCount;
			if (getAst1() == null) {
				paramCount = 0;
			} else {
				paramCount = getAst1().populateTuples(tuples);
				assert paramCount >= 0;
				if (paramCount == 0) {
					throw new SemanticException("Cannot print the result. The expression doesn't return anything.");
				}
			}

			if (getAst2() != null) {
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 1;
			}

			if (outputToken == Token.GT) {
				tuples.printToFile(paramCount, false); // false = no append
			} else if (outputToken == Token.APPEND) {
				tuples.printToFile(paramCount, true); // false = no append
			} else if (outputToken == Token.PIPE) {
				tuples.printToPipe(paramCount);
			} else {
				tuples.print(paramCount);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	// we don't know if it is a scalar
	private final class ExtensionAst extends AST {

		private String extensionKeyword;

		private ExtensionAst(String keyword, AST paramAst) {
			super(paramAst);
			this.extensionKeyword = keyword;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			int paramCount;
			JawkExtension extension = extensions.get(extensionKeyword);
			if (getAst1() == null) {
				paramCount = 0;
			} else {
				int argCount = countParams((FunctionCallParamListAst) getAst1());
				/// Get all required assoc array parameters:
				int[] reqArrayIdxs = extension.getAssocArrayParameterPositions(extensionKeyword, argCount);
				assert reqArrayIdxs != null;

				for (int idx : reqArrayIdxs) {
					AST paramAst = getParamAst((FunctionCallParamListAst) getAst1(), idx);
					assert getAst1() instanceof FunctionCallParamListAst;
					// if the parameter is an IDAst...
					if (paramAst.getAst1() instanceof IDAst) {
						// then force it to be an array,
						// or complain if it is already tagged as a scalar
						IDAst idAst = (IDAst) paramAst.getAst1();
						if (idAst.isScalar()) {
							throw new SemanticException(
									"Extension '"
											+ extensionKeyword
											+ "' requires parameter position "
											+ idx
											+ " be an associative array, not a scalar.");
						}
						idAst.setArray(true);
					}
				}

				paramCount = getAst1().populateTuples(tuples);
				assert paramCount >= 0;
			}
			// isInitial == true ::
			// retval of this extension is not a function parameter
			// of another extension
			// true iff Extension | FunctionCallParam | FunctionCallParam | etc.
			boolean isInitial;
			if (getParent() instanceof FunctionCallParamListAst) {
				AST ptr = getParent();
				while (ptr instanceof FunctionCallParamListAst) {
					ptr = ptr.getParent();
				}
				isInitial = !(ptr instanceof ExtensionAst);
			} else {
				isInitial = true;
			}
			JawkExtension.ExtensionFunction func = extension.resolve(extensionKeyword);
			tuples.extension(extensionKeyword, func, paramCount, isInitial);
			popSourceLineNumber(tuples);
			// an extension always returns a value, even if it is blank/null
			return 1;
		}

		private AST getParamAst(FunctionCallParamListAst pAst, int pos) {
			for (int i = 0; i < pos; ++i) {
				pAst = (FunctionCallParamListAst) pAst.getAst2();
				if (pAst == null) {
					throw new SemanticException("More arguments required for assoc array parameter position specification.");
				}
			}
			return pAst;
		}

		private int countParams(FunctionCallParamListAst pAst) {
			int cnt = 0;
			while (pAst != null) {
				pAst = (FunctionCallParamListAst) pAst.getAst2();
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

		private Token outputToken;

		private PrintfAst(AST exprList, Token outToken, AST outputExpr) {
			super(exprList, outputExpr);
			this.outputToken = outToken;
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);

			int paramCount;
			if (getAst1() == null) {
				paramCount = 0;
			} else {
				paramCount = getAst1().populateTuples(tuples);
				assert paramCount >= 0;
				if (paramCount == 0) {
					throw new SemanticException("Cannot printf the result. The expression doesn't return anything.");
				}
			}

			if (getAst2() != null) {
				int ast2Result = getAst2().populateTuples(tuples);
				assert ast2Result == 1;
			}

			if (outputToken == Token.GT) {
				tuples.printfToFile(paramCount, false); // false = no append
			} else if (outputToken == Token.APPEND) {
				tuples.printfToFile(paramCount, true); // false = no append
			} else if (outputToken == Token.PIPE) {
				tuples.printfToPipe(paramCount);
			} else {
				tuples.printf(paramCount);
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class GetlineAst extends ScalarExpressionAst {

		private GetlineAst(AST pipeExpr, AST lvalueAst, AST inRedirect) {
			super(pipeExpr, lvalueAst, inRedirect);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			if (getAst1() != null) {
				int ast1Result = getAst1().populateTuples(tuples);
				assert ast1Result == 1;
				// stack has getAst1() (i.e., "command")
				tuples.useAsCommandInput();
			} else if (getAst3() != null) {
				// getline ... < getAst3()
				int ast3Result = getAst3().populateTuples(tuples);
				assert ast3Result == 1;
				// stack has getAst3() (i.e., "filename")
				tuples.useAsFileInput();
			} else {
				tuples.getlineInput();
			}
			// 2 resultant values on the stack!
			// 2nd - -1/0/1 for io-err,eof,success
			// 1st(top) - the input
			if (getAst2() == null) {
				tuples.assignAsInput();
				// stack still has the input, to be popped below...
				// (all assignment results are placed on the stack)
			} else if (getAst2() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst2();
				tuples.assign(idAst.offset, idAst.isGlobal);
				if (idAst.id.equals("RS")) {
					tuples.applyRS();
				}
			} else if (getAst2() instanceof ArrayReferenceAst) {
				ArrayReferenceAst arr = (ArrayReferenceAst) getAst2();
				// push the index
				assert arr.getAst2() != null;
				int arrAst2Result = arr.getAst2().populateTuples(tuples);
				assert arrAst2Result == 1;
				// push the array ref itself
				IDAst idAst = (IDAst) arr.getAst1();
				tuples.assignArray(idAst.offset, idAst.isGlobal);
			} else if (getAst2() instanceof DollarExpressionAst) {
				DollarExpressionAst dollarExpr = (DollarExpressionAst) getAst2();
				if (dollarExpr.getAst2() != null) {
					int ast2Result = dollarExpr.getAst2().populateTuples(tuples);
					assert ast2Result == 1;
				}
				// stack contains eval of dollar arg
				tuples.assignAsInputField();
			} else {
				throw new SemanticException("Cannot getline into a " + getAst2());
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
			AST returnable = searchFor(AstFlag.RETURNABLE);
			if (returnable == null) {
				throw new SemanticException("Cannot use return here.");
			}
			if (getAst1() != null) {
				int ast1Result = getAst1().populateTuples(tuples);
				assert ast1Result == 1;
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
			if (getAst1() != null) {
				int ast1Result = getAst1().populateTuples(tuples);
				assert ast1Result == 1;
				tuples.exitWithCode();
			} else {
				tuples.exitWithoutCode();
			}
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private final class DeleteStatementAst extends AST {

		private DeleteStatementAst(AST symbolAst) {
			super(symbolAst);
		}

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			assert getAst1() != null;

			if (getAst1() instanceof ArrayReferenceAst) {
				assert getAst1().getAst1() != null; // a in a[b]
				assert getAst1().getAst2() != null; // b in a[b]
				IDAst idAst = (IDAst) getAst1().getAst1();
				if (idAst.isScalar()) {
					throw new SemanticException("delete: Cannot use a scalar as an array.");
				}
				idAst.setArray(true);
				int idxResult = getAst1().getAst2().populateTuples(tuples);
				assert idxResult == 1;
				// idx on the stack
				tuples.deleteArrayElement(idAst.offset, idAst.isGlobal);
			} else if (getAst1() instanceof IDAst) {
				IDAst idAst = (IDAst) getAst1();
				if (idAst.isScalar()) {
					throw new SemanticException("delete: Cannot delete a scalar.");
				}
				idAst.setArray(true);
				tuples.deleteArray(idAst.offset, idAst.isGlobal);
			} else {
				throw new Error("Should never reach here : delete for " + getAst1());
			}

			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class BreakStatementAst extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			AST breakable = searchFor(AstFlag.BREAKABLE);
			if (breakable == null) {
				throw new SemanticException("cannot break; not within a loop");
			}
			assert breakable != null;
			tuples.gotoAddress(breakable.breakAddress());
			popSourceLineNumber(tuples);
			return 0;
		}
	}

	private class NextStatementAst extends AST {

		@Override
		public int populateTuples(AwkTuples tuples) {
			pushSourceLineNumber(tuples);
			AST nextable = searchFor(AstFlag.NEXTABLE);
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
			AST continueable = searchFor(AstFlag.CONTINUEABLE);
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
	private final class FunctionProxy implements Supplier<Address> {

		private FunctionDefAst functionDefAst;
		private String id;

		private FunctionProxy(String id) {
			this.id = id;
		}

		private void setFunctionDefinition(FunctionDefAst functionDef) {
			if (functionDefAst != null) {
				throw parserException("function " + functionDef + " already defined");
			} else {
				functionDefAst = functionDef;
			}
		}

		private boolean isDefined() {
			return functionDefAst != null;
		}

		@Override
		public Address get() {
			return functionDefAst.getAddress();
		}

		private String getFunctionName() {
			return id;
		}

		private int getFunctionParamCount() {
			return functionDefAst.paramCount();
		}

		@Override
		public String toString() {
			return super.toString() + " (" + id + ")";
		}

		private void checkActualToFormalParameters(AST actualParams) {
			functionDefAst.checkActualToFormalParameters(actualParams);
		}
	}

	/**
	 * Adds {varName -&gt; offset} mappings to the tuples so that global variables
	 * can be set by the interpreter while processing filename and name=value
	 * entries from the command-line.
	 * Also sends function names to the tuples, to provide the back end
	 * with names to invalidate if name=value assignments are passed
	 * in via the -v or ARGV arguments.
	 *
	 * @param tuples The tuples to add the mapping to.
	 */
	public void populateGlobalVariableNameToOffsetMappings(AwkTuples tuples) {
		for (String varname : symbolTable.globalIds.keySet()) {
			IDAst idAst = symbolTable.globalIds.get(varname);
			// The last arg originally was ", idAst.isScalar", but this is not set true
			// if the variable use is ambiguous. Therefore, assume it is a scalar
			// if it's Token.NOT used as an array.
			tuples.addGlobalVariableNameToOffsetMapping(varname, idAst.offset, idAst.isArray);
		}
		tuples.setFunctionNameSet(symbolTable.functionProxies.keySet());
	}

	private class AwkSymbolTableImpl {

		int numGlobals() {
			return globalIds.size();
		}

		// "constants"
		private BeginAst beginAst = null;
		private EndAst endAst = null;

		// functions (proxies)
		private Map<String, FunctionProxy> functionProxies = new HashMap<String, FunctionProxy>();

		// variable management
		private Map<String, IDAst> globalIds = new HashMap<String, IDAst>();
		private Map<String, Map<String, IDAst>> localIds = new HashMap<String, Map<String, IDAst>>();
		private Map<String, Set<String>> functionParameters = new HashMap<String, Set<String>>();
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
			if (beginAst == null) {
				beginAst = new BeginAst();
			}
			return beginAst;
		}

		AST addEND() {
			if (endAst == null) {
				endAst = new EndAst();
			}
			return endAst;
		}

		private IDAst getID(String id) {
			if (functionProxies.get(id) != null) {
				throw parserException("cannot use " + id + " as a variable; it is a function");
			}

			// put in the pool of ids to guard against using it as a function name
			ids.add(id);

			Map<String, IDAst> map;
			if (currentFunctionName == null) {
				map = globalIds;
			} else {
				Set<String> set = functionParameters.get(currentFunctionName);
				// we need "set != null && ..." here because if function
				// is defined with no args (i.e., function f() ...),
				// then set is null
				if (set != null && set.contains(id)) {
					map = localIds.get(currentFunctionName);
					if (map == null) {
						map = new HashMap<String, IDAst>();
						localIds.put(currentFunctionName, map);
					}
				} else {
					map = globalIds;
				}
			}
			assert map != null;
			IDAst idAst = map.get(id);
			if (idAst == null) {
				idAst = new IDAst(id, map == globalIds);
				idAst.offset = map.size();
				assert idAst.offset != AVM.NULL_OFFSET;
				map.put(id, idAst);
			}
			return idAst;
		}

		AST addID(String id) throws ParserException {
			IDAst retVal = getID(id);
			/// ***
			/// We really don't know if the evaluation is for an array or for a scalar
			/// here, because we can use an array as a function parameter (passed by reference).
			/// ***
			// if (retVal.isArray)
			// throw parserException("Cannot use "+retVal+" as a scalar.");
			// retVal.isScalar = true;
			return retVal;
		}

		int addFunctionParameter(String functionName, String id) {
			Set<String> set = functionParameters.get(functionName);
			if (set == null) {
				set = new HashSet<String>();
				functionParameters.put(functionName, set);
			}
			if (set.contains(id)) {
				throw parserException("multiply defined parameter " + id + " in function " + functionName);
			}
			int retval = set.size();
			set.add(id);
			Map<String, IDAst> map = localIds.get(functionName);
			if (map == null) {
				map = new HashMap<String, IDAst>();
				localIds.put(functionName, map);
			}
			assert map != null;
			IDAst idAst = map.get(id);
			if (idAst == null) {
				idAst = new IDAst(id, map == globalIds);
				idAst.offset = map.size();
				assert idAst.offset != AVM.NULL_OFFSET;
				map.put(id, idAst);
			}

			return retval;
		}

		IDAst getFunctionParameterIDAST(String functionName, String fIdString) {
			return localIds.get(functionName).get(fIdString);
		}

		AST addArrayID(String id) throws ParserException {
			IDAst retVal = getID(id);
			if (retVal.isScalar()) {
				throw parserException("Cannot use " + retVal + " as an array.");
			}
			retVal.setArray(true);
			return retVal;
		}

		AST addFunctionDef(String functionName, AST paramList, AST block) {
			if (ids.contains(functionName)) {
				throw parserException("cannot use " + functionName + " as a function; it is a variable");
			}
			FunctionProxy functionProxy = functionProxies.get(functionName);
			if (functionProxy == null) {
				functionProxy = new FunctionProxy(functionName);
				functionProxies.put(functionName, functionProxy);
			}
			FunctionDefAst functionDef = new FunctionDefAst(functionName, paramList, block);
			functionProxy.setFunctionDefinition(functionDef);
			return functionDef;
		}

		AST addFunctionCall(String id, AST paramList) {
			FunctionProxy functionProxy = functionProxies.get(id);
			if (functionProxy == null) {
				functionProxy = new FunctionProxy(id);
				functionProxies.put(id, functionProxy);
			}
			return new FunctionCallAst(functionProxy, paramList);
		}

		AST addArrayReference(String id, AST idxAst) throws ParserException {
			return new ArrayReferenceAst(addArrayID(id), idxAst);
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

		AST addREGEXP(String localRegexp) {
			return new RegexpAst(localRegexp);
		}
	}

	private ParserException parserException(String msg) {
		return new ParserException(
				msg,
				scriptSources.get(scriptSourcesCurrentIndex).getDescription(),
				reader.getLineNumber());
	}
}
