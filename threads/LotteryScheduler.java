package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random; //New from PriorityScheduler

//import PriorityScheduler.PriorityQueue;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    	super();
    }
    
    protected LotteryThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);

		return (LotteryThreadState) thread.schedulingState;
	}
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public LotteryQueue newThreadQueue(boolean transferPriority) {
    	return new LotteryQueue(transferPriority);
    }
    
    /**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE; 
	
	protected class LotteryThreadState extends PriorityScheduler.ThreadState {

		public LotteryThreadState(KThread thread) {
			super(thread);
			setPriority(priorityDefault);
		}

		
		@Override
		public void finish() {
			if (this.joinQueue == null) {
				return;
			}
			this.resourcePriorities.remove(joinQueue);
			Collection<Integer> allValues = this.resourcePriorities.values();
			int effectiveTickets = this.priority;
			for(Integer thisP: allValues){
				effectiveTickets += thisP;
			}
			this.effectivePriority = effectiveTickets;
			if (this.waitForAccessQueue != null) {
				if (waitForAccessQueue.resourceHolder() != null) {
					((LotteryThreadState) waitForAccessQueue.resourceHolder()).updateEffectivePriority(this.waitForAccessQueue);
				}
			}
		}
		
		@Override
		public void updateEffectivePriority(PriorityQueue waitQueue) {
			// Update your hashtable with this effective priority
				if (!waitQueue.transferPriority) {
					return;
				}
				int waitingTickets = waitQueue.getPQ();
				Lib.debug('t', "Printing waitQueue");
				waitQueue.print();
				Lib.debug('t', "waitingPriority: " + waitingTickets);
				this.resourcePriorities.put(waitQueue,waitingTickets);
				Collection<Integer> allValues = this.resourcePriorities.values();
				int effectiveTickets = this.priority;
				for(Integer thisP: allValues){
					effectiveTickets += thisP;
				}
				this.effectivePriority = effectiveTickets;
				Lib.debug('t', "new priority for " + thread + ": " + effectivePriority);
				if (this.waitForAccessQueue != null) {
					if (waitForAccessQueue.resourceHolder() != null) {
						((LotteryThreadState) waitForAccessQueue.resourceHolder()).
						updateEffectivePriority(this.waitForAccessQueue);
					}
				}
		}
	}

	protected class LotteryQueue extends PriorityScheduler.PriorityQueue{

		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
			//waitQueue = new LinkedList<LotteryThreadState>();
		}

		protected int getPQ() {
			int totalTickets = 0;
			if (transferPriority) {
				for (int i = 0; i < this.waitQueue.size(); i++) {
					ThreadState thisTS = waitQueue.get(i);
					totalTickets += thisTS.getEffectivePriority();
				}
			}
			return totalTickets;
		}

		protected ThreadState pickNextThread() {
			Lib.debug('t', "running the new pick next thread" + waitQueue.size());
			int[] eachTickets = new int[this.waitQueue.size()];
			int totalTickets = 0;
			for(int i = 0; i< this.waitQueue.size();i++){
				ThreadState intTS = waitQueue.get(i);
				int interTickets = intTS.getEffectivePriority();
				totalTickets += interTickets;
				eachTickets[i] = totalTickets;
				Lib.debug('t', "interTickets " + interTickets);
			}
			if (this.waitQueue.isEmpty()){
				return null;
			} else {
				Random generator = new Random ( 110100100 );
				int winningTicket = generator.nextInt(totalTickets);
				for(int i = 0; i < eachTickets.length; i++){
					if(winningTicket < eachTickets[i]){
						return waitQueue.get(i);
					}
				}
			}
			return null;
		}
		
		//protected LinkedList<LotteryThreadState> waitQueue;
		//protected LotteryThreadState resourceHolder;
	}


}