package nachos.threads;

import nachos.machine.*;

import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			waitQueue = new LinkedList<ThreadState>();
			resourceHolder = null;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			if (this.resourceHolder() != null) {
				this.resourceHolder().resourcePriorities.remove(waitQueue);
				if(!this.resourceHolder().resourcePriorities.isEmpty()) {
					//Lib.debug('t', "This thread has resourcePriorities");
					PriorityQueue resourceQueue = this.resourceHolder().resourcePriorities.keys().nextElement();
					if (resourceQueue.transferPriority) {
						this.resourceHolder().updateEffectivePriority(resourceQueue);
					}
				} else {
					this.resourceHolder().effectivePriority = this.resourceHolder().priority;
				}
			}
			
			ThreadState nextTS = pickNextThread();
			if(nextTS != null){
				this.waitQueue.remove(nextTS);
				this.acquire(nextTS.thread);
				return nextTS.thread;
			}
			else{
				return null;
			}
		}

		/**Get the threadstate which has acquired this resource**/
		protected ThreadState resourceHolder() {
			return resourceHolder;
		}

		/** UPDATE THIS PRIORITYQUEUES MAX EFFECTIVE PRIORITY **/
		protected int getPQ() {
			int maxEffectivePriority = -1;
			ThreadState thisTS;
			if (transferPriority) {
				for (int i = 0; i < this.waitQueue.size(); i++) {
					thisTS = waitQueue.get(i);
					if (thisTS.getEffectivePriority() > maxEffectivePriority){
						maxEffectivePriority = thisTS.getEffectivePriority();
					}
				}
			}
			return maxEffectivePriority;
		}


		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			Lib.debug('t', "Choosing next thread");
			this.print();
			// Look at the thread on the top of the queue  
			ThreadState nextTS = null;
			ThreadState intTS;
			int interPriority; // intermediate priority variable
			int maxPriority = -1; // max priority
			
			for (int i = 0; i < this.waitQueue.size(); i++) {
				// should use iterator instead
				intTS = waitQueue.get(i); //intermediate threadState pointer
				
				interPriority = intTS.getEffectivePriority();
				/*
				if (transferPriority) {
					interPriority = intTS.getEffectivePriority();
				}// else {
					//interPriority = intTS.getPriority();
				//}
				 * */
				
				if (interPriority > maxPriority) {
					nextTS = intTS;
					maxPriority = interPriority;
				}
			}
			return nextTS;
		}

		/** PRINT THAT SHIT **/

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState intTS;
			for (int i = 0; i < this.waitQueue.size(); i++) {
				// use an iterator
				intTS = waitQueue.get(i);
				Lib.debug('t', intTS.thread+": "+intTS.getEffectivePriority());
				/*
				if (transferPriority) {
					//System.out.println(intTS.thread+": "+intTS.getEffectivePriority());
					Lib.debug('t', intTS.thread+": "+intTS.getEffectivePriority());
				} else {
					//System.out.println(intTS.thread+": "+intTS.getPriority());
					Lib.debug('t', intTS.thread+": "+intTS.getPriority());
				}
				*/
			}
			return;	    
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		protected LinkedList<ThreadState> waitQueue;
		protected ThreadState resourceHolder;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.resourcePriorities = new Hashtable<PriorityQueue,Integer>();
			setPriority(priorityDefault);
			waitForAccessQueue = null;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			return effectivePriority;
		}

		/**
		 * BLah blah blac.
		 *
		 * @return      VOID
		 */
		public void updateEffectivePriority(PriorityQueue waitQueue) {
			// Update your hashtable with this effective priority
			if (!waitQueue.transferPriority) {
				return;
			}
			int maxPriority = waitQueue.getPQ();
			Lib.debug('t', "Printing waitQueue");
			waitQueue.print();
			Lib.debug('t', "maxPriority: " + maxPriority);
			this.resourcePriorities.put(waitQueue,maxPriority);

			// Find the max priority of your resources after update
			Collection<Integer> allValues = this.resourcePriorities.values();
			//Integer[] allVals = (Integer[]) allValues.toArray();
			int maxP = this.priority;
			for(Integer thisP: allValues){
				if(thisP.intValue() > maxP){
					maxP = thisP;
				}
			}

			// Change this thread state's maxEffectivePriority (max donation)
			this.effectivePriority = maxP;
			Lib.debug('t', "new priority for " + thread + ": " + effectivePriority);

			// Now donate to the wait Queue that this thread state is waiting on.
			if (this.waitForAccessQueue != null) {
				if (waitForAccessQueue.resourceHolder() != null) {
					waitForAccessQueue.resourceHolder().updateEffectivePriority(this.waitForAccessQueue);
				}
			}
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			Lib.debug('t', "Setting priority from " + this.priority + " to " + priority);
			if (this.priority == priority)
				return;
			
			this.priority = priority;
			
			if (waitForAccessQueue == null) {
				this.effectivePriority = priority;
			} 
			
			//Now we need to update current thread state's priority
			if(!this.resourcePriorities.isEmpty()) {
				Lib.debug('t', "This thread has resourcePriorities");
				PriorityQueue resourceQueue = this.resourcePriorities.keys().nextElement();
				if (resourceQueue.transferPriority) {
					this.updateEffectivePriority(resourceQueue);
				}
			}

			if(this.waitForAccessQueue != null){
				Lib.debug('t', "This thread is in a waitForAccessQueue");
			    if(this.waitForAccessQueue.transferPriority){
			        this.waitForAccessQueue.resourceHolder().updateEffectivePriority(this.waitForAccessQueue);
			    }
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			waitQueue.waitQueue.add(this);
			this.waitForAccessQueue = waitQueue;
			
			if (waitQueue.transferPriority){
			    ThreadState rHolder = waitQueue.resourceHolder();
			    Lib.debug('t', "rHolder: " + rHolder.thread);
			    Lib.debug('t', "rHolder priority: " + rHolder.effectivePriority);
			    Lib.debug('t', "my priority: " + effectivePriority);
			    rHolder.updateEffectivePriority(waitQueue);
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			this.waitForAccessQueue = null;
			waitQueue.resourceHolder = this;
			if (waitQueue.transferPriority){
			    this.updateEffectivePriority(waitQueue);
			}
		}	
		
		public void join(KThread thread){
			ThreadState currentTS = getThreadState(KThread.currentThread());
	   		int newDonation = currentTS.getEffectivePriority();
	   		((ThreadState) thread.schedulingState).joinQueue = new PriorityQueue(true);
	   		boolean intStatus = Machine.interrupt().disable();
	   		joinQueue.acquire(thread);
	   		joinQueue.waitForAccess(KThread.currentThread());
	   		Machine.interrupt().restore(intStatus);
	    	this.resourcePriorities.put(joinQueue,newDonation);
	    	
	    	if (this.waitForAccessQueue != null) {
	          if(this.waitForAccessQueue.transferPriority) {
	           	    this.waitForAccessQueue.resourceHolder().updateEffectivePriority(this.waitForAccessQueue);
	          }
	    	}
		}
		
		public void finish() {
			if (this.joinQueue == null) {
				return;
			}
		    this.resourcePriorities.remove(joinQueue);
			Collection<Integer> allValues = this.resourcePriorities.values();
			int maxP = this.priority;
			for(Integer thisP: allValues){
				if(thisP.intValue() > maxP){
					maxP = thisP;
				}
			}
			this.effectivePriority = maxP;
			Lib.debug('t', "new priority for " + thread + ": " + effectivePriority);
			if (this.waitForAccessQueue != null) {
				if (waitForAccessQueue.resourceHolder() != null) {
					waitForAccessQueue.resourceHolder().updateEffectivePriority(this.waitForAccessQueue);
				}
			}
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int effectivePriority;
		protected PriorityQueue joinQueue;
		protected Hashtable<PriorityQueue, Integer> resourcePriorities;
		protected PriorityQueue waitForAccessQueue;
	}
}
