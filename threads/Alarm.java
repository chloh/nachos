package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */

	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
		this.waitQueue = new PriorityQueue<KThread>(10, new Comparator<KThread>() {
			public int compare(KThread t0, KThread t1) {
				if(t0.time < t1.time) {
					return -1;
				} else if (t0.time > t1.time) {
					return 1;
				} else {
					return 0;
				}
			}
		}
		);

	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread
	 * that should be run.
	 */
	public void timerInterrupt() {
		long currentTime = Machine.timer().getTime();
		long wakeTime;
		KThread thread;
		
		boolean intStatus = Machine.interrupt().disable();
		while(!waitQueue.isEmpty()) {
			thread = waitQueue.poll();
			wakeTime = thread.time;
			if(wakeTime <= currentTime) {
				thread.ready();
			} else {
				waitQueue.offer(thread);
				break;
			}
		}
		Machine.interrupt().restore(intStatus);
		
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks,
	 * waking it up in the timer interrupt handler. The thread must be
	 * woken up (placed in the scheduler ready set) during the first timer
	 * interrupt where
	 *
	 * <p><blockquote>
	 * (current time) >= (WaitUntil called time)+(x)
	 * </blockquote>
	 *
	 * @param	x	the minimum number of clock ticks to wait.
	 *
	 * @see	nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		long wakeTime = Machine.timer().getTime() + x;
		KThread currentThread = KThread.currentThread();
		
		boolean intStatus = Machine.interrupt().disable();
		currentThread.time = wakeTime;
		waitQueue.add(currentThread);
		
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

	}
	private PriorityQueue<KThread> waitQueue;
}
