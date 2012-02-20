package nachos.threads;

import nachos.machine.*;
import nachos.ag.*;

public class BoatTest extends AutoGrader {
	
	private static class aTest implements Runnable {
		
		public void run() {
			Boat.selfTest();
		}
	}
	
	
	public static void runTest() {
		System.out.println("**** Boat testing begins ****");
		
		KThread aThread = new KThread( new aTest());
		
		aThread.fork();
		aThread.join();
		
		KThread.yield();
		System.out.println("**** Boat testing end ****");
	}
	
	
	
}