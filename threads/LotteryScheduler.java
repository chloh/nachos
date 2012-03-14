package nachos.threads;

import nachos.machine.*;

import java.util.Collection;
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
    
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
    	return new PriorityQueue(transferPriority);
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
					waitForAccessQueue.resourceHolder().updateEffectivePriority(this.waitForAccessQueue);
				}
			}
		}
		
		public void updateEffectivePriority(PriorityQueue waitQueue) {
			// Update your hashtable with this effective priority
				if (!waitQueue.transferPriority) {
					return;
				}
				int waitingTickets = waitQueue.getPQ();
				this.resourcePriorities.put(waitQueue,waitingTickets);
				Collection<Integer> allValues = this.resourcePriorities.values();
				int effectiveTickets = this.priority;
				for(Integer thisP: allValues){
					effectiveTickets += thisP;
				}
				this.effectivePriority = effectiveTickets;
				if (this.waitForAccessQueue != null) {
					if (waitForAccessQueue.resourceHolder() != null) {
						waitForAccessQueue.resourceHolder().
						updateEffectivePriority(this.waitForAccessQueue);
					}
				}
		}
	
		
	}
	
	protected class LotteryQueue extends PriorityScheduler.PriorityQueue{

		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
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
			int[] eachTickets = new int[this.waitQueue.size()];
			int totalTickets = 0;
			for(int i = 0; i< this.waitQueue.size();i++){
				ThreadState intTS = waitQueue.get(i);
				int interTickets = intTS.getEffectivePriority();
				totalTickets += interTickets;
				eachTickets[i] = totalTickets;
			}
			Random generator = new Random ( 110100100 );
			int winningTicket = generator.nextInt(totalTickets);
			for(int i = 0; i < eachTickets.length; i++){
				if(winningTicket < eachTickets[i]){
					return waitQueue.get(i);
				}
			}
			return null;
		}
		
	}
		

}
