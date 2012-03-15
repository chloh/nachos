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
		this.childIDs = new Hashtable<Integer,UserProcess>();
		this.childIDsStatus = new Hashtable<Integer,Integer>();
		this.FDs = new OpenFile[16];

		((UserKernel) Kernel.kernel).PIDLock.acquire();
		PID = totalPID;
		((UserKernel) Kernel.kernel).currentProcesses.add(PID);
		Lib.debug('c', "Process " + PID + " constructed");

		totalPID++;

		UserKernel.PIDLock.release();

		FDs[0] = UserKernel.console.openForReading();
		Lib.debug('c', "after open for reading");
		FDs[1] = UserKernel.console.openForWriting();
		Lib.debug('c', "after open for writing");
		readyToJoin = new Semaphore(0);
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
		if (!load(name, args)) {
			Lib.debug('e', "loading failed");
			return false;
		}
		Lib.debug('e', "before execute" + PID);
		initialThread = new UThread(this);
		//readyToJoin.V();
		initialThread.setName(name);
		initialThread.fork();

		Lib.debug('e', "new thread forked" + PID);
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		Lib.debug('c', "saveState()" + PID);
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Lib.debug('c', "restoreState()" + PID);
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
		Lib.debug('c', "readVirtualMemoryString");

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		Lib.debug('c', "bytesRead to get string: "+bytesRead);
		for (int length=0; length<bytesRead; length++) {
			Lib.debug('c', "string up to length: "+new String(bytes, 0, length));
			if (bytes[length] == 0) {
				Lib.debug('c', "got this string: "+new String(bytes, 0, length));
				return new String(bytes, 0, length);
			}
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
		int val = 0;
		try {
			Lib.debug('c', "reading virtualMemory");
			Lib.debug('c', "reading length: "+length);

			// find vpn, which will give us the ppn and the offset on the page we’re reading
			int startVPN = Processor.pageFromAddress(vaddr);
			int readOffset = Processor.offsetFromAddress(vaddr);
			
			int newAddr = startVPN*pageSize;
			int numPages = 0;
			int currentPage = startVPN;
			Lib.debug('c', "length: "+length);
			Lib.debug('c', "pageSize: "+pageSize);
			Lib.debug('c', "readOffset: "+readOffset);
			while (newAddr < vaddr+length) {
				numPages++;
				newAddr += pageSize;
				pageTable[currentPage].used = true;
				currentPage++;
			}
			
			// make an array of ppns in case the length of what we’re reading overflows to more than one page
			int[] ppnArray = new int[numPages];
			for (int i = 0; i < ppnArray.length; i++) {
				ppnArray[i] = pageTable[startVPN+i].ppn;
			}
			Lib.debug('c', "numPages: "+numPages);
			Lib.debug('c', "pageTable.size: "+pageTable.length);
			Lib.debug('c', "ppnArray.size: "+ppnArray.length);
			val = ((UserKernel) Kernel.kernel).readPhysMem(ppnArray, readOffset, length, data, offset);
			Lib.debug('c', "read this much from phys memory: "+val);
			Lib.debug('c', "in buffer: " + new String(data));
			return val;
		} catch (Exception e) {
			Lib.debug('c', "exception in readVirtualMemory: " + e.getMessage());
			return val;
		}
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
		// TODO: don't change the pageTable bits until we have successfully written to them
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
		int val = 0;
		try {
			Lib.debug('c', "writing virtualMemory");
			Lib.debug('c', "how much: "+length);
			Lib.debug('c', "data we're writing: "+ new String(data));
			// find vpn and the offset on the page we’re reading
			int startVPN = Processor.pageFromAddress(vaddr);
			int writeOffset = Processor.offsetFromAddress(vaddr);

			int newAddr = startVPN*pageSize;
			int numPages = 0;
			int currentPage = startVPN;
			while (newAddr < vaddr+length) {
				numPages++;
				newAddr += pageSize;
				pageTable[currentPage].used = true;
				pageTable[currentPage].dirty = true;
				currentPage++;
			}

			int[] ppnArray = new int[numPages];
			for (int i = 0; i < ppnArray.length; i++) {
				ppnArray[i] = pageTable[startVPN+i].ppn;
			}
			val = ((UserKernel) Kernel.kernel).writePhysMem(ppnArray, writeOffset, length, data, offset);
			Lib.debug('c', "Wrote this much to memory: "+ val);
			if (val < length) {
				return -1;
			}
			return ((UserKernel) Kernel.kernel).writePhysMem(ppnArray, writeOffset, length, data, offset);
		} catch (Exception e) {
			Lib.debug('c', "exception in writeVirtualMemory: "+e.getMessage());
			return val;
		}
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
		Lib.debug('e', "UserProcess.load(\"" + name + "\")");
		Lib.debug('e', "UserProcess.load num args " + args.length);
		for (int i = 0; i < args.length; i++) {
			Lib.debug('e', "UserProcess.load arg "+i+": "+args[i]);
		}

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			Lib.debug('e', "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			Lib.debug('e', "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				Lib.debug('c', "\tfragmented executable");
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
			Lib.debug('e', "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		Lib.debug('c', "numPages: " + numPages);

		if (!loadSections())
			return false;

		Lib.debug('e', "After loadSections");

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
		pageTable = new TranslationEntry[numPages];
		// TODO: check if the number of pages requested from getMemory is correct
		int [] pages = ((UserKernel) Kernel.kernel).getMemory(numPages);

		for (int i = 0; i < pages.length; i++) {
			pageTable[i] = new TranslationEntry(i, pages[i], true, false, false, false);
		}

		TranslationEntry pageEntry;
		int ppn;
		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;

				pageEntry = pageTable[vpn];
				ppn = pageEntry.ppn;
				section.loadPage(i, ppn);

				if (section.isReadOnly()) {
					pageEntry.readOnly = true;
				}

				//if (section.isReadOnly()) {
				//pageTable[vpn] = new TranslationEntry (vpn, first, true, true, false, false);
				//} else { 
				//pageTable[vpn] = new TranslationEntry (vpn, first, true, false, false, false);
				//}
				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
			}
		}

		Lib.debug('c', "Printing Page table!");
		for (int i = 0; i < pageTable.length; i++) {
			Lib.debug('c', "vpn: " + i + " ppn: " + pageTable[i].ppn);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		int[] pages = new int[numPages];
		for (int s=0; s<numPages; s++) {
			int ppn = pageTable[s].ppn;
			pageTable[s] = null;
			pages[s] = ppn;
		}
		((UserKernel) Kernel.kernel).freeMemory(pages);
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
	 * C args:
	 * int status
	 */
	private int handleExit(int a0){
		try {
			//if (PID == 0) {
			//return -1;
			//}
			Lib.debug('b', "calling exit: PID" + PID);
			if (a0 == -1) {
				parent.childIDsStatus.put(this.PID, a0); //a0 is status
				return 0;
			}
			//terminate thread?
			for (int i = 0; i < FDs.length; i++) {
				if (FDs[i] != null) {
					FDs[i].close();
					FDs[i] = null;
				}
			}


			// disown children
			for (UserProcess child : childIDs.values()) {
				child.parent = null;
			}
			Lib.debug('b', "after disowning: PID" + PID);

			// tell parent your exit status
			if (parent != null) {
				parent.childIDsStatus.put(this.PID, a0); //a0 is status
			}

			if (((UserKernel) Kernel.kernel).currentProcesses.remove(PID)) {
				if (((UserKernel) Kernel.kernel).currentProcesses.isEmpty()) {
					Lib.debug('b', "last process terminating: PID" + PID);
					Lib.debug('b', "before unloadSections: PID" + PID);
					unloadSections();
					Lib.debug('b', "after unloadSections: PID" + PID);
					Kernel.kernel.terminate();
				}
			} else {
				Lib.debug('b', "Unable to remove process from currentProcesses");
			}
			Lib.debug('b', "After termination: PID" + PID);
			Lib.debug('b', "before unloadSections: PID" + PID);
			unloadSections();
			coff.close(); 
			Lib.debug('b', "after unloadSections: PID" + PID);
			readyToJoin.V();
			UThread.finish();
			
			return 0;
		} catch (Exception e){
			Lib.debug('c', "handleExit: "+e.getMessage());
			return -1;
		}
	}

	/**
	 * Handle the exec() system call. 
	 * C args:
	 * char* file: pointer to file name
	 * int argc: number of arguments
	 * char* argv[]: pointer to an array of arguments
	 */
	private int handleExec(int a0, int a1, int a2){
		try {
			Lib.debug('e', "calling exec" + PID);
			String name = readVirtualMemoryString(a0,256);
			int start = a2;
			String[] argv = new String[a1];
			UserProcess child = new UserProcess();
			child.parent = this;
			Lib.debug('e', "name: " + name);
			Lib.debug('e', "num arguments: " + a1);

			byte[] addr = new byte[4];
			int bytesRead = readVirtualMemory(a2, addr);
			int startOfArgs = Lib.bytesToInt(addr, 0);
			boolean success;
			String str;
			Lib.debug('e', "Before loop");
			int currentArg = startOfArgs;
			for(int i = 0; i < a1; i++){
				// TODO: this parser is not getting the arguments correctly
				//These are the arguments to the child process, they can
				//be arbitrarily long so for each argv[i] we need to loop
				//in the memory from the start position until we reach a 
				//null terminator. Once we do, we update the start pointer
				//so argv[i+1] starts reading from memory at the correct 
				//place
				// why are we not using readVirtualMemoryString here? It finds the null-terminator for us.
				// readVirtualMemoryString(start, 256)
				Lib.debug('e', "In loop");
				str = readVirtualMemoryString(currentArg,256);
				Lib.debug('e', "after reading virtual memory");
				if (str == null) {
					argv[i] = "";
				} else {
					argv[i] = str;
				}
				Lib.debug('e', "argv["+i+"]: "+ argv[i]);
				currentArg += argv[i].length() + 1; //1 from null terminator
			}
			Lib.debug('e', "After loop");
			success = child.execute(name, argv); //call the child with argv
			if (!success) {
				return -1;
			}
			childIDs.put(child.PID, child);
			Lib.debug('e', "exiting exec" + PID);
			return child.PID;
		} catch(Exception e) {
			return -1;
		}
	}

	/**
	 * Handle the join() system call.
	 * C args:
	 * int processID: pid of child to join on
	 * int* status: pointer to exit status of child
	 */
	private int handleJoin(int a0, int a1) {
		Lib.debug('j', "calling join" + PID);
		try {
			Lib.debug('j', "status: "+a1);
			if (childIDs.containsKey(a0)) { 		
				UserProcess child = childIDs.get(a0); //check if null
				if (child == null) {
					return -1;
				}
				/*
				if (child.initialThread == null) {
					Lib.debug('j', "before ready to join");
					child.readyToJoin.P();
				}*/
				child.readyToJoin.P();

				Lib.debug('j', "joining on child");
				child.initialThread.join();
				Lib.debug('j', "my PID: "+PID);
				Lib.debug('j', "child done");
				int childExitStatus = childIDsStatus.get(child.PID);
				Lib.debug('j', "child exit status: "+childExitStatus);
				//convert childExitStatus to array of bytes
				byte[] exitStatus = Lib.bytesFromInt(childExitStatus);
				//writeVirtuajlMemory(a1, childExitStatus);
				writeVirtualMemory(a1, exitStatus);
				childIDs.remove(a0);
				Lib.debug('j', "exiting join: " + PID);
				if (childExitStatus == 0) {
					return 1;
				} else {
					return 0;
				}
			} else {
				return -1;
			}
		} catch (Exception e) {
			Lib.debug('j', "join error: " + e.getMessage());
			return -1;
		}
	}


	/**
	 * Handle the halt() system call. 
	 */
	private int handleHalt() {
		Lib.debug('c', "calling halt" + PID);
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
	 * C args:
	 * char* name: pointer to name
	 */
	private int handleCreate(int a0){
		int value = -1;
		try{ 
			Lib.debug('c', "calling creat: PID" + PID);
			String name = readVirtualMemoryString(a0,256);
			if (name == null) {
				return -1;
			}
			boolean full = true;
			for(int i = 0; i < FDs.length;i++){
				if(FDs[i] == null) {
					full = false;
					break;
				}
			}
			if(full){
				return -1;
			}
			Lib.debug('c', "opening file");
			Lib.debug('c', "name: "+name);
			OpenFile creatFile = UserKernel.fileSystem.open(name,true);
			//we can only have 16 files make sure to check before making
			if (creatFile != null){
				Lib.debug('c', "creatFile is not null");
				for(int i = 0; i < FDs.length; i++){
					if (FDs[i] == null) {
						FDs[i] = creatFile;
						//positions[i] = 0;
						value = i;
						break;
					}
				}
				Lib.debug('c', "exiting creat" + PID);
				return value; // either an index [0,16] or -1
			} else {
				return -1;
			}
		} catch(Exception e) {
			Lib.debug('c', "exception in creat: " + e.getMessage());
			return -1;
		}
	}


	/**
	 * Handle the open() system call. 
	 * C args:
	 * char* name: pointer to name
	 */
	private int handleOpen(int a0){
		int value = -1;
		try {
			Lib.debug('c', "calling open" + PID);
			String name = readVirtualMemoryString(a0,256);
			OpenFile openFile = UserKernel.fileSystem.open(name, false);
			if (openFile == null) {
				return -1;
			} else {
				for(int i = 0; i < FDs.length; i++){
					if (FDs[i] == null) {
						FDs[i] = openFile;
						//positions[i] = 0;
						value = i;
						break;
					}
				}
				Lib.debug('c', "exiting open " + PID);
				return value; // value is initially -1, returns if no successful FD, else returns FD
			}
		}catch(Exception e) {
			return -1;
		}
	}

	/**
	 * Handle the read() system call. 
	 * C args:
	 * int fileDescriptor
	 * void* buffer: location to put stuff we read
	 * int count: how much to read
	 */
	private int handleRead(int a0, int a1, int a2){
		try {
			Lib.debug('c', "calling read");
			if (a0 >= 16 || a0 < 0) {
				return -1;
			}
			if(FDs[a0] != null){
				byte[] buffer = new byte[a2];
				//int pos = positions[a0];
				int amount = FDs[a0].read(buffer, 0, a2);
				Lib.debug('c', "read in: "+new String(buffer));
				//FDs[a0].read(pos, buffer, 0, a2);
				//positions[a0] += amount;
				Lib.debug('c', "exiting read" + PID);
				int val = writeVirtualMemory(a1, buffer, 0, amount);
				return val;
			} else {
				return -1;
			}
		} catch(Exception e) {
			return -1;
		}
	}

	/**
	 * Handle the write() system call. 
	 * C args
	 * int fileDescriptor
	 * void* buffer: content to write
	 * int count: how much to write
	 */
	private int handleWrite(int a0, int a1, int a2){
		try{
			if (a0 >= 16 || a0 < 0) {
				return -1;
			}
			Lib.debug('c', "calling write " + PID);
			Lib.debug('c', "fd: " + a0);
			Lib.debug('c', "vaddr: " + a1);
			Lib.debug('c', "size: " + a2);

			if (FDs[a0] != null) {
				byte[] buffer = new byte[a2];
				//int start = positions[a0];
				//	    		Lib.debug('c', "before readvirtuelmem in write syscall");
				int amount = readVirtualMemory( a1, buffer, 0, a2);
				//positions[a0] += amount;
				Lib.debug('c', "what's in buffer now: "+new String(buffer));
				int val = 0;
				//for (int i = 0; i < amount; i++) {
				val = FDs[a0].write(buffer, 0, amount);
				//}
				Lib.debug('c', "wrote out how much to FD "+a0+": "+val);
				return val;
			} else {
				return -1;
			}
		} catch(Exception e) {
			Lib.debug('c', "exception in write: "+e.getMessage());
			return -1;
		}
	}

	/**
	 * Handle the close() system call. 
	 * C args
	 * int fileDescriptor
	 */
	private int handleClose(int a0){
		try {
			Lib.debug('c', "calling close" + PID);
			// added bullet proofing
			if(a0 >= 16 || a0 < 0){
				return -1;
			}
			if (FDs[a0] != null) {
				FDs[a0].close();
				FDs[a0] = null;
				//positions[a0] = 0;
				Lib.debug('c', "ending close" + PID);
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
	 * C args
	 * char* name: pointer to name
	 */
	private int handleUnlink(int a0){
		try{
			Lib.debug('c', "calling unlink" + PID);
			String name = readVirtualMemoryString(a0,256);
			if (name == null) {
				return -1;
			} else {
				if(UserKernel.fileSystem.remove(name)){
					return 0;
				} else {
					return -1;
				} 
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
		//Lib.debug('s', "calling syscall " + syscall);
		Lib.debug('c', "calling syscall " + syscall);
		switch (syscall) {
		case syscallHalt:
			//Lib.debug('b', "halt");
			return handleHalt();
		case syscallExit:
			//Lib.debug('b', "exit");
			return handleExit(a0);
		case syscallExec:
			//Lib.debug('b', "exec");
			return handleExec(a0,a1,a2);
		case syscallJoin:
			//Lib.debug('b', "join");
			return handleJoin(a0,a1);
		case syscallCreate:
			//Lib.debug('b', "create");
			return handleCreate(a0);
		case syscallOpen:
			//Lib.debug('b', "open");
			return handleOpen(a0);
		case syscallRead:
			//Lib.debug('b', "read");
			return handleRead(a0,a1,a2);
		case syscallWrite:
			//Lib.debug('b', "write");
			return handleWrite(a0,a1,a2);
		case syscallClose:
			//Lib.debug('b', "close");
			return handleClose(a0);
		case syscallUnlink:
			//Lib.debug('b', "unlink");
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
			// if error
			//if (result == -1) {
			//handleExit(1);
			//break;
			//}
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;				       

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			Lib.debug('b', "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			// TODO: fix this.  not really sure what it should do
			// modified these based on piazza post @424
			handleExit(-1);
			break;
			//Lib.assertNotReached("Unexpected exception");
		}
	}

	// TODO: fill this in and use it where ever we use virtual addresses
	boolean validAddress(int vaddr) {
		return false;
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
	private OpenFile[] FDs;
	//private int[] positions = new int[16];

	// For PART III
	UThread initialThread;
	private UserProcess parent;
	private Hashtable<Integer, UserProcess> childIDs;
	private Hashtable<Integer, Integer> childIDsStatus;
	// I think we need to instantiate the lock above the UserProcess level
	//private static Lock lock;
	private int PID;
	private static int totalPID;
	//private static Lock mutex = new Lock();
	public Semaphore readyToJoin;
}
