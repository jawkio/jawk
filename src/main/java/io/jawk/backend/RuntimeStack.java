package io.jawk.backend;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.jawk.intermediate.UninitializedObject;

/**
 * Runtime stack used by the AVM interpreter.
 */
class RuntimeStack {

	static final UninitializedObject BLANK = new UninitializedObject();
	private static final Object[] NULL_LOCALS_SENTINEL = new Object[0];

	private Object[] globals = null;
	private List<String> globalNamesByOffset = Collections.emptyList();
	private Map<String, Integer> globalOffsetsByName = Collections.emptyMap();
	private Object[] locals = null;
	private Deque<Object[]> localsStack = new ArrayDeque<Object[]>();
	private Deque<Integer> returnIndexes = new ArrayDeque<Integer>();

	@SuppressWarnings("unused")
	public void dump() {
		System.out.println("globals = " + Arrays.toString(globals));
		System.out.println("globalNamesByOffset = " + globalNamesByOffset);
		System.out.println("locals = " + Arrays.toString(locals));
		System.out.println("localsStack = " + localsStack);
		System.out.println("returnIndexes = " + returnIndexes);
	}

	Object[] getNumGlobals() {
		return globals;
	}

	void reset() {
		clearGlobals();
		resetTransientState();
	}

	void clearGlobals() {
		globals = null;
		globalNamesByOffset = Collections.emptyList();
		globalOffsetsByName = Collections.emptyMap();
	}

	void resetTransientState() {
		locals = null;
		localsStack.clear();
		returnIndexes.clear();
		returnValue = null;
	}

	/** Must be one of the first methods executed. */
	void setNumGlobals(long l, Map<String, Integer> offsetsByName) {
		globals = new Object[(int) l];
		for (int i = 0; i < l; i++) {
			globals[i] = null;
		}
		setGlobalLayoutMetadata((int) l, offsetsByName);
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

	void rebindGlobals(List<String> namesByOffset) {
		globals = new Object[namesByOffset.size()];
		for (int i = 0; i < globals.length; i++) {
			globals[i] = null;
		}
		setGlobalLayoutMetadata(namesByOffset);
	}

	Map<String, Object> snapshotGlobalVariables() {
		Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
		if (globals == null) {
			return snapshot;
		}
		for (int i = 0; i < globals.length && i < globalNamesByOffset.size(); i++) {
			String name = globalNamesByOffset.get(i);
			if (name != null) {
				snapshot.put(name, globals[i]);
			}
		}
		return snapshot;
	}

	boolean hasGlobalVariable(String name) {
		return globalOffsetsByName.containsKey(name);
	}

	Object getGlobalVariable(String name) {
		Integer offset = globalOffsetsByName.get(name);
		return offset == null ? null : globals[offset.intValue()];
	}

	void setGlobalVariable(String name, Object value) {
		Integer offset = globalOffsetsByName.get(name);
		if (offset != null) {
			globals[offset.intValue()] = value;
		}
	}

	String getGlobalName(int offset) {
		return offset >= 0 && offset < globalNamesByOffset.size() ? globalNamesByOffset.get(offset) : null;
	}

	Object getVariable(long offset, boolean isGlobal) {
		if (isGlobal) {
			return globals[(int) offset];
		} else {
			return locals[(int) offset];
		}
	}

	Object setVariable(long offset, Object val, boolean isGlobal) {
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
		if (isGlobal) {
			globals[(int) offset] = null;
		} else {
			locals[(int) offset] = null;
		}
	}

	void setFilelistVariable(int offset, Object value) {
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

	private void setGlobalLayoutMetadata(int numGlobals, Map<String, Integer> offsetsByName) {
		List<String> namesByOffset = new ArrayList<String>(Collections.nCopies(numGlobals, (String) null));
		if (offsetsByName != null) {
			for (Map.Entry<String, Integer> entry : offsetsByName.entrySet()) {
				int offset = entry.getValue().intValue();
				if (offset >= 0 && offset < numGlobals) {
					namesByOffset.set(offset, entry.getKey());
				}
			}
		}
		setGlobalLayoutMetadata(namesByOffset);
	}

	private void setGlobalLayoutMetadata(List<String> namesByOffset) {
		globalNamesByOffset = new ArrayList<String>(namesByOffset);
		Map<String, Integer> offsetsByName = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < namesByOffset.size(); i++) {
			String name = namesByOffset.get(i);
			if (name != null) {
				offsetsByName.put(name, Integer.valueOf(i));
			}
		}
		globalOffsetsByName = offsetsByName;
	}
}
