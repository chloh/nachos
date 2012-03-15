package nachos.userprog;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */


	public UserKernel() {
		super();
	}

	public int[] getMemory(int numPages) {
		lock.acquire();
		int[] arr = new int[numPages];
		if (pageList.size() > numPages) {
			for(int i=0; i<numPages; i++){
				arr[i] = pageList.removeFirst();
			}
		} else {
			lock.release();
			//return error;
		}
		lock.release();
		return arr; 
	}

	public void freeMemory(int[] pageArray) {
		for(int i=0; i<pageArray.length; i++){
			lock.acquire();
			byte[] memory = Machine.processor().getMemory();
			for (int j = pageArray[i]*pageSize; j < (pageArray[i]+1)*pageSize; j++) {
				memory[j] = 0;
			}
			// TODO: zero out pages in memory before freeing them
			pageList.addFirst(pageArray[i]);
			lock.release();
		}
	}

	public int readPhysMem(int[] ppnArray, int readOffset, int length, byte[] data, int writeOffset) {
		int amount = 0;
		try {
			Lib.debug('c', "reading physical memory");
			byte[] memory = Machine.processor().getMemory();
			// determine where to start reading the memory
			int start = (ppnArray[0] * pageSize) + readOffset;
			int end = (ppnArray[0] + 1) * pageSize;
			amount = end - start;
			//check that bounds do not exceed memory
			if (length <= amount) {
				System.arraycopy(memory, start, data, writeOffset, length);
				return length;
			} else {
				System.arraycopy(memory, start, data, writeOffset, amount);
			}
			// if there are more pages we need to access
			if (ppnArray.length > 1) {
				length -= amount;
				int amountAdded;
				for (int i = 1; i < ppnArray.length; i++) {
					start = (ppnArray[i]*pageSize);
					// if the last page is not a whole page
					if (length < pageSize) amountAdded = length;
					else { 
						amountAdded = pageSize;
					}
					amount += amountAdded;
					writeOffset += amountAdded;
					System.arraycopy(memory, start, data, writeOffset, amountAdded);
					length -= amountAdded;
				}
			}
			return amount;
		} catch(Exception e) {
			Lib.debug('c', "exception in readPhysMem: " + e.getMessage());
			return amount;
		}
	}

	public int writePhysMem(int[] ppnArray, int writeOffset, int length, byte[] data, int readOffset) {
		int amount = 0;
		try {
			// TODO: check that bounds do not exceed memory
			byte[] memory = Machine.processor().getMemory();
			
			// determine where to start writing the memory
			int start = (ppnArray[0] * pageSize) + writeOffset;		
			int end = (ppnArray[0] + 1) * pageSize;
			amount = end - start;
			if (length <= amount) {
				System.arraycopy(data, readOffset, memory, start, length);
				return length;
			} else {
				System.arraycopy(data, readOffset, memory, start, amount);
			}
			// if there are more pages we need to access
			if (ppnArray.length > 1) {
				length -= amount;
				int amountAdded;
				for (int i = 1; i < ppnArray.length; i++) {
					start = (ppnArray[i]*pageSize);
					// if the last page is not a whole page
					if (length < pageSize) amountAdded = length;
					else { 
						amountAdded = pageSize;
					}
					amount += amountAdded;
					writeOffset += amountAdded;
					System.arraycopy(data, readOffset, memory, start, amountAdded);				
					length -= amountAdded;
				}
			}
			return amount;
		} catch(Exception e) {
			Lib.debug('c', "writePhyMem had an exception: "+e.getMessage());
			return amount;
		}
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());
		lock = new Lock();
		PIDLock = new Lock();
		pageList = new LinkedList<Integer>();
		int numPhysPages = Machine.processor().getNumPhysPages();
		for(int i=0; i<numPhysPages; i++){
			pageList.add(i);
		}
		currentProcesses = new HashSet<Integer>();
		//savedPStates = new Hashtable<Integer, PState>();



		Lib.debug('c', "creating the UserKernel");

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() { exceptionHandler(); }
		});
	}

	/**
	 * Test the console device.
	 */	
	public void selfTest() {
		super.selfTest();
		
		//System.out.println("Testing the console device. Typed characters");
		//System.out.println("will be echoed until q is typed.");

		//char c;

		//do {
			//Lib.debug('c', "before readByte");
			//c = (char) console.readByte(true);
			//Lib.debug('c', "before writeByte");
			//console.writeByte(c);
			//Lib.debug('c', "after writeByte");
		//}
		//while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 *
	 * @return	the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever
	 * a user instruction causes a processor exception.
	 *
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 *
	 * @see	nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		Lib.debug('c', "calling run in userkernel");

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();	
		Lib.debug('c', "shellprogram: " + shellProgram);

		Lib.assertTrue(process.execute(shellProgram, new String[] { }));
		Lib.debug('c', "After execute");

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	private LinkedList<Integer> pageList;
	Lock lock; 

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	// Adding the PIDLock here
	public static Lock PIDLock;;
	//public static Hashtable<Integer, PState> savedPStates;
	public HashSet<Integer> currentProcesses;

	private static int pageSize = Machine.processor().pageSize;
}
