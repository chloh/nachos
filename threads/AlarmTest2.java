package nachos.threads;

import nachos.machine.*;
import nachos.ag.*;

//A Tester for the Alarm class
public class AlarmTest2 extends AutoGrader {
	
	public static void runTest() {
		System.out.println("**** Alarm testing begins ****");
		Runnable wait1 = new Runnable() {
			public void run() {
				//Alarm obj = new Alarm();
				System.out.println("Waiting for: " + 1000);
				System.out.println("1 Start time: " + Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(1000);
				//System.out.println("Current time: " + Machine.timer().getTime());
			}       
		};
		KThread newThread1 = new KThread(wait1);
		newThread1.setName("WaitingThread");
		newThread1.fork();
		Runnable wait2 = new Runnable() {
			public void run() {
				//Alarm obj2 = new Alarm();
				System.out.println("Waiting for: " + 20);
				System.out.println("2 Start time: " + Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(20);
				//System.out.println("Current time: " + Machine.timer().getTime());
			}
		};
		
		KThread newThread2 = new KThread(wait2);
		newThread2.setName("WaitingThread2");
		newThread2.fork();
		
		//wait1.run();
		//wait2.run();
		newThread1.join();
		System.out.println("1 Finished at: " + Machine.timer().getTime());
		newThread2.join();
		System.out.println("2 Finished at: " + Machine.timer().getTime());
		System.out.println("**** Alarm testing end ****");
	}
}