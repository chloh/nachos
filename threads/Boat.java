package nachos.threads;
import nachos.ag.BoatGrader;
import java.util.Hashtable;

public class Boat
{
	static BoatGrader bg;
	static int boatLocation; // Oahu = 0, Molokai = 1
	static int numChildrenOnOahu;
	static int numChildrenOnMolokai;
	static int numAdultsOnOahu;
	static int numAdultsOnMolokai;
	static int numChildrenOnBoat;
	static Condition waitOnOahu;
	static Condition waitOnMolokai;
	static Condition waitOnBoatChildren;
	static Lock lock;
	static Hashtable locations; // keys: thread, values: location (Oahu = 0, Molokai = 1)
	static boolean gameOver;
	static Communicator communicator; 

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		boatLocation = 0; // boat starts on oahu
		numChildrenOnOahu = 0;
		numChildrenOnMolokai = 0;
		numAdultsOnOahu = 0;
		numAdultsOnMolokai = 0;
		numChildrenOnBoat = 0;
		lock = new Lock();
		waitOnOahu = new Condition(lock);
		waitOnMolokai = new Condition(lock);
		waitOnBoatChildren = new Condition(lock);
		locations = new Hashtable();
		gameOver = false;	
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		KThread t = null;
		Runnable r = null;
		// create children threads first
		for(int j = 0; j < children; j++) {
			r = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			t = new KThread(r);
			t.setName("Child " + Integer.toString(j));
			t.fork();
			locations.put(t, 0); // all children start on Oahu
		}

		// create adult threads
		for(int i = 0; i < adults; i++) {
			r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			t = new KThread(r);
			t.setName("Adult " + Integer.toString(i));
			t.fork();
			locations.put(t, 0); // all adults start on Oahu
		}

		while (true) {
			adultsOnMolokai = communicator.listen();
			childrenOnMolokai = communicator.listen();

			if (adults==adultsOnMolokai && children==childrenOnMolokai) {
				gameOver = true;
				return;
			}

		}
	}

	static void AdultItinerary()
	{
		/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
		 */
		KThread currentThread = KThread.currentThread();
		lock.acquire();
		if locations.get(currentThread) == 0 {
			numAdultsOnOahu++;
		}
		lock.release();
		
		lock.acquire();
		int currentLocation = locations.get(currentThread);

		while(!gameOver) {

			// adults only act if they are on Oahu; if currentLocation == 0
			if(currentLocation == 1) { //Molokai = 1
				waitOnMolokai.sleep();
				continue;
			}

			// if the boat isn’t at the current location
			if(boatLocation != currentLocation) { 
				waitOnOahu.sleep();
				continue;
			}

			// if the boat is full
			if(numChildrenOnBoat != 0) {
				waitOnOahu.sleep();
				continue;
			}


			// if there are enough children to send over
			if(numChildrenOnOahu >= 2) {
				waitOnOahu.sleep();
				continue;
			} else { // send adult to Molokai
				bg.AdultRowToMolokai();

				// this is the special case where the first thread is a single adult
				if(numC == 0) row back, get off boat, wake all, yield; 
				numAdultsOnOahu--;
				boatLocation = 1;
				change!
				numAdultsOnMolokai++;
				locations.remove(currentThread);
				locations.put(currentThread, 1);
				waitOnMolokai.wakeAll();
				// adults never move back to Oahu so we don’t need this 
				// thread anymore
				break;
			}
		}
		lock.release();
		return;
	}

	static void ChildItinerary()
	{
		lock.acquire();
		if locations.get(currentThread) == 0 {
			numChildrenOnOahu++;
		} else {
			numChildrenOnMolokai++;
		}
		lock.release();

		lock.acquire();
		int currentLocation = locations.get(currentThread);
		CV currentCV;

		while(!gameOver) {

			// updating local current vars
			if(currentLocation == 0) {
				currentCV = waitOnOahu;
			} else {
				currentCV = waitOnMolokai;
			}

			// if the boat is not here
			if(boatLocation != currentLocation) {
				currentCV.sleep();
				continue;
			}


			// if the boat is full
			if(numChildrenOnBoat >= 2) {
				currentCV.sleep();
				continue;
			}


			if(currentLocation == 0) { // if child is on Oahu
				if(numChildrenOnOahu == 2) { 
					// This is only the end if adults = 0 also
					gameOver = true;
				}
				if(numChildrenOnBoat == 0) { // no children on boat
					if(numChildrenOnOahu >= 2) {
						numChildrenOnBoat++;
						numChildrenOnOahu--;
						bg.ChildRowToMolokai();
						waitOnBoatChildren.sleep();
						boatLocation = 1;
						numChildrenOnBoat--;
						numChildrenOnMolokai++;
						locations.remove(currentThread);
						locations.put(currentThread, 1);
						most up to date version!
						communicator.speak(numAdultsOnMolokai);
						communicator.speak(numChildrenOnMolokai);
						waitOnMolokai.wakeAll();
					} else {
						waitOnOahu.sleep();
						continue;
					}
				} else { // if one child on boat
					numChildrenOnBoat++;
					numChildrenOnOahu--;
					bg.ChildRideToMolokai();
					waitOnBoatChildren.wake();
					numChildrenOnBoat--;
					numChildrenOnMolokai++;
					locations.remove(currentThread);
					locations.put(currentThread, 1);
				}
			} else { // if the child is on Molokai
				if(numChildrenOnBoat == 0) { // no children on boat
					// don’t need to increment/decrement 
					// numChildrenOnBoat
					numChildrenOnMolokai--;
					bg.ChildRowToOahu();
					boatLocation = 0;
					numChildrenOnOahu++;
					locations.remove(currentThread);
					locations.put(currentThread, 0);
					waitOnOahu.wakeAll();
				} else { // there is already one child on the boat
					waitOnMolokai.sleep();
					continue;
				}
			}
			currentThread.ready(); // add child back to ready queue
		}
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
