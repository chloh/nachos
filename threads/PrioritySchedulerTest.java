package nachos.threads;

import nachos.machine.*;
import nachos.ag.*;
import java.util.Random;

/**
 * A Tester for the PriorityScheduler class
 */
public class PrioritySchedulerTest extends AutoGrader{

	/**
	 * PingPongWorker class, which implements a thread runs in an infinite loop
	 * and randomly changes its own or its partner thread's priority. 
	 */
	private static class PingPongWorker implements Runnable {

		/** Constructor 
		 */
		PingPongWorker(String name, char marker) {
			this.name = name;
			this.marker = marker;
			this.partner = null;
			this.amIDone = false;
		}

		/** setPartner(): set the partner thread for the 
		 *  thread running this PingPongWorker.
		 */
		public void setPartner(KThread partner) {
			this.partner = partner;
		}

		/** terminate(): tell this PingPongWorker to terminate.
		 */
		public void terminate() {
			this.amIDone = true;
		}

		/** run() method for the PingPongWorker thread.
		 */
		public void run() {

			System.out.println("** "+name+" begins");

			/* Allocate a random number generator */
			Random rng = new Random();

			/* Loop until somebody calls terminate() */
			while(amIDone == false) {
				/* "Sleep" for some amount of time */
				long wakeTime = Machine.timer().getTime() + 50;
				while (wakeTime > Machine.timer().getTime()) { 
					KThread.yield();
				}
				/* Print some character to show some output */
				System.out.print(this.marker);

				/* Roll a dice */
				int dice = rng.nextInt(100);
				if ((dice % 20 == 0) || (dice % 20 == 1)) {
					/* 2 times out of 20 do a priority change */

					/* Pick a priority change target with 50% probability */
					KThread target;
					if (dice % 20 == 0)
						target = KThread.currentThread();
					else
						target = partner;

					/* Pick a new random priority, different from the target's current one */
					int oldPriority = target.getPriority();
					int newPriority = rng.nextInt(6);
					while (newPriority == oldPriority) {
						newPriority = rng.nextInt(6);
					}

					/* Update the target's priority */
					System.out.println(target.getName()+"'s priority changed from "+
							oldPriority+" to "+newPriority);
					target.setPriority(newPriority);
				}
			}

			System.out.println("** "+name+" exits.");
		}

		private String name;		// The worker's name 				
		private KThread partner;	// The worker's partner thread 			
		private char marker;		// The character to be printed at each iteration 
		private boolean amIDone;	// true if the worker must terminate 
	}

	/* runPingPongTest(): runs the ping-pong test for the PriorityScheduler class
	 */
	private static void runPingPongTest() {

		System.out.println("#### Ping-Pong test starts ####\n");
		System.out.println("    The output will be sequences of '+' and '-' symbols, with\n"+
				"    notification of priority changes. Visual inspection of this\n"+
				"    output tells you whether the test is successful or not. When\n"+
				"    both threads have the same priority, they should print '+' and\n"+
				"    '-' in an interleaved manner. Otherwise, only the thread with\n"+
				"    the highest priority (i.e., smalled numerical priority value)\n"+
				"    should print its marker. The thread printing '+' is called 'Plus'\n"+
				"    and the thread printing '-' is called 'Minus'. Initially both\n"+
		"    both threads have the same priority.\n");

		/* Create two ping-pong workers in threads with default priority */
		PingPongWorker pingWorker = new PingPongWorker("Plus",'+');
		PingPongWorker pongWorker = new PingPongWorker("Minus",'-');

		KThread ping = new KThread(pingWorker);
		ping.setName("Plus");
		KThread pong = new KThread(pongWorker);
		pong.setName("Minus");

		/* Establish ping-pong partnership */
		pingWorker.setPartner(pong);
		pongWorker.setPartner(ping);

		/* Start the two threads */
		ping.fork();
		pong.fork();

		/* Wait for a moderate lapse of time */
		ThreadedKernel.alarm.waitUntil(50000);

		/* Terminate the threads */
		pingWorker.terminate();
		pongWorker.terminate();

		/* Wait for their termination */
		ping.join();
		pong.join();

		System.out.println("#### Ping-Pong test ends ####\n");

	}

	/**
	 * A NamedLock class, which allows locks to be identified
	 * by a name, which comes in handy for test output
	 */
	private static class NamedLock extends Lock {

		/* Constructor 
		 */
		public NamedLock(String name) {
			super();
			this.name = name;
		}

		/* getName() 
		 */
		public String getName() {
			return this.name;
		}

		/* setName() 
		 */
		public void setName(String name) {
			this.name = name;
		}

		private String name;
	}

	/**
	 * PriorityDonationWorker class, which implements a thread that runs in
	 * an infinite loop, with a given priority,  that may go through the loop 
	 * an arbitrary number of times or only once, that attempts to lock and 
	 * unlock a set of (named) locks. 
	 */
	private static class PriorityDonationWorker implements Runnable {

		/* Constructor */
		PriorityDonationWorker(String name, boolean once, 
				int priority, NamedLock locks[]) {
			this.name = name;
			this.once = once;
			this.priority = priority;
			this.locks = locks;
			this.amIDone = false;
		}

		/** terminate()
		 */
		public void terminate() {
			this.amIDone = true;
		}

		/** getName()
		 */
		public String getName() {
			return this.name;
		}

		/** run() method 
		 */
		public void run() {

			System.out.println("** "+name+" begins");

			/* Create a random number generator */
			Random rng = new Random();

			/* Setting the prescribed priority */
			KThread.currentThread().setPriority(this.priority); 

			/* Loop until some other thread has called terminate() */
			while(amIDone == false) {

				/* Acquiring locks I am supposed to acquire */
				for (int i=0; i < locks.length; i++) {
					System.out.println(this.name+" trying to acquire "+
							locks[i].getName());
					locks[i].acquire();
					System.out.println(this.name+" has acquired "+
							locks[i].getName());
					KThread.yield();
				}

				/* "Sleep" for a while */
				long wakeTime = Machine.timer().getTime() + 20000;
				while (wakeTime > Machine.timer().getTime()) { 
					KThread.yield();
				}

				/* Releasing the locks I have acquired, in reversed order */
				for (int i=locks.length-1; i >= 0; i--) {
					System.out.println(this.name+" about to release "+
							locks[i].getName());
					locks[i].release();
					System.out.println(this.name+" has released "+
							locks[i].getName());
					KThread.yield();
				}

				/* Am I a thread that runs just once? If yes, then break */
				if (once) {
					break;
				}
			}

			System.out.println("** "+name+" exits");
		}

		private String name;		// Name of the worker             
		private boolean once;		// true if worker should not loop 
		private int priority;		// Prescribed priority
		private NamedLock locks[];      // Array of locks to lock and unlock
		private boolean amIDone;	// true if the worker must terminate
	}

	/* runPriorityDonationTest1(): A simple priority donation test with one lock.
	 */
	private static void runPriorityDonationTest1() {

		System.out.println("#### Priority Donation test #1 ####");
		System.out.println("    This test succeeds if there is no deadlock. Note that due\n"+
				"    to randomness, the test may succeed many times and then fails.\n"+
		"    So you want to run it many, many times\n");

		/* Create an array with only lock lock */
		NamedLock[] locks = new NamedLock[1];
		locks[0] = new NamedLock("lock0");

		/* Create a Mid-priority thread that runs forever and doesn't
           deal with any locks */
		PriorityDonationWorker workerMi = 
			new PriorityDonationWorker("M-Priority",
					false,1,new NamedLock[0]);
		/* Create a Low-priority thread that runs forever and deals
           with all locks */
		PriorityDonationWorker workerLo = 
			new PriorityDonationWorker("L-Priority",
					false,0,locks);
		/* Create a Hi-priority thread that runs once and deals
           with all locks */
		PriorityDonationWorker workerHi = 
			new PriorityDonationWorker("H-Priority",
					true,7,locks);

		/* Create and name all threads */
		KThread threadMi = new KThread(workerMi);
		threadMi.setName(workerMi.getName());
		KThread threadLo = new KThread(workerLo);
		threadLo.setName(workerLo.getName());
		KThread threadHi = new KThread(workerHi);
		threadHi.setName(workerHi.getName());;

		/* Fork the Low-priority thread */
		System.out.println("before low");
		threadLo.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Mid-priority thread */
		System.out.println("before med");
		threadMi.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Hi-priority thread */
		System.out.println("before hi");
		threadHi.fork();

		/* Waiting for the Hi-priority thread 
		 * If priority Donation is not implemented (correctly),
           this will deadlock as the Lo worker never gets to run */
		threadHi.join();

		/* Wait thread termination */
		workerMi.terminate();
		threadMi.join();

		workerLo.terminate();
		threadLo.join();

		System.out.println("#### Priority Donation test #1 ends ####\n");

	}

	/* runPriorityDonationTest2(): A more complicated priority donation test with two locks. 
	 */
	private static void runPriorityDonationTest2() {

		System.out.println("#### Priority Donation test #2 ####");
		System.out.println("    This test succeeds if there is no deadlock. Note that due\n"+
				"    to randomness, the test may succeed many times and then fails.\n"+
		"    So you want to run it many, many times\n");

		/* Create an array of two named locks */
		NamedLock allLocks[] = new NamedLock[2];
		allLocks[0] = new NamedLock("lock1");
		allLocks[1] = new NamedLock("lock2");
		/* Create an array with only the first lock */
		NamedLock firstLock[] = new NamedLock[1];
		firstLock[0] = allLocks[0];
		/* Create an array with only the second lock */
		NamedLock secondLock[] = new NamedLock[1];
		secondLock[0] = allLocks[1];
		/* Create an array with no lock */
		NamedLock noLocks[] = new NamedLock[0];

		/* Create a Mid-priority worker, runs forever, priority 6, no locks */
		PriorityDonationWorker workerMi = 
			new PriorityDonationWorker("Mid-Priority",
					false,1,noLocks);

		/* Create a Low-priority worker, runs forever, priority 7, all locks */
		PriorityDonationWorker workerLo = 
			new PriorityDonationWorker("Low-Priority",
					false,0,allLocks);

		/* Create a Higher-priority worker, runs once, priority 2, first lock */
		PriorityDonationWorker workerHigher = 
			new PriorityDonationWorker("Higher-Priority",
					true,7,firstLock);

		/* Create a Highest-priority worker, runs once, priority 1, second lock */
		PriorityDonationWorker workerHighest = 
			new PriorityDonationWorker("Highest-Priority",
					true,1,secondLock);

		/* Create and name threads */
		KThread threadMi = new KThread(workerMi);
		threadMi.setName(workerMi.getName());
		KThread threadLo = new KThread(workerLo);
		threadLo.setName(workerLo.getName());
		KThread threadHigher = new KThread(workerHigher);
		threadHigher.setName(workerHigher.getName());
		KThread threadHighest = new KThread(workerHighest);
		threadHighest.setName(workerHighest.getName());

		/* Fork the Low-priority thread */
		threadLo.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Mid-priority thread */
		threadMi.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Higher- and Highest-priority thread */
		threadHigher.fork();
		threadHighest.fork();

		/* Wait for the Higher- and Highest-priority threads 
		 * If priority Donation is not implemented (correctly),
           this will deadlock as the Lo worker never gets to run */
		threadHighest.join();
		threadHigher.join();

		/* Terminate Mid-priority thread */
		workerMi.terminate();
		threadMi.join();

		/* Terminate Lo-priority thread */
		workerLo.terminate();
		threadLo.join();

		System.out.println("#### Priority Donation test #2 ends ####\n");

	}

	/* getSomeLocks(): Given an input array of locks and selects a few at random,
	 *                 which are returned at an array.
	 */
	private static NamedLock[] getSomeLocks(NamedLock locks[]) {
		NamedLock[] someLocks; 
		boolean[] selected; 
		int counter,numSelected;
		Random rng = new Random();

		/* Make a pass through the locks and mark some for selection */
		selected = new boolean[locks.length];
		numSelected = 0;
		for (int i=0; i < locks.length; i++) {
			selected[i] = (rng.nextInt(1000) % 2 == 0);
			if (selected[i]) {
				numSelected++;
			}
		}

		/* Put the selected locks in an array */
		someLocks = new NamedLock[numSelected];
		counter = 0;
		for (int i=0; i < locks.length; i++) {
			if (selected[i]) {
				someLocks[counter++] = locks[i];
			}
		}
		return someLocks;
	}
	
	private static class JoinThread implements Runnable {

		public KThread joinedThread;
		public String name;
		public int priority;
		public NamedLock lock;
		public boolean done = false;
		public boolean runOnce = false;
		
		public JoinThread(KThread joinedThread, String name, int priority){
			this.joinedThread = joinedThread;
			this.name = name;
			this.priority = priority;
		}
		
		public JoinThread(KThread joinedThread, String name, int priority, boolean runOnce){
			this.joinedThread = joinedThread;
			this.name = name;
			this.priority = priority;
			this.runOnce = runOnce;
		}
		
		public JoinThread(KThread joinedThread, String name, int priority, NamedLock lock){
			this.joinedThread = joinedThread;
			this.name = name;
			this.priority = priority;
			this.lock = lock;
		}
		
		@Override
		public void run() {
			
			KThread.currentThread().setPriority(this.priority);
			if(this.name == RANDOM_THREAD){
				return;
			}
			if(joinedThread != null){
				Lib.debug('t',this.name + " joining thread: " + joinedThread);
				joinedThread.join();
			}
			if(lock != null){
				System.out.println(this.name + " is acquiring lock");
				lock.acquire();
				while(!done){
					KThread.yield();
				}
				System.out.println(this.name + " is releasing the lock");
				lock.release();
			} 
			Lib.debug('t',"Finished: " + this.name);
		}
		
		
	}

	/* runPriorityDonationTest3(): A complex random test with many high-priority
	 *    threads that all care about different locks. 
	 *
	 */
	private static void runPriorityDonationTest3() {

		int testSize = 10;  // number of high-priority threads and of locks

		System.out.println("#### Priority Donation test #3 ####");
		System.out.println("    This test succeeds if there is no deadlock. Note that due\n"+
				"    to randomness, the test may succeed many times and then fails.\n"+
		"    So you want to run it many, many times\n");

		/* Create a random number generator */
		Random rng = new Random();

		/* Create an array of locks */
		NamedLock allLocks[] = new NamedLock[testSize];
		for (int i=0; i < testSize; i++) {
			allLocks[i] = new NamedLock("lock"+i);
		}

		/* Create a Mid-priority worker, runs forever, priority 6, no locks */
		NamedLock noLocks[] = new NamedLock[0];
		PriorityDonationWorker workerMi = 
			new PriorityDonationWorker("Mid-Priority",
					false,1,noLocks);

		/* Create a Mid-priority worker, runs forever, priority 7, all locks */
		PriorityDonationWorker workerLo = 
			new PriorityDonationWorker("Low-Priority",
					false,0,allLocks);

		/* Creating a bunch of high priority workers, each dealing
		 * with a random subset of the locks */
		PriorityDonationWorker workerHi[] =
			new PriorityDonationWorker[testSize];
		for (int i=0; i < testSize; i++) {
			int priority = rng.nextInt(3) + 5;
			workerHi[i] =  new PriorityDonationWorker(
					"High-Priority-"+i+"-"+priority,
					true, priority,
					getSomeLocks(allLocks));
		}

		/* Create and name all threads */
		KThread threadMi = new KThread(workerMi);
		threadMi.setName("Mid-Priority");

		KThread threadLo = new KThread(workerLo);
		threadLo.setName("Lo-Priority");

		KThread[] threadHi = new KThread[testSize];
		for (int i=0; i < testSize; i++) {
			threadHi[i] = new KThread(workerHi[i]);
			threadHi[i].setName(workerHi[i].getName());
		}

		/* Fork the Lo-priority thread */
		threadLo.fork();
		ThreadedKernel.alarm.waitUntil(50*testSize);

		/* Fork the Mid-priority thread */
		threadMi.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork all Hi-priority thread */
		for (int i=0; i < testSize; i++) {
			threadHi[i].fork();
		}

		/* Wait for the Hi-Priority threads
		 * If priority Donation is not implemented (correctly),
           this will deadlock as the Lo worker never gets to run */
		for (int i=0; i < testSize; i++) {
			threadHi[i].join();
		}

		/* Wait for the Mid- and Low-priority threads */
		workerMi.terminate();
		threadMi.join();
		workerLo.terminate();
		threadLo.join();

		System.out.println("#### Priority Donation test #3 ends ####\n");
	}
	
	/**
	 * This thread only runs once and calls join on the threadToJoin
	 * @author david
	 *
	 */
	private static class PriorityDonationJoinWorker implements Runnable {

		/* Constructor */
		PriorityDonationJoinWorker(String name, 
				int priority, KThread threadToJoin) {
			this.name = name;
			this.priority = priority;
			this.threadToJoin = threadToJoin;
		}

		/** getName()
		 */
		public String getName() {
			return this.name;
		}

		/** run() method 
		 */
		public void run() {

			System.out.println("** "+name+" begins");

			/* Setting the prescribed priority */
			KThread.currentThread().setPriority(this.priority); 

			Lib.debug('t', KThread.currentThread()+" joining on " + this.threadToJoin);
			this.threadToJoin.join();

			System.out.println("** "+name+" exits");
		}

		private String name;		// Name of the worker             
		private int priority;		// Prescribed priority
		private KThread threadToJoin;
	}
	
	
	private static class PriorityDonationWorkerITimes implements Runnable {

		/* Constructor */
		PriorityDonationWorkerITimes(String name, int timesToRun,
				int priority) {
			this.name = name;
			this.priority = priority;
			this.timesToRun = timesToRun;
		}

		/** getName()
		 */
		public String getName() {
			return this.name;
		}

		/** run() method 
		 */
		public void run() {

			System.out.println("** "+name+" begins");

			/* Setting the prescribed priority */
			KThread.currentThread().setPriority(this.priority); 

			for (int i = 0; i < timesToRun; i++) {
				/* "Sleep" for a while */
				long wakeTime = Machine.timer().getTime() + 2000;
				while (wakeTime > Machine.timer().getTime()) { 
					KThread.yield();
				}
			}

			System.out.println("** "+name+" exits");
		}

		private String name;		// Name of the worker             
		private int priority;		// Prescribed priority
		private int timesToRun;
	}
	
	private static void runPriorityDonationJoinTest1() {

		System.out.println("#### Priority Donation join test ####");
		System.out.println("    This test is to check that priority donation " +
				"occurs with join");

		/* Create an array with only lock lock */
		NamedLock[] locks = new NamedLock[1];
		locks[0] = new NamedLock("lock0");

		/* Create a Mid-priority thread that runs forever and doesn't
           deal with any locks */
		PriorityDonationWorker workerMi = 
			new PriorityDonationWorker("M-Priority",
					false,7,new NamedLock[0]);
		/* Create a Low-priority thread that runs forever and deals
           with all locks */
		PriorityDonationWorkerITimes workerLo = 
			new PriorityDonationWorkerITimes("L-Priority",
					5,0);

		/* Create and name all threads */
		KThread threadMi = new KThread(workerMi);
		threadMi.setName(workerMi.getName());
		KThread threadLo = new KThread(workerLo);
		threadLo.setName(workerLo.getName());
		
		/* Create a Hi-priority thread that runs once and deals
           with all locks */
		PriorityDonationJoinWorker workerHi = 
			new PriorityDonationJoinWorker("H-Priority",
					7, threadLo);
		
		KThread threadHi = new KThread(workerHi);
		threadHi.setName(workerHi.getName());;

		/* Fork the Low-priority thread */
		System.out.println("before low");
		threadLo.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Mid-priority thread */
		System.out.println("before med");
		threadMi.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Hi-priority thread */
		System.out.println("before hi");
		threadHi.fork();

		/* Waiting for the Hi-priority thread 
		 * If priority Donation is not implemented (correctly),
           this will deadlock as the Lo worker never gets to run */
		threadHi.join();

		/* Wait thread termination */
		workerMi.terminate();
		threadMi.join();

		//threadLo.join();

		System.out.println("#### Priority Donation join test ends ####\n");

	}
	
	private static void runPriorityDonationJoinTest2() {

		System.out.println("#### Priority Donation join test2 ####");
		System.out.println("    This test is to check that priority donation " +
				"occurs with join");

		/* Create an array with only lock lock */
		NamedLock[] locks = new NamedLock[1];
		locks[0] = new NamedLock("lock0");

		/* Create a Mid-priority thread that runs forever and doesn't
           deal with any locks */
		PriorityDonationWorker workerMi = 
			new PriorityDonationWorker("M-Priority",
					false,1,new NamedLock[0]);
		
		/*
		 * Create another low-priority thread similar to below
		 */
		PriorityDonationWorkerITimes workerLo2 = 
				new PriorityDonationWorkerITimes("L-Priority2",
						5,0);
	
		/* Create a Low-priority thread that runs forever and deals
           with all locks */
		PriorityDonationWorkerITimes workerLo = 
			new PriorityDonationWorkerITimes("L-Priority",
					5,0);
		
		/* Create and name all threads */
		KThread threadMi = new KThread(workerMi);
		threadMi.setName(workerMi.getName());
		KThread threadLo = new KThread(workerLo);
		KThread threadLo2 = new KThread(workerLo);
		threadLo.setName(workerLo.getName());
		
		/* Create a Hi-priority thread that runs once and deals
           with all locks */
		PriorityDonationJoinWorker workerHi = 
			new PriorityDonationJoinWorker("H-Priority",
					7, threadLo);
		
		KThread threadHi = new KThread(workerHi);
		threadHi.setName(workerHi.getName());;

		/* Fork the Low-priority thread */
		System.out.println("before low");
		threadLo.fork();
		ThreadedKernel.alarm.waitUntil(500);
		
		/* Fork the second Low-priority thread */
		System.out.println("before low2");
		threadLo2.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Mid-priority thread */
		System.out.println("before med");
		threadMi.fork();
		ThreadedKernel.alarm.waitUntil(500);

		/* Fork the Hi-priority thread */
		System.out.println("before hi");
		threadHi.fork();

		/* Waiting for the Hi-priority thread 
		 * If priority Donation is not implemented (correctly),
           this will deadlock as the Lo worker never gets to run */
		threadHi.join();

		//threadLo2.join();
		
		/* Wait thread termination */
		workerMi.terminate();
		threadMi.join();

		//threadLo.join();

		System.out.println("#### Priority Donation join test ends ####\n");

	}
	
	private static void runPriorityDonationJoinTest3() {

		System.out.println("#### Priority Donation join test3 ####");
		System.out.println("    This test is to check that priority donation " +
				"occurs with join");
		

		NamedLock lock = new NamedLock("Lock0");
		

		
		// This thread won't have to do anything
		JoinThread workerE = new JoinThread(null,"E",0, lock);
		KThread E = new KThread(workerE);
		E.setName("E");
		
		// D calls E.join()
		JoinThread workerD = new JoinThread(E,"D",1);
		KThread D = new KThread(workerD);
		D.setName("D");
		
		// C calls D.join()
		JoinThread workerC = new JoinThread(D,"C",2);
		KThread C = new KThread(workerC);
		C.setName("C");
		
		// B calls C.join()
		JoinThread workerB = new JoinThread(C,"B",3);
		KThread B = new KThread(workerB);	
		B.setName("B");
		
		JoinThread workerF = new JoinThread(null,"F",3,lock);
		KThread F = new KThread(workerF);
		
		// A calls B.join()
		JoinThread workerA = new JoinThread(B,"A",7,lock);
		KThread A = new KThread(workerA);
		A.setName("A");
		
		E.fork();
		ThreadedKernel.alarm.waitUntil(1000);
		D.fork();
		ThreadedKernel.alarm.waitUntil(1000);
		C.fork();
		ThreadedKernel.alarm.waitUntil(1000);
		B.fork();
		ThreadedKernel.alarm.waitUntil(1000);
		A.fork();
		ThreadedKernel.alarm.waitUntil(500);
		F.fork();
		workerF.done = true;
		//surrender the lock only after everyone's been forked
		workerE.done = true;
		workerA.done = true;
		
		

		Lib.debug('t',"#### Priority Donation join test ends ####\n");

	}
	
	private static void runPriorityDonationJoinTest4() {

		System.out.println("#### Priority Donation join test4 ####");
		System.out.println("    This test is to check that priority donation " +
				"occurs with join");
		
		NamedLock lock = new NamedLock("Lock0");
		
		// This thread won't have to do anything
		JoinThread workerF = new JoinThread(null,"F",5, lock);
		KThread F = new KThread(workerF);
		F.setName("F");
		
		// This thread won't have to do anything
		JoinThread workerE = new JoinThread(null,"E",0, lock);
		KThread E = new KThread(workerE);
		E.setName("E");
		
		// D calls E.join()
		JoinThread workerD = new JoinThread(E,"D",4);
		KThread D = new KThread(workerD);
		D.setName("D");
		
		// C calls D.join()
		JoinThread workerC = new JoinThread(D,"C",5);
		KThread C = new KThread(workerC);
		C.setName("C");
		
		// B calls C.join()
		JoinThread workerB = new JoinThread(C,"B",6);
		KThread B = new KThread(workerB);	
		B.setName("B");
		
		// A calls B.join()
		JoinThread workerA = new JoinThread(B,"A",7);
		KThread A = new KThread(workerA);
		A.setName("A");
		
		F.fork();
		ThreadedKernel.alarm.waitUntil(500);
		
		A.fork();
		B.fork();
		C.fork();
		D.fork();
		E.fork();
		
		A.join();
		System.out.println("Resetting boolean");
		//surrender the lock only after everyone's been forked
		workerE.done = true;
		

		System.out.println("#### Priority Donation join test ends ####\n");

	}


	/**
	 * Tests whether this module is working.
	 */
	public static void runTest() {
		System.out.println("######################################");
		System.out.println("## PriorityScheduler testing begins ##");
		System.out.println("######################################\n");

		/* A simple ping-pong test */
		//runPingPongTest();

		/* Simplest priority donation test */
		//runPriorityDonationTest1();

		/*  More sophisticated donation test */
		//runPriorityDonationTest2();

		/*  Complex donation test */
		//runPriorityDonationTest3();
		
		/*  Join test */
		//runPriorityDonationJoinTest1();
		
		/*  Join test2 */
		//runPriorityDonationJoinTest3();
		
		runPriorityDonationJoinTest4();

		
		System.out.println("####################################");
		System.out.println("## PriorityScheduler testing ends ##");
		System.out.println("####################################\n");
	}
	private static final String RANDOM_THREAD = "_R_";
}