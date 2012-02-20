package nachos.threads;

import nachos.machine.*;
import nachos.ag.*;
/**
 * A Tester for the Alarm class
 *
 * AlarmTest works because it creates threads with objects that changes the
 * thread's wait time. Because the machine can only use one alarm at a time,
 * you need to use the ThreadedKernel's alarm. Using the alarm in a runnable
 * obj only affects the threads that uses it. So each thread will have their
 * a waitTime euqal to when they forked + x. This more than one thread can 
 * have the same wakeup time and appear almost concurrently.
 *
 * A bad example is having Alarms object created and running in before or 
 * after forks, such as:
 *
 * new Alarm().waitUntil(100);
 * new KThread.(Runnable obj).fork();
 * new Alarm().waitUntil(200);
 * new KThread.(Runnable obj).fork();
 * 
 * This does not work because it is affecting the current thread instead of
 * the new created threads. Thus, the currentthread will have waitUntil(x)
 * before running the 1st thread fork(), and the waitUntil again to run the 
 * 2nd thread.
 */
public class AlarmTest extends AutoGrader{

	//aTest class--waits and prints when finished waiting
	private static class aTest implements Runnable {
		//wait time
		private long wTime;

		//aTest constructor
		public aTest(long x) {
			wTime=x;
		}

		public void run() {
			//set wait time for thread
			ThreadedKernel.alarm.waitUntil(wTime);

			//finished waiting
			System.out.println("Alarm Ringing! (time = "
					+Machine.timer().getTime()+")");
		}
	}

	public static void runTest() {
		System.out.println("**** Alarm testing begins ****");

		//wait intervals
		long wTime1=50000;
		long wTime2=300000;
		long wTime3=1000000;

		//create threads for each new aTest object

		KThread aThread1 = new KThread( new aTest(wTime1) );
		KThread aThread2 = new KThread( new aTest(wTime2) );
		KThread aThread3 = new KThread( new aTest(wTime3) );

		//name each thread
		aThread1.setName( "aThread-1" );
		aThread2.setName( "aThread-2" );
		aThread3.setName( "aThread-3" );

		//run threads with alarms      
		aThread1.fork();
		aThread2.fork();
		aThread3.fork();

		//join threads with alarms
		aThread1.join();
		aThread2.join();
		aThread3.join();

		KThread.yield();
		System.out.println("**** Alarm testing end ****");
    }
}

/*
A Tester for the Alarm class

import java.util.Random;

public class AlarmTest extends AutoGrader{

    public static void runTest() {
        System.out.println("**** Alarm testing begins ****");
        Runnable wait1 = new Runnable() {
                public void run() {
                        Alarm obj = new Alarm();
                        obj.waitUntil(1000);
                        System.out.println("OK");
                        System.out.println(obj.toString());
                }       
        };
        KThread newThread1 = new KThread(wait1);
        newThread1.setName("WaitingThread");
        Runnable wait2 = new Runnable() {
                public void run() {
                        Alarm obj2 = new Alarm();
                        obj2.waitUntil(20);
                        System.out.println("OK2");
                        System.out.println(obj2.toString());
                }
        };
        KThread newThread2 = new KThread(wait2);
        newThread2.setName("WaitingThread2");
        wait1.run();
        wait2.run();
        System.out.println("**** Alarm testing end ****");
    }
*/