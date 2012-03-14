package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Hashtable;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	UserKernel.PIDLock.acquire(); // atomic construction
	PID = totalPID;
	totalPID++;
	UserKernel.PIDLock.release();
	FDs[0] = UserKernel.console.openForReading();
	FDs[1] = UserKernel.console.openForWriting();
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	initialThread = new UThread(this);
	initialThread.setName(name);
	initialThread.fork();
	
	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, vpn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    
 // PART III CODE
    /**
     * Handle the exit() system call. 
     */
    private int handleExit(int a0){
    	try {
	    	//terminate thread?
	    	for (int i = 0; i < FDs.length; i++) {
	    		if (FDs[i] != null) {
	    			FDs[i].close();
	    		}
	    	}
	    	unloadSections();
	    	
	    	/*
	    	 * for child in childIDs:
	    	 *   childIDs[child] = null
	    	 */
	    	for (UserProcess child : childIDs.values()){	// disown children
	    		child.parent = null;
	    	}
			
	    	// tell parent your exit status
	    	if (parent != null) {
	    		parent.childIDsStatus.put(this.PID,a0); //a0 is status
	    	}
	
	    	// if I am root
	    	if (PID == 0) {
	    		Kernel.kernel.terminate();
	    	}
	    	// what child is this referring to?
	    	//child.parent = this; 
	    	return 0;
    	} catch (Exception e){
    		return -1;
    	}
    }
    
    /**
     * Handle the exec() system call. 
     */
    private int handleExec(int a0, int a1, int a2){
    	try {
	    	String name = readVirtualMemoryString(a0,256);
	    	int start = a2;
	    	String[] argv = new String[argc];
	    	UserProcess child = new UserProcess();
	    	child.parent = this;
	    	boolean success;
	    	for(int i = 0; i < argc; i++){
	    		//These are the arguments to the child process, they can
	    		//be arbitrarily long so for each argv[i] we need to loop
	    		//in the memory from the start position until we reach a 
	    		//null terminator. Once we do, we update the start pointer
	    		//so argv[i+1] starts reading from memory at the correct 
	    		//place
	    		// why are we not using readVirtualMemoryString here? It finds the null-terminator for us.
	    		// readVirtualMemoryString(start, 256)
	    		argv[i] = readVirtualMemoryString(start,256);
	    		start += argv[i].length() + 1; //1 from null terminator
	    	}
	    	success = child.execute(name, argv); //call the child with argv
	    	childIDs.put(child.PID, child);
	    	return child.PID;
    	} catch(Exception e) {
    		return -1;
    	}
    }
    
    /**
     * Handle the join() system call.
     */
    private int handleJoin(int a0, int a1) {
    	if (childIDs.containsKey(a0)) { 		
    		UserProcess child = childIDs.get(a0); //check if null
    		child.initialThread.join();
    		int childExitStatus = childIDsStatus.get(child.PID);
    		//convert childExitStatus to array of bytes
    		byte[] exitStatus = Lib.bytesFromInt(childExitStatus);
    		//writeVirtualMemory(a1, childExitStatus);
    		writeVirtualMemory(a1, exitStatus);
    		childIDs.remove(a0);
    		return 0;
    	} else {
    		return -1;
    	}
    }
    
    
    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
	    if (PID == 0) {
	    	Machine.halt();
	    	return 0;
	    } else {
	    	Lib.assertNotReached("Machine.halt() did not halt machine!");
	    	return -1;
	    }
		
    }

 // PART I CODE
    /**
     * Handle the creat() system call. 
     */
    private int handleCreate(int a0){
    	int value = -1;
    	try{ 
    		
    		String name = readVirtualMemoryString(a0,256);
    		boolean full = true;
    		for(int i = 0; i < FDs.length;i++){
    			if(FDs[i] == null) {
    				full = false;
    			}
    		}
    		if(full){
    			return -1;
    		}
    		OpenFile creatFile = UserKernel.fileSystem.open(name,true);
    		//we can only have 16 files make sure to check before making
    		if (creatFile != null){
    			for(int i = 0; i < FDs.length; i++){
    				if (FDs[i] == null) {
						FDs[i] = creatFile;
						positions[i] = 0;
						value = i;
    				}
    			}
    			return value; // either an index [0,16] or -1
    		} else {
    			return -1;
    		}
    	} catch(Exception e) {
			return -1;
    	}
    }

    
    /**
     * Handle the open() system call. 
     */
    private int handleOpen(int a0){
    	try {
    		String name = readVirtualMemoryString(a0,256);
    		OpenFile openFile = UserKernel.fileSystem.open(name,false);
    		int value = -1;
    	    if (openFile == null) {
    	    	return -1;
    	    } else {
    			for(int i = 0; i < FDs.length; i++){
    				if (FDs[i] == null) {
    					FDs[i] = openFile;
    					positions[i] = 0;
    					value = i;
    				}
    			}
    			return value; // value is initially -1, returns if no successful FD, else returns FD
    	    }
    	}catch(Exception e) {
    		return -1;
    	}
    }
   
	/**
     * Handle the read() system call. 
     */
    private int handleRead(int a0, int a1, int a2){
    	try {
    		if(FDs[a0] != null){
    			byte[] buffer = new byte[a2];
    			int pos = positions[a0];
    			FDs[a0].read(pos, buffer, 0, a2);
    			positions[a0] += a2;
    			return writeVirtualMemory(a1,buffer,0,a2); // is size -> a2
    		} else {
    			return -1;
    		}
    	} catch(Exception e) {
    		return -1;
    	}
    }
    
    /**
     * Handle the write() system call. 
     */
    private int handleWrite(int a0, int a1, int a2){
    	try{
	    	if (FDs[a0] != null) {
	    		byte[] buffer = new byte[a2];
	    		int start = positions[a0];
	    		int amount = readVirtualMemory( a1, buffer, 0, a2);
	    		positions[a0] += amount;
	    		return FDs[a0].write(start, buffer, 0, amount);
	    	} else {
	    		return -1;
	    	}
    	} catch(Exception e) {
    		return -1;
    	}
    }
    
    /**
     * Handle the close() system call. 
     */
    private int handleClose(int a0){
    	try {
    		if (FDs[a0] != null) {
    			FDs[a0].close();
    			FDs[a0] = null;
    			positions[a0] = 0;
    			return 0;
    		} else {
    			return -1;
    		}
    	} catch(Exception e) {
    		return -1;
    	}
    }
    
    /**
     * Handle the unlink() system call. 
     */
    private int handleUnlink(int a0){
    	try{
    		String name = readVirtualMemoryString(a0,256);
    		if(UserKernel.fileSystem.remove(name)){
    		//if (StubFileSystem.remove(name)) {
    			return 0;
    		} else {
    			return -1;
    		} 
    	} catch(Exception e) {
    		return -1;
    	}		
    }
    
    private static final int
        syscallHalt = 0,
		syscallExit = 1,
		syscallExec = 2,
		syscallJoin = 3,
		syscallCreate = 4,
		syscallOpen = 5,
		syscallRead = 6,
		syscallWrite = 7,
		syscallClose = 8,
		syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
	    return handleExit(a0);
	case syscallExec:
		return handleExec(a0,a1,a2);
	case syscallJoin:
		return handleJoin(a0,a1);
	case syscallCreate:
		return handleCreate(a0);
	case syscallOpen:
		return handleOpen(a0);
	case syscallRead:
		return handleRead(a0,a1,a2);
	case syscallWrite:
		return handleWrite(a0,a1,a2);
	case syscallClose:
		return handleClose(a0);
	case syscallUnlink:
	    return handleUnlink(a0);
	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    // Group 33 implementation
    // For PART I
    private OpenFile[] FDs = new OpenFile[16];
    private int[] positions = new int[16];
    
    // For PART III
    UThread initialThread;
    private Communicator communicator;
    private UserProcess parent;
    private Hashtable<Integer, UserProcess> childIDs;
    private Hashtable<Integer, Integer> childIDsStatus;
    // I think we need to instantiate the lock above the UserProcess level
    //private static Lock lock;
    private int PID;
    private static int totalPID;
}
