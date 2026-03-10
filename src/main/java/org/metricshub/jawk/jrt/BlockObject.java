package org.metricshub.jawk.jrt;

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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * An item which blocks until something useful can be
 * done with the object. The BlockManager multiplexes
 * BlockObjects such that unblocking one
 * BlockObject causes the BlockManager to dispatch
 * the notifier tag result of the BlockObject.
 * <p>
 * BlockObjects are chained. The BlockManager
 * blocks on all chained BlockObjects until one
 * is unblocked.
 * <p>
 * Subclasses must provide meaningful block()
 * and getNotifierTag() routines.
 * <p>
 * BlockObjects do not actually perform the client
 * blocking. This is done by the BlockManager at the
 * AVM (interpreted) or compiled runtime environment.
 * The AVM/compiled environments make special provision
 * to return the head block object to the BlockManager
 * (within _EXTENSION_ keyword processing).
 *
 * @see BlockManager
 * @author Danny Daglas
 */
public abstract class BlockObject {

	private BlockObject nextBlockObject = null;

	/**
	 * <p>
	 * Constructor for BlockObject.
	 * </p>
	 */
	protected BlockObject() {}

	/**
	 * Construct a meaningful notifier tag for this BlockObject.
	 *
	 * @return a {@link java.lang.String} object
	 */
	public abstract String getNotifierTag();

	/**
	 * Block until meaningful data is made available for
	 * the client application. This is called by the BlockManager
	 * in a way such that the BlockManager waits for one
	 * BlockObject to unblock.
	 *
	 * @throws java.lang.InterruptedException if any.
	 */
	public abstract void block() throws InterruptedException;

	/**
	 * Eliminate the rest of the BlockObject chain.
	 */
	public final void clearNextBlockObject() {
		this.nextBlockObject = null;
	}

	/**
	 * Chain this BlockObject to another BlockObject.
	 * The chain is linear and there is no upper bounds on
	 * the number of BlockObjects that can be supported.
	 *
	 * @param bo a {@link org.metricshub.jawk.jrt.BlockObject} object
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Block chain uses direct references between block objects")
	public void setNextBlockObject(BlockObject bo) {
		this.nextBlockObject = bo;
	}

	/**
	 * <p>
	 * Getter for the field <code>nextBlockObject</code>.
	 * </p>
	 *
	 * @return a {@link org.metricshub.jawk.jrt.BlockObject} object
	 */
	protected final BlockObject getNextBlockObject() {
		return nextBlockObject;
	}

	/**
	 * Obtain all chained BlockObjects as a List,
	 * including this one.
	 * A BlockObject chain cycle causes a runtime exception
	 * to be thrown.
	 *
	 * @return A List of chained BlockObjects, including
	 *         this one.
	 * @throws org.metricshub.jawk.jrt.AwkRuntimeException if the BlockObject
	 *         chain contains a cycle.
	 */
	public List<BlockObject> getBlockObjects() {
		List<BlockObject> retval = new LinkedList<BlockObject>();
		Set<BlockObject> blockObjects = new HashSet<BlockObject>();
		BlockObject ref = this;
		while (ref != null) {
			if (blockObjects.contains(ref)) {
				throw new AwkRuntimeException(
						"Block chain contains a cycle (duplicate) : " + ref.getClass().getName() + " / " + ref.getNotifierTag());
			} else {
				blockObjects.add(ref);
			}
			retval.add(ref);
			ref = ref.getNextBlockObject();
		}
		return retval;
	}

	/**
	 * {@inheritDoc}
	 * Ensure non-evaluation of a BlockObject by throwing an AWK Runtime
	 * exception, in case it leaks into AWK evaluation space.
	 */
	@Override
	public final String toString() {
		throw new AwkRuntimeException("Extension Violation : Cannot AWK-evaluate a BlockObject.");
	}
}
