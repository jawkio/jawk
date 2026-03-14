package org.metricshub.jawk.intermediate;

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
public enum Opcode {
	/**
	 * Pops an item off the operand stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 */
	POP,
	/**
	 * Pushes an item onto the operand stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 */
	PUSH,
	/**
	 * Pops and evaluates the top-of-stack; if
	 * false, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 */
	IFFALSE,
	/**
	 * Converts the top-of-stack to a number.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x (as a number)
	 */
	TO_NUMBER,
	/**
	 * Pops and evaluates the top-of-stack; if
	 * true, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 */
	IFTRUE,
	/**
	 * Jumps to a specified address. The operand stack contents
	 * are unaffected.
	 */
	GOTO,
	/**
	 * A no-operation. The operand stack contents are
	 * unaffected.
	 */
	NOP,
	/**
	 * Prints N number of items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 */
	PRINT,
	/**
	 * Prints N number of items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 */
	PRINT_TO_FILE,
	/**
	 * Prints N number of items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 */
	PRINT_TO_PIPE,
	/**
	 * Performs a formatted print of N items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 */
	PRINTF,
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 */
	PRINTF_TO_FILE,
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 */
	PRINTF_TO_PIPE,
	/** Constant <code>SPRINTF=270</code> */
	SPRINTF,
	/**
	 * Depending on the argument, pop and evaluate the string length of the top-of-stack
	 * or evaluate the string length of $0; in either case, push the result onto
	 * the stack.
	 * <p>
	 * The input field length evaluation mode is provided to support backward
	 * compatibility with the deprecated usage of length (i.e., no arguments).
	 * <p>
	 * Argument: 0 to use $0, use top-of-stack otherwise
	 * <p>
	 * If argument is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: length-of-$0 ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: length-of-x ...
	 * </blockquote>
	 */
	LENGTH,
	/**
	 * Pop and concatenate two strings from the top-of-stack; push the result onto
	 * the stack.
	 * <p>
	 * Stack before: x y ...<br/>
	 * Stack after: x-concatenated-with-y ...
	 */
	CONCAT,
	/**
	 * Assigns the top-of-stack to a variable. The contents of the stack
	 * are unaffected.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 */
	ASSIGN,
	/**
	 * Assigns an item to an array element. The item remains on the stack.
	 * <p>
	 * Argument 1: offset of the particular associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: index-into-array item ...<br/>
	 * Stack after: item ...
	 */
	ASSIGN_ARRAY,
	/**
	 * Assigns the top-of-stack to $0. The contents of the stack are unaffected.
	 * Upon assignment, individual field variables are recalculated.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 */
	ASSIGN_AS_INPUT,
	/**
	 * Assigns an item as a particular input field; the field number can be 0.
	 * Upon assignment, associating input fields are affected. For example, if
	 * the following assignment were made:
	 * <blockquote>
	 *
	 * <pre>
	 * $3 = "hi"
	 * </pre>
	 *
	 * </blockquote>
	 * $0 would be recalculated. Likewise, if the following assignment were made:
	 * <blockquote>
	 *
	 * <pre>
	 * $0 = "hello there"
	 * </pre>
	 *
	 * </blockquote>
	 * $1, $2, ... would be recalculated.
	 * <p>
	 * Stack before: field-num x ...<br/>
	 * Stack after: x ...
	 */
	ASSIGN_AS_INPUT_FIELD,
	/**
	 * Obtains an item from the variable manager and push it onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 */
	DEREFERENCE,
	/**
	 * Increase the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x+n ...
	 */
	PLUS_EQ,
	/**
	 * Decreases the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x-n ...
	 */
	MINUS_EQ,
	/**
	 * Multiplies the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x*n ...
	 */
	MULT_EQ,
	/**
	 * Divides the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x/n ...
	 */
	DIV_EQ,
	/**
	 * Takes the modules of the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x%n ...
	 */
	MOD_EQ,
	/**
	 * Raises the contents of the variable to the power of the adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x^n ...
	 */
	POW_EQ,
	/**
	 * Increase the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x+n ...
	 */
	PLUS_EQ_ARRAY,
	/**
	 * Decreases the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x-n ...
	 */
	MINUS_EQ_ARRAY,
	/**
	 * Multiplies the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x*n ...
	 */
	MULT_EQ_ARRAY,
	/**
	 * Divides the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x/n ...
	 */
	DIV_EQ_ARRAY,
	/**
	 * Takes the modulus of the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x%n ...
	 */
	MOD_EQ_ARRAY,
	/**
	 * Raises the contents of an indexed array to the power of an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x^n ...
	 */
	POW_EQ_ARRAY,
	/**
	 * Increases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x+n ...
	 */
	PLUS_EQ_INPUT_FIELD,
	/**
	 * Decreases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x-n ...
	 */
	MINUS_EQ_INPUT_FIELD,
	/**
	 * Multiplies the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x*n ...
	 */
	MULT_EQ_INPUT_FIELD,
	/**
	 * Divides the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x/n ...
	 */
	DIV_EQ_INPUT_FIELD,
	/**
	 * Takes the modulus of the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x%n ...
	 */
	MOD_EQ_INPUT_FIELD,
	/**
	 * Raises the contents of an input field to the power of an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x^n ...
	 */
	POW_EQ_INPUT_FIELD,

	/**
	 * Seeds the random number generator. If there are no arguments, the current
	 * time (as a long value) is used as the seed. Otherwise, the top-of-stack is
	 * popped and used as the seed value.
	 * <p>
	 * Argument: # of arguments
	 * <p>
	 * If # of arguments is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 */
	SRAND,
	/**
	 * Obtains the next random number from the random number generator
	 * and push it onto the stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: random-number ...
	 */
	RAND,
	/**
	 * Built-in function that pops the top-of-stack, removes its fractional part,
	 * if any, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: (int)x ...
	 */
	INTFUNC,
	/**
	 * Built-in function that pops the top-of-stack, takes its square root,
	 * and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sqrt(x) ...
	 */
	SQRT,
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.log method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: log(x) ...
	 */
	LOG,
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.exp method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: exp(x) ...
	 */
	EXP,
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.sin method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sin(x) ...
	 */
	SIN,
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.cos method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: cos(x) ...
	 */
	COS,
	/**
	 * Built-in function that pops the first two items off the stack,
	 * calls the java.lang.Math.atan2 method
	 * with these as arguments, and places the result onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: atan2(x1,x2) ...
	 */
	ATAN2,
	/**
	 * Built-in function that searches a string as input to a regular expression,
	 * the location of the match is pushed onto the stack.
	 * The RSTART and RLENGTH variables are set as a side effect.
	 * If a match is found, RSTART and function return value are set
	 * to the location of the match and RLENGTH is set to the length
	 * of the substring matched against the regular expression.
	 * If no match is found, RSTART (and return value) is set to
	 * 0 and RLENGTH is set to -1.
	 * <p>
	 * Stack before: string regexp ...<br/>
	 * Stack after: RSTART ...
	 */
	MATCH,
	/**
	 * Built-in function that locates a substring within a source string
	 * and pushes the location onto the stack. If the substring is
	 * not found, 0 is pushed onto the stack.
	 * <p>
	 * Stack before: string substring ...<br/>
	 * Stack after: location-index ...
	 */
	INDEX,
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in $0 and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 * <p>
	 * Stack before: regexp replacement-string ...<br/>
	 * Stack after: ...
	 */
	SUB_FOR_DOLLAR_0,
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a field reference and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 * <p>
	 * Stack before: field-num regexp replacement-string ...<br/>
	 * Stack after: ...
	 */
	SUB_FOR_DOLLAR_REFERENCE,
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular variable and replaces it with another.
	 * <p>
	 * Argument 1: variable offset in variable manager<br/>
	 * Argument 2: is global variable<br/>
	 * Argument 3: is global sub
	 * <p>
	 * Stack before: regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 */
	SUB_FOR_VARIABLE,
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular array cell and replaces it with another.
	 * <p>
	 * Argument 1: array map offset in variable manager<br/>
	 * Argument 2: is global array map<br/>
	 * Argument 3: is global sub
	 * <p>
	 * Stack before: array-index regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 */
	SUB_FOR_ARRAY_REFERENCE,
	/**
	 * Built-in function to split a string by a regexp and put the
	 * components into an array.
	 * <p>
	 * Argument: # of arguments (parameters on stack)
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string array ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string array regexp ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 */
	SPLIT,
	/**
	 * Built-in function that pushes a substring of the top-of-stack
	 * onto the stack.
	 * The tuple argument indicates whether to limit the substring
	 * to a particular end position, or to take the substring
	 * up to the end-of-string.
	 * <p>
	 * Argument: # of arguments
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string start-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string start-pos end-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 */
	SUBSTR,
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to lower case and pushes the result onto the stack.
	 * <p>
	 * Stack before: STRING-ARGUMENT ...<br/>
	 * Stack after: string-argument ...
	 */
	TOLOWER,
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to upper case and pushes the result onto the stack.
	 * <p>
	 * Stack before: string-argument ...<br/>
	 * Stack after: STRING-ARGUMENT ...
	 */
	TOUPPER,
	/**
	 * Built-in function that executes the top-of-stack as a system command
	 * and pushes the return code onto the stack.
	 * <p>
	 * Stack before: cmd ...<br/>
	 * Stack after: return-code ...
	 */
	SYSTEM,

	/**
	 * Swaps the top two elements of the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x2 x1 ...
	 */
	SWAP,

	/**
	 * Numerically adds the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1+x2 ...
	 */
	ADD,
	/**
	 * Numerically subtracts the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1-x2 ...
	 */
	SUBTRACT,
	/**
	 * Numerically multiplies the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1*x2 ...
	 */
	MULTIPLY,
	/**
	 * Numerically divides the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1/x2 ...
	 */
	DIVIDE,
	/**
	 * Numerically takes the modulus of the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1%x2 ...
	 */
	MOD,
	/**
	 * Numerically raises the top element to the power of the next element with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1^x2 ...
	 */
	POW,

	/**
	 * Increases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x+1 ...
	 */
	INC,
	/**
	 * Decreases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x-1 ...
	 */
	DEC,
	/**
	 * Increases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x+1 ...
	 */
	INC_ARRAY_REF,
	/**
	 * Decreases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x-1 ...
	 */
	DEC_ARRAY_REF,
	/**
	 * Increases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x+1
	 */
	INC_DOLLAR_REF,
	/**
	 * Decreases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x-1
	 */
	DEC_DOLLAR_REF,

	/**
	 * Duplicates the top-of-stack on the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x x ...
	 */
	DUP,
	/**
	 * Evaluates the logical NOT of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: !x ...
	 */
	NOT,
	/**
	 * Evaluates the numerical NEGATION of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: -x ...
	 */
	NEGATE,

	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if equal, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1==x2
	 */
	CMP_EQ,
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &lt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&lt;x2
	 */
	CMP_LT,
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &gt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&gt;x2
	 */
	CMP_GT,
	/**
	 * Applies a regular expression to the top stack element; pushes 1 if it matches,
	 * 0 if it does not match.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: (x1 ~ /x2/) ...
	 */
	MATCHES,

	/** Constant <code>DEREF_ARRAY=336</code> */
	DEREF_ARRAY,

	// for (x in y) {keyset} support
	/**
	 * Retrieves and pushes a set of keys from an associative array onto the stack.
	 * The set is stored in a {@link java.util.Deque} for iteration.
	 * <p>
	 * Stack before: associative-array ...<br/>
	 * Stack after: key-list-set ...
	 */
	KEYLIST,
	/**
	 * Tests whether the key list (deque) is empty; jumps to the argument
	 * address if empty, steps to the next instruction if not.
	 * <p>
	 * Argument: jump-address-if-empty
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: ...
	 */
	IS_EMPTY_KEYLIST,
	/**
	 * Removes an item from the key list (deque) and pushes it onto the operand stack.
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: 1st-item ...
	 */
	GET_FIRST_AND_REMOVE_FROM_KEYLIST,

	// assertions
	/**
	 * Checks whether the top-of-stack is of a particular class type;
	 * if not, an AwkRuntimeException is thrown.
	 * The stack remains unchanged upon a successful check.
	 * <p>
	 * Argument: class-type (i.e., java.util.Deque.class)
	 * <p>
	 * Stack before: obj ...<br/>
	 * Stack after: obj ...
	 */
	CHECK_CLASS,

	// input
	// * Obtain an input string from stdin; push the result onto the stack.
	/**
	 * Push an input field onto the stack.
	 * <p>
	 * Stack before: field-id ...<br/>
	 * Stack after: x ...
	 */
	GET_INPUT_FIELD,
	/**
	 * Pushes an input field onto the stack using an embedded field index.
	 * <p>
	 * Argument: field-id
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 */
	GET_INPUT_FIELD_CONST,
	/**
	 * Consume next line of input; assigning $0 and recalculating $1, $2, etc.
	 * The input can come from the following sources:
	 * <ul>
	 * <li>stdin
	 * <li>filename arguments
	 * </ul>
	 * The operand stack is unaffected.
	 */
	CONSUME_INPUT,
	/**
	 * Obtains input from stdin/filename-args and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: input-string return-code ...
	 */
	GETLINE_INPUT,
	/**
	 * Obtains input from a file and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the file is opened and the handle
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * <p>
	 * Stack before: filename ...<br/>
	 * Stack after: input-string return-code ...
	 */
	USE_AS_FILE_INPUT,
	/**
	 * Obtains input from a command (process) and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the a process is spawned to execute
	 * the specified command and the process reference
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 * <p>
	 * Stack before: command-line ...<br/>
	 * Stack after: input-string return-code ...
	 */
	USE_AS_COMMAND_INPUT,

	// variable housekeeping
	/**
	 * Assign the NF variable offset. This is important for the
	 * AVM to set the variables as new input lines are processed.
	 * <p>
	 * The operand stack is unaffected.
	 */
	NF_OFFSET,
	/**
	 * Assign the NR variable offset. This is important for the
	 * AVM to increase the record number as new input lines received.
	 * <p>
	 * The operand stack is unaffected.
	 */
	NR_OFFSET,
	/**
	 * Assign the FNR variable offset. This is important for the
	 * AVM to increase the "file" record number as new input lines are received.
	 * <p>
	 * The operand stack is unaffected.
	 */
	FNR_OFFSET,
	/**
	 * Assign the FS variable offset. This is important for the
	 * AVM to know how to split fields upon incoming records of input.
	 * <p>
	 * The operand stack is unaffected.
	 */
	FS_OFFSET,
	/**
	 * Assign the RS variable offset. This is important for the
	 * AVM to know how to create records from the stream(s) of input.
	 * <p>
	 * The operand stack is unaffected.
	 */
	RS_OFFSET,
	/**
	 * Assign the OFS variable offset. This is important for the
	 * AVM to use when outputting expressions via PRINT.
	 * <p>
	 * The operand stack is unaffected.
	 */
	OFS_OFFSET,
	/**
	 * Assign the RSTART variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 */
	RSTART_OFFSET,
	/**
	 * Assign the RLENGTH variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 */
	RLENGTH_OFFSET,
	/**
	 * Assign the FILENAME variable offset. The AVM sets this variable while
	 * processing files from the command-line for input.
	 * <p>
	 * The operand stack is unaffected.
	 */
	FILENAME_OFFSET,
	/**
	 * Assign the SUBSEP variable offset. The AVM uses this variable while
	 * building an index of a multi-dimensional array.
	 * <p>
	 * The operand stack is unaffected.
	 */
	SUBSEP_OFFSET,
	/**
	 * Assign the CONVFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings.
	 * <p>
	 * The operand stack is unaffected.
	 */
	CONVFMT_OFFSET,
	/**
	 * Assign the OFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings for printing.
	 * <p>
	 * The operand stack is unaffected.
	 */
	OFMT_OFFSET,
	/**
	 * Assign the ENVIRON variable offset. The AVM provides environment
	 * variables through this array.
	 * <p>
	 * The operand stack is unaffected.
	 */
	ENVIRON_OFFSET,
	/**
	 * Assign the ARGC variable offset. The AVM provides the number of
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 */
	ARGC_OFFSET,
	/**
	 * Assign the ARGV variable offset. The AVM provides command-line
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 */
	ARGV_OFFSET,

	/**
	 * Apply the RS variable by notifying the partitioning reader that
	 * there is a new regular expression to use when partitioning input
	 * records.
	 * <p>
	 * The stack remains unaffected.
	 */
	APPLY_RS,

	/**
	 * Call a user function.
	 * <p>
	 * Stack before: x1, x2, ..., xn <br>
	 * Stack after: f(x1, x2, ..., xn)
	 */
	CALL_FUNCTION,

	/**
	 * Define a user function.
	 * <p>
	 * Stack remains unchanged
	 */
	FUNCTION,

	/**
	 * Sets the return value of a user function.
	 * <p>
	 * Stack before: x <br>
	 * Stack after: ...
	 */
	SET_RETURN_RESULT,

	/**
	 * Get the return value of the user function that was called
	 * <p>
	 * Stack before: ... <br>
	 * Stack after: x
	 */
	RETURN_FROM_FUNCTION,

	/**
	 * Internal: sets the number of global variables
	 */
	SET_NUM_GLOBALS,

	/**
	 * Close the specified file.
	 * <p>
	 * Stack before: file name <br>
	 * Stack after: result of the close operation
	 */
	CLOSE,

	/**
	 * Convert a list of array indices to a concatenated string with SUBSEP.
	 * This is used for multidimensional arrays.
	 * <p>
	 * Stack before: i1, i2, ..., in <br>
	 * Stack after: "i1SUBSEPi2SUBSEP...in"
	 */
	APPLY_SUBSEP,

	/**
	 * Deletes an entry in an array.
	 * <p>
	 * Stack before: i <br>
	 * Stack after: ...
	 */
	DELETE_ARRAY_ELEMENT,

	/**
	 * Internal.
	 * <p>
	 * Stack remains unchanged.
	 */
	SET_EXIT_ADDRESS,

	/**
	 * Internal.
	 * <p>
	 * Stack remains unchanged.
	 */
	SET_WITHIN_END_BLOCKS,

	/**
	 * Terminates execution and returns specified exit code.
	 * <p>
	 * Stack before: integer <br>
	 * Stack after: N/A
	 */
	EXIT_WITH_CODE,

	/**
	 * Returns a regex pattern.
	 * <p>
	 * Stack before: ... <br>
	 * Stack after: the regex pattern object
	 */
	REGEXP,

	/**
	 * Returns a pair of regex patterns.
	 * <p>
	 * Stack before: pattern1, pattern2 <br>
	 * Stack after: regex pair object
	 */
	CONDITION_PAIR,

	/**
	 * Returns whether the specified key is in the array.
	 * <p>
	 * Stack before: key, array <br>
	 * Stack after: true|false
	 */
	IS_IN,

	/**
	 * Deprecated.
	 */
	THIS,

	/**
	 * Call a function from an extension
	 * <p>
	 * Stack before: x1, x2, ..., xn <br>
	 * Stack after: f(x1, x2, ..., xn)
	 */
	EXTENSION,

	/**
	 * Delete the specified array.
	 * <p>
	 * Stack remains unchanged.
	 */
	DELETE_ARRAY,

	/**
	 * Converts the top stack element to a number;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ... (as a number)
	 */
	UNARY_PLUS,

	/**
	 * Terminates execution without specifying an exit code.
	 * <p>
	 * Stack before: N/A <br>
	 * Stack after: N/A
	 */
	EXIT_WITHOUT_CODE,

	/**
	 * Assign to the special variable NF via JRT and push the assigned value.
	 * <p>
	 * Stack before: value ...<br/>
	 * Stack after: value ...
	 */
	ASSIGN_NF,
	/**
	 * Push the current value of the special variable NF via JRT.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: NF ...
	 */
	PUSH_NF,

	/** Assign to NR via JRT and push the assigned value. */
	ASSIGN_NR,
	/** Push the current NR via JRT. */
	PUSH_NR,

	/** Assign to FNR via JRT and push the assigned value. */
	ASSIGN_FNR,
	/** Push the current FNR via JRT. */
	PUSH_FNR,

	/** Assign to FS via JRT and push the assigned value. */
	ASSIGN_FS,
	/** Push the current FS via JRT. */
	PUSH_FS,

	/** Assign to RS via JRT and push the assigned value. */
	ASSIGN_RS,
	/** Push the current RS via JRT. */
	PUSH_RS,

	/** Assign to OFS via JRT and push the assigned value. */
	ASSIGN_OFS,
	/** Push the current OFS via JRT. */
	PUSH_OFS,

	/** Assign to ORS via JRT and push the assigned value. */
	ASSIGN_ORS,
	/** Push the current ORS via JRT. */
	PUSH_ORS,

	/** Assign to RSTART via JRT and push the assigned value. */
	ASSIGN_RSTART,
	/** Push the current RSTART via JRT. */
	PUSH_RSTART,

	/** Assign to RLENGTH via JRT and push the assigned value. */
	ASSIGN_RLENGTH,
	/** Push the current RLENGTH via JRT. */
	PUSH_RLENGTH,

	/** Assign to FILENAME via JRT and push the assigned value. */
	ASSIGN_FILENAME,
	/** Push the current FILENAME via JRT. */
	PUSH_FILENAME,

	/** Assign to SUBSEP via JRT and push the assigned value. */
	ASSIGN_SUBSEP,
	/** Push the current SUBSEP via JRT. */
	PUSH_SUBSEP,

	/** Assign to CONVFMT via JRT and push the assigned value. */
	ASSIGN_CONVFMT,
	/** Push the current CONVFMT via JRT. */
	PUSH_CONVFMT,

	/** Assign to OFMT via JRT and push the assigned value. */
	ASSIGN_OFMT,
	/** Push the current OFMT via JRT. */
	PUSH_OFMT,

	/** Assign to ARGC via JRT and push the assigned value. */
	ASSIGN_ARGC,
	/** Push the current ARGC via JRT. */
	PUSH_ARGC,

	/**
	 * Assign the ORS variable offset. This is important for the
	 * AVM to use when outputting expressions via PRINT.
	 * <p>
	 * The operand stack is unaffected.
	 */
	ORS_OFFSET,

	/**
	 * Increases the variable reference by one; pushes the original value
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ... or 0 if uninitialized
	 */
	POSTINC,

	/**
	 * Decreases the variable reference by one; pushes the original value
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ... or 0 if uninitialized
	 */
	POSTDEC,

	/**
	 * Read stdin for simple AWK expression evaluation.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: ...
	 */
	SET_INPUT_FOR_EVAL;

	private static final Opcode[] VALUES = values();

	public static Opcode fromId(int id) {
		if (id < 0 || id >= VALUES.length) {
			throw new IllegalArgumentException("Unknown opcode: " + id);
		}
		return VALUES[id];
	}
}
