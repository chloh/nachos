package nachos.threads;

import nachos.machine.*;
import nachos.ag.*;

//A Tester for the Alarm class
public class AlarmTest2 extends AutoGrader {
	
	public static void runTest() {
		System.out.println("**** Alarm testing begins ****");
		Runnable wait1 = new Runnable() {
			public void run() {
				Alarm obj = new Alarm();
				System.out.println("Waiting for: " + 1000);
				System.out.println("Current time: " + Machine.timer().getTime());
				obj.waitUntil(1000);
				System.out.println("Current time: " + Machine.timer().getTime());
				System.out.println(obj.toString());
			}       
		};
		KThread newThread1 = new KThread(wait1);
		newThread1.setName("WaitingThread");
		Runnable wait2 = new Runnable() {
			public void run() {
				Alarm obj2 = new Alarm();
				System.out.println("Waiting for: " + 20);
				System.out.println("Current time: " + Machine.timer().getTime());
				obj2.waitUntil(20);
				System.out.println("Current time: " + Machine.timer().getTime());
				System.out.println(obj2.toString());
			}
		};
		KThread newThread2 = new KThread(wait2);
		newThread2.setName("WaitingThread2");
		wait1.run();
		wait2.run();
		System.out.println("**** Alarm testing end ****");
	}
}