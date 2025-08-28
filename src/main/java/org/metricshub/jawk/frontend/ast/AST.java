package org.metricshub.jawk.frontend.ast;

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

import java.io.PrintStream;
import java.util.EnumSet;
import org.metricshub.jawk.frontend.AstNode;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;

public abstract class AST extends AstNode {

	public interface SourceInfoProvider {
		String getSourceDescription();

		int getLineNumber();
	}

	private static SourceInfoProvider sourceInfoProvider;

	public static void setSourceInfoProvider(SourceInfoProvider provider) {
		sourceInfoProvider = provider;
	}

	private final String sourceDescription;
	private final int lineNo;
	private AST parent;
	private AST ast1, ast2, ast3, ast4;
	private final EnumSet<AstFlag> flags = EnumSet.noneOf(AstFlag.class);

	protected AST() {
		SourceInfoProvider provider = sourceInfoProvider;
		if (provider == null) {
			throw new IllegalStateException("SourceInfoProvider not set");
		}
		this.sourceDescription = provider.getSourceDescription();
		this.lineNo = provider.getLineNumber();
	}

	protected AST(AST ast1) {
		this();
		this.ast1 = ast1;
		if (ast1 != null) {
			ast1.parent = this;
		}
	}

	protected AST(AST ast1, AST ast2) {
		this();
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
		this();
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
		this();
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

	protected final void addFlag(AstFlag flag) {
		flags.add(flag);
	}

	public final boolean hasFlag(AstFlag flag) {
		return flags.contains(flag);
	}

	public Address breakAddress() {
		return null;
	}

	public Address continueAddress() {
		return null;
	}

	public Address nextAddress() {
		return null;
	}

	public Address returnAddress() {
		return null;
	}

	public final AST getParent() {
		return parent;
	}

	protected final void setParent(AST p) {
		parent = p;
	}

	public final AST getAst1() {
		return ast1;
	}

	protected final void setAst1(AST a1) {
		ast1 = a1;
	}

	public final AST getAst2() {
		return ast2;
	}

	protected final void setAst2(AST a2) {
		ast2 = a2;
	}

	public final AST getAst3() {
		return ast3;
	}

	protected final void setAst3(AST a3) {
		ast3 = a3;
	}

	public final AST getAst4() {
		return ast4;
	}

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

	@Override
	public abstract int populateTuples(AwkTuples tuples);

	protected final void pushSourceLineNumber(AwkTuples tuples) {
		tuples.pushSourceLineNumber(lineNo);
	}

	protected final void popSourceLineNumber(AwkTuples tuples) {
		tuples.popSourceLineNumber(lineNo);
	}

	private boolean isBegin = isBegin();

	public final boolean isBeginFlag() {
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

	public final boolean isEndFlag() {
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

	public final boolean isFunctionFlag() {
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

	public class SemanticException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public SemanticException(String msg) {
			super(msg + " (" + sourceDescription + ":" + lineNo + ")");
		}
	}

	public final void throwSemanticException(String msg) {
		throw new SemanticException(msg);
	}

	@Override
	public String toString() {
		return getClass().getName().replaceFirst(".*[$.]", "");
	}

	public enum AstFlag {
		BREAKABLE,
		NEXTABLE,
		CONTINUEABLE,
		RETURNABLE,
		NON_STATEMENT
	}
}
