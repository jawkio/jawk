package org.metricshub.jawk.backend;

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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.metricshub.jawk.intermediate.UninitializedObject;
import org.metricshub.jawk.jrt.AssocArray;

/**
 * Runtime stack used by the AVM interpreter.
 */
class RuntimeStack {

	static final UninitializedObject BLANK = new UninitializedObject();
	private static final Object[] NULL_LOCALS_SENTINEL = new Object[0];

	private Object[] globals = null;
	private Object[] locals = null;
	private Deque<Object[]> localsStack = new ArrayDeque<Object[]>();
	private Deque<Integer> returnIndexes = new ArrayDeque<Integer>();

	@SuppressWarnings("unused")
	public void dump() {
		System.out.println("globals = " + Arrays.toString(globals));
		System.out.println("locals = " + Arrays.toString(locals));
		System.out.println("localsStack = " + localsStack);
		System.out.println("returnIndexes = " + returnIndexes);
	}

	Object[] getNumGlobals() {
		return globals;
	}

	void reset() {
		globals = null;
		locals = null;
		localsStack.clear();
		returnIndexes.clear();
		returnValue = null;
	}

	/** Must be one of the first methods executed. */
	void setNumGlobals(long l) {
		assert l >= 0;
		assert globals == null;
		globals = new Object[(int) l];
		for (int i = 0; i < l; i++) {
			globals[i] = null;
		}
		// must accept multiple executions
		// expandFrameIfNecessary(num_globals);
	}

	/*
	 * // this assumes globals = Object[0] upon initialization
	 * private void expandFrameIfNecessary(int num_globals) {
	 * if (num_globals == globals.length)
	 * // no need for expansion;
	 * // do nothing
	 * return;
	 * Object[] new_frame = new Object[num_globals];
	 * for (int i=0;i<globals.length;++i)
	 * new_frame[i] = globals[i];
	 * globals = new_frame;
	 * }
	 */

	Object getVariable(long offset, boolean isGlobal) {
		assert globals != null;
		assert offset != AVM.NULL_OFFSET;
		if (isGlobal) {
			return globals[(int) offset];
		} else {
			return locals[(int) offset];
		}
	}

	Object setVariable(long offset, Object val, boolean isGlobal) {
		assert globals != null;
		assert offset != AVM.NULL_OFFSET;
		if (isGlobal) {
			globals[(int) offset] = val;
			return val;
		} else {
			locals[(int) offset] = val;
			return val;
		}
	}

	// for _DELETE_ARRAY_
	void removeVariable(long offset, boolean isGlobal) {
		assert globals != null;
		assert offset != AVM.NULL_OFFSET;
		if (isGlobal) {
			assert globals[(int) offset] == null || globals[(int) offset] instanceof AssocArray;
			globals[(int) offset] = null;
		} else {
			assert locals[(int) offset] == null || locals[(int) offset] instanceof AssocArray;
			locals[(int) offset] = null;
		}
	}

	void setFilelistVariable(int offset, Object value) {
		assert globals != null;
		assert offset != AVM.NULL_OFFSET;
		globals[offset] = value;
	}

	void pushFrame(long numFormalParams, int positionIdx) {
		localsStack.push(locals == null ? NULL_LOCALS_SENTINEL : locals);
		locals = new Object[(int) numFormalParams];
		returnIndexes.push(positionIdx);
	}

	/** returns the position index */
	int popFrame() {
		Object[] restoredLocals = localsStack.pop();
		locals = restoredLocals == NULL_LOCALS_SENTINEL ? null : restoredLocals;
		return returnIndexes.pop();
	}

	void popAllFrames() {
		for (int i = localsStack.size(); i > 0; i--) {
			Object[] restoredLocals = localsStack.pop();
			locals = restoredLocals == NULL_LOCALS_SENTINEL ? null : restoredLocals;
			returnIndexes.pop();
		}
	}

	private Object returnValue;

	void setReturnValue(Object obj) {
		assert returnValue == null;
		returnValue = obj;
	}

	Object getReturnValue() {
		Object retval;
		if (returnValue == null) {
			retval = BLANK;
		} else {
			retval = returnValue;
		}
		returnValue = null;
		return retval;
	}
}
