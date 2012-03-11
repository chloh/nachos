package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	lock = new Lock();
    	CVSpeak = new Condition(lock);
    	CVListen = new Condition(lock);
    	CVBoth = new Condition(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	lock.acquire();
    	while(currentSpeaker != null) {
    		CVSpeak.sleep();
    	}
    	msg = word;
    	currentSpeaker = KThread.currentThread();
    	if(currentListener == null) {
    		CVBoth.sleep();
    	} else {
    		CVBoth.wake();
    		CVBoth.sleep();
    	}
    	
    	lock.release();
    	return;
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	lock.acquire();
    	while(currentListener != null) {
    		CVListen.sleep();
    	}
    	currentListener = KThread.currentThread();
    	if(currentSpeaker == null) {
    		CVBoth.sleep();
    		CVBoth.wake();
    	} else {
    		CVBoth.wake();
    	}
    	int out = msg;
    	currentListener = null;
    	currentSpeaker = null;
    	
    	CVListen.wake();
    	CVSpeak.wake();
    	lock.release();
    	return out;
    }
    
    private Lock lock;
    private Condition CVSpeak, CVListen, CVBoth;
    private KThread currentSpeaker, currentListener;
    private int msg;
}
