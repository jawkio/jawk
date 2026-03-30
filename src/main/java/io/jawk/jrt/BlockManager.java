package io.jawk.jrt;

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

import java.util.LinkedList;
import java.util.List;

/**
 * Manages multiple blocking code segments simultaneously such that
 * unblocking one block condition releases the block of all other
 * block code segments.
 *
 * @see BlockObject
 * @author Danny Daglas
 */
public class BlockManager {

	private String notifier = null;

	/**
	 * Executes all block segments simultaneously, waiting for
	 * one block release.
	 * <p>
	 * The algorithm is as follows:
	 * <ul>
	 * <li>Collect linked block objects into a List.
	 * <li>Spawn a BlockThread for each block object.
	 * <li>Wait for notification from any of the BlockThreads.
	 * <li>Interrupt remaining block threads.
	 * <li>Wait for each BlockThread to die.
	 * <li>Return the block object notifier which satisfied their block condition.
	 * </ul>
	 * <p>
	 * And, the BlockThread algorithm is as follows:
	 * <ul>
	 * <li>try, catch for InterruptedException ...
	 * <ul>
	 * <li>Execute the BlockObject block segment.
	 * <li>Assign the notifier from this BlockObject
	 * if one isn't already assigned (to mitigate
	 * a race condition).
	 * <li>Notify the BlockManager.
	 * </ul>
	 * <li>If interrupted, do nothing and return.
	 * </ul>
	 *
	 * @param bo BlockObject to employ. Other block objects
	 *        may be linked to this block object. In this event,
	 *        employ all block objects simultaneously.
	 * @return a {@link java.lang.String} object
	 */
	public String block(BlockObject bo) {
		// get all block objects
		List<BlockObject> bos = bo.getBlockObjects();
		// each block object contains a wait statement
		// (either indefinite or timed)

		// for each block object
		// spawn a thread (preferably using a threadpool)
		// do the wait
		// signal a break in the block
		// interrupt all other threads, resulting in InterruptedExceptions

		List<Thread> threadList = new LinkedList<Thread>();
		String blockNotifier = null;
		synchronized (this) {
			notifier = null;
			for (BlockObject blockobj : bos) {
				// spawn a thread
				Thread t = new BlockThread(blockobj);
				t.start();
				threadList.add(t);
			}

			// now, wait for notification from one of the BlockThreads
			while (notifier == null) {
				try {
					this.wait();
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}
			blockNotifier = notifier;
		}

		// block successful, interrupt other blockers
		// and wait for thread deaths
		for (Thread t : threadList) {
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

		// return who was the notifier
		return blockNotifier;
	}

	private final class BlockThread extends Thread {

		private BlockObject bo;

		private BlockThread(BlockObject bo) {
			setName("BlockThread for " + bo.getNotifierTag());
			this.bo = bo;
		}

		@Override
		public void run() {
			try {
				bo.block();
				synchronized (BlockManager.this) {
					if (notifier == null) {
						notifier = bo.getNotifierTag();
					}
					BlockManager.this.notifyAll();
				}
			} catch (InterruptedException ie) {
				currentThread().interrupt();
			} catch (RuntimeException re) {
				throw re;
			}
		}
	}
}
