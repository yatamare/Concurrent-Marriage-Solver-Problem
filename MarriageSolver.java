//Hunter McGregor
package assignment4;

/* Imports go here */
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.actor.UntypedActor;
import scala.concurrent.duration.Duration;
/* End of imports  */

public class MarriageSolver
{
	////////// Datatype

	/**
	* A class used to store the data about each individual person
	*
	* name - the name of the person
	* spouse - the current spouse of the person
	* proposalSent - an array of bool values to keep track of how has been asked
	* loveInterests - an array of persons ranked 0-n (lowest being the best)
	*/
	public static class Person
	{
		private String name;
		private Person spouse;

		public boolean[] proposalSent;
		public Person[] loveInterests;

		/** contructor */
		public Person(String name)
		{
			this.name = name;
			this.spouse = null;
		}

		/** Returning the name of the Person */
		public String getName() { return this.name; }

		/** Returning the spouse of the Person */
		public Person getSpouse() { return this.spouse; }

		/** Setting the spuse of the person */
		public void setSpouse(Person newSpouse) { this.spouse = newSpouse; }

		/** Setting the love interests of the person */
		public void setLoveInterests(Person[] newLoves) 
		{ 
			this.loveInterests = newLoves; 

			this.proposalSent = new boolean[this.loveInterests.length];

			for (int i = 0; i < proposalSent.length; i += 1)
			{
				this.proposalSent[i] = false;
			}
		}

		/** Find the love rating of a person*/
		public int findLoveRating(String loversName)
		{
			for (int i = 0; i < loveInterests.length; i += 1)
			{
				if (loveInterests[i].getName() == loversName)
					return i + 1;
			}

			// if the person isn't found
			return 0;
		}
	}

	////////// Actors

	/**
	* The actor class for the marriage planner
	* The planner holds the list of people currently looking, the references
	* of all the male actors, and the reference to the female actor.
	* Its first message received will be an array of person types which
	* are all currently looking for a wife. It creates one female actor,
	* and a male actor for each person looking for a wife. It then tells 
	* all the male actors to begin thier search for a wife. The planner
	* is notified by a male actor if it has found a successful wife, it 
	* then checks to make sure everyone has found a wife, and if so it
	* notifies the inbox. If the planner receives a divorce request from 
	* the female actor, it then tells the male actor which the divorce was
	* reference to begin his search from where he left off.
	*
	* Receives: Response, Divorce, Find
	* Sends: Reponse, Find
	*/
	public static class MarriagePlanner extends UntypedActor
	{
		// references to the parts of the system
		private ActorRef inboxRef;
		private ActorRef femaleActor;
		private ActorRef[] maleActors;

		// the list of males using the marriage planner service
		private Person[] maleList;

		// called once planner has recieved news of either:
		// - new clients
		// - a successful marriage
		// - a divorced marriage
		public void onReceive(Object message)
		{
			// if the planner has recieved a new set of client to look for:
			// - assign the inbox
			// - store all the clients in an array
			// - create the female actor
			// - create an array of the male actors, and tell htem to start the search
			if (message instanceof Find)
			{
				inboxRef = getSender();
				maleList = ((Find) message).mailList;

				femaleActor = getContext().actorOf(Props.create(Females.class));
				femaleActor.tell(new Find(null), getSelf());

				maleActors = new ActorRef[maleList.length];

				for (int i = 0; i < maleList.length; i += 1)
				{
					maleActors[i] = getContext().actorOf(Props.create(Males.class));
					maleActors[i].tell(new Response(maleList[i], 'n', femaleActor), getSelf());
				}
			}
			// if the planner has recieved word of a successful marriage:
			// - we must check to see all the males have found a wife.
			// - if all males have found a wife, let the inbox know
			else if (message instanceof Response)
			{
				boolean allFoundMate = true;
 
				for (int i = 0; i < maleList.length; i += 1)
					if (maleList[i].getSpouse() == null)
						allFoundMate = false;

				if (allFoundMate == true)
					inboxRef.tell(new Response(null, 'y', null), getSelf());
			}
			// if the planner has recieved word of a divorce:
			// - find who was divorced
			// - tell them to begin the search again
			else if (message instanceof Divorce)
			{
				for (int i = 0; i < maleList.length; i += 1)
					if (((Divorce) message).to.getName() == maleList[i].getName())
						maleActors[i].tell(new Response(null, 'n', null), getSelf());
			}
			// else we where passed a message we were not meant to receive:
			// - unhandle the message
			else
			{
				unhandled(message);
			}
		}
	}

	/**
	* The actor class for males.
	* The very first message a male actor will receive is a
	* speical response message carrying all the data about
	* who he is, and the address to the female actor. He then
	* sends out a request to female he loves the most and waits
	* for a response. On receiving of the response, he either 
	* tells the planner it was a successful marriage, or he then
	* asks the next female in the list. The male is never told 
	* directly about a diveroce, he just is simply told to keep
	* searching from were he last left off.
	*
	* Receives: Response
	* Sends: Response, Proposal
	*/
	public static class Males extends UntypedActor
	{
		// reference to the planner & the female actor
		private ActorRef planner;
		private ActorRef femaleActor;

		// the current male searching for a mate
		private Person currentMale = null;	

		// called once the male has received news of:
		// - a response message with all the set up data
		// - a response to his proposal
		public void onReceive(Object message)
		{
			// the male has received a reponse from the planner, or female
			// - verify the male actor has been set up
			// - take proper action to deal with the message recieved
			if (message instanceof Response)
			{
				// if the actor received a yes
				if (((Response) message).answer == 'y')
				{
					// set the spouse of the male that asked
					currentMale.setSpouse(((Response) message).from);

					// let the parent know you found a mate
					planner.tell(new Response(currentMale, 'y', null), getSelf());

				}
				else // the answer == no
				{
					// The child actor will need to be set up, it is passed a special
					// response message containing all the data needed to set up the child.
					if (currentMale == null)
					{
						this.planner = getSender();
						this.femaleActor = ((Response) message).special;
						currentMale = ((Response) message).from;
					}

					// find the next female to ask
					for (int i = 0; i < currentMale.proposalSent.length; i += 1)
					{
						// if there is a female that hasn't been asked
						if (currentMale.proposalSent[i] == false)
						{
							// mark the asked (proposalSent) index as true
							currentMale.proposalSent[i] = true;

							// send a proposal to the female
							femaleActor.tell(new Proposal(currentMale, currentMale.loveInterests[i]), getSelf());
							break;
						}
					}

				}
			}
			// else we where passed a message we were not meant to receive:
			// - unhandle the message
			else
			{
				unhandled(message);
			}
		}
	}

	/**
	* The actor class for the females.
	* After recieving a proposal from a male actor, it then checks to
	* see if female can take a new husband, and divorce her old one, 
	* or sends the no repsonse. Sends the divorce request to the 
	* planner or the response to the sender (male actor).
	*
	* Receives: Proposal, and Find
	* Sends: Response, and Divorce
	*/
	public static class Females extends UntypedActor
	{
		// reference to the planner
		ActorRef planner;

		// called once the female has received new of:
		// - A message from the planner, stating its address
		// - A proposal from a male actor
		public void onReceive(Object message)
		{
			// the female has received a message from the planner
			// - Stores the address of the planner (for divorces)
			if (message instanceof Find)
			{
				planner = getSender();
			}
			// the female has received a proposal from a male
			// - if the female is unmarried, reponse yes
			// - if the female is married, respond accordingly
			else if (message instanceof Proposal)
			{
				// save the people send and recieving the message
				Person from = ((Proposal) message).from;
				Person to = ((Proposal) message).to;

				// if the female is currently unmarried
				if (to.getSpouse() == null)
				{
					to.setSpouse(from);
					getSender().tell(new Response(to, 'y', null), getSelf());
				}
				// the female is currently married
				else
				{
					// find the lover rating of the current spouse and the asker
					int asker = to.findLoveRating(from.getName());
					int current = to.findLoveRating(to.getSpouse().getName());

					// if the female like the asker more
					if (asker < current) // is less then cause its ranked 1-n, 1 being the best
					{
						// dump the old husband
						Person xhusband = to.getSpouse();
						xhusband.setSpouse(null);

						// set the new husband
						to.setSpouse(from);

						// divorce the old husband
						planner.tell(new Divorce(xhusband), getSelf());

						// and tell the sender yes
						getSender().tell(new Response(to, 'y', null), getSelf());
					}
					else
					{
						// tell the sender no
						getSender().tell(new Response(to, 'n', null), getSelf());
					}
				}
			}
			else
			{
				unhandled(message);
			}
		}
	}

	////////// Messages

	/**
	* Used by the male actors to proposal to the females actors
	*
	* Sent By: Male Actor
	* Recieved By: Female Actor
	*/
	public static class Proposal implements Serializable
	{
		private static final long serialVersionUID = 912321L;

		public Person from;
		public Person to;

		public Proposal(Person from, Person to)
		{
			this.from = from;
			this.to = to;
		}
	}

	/**
	* Used by the female actor to notify the planner of a divorce
	*
	* Sent By: Female Actor
	* Recieved by: Planner
	*/
	public static class Divorce implements Serializable
	{
		private static final long serialVersionUID = 912432L;

		public Person to;

		public Divorce(Person to)
		{
			this.to = to;
		}
	}

	/**
	* Used by the female actors to reponse to the proposal requests.
	* Used by the male actors to let the planner know a mate has been found
	* Used by the planner to prime the male actors for search
	*
	* Sent By: Planner, Male Actor, and Female Actor
	* Recieved By: Planner, Male Actor
	*/
	public static class Response implements Serializable
	{

		private static final long serialVersionUID = 912123L;

		public Person from;
		public char answer;

		// used in the priming of new children, hold the female actor reference
		public ActorRef special;

		public Response(Person from, char answer, ActorRef special)
		{
			this.from = from;
			this.answer = answer;

			this.special = special;
		}

	}

	/**
	* Used by inbox to pass the array of males looking for a wife to the planner
	* Used by the planner to prime the female actor for divorce tells
	*
	* Sent By: Inbox, Planner
	* Recieved By: Planner, Female
	*/
	public static class Find implements Serializable
	{
		private static final long serialVersionUID = 1L;

		Person[] mailList;

		public Find(Person[] mailList)
		{
			this.mailList = mailList;
		}
	}

	////////// Main
	
	/**
	* Testing of the actor system.
	*/
	public static void main(String[] args) 
	{
		// Creating the people for the marriage
		// Males
		Person john = new Person("john");
		Person frankie = new Person("frankie");
		Person chris = new Person("chris");
		Person tucker = new Person("tucker");
		Person geoff = new Person("geoff");

		// Females
		Person courtney = new Person("courtney");
		Person ana = new Person("ana");
		Person laura = new Person("laura");
		Person niki = new Person("niki");
		Person olivia = new Person("olivia");

		// Creating the arrays of love Interests
		Person[] johnInterests = {niki, courtney, laura, ana, olivia};
		Person[] frankieInterests = {laura, niki, ana, courtney, olivia};
		Person[] chrisInterests = {olivia, niki, ana, laura, courtney};
		Person[] tuckerInterests = {courtney, olivia, niki, laura, ana};
		Person[] geoffInterests = {courtney, laura, niki, ana, olivia};

		Person[] courtneyInterests = {geoff, frankie, chris, tucker, john};
		Person[] anaInterests = {tucker, frankie, geoff, chris, john};
		Person[] lauraInterests = {john, geoff, chris, frankie, tucker};
		Person[] nikiInterests = {geoff, john, chris, frankie, tucker,};
		Person[] oliviaInterests = {john, chris, tucker, frankie, geoff};

		// Setting the love interests
		john.setLoveInterests(johnInterests);
		frankie.setLoveInterests(frankieInterests);
		chris.setLoveInterests(chrisInterests);
		tucker.setLoveInterests(tuckerInterests);
		geoff.setLoveInterests(geoffInterests);

		courtney.setLoveInterests(courtneyInterests);
		ana.setLoveInterests(anaInterests);
		laura.setLoveInterests(lauraInterests);
		niki.setLoveInterests(nikiInterests);
		olivia.setLoveInterests(oliviaInterests);

		// create arrays to store the males so they can be sent
		Person[] maleArray = {john, frankie, chris, tucker, geoff};

		// create the actor system
		final ActorSystem actorSystem = ActorSystem.create("actor-system");

		// create the male and female actor
		final ActorRef weddingPlanner = actorSystem.actorOf(Props.create(MarriagePlanner.class), "planner");

		// create an inbox
		final Inbox inbox =  Inbox.create(actorSystem);

		// tell the workers to find a match
		inbox.send(weddingPlanner, new Find(maleArray));

		// wait up to 30 seconds for a reply from the working
		try {
			inbox.receive(Duration.create(60, TimeUnit.SECONDS));
		} catch (TimeoutException e) {
			System.out.println("Time out after waiting 30 seconds.");
			actorSystem.terminate();
			System.exit(1);
		}

		for (int i = 0; i < maleArray.length; i += 1)
		{
			System.out.println(maleArray[i].getName() + " has married " + maleArray[i].getSpouse().getName());
		}

		// shut down the system
		actorSystem.terminate();
	}	
}
