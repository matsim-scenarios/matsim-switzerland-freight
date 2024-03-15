package ch.sbb.intermodalfreight.simulate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.mobsim.qsim.interfaces.SignalGroupState;
import org.matsim.core.mobsim.qsim.interfaces.SignalizeableItem;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

/**
 * 
 * Handles the crane dynamics at the terminals.
 * 
 * - The train-stack queue is modeled via the access/egress times given in the stop attributes (each container entering or leaving a train requires a certain amount of time).
 * - The truck-stack queue is modeled as a capacity constrained link which is deactivated whenever the crane is busy handling the train-stack queue
 *   (= if there is a train in the terminal and there are containers waiting to be loaded or unloaded between the stack and the train).
 * - The truck-stack queue will be activated again if crane is no longer busy handling the train-stack queue
 *   (= if there is no train in the terminal or if there are no more containers waiting to be loaded or unloaded between the stack and the train).
 * - The truck-stack queue is deactivated and activated by using a dynamic signal which is placed on the end of the truck-stack queue link.
 * 
 * @author ikaddoura
 *
 */
public class TerminalQueueDeactivationEventHandler implements
		VehicleArrivesAtFacilityEventHandler,
		VehicleDepartsAtFacilityEventHandler,
		TransitDriverStartsEventHandler,
		VehicleEntersTrafficEventHandler,
		VehicleLeavesTrafficEventHandler,
		LinkEnterEventHandler,
		LinkLeaveEventHandler,
		AgentWaitingForPtEventHandler,
		PersonEntersVehicleEventHandler,
		PersonLeavesVehicleEventHandler, 
		PersonDepartureEventHandler, 
		MobsimInitializedListener,
		MobsimBeforeSimStepListener {
	
	private static final Logger log = LogManager.getLogger(TerminalQueueDeactivationEventHandler.class);
	
	private final String carKVLegPrefix = "carKV_";

	private final Map<Id<Link>, SignalizeableItem> queueLink2signal;
	private final Set<Id<Vehicle>> transitVehicles;
	private final Set<Id<Person>> transitDrivers;
	private final Map<Id<Vehicle>, Id<TransitRoute>> train2route;
	private final Map<Id<TransitStopFacility>, Set<Id<Person>>> terminal2waitingPersons;
	private final Map<Id<TransitStopFacility>, Set<Id<Vehicle>>> terminal2trains;
	private final Map<Id<Vehicle>, Id<TransitStopFacility>> train2terminal;
	private final Map<Id<Vehicle>, Set<Id<Person>>> train2passengers;
	private final Map<Id<Person>, Integer> person2legCounter;
	private final Map<Id<Person>, Id<Link>> person2destinationStopLink;
	private final Map<Id<Person>, String> person2nextTrainRouteDescription;
	private final Map<Id<Link>, Set<Id<Vehicle>>> link2vehicles;
	private final Map<Id<Vehicle>, Id<Person>> vehicle2person;	
	private final Map<Id<TransitStopFacility>, QueueStatus> terminal2queueStatus;
	
	private final Set<Id<Link>> relevantLinks;
	private final Map<Id<TransitStopFacility>, Set<Link>> terminal2queueLinks;
	private final Map<Id<TransitStopFacility>, Set<Link>> terminal2stackLinks;
	private final Map<Id<Vehicle>, Integer> train2capacity;	


	private final Scenario scenario;
	
	private enum QueueStatus {
		Active, Deactive
	}
	
	public TerminalQueueDeactivationEventHandler(Scenario scenario) {
		this.scenario = scenario;	
		
		relevantLinks = new HashSet<>();
		terminal2queueLinks = new HashMap<>();
		terminal2stackLinks = new HashMap<>();
		queueLink2signal = new HashMap<>();
		transitVehicles = new HashSet<>();
		transitDrivers = new HashSet<>();
		train2capacity = new HashMap<>();
		
		train2route = new HashMap<>();
		terminal2waitingPersons = new HashMap<>();
		terminal2trains = new HashMap<>();
		train2terminal = new HashMap<>();
		train2passengers = new HashMap<>();
		person2legCounter = new HashMap<>();
		person2destinationStopLink = new HashMap<>();
		person2nextTrainRouteDescription = new HashMap<>();
		link2vehicles = new HashMap<>();
		vehicle2person = new HashMap<>();	
		terminal2queueStatus = new HashMap<>();
		
		for (Id<Vehicle> vehicleId : this.scenario.getTransitVehicles().getVehicles().keySet()) {
			Vehicle vehicle = this.scenario.getTransitVehicles().getVehicles().get(vehicleId);
			VehicleType vehicleType = vehicle.getType();
			int standingRoom = vehicleType.getCapacity().getStandingRoom();
			int seats = vehicleType.getCapacity().getSeats();
			train2capacity.put(vehicleId, standingRoom + seats);
		}
				
		for (TransitStopFacility terminal : this.scenario.getTransitSchedule().getFacilities().values()) {
			
			// initialize queue links
			Set<Link> queueLinks = getQueueLinks(terminal.getId());
			this.terminal2queueLinks.put(terminal.getId(), queueLinks);
			
			// initialize stack links
			Set<Link> stackLinks = getStackLinks(terminal.getId());
			this.terminal2stackLinks.put(terminal.getId(), stackLinks);
			
			// initialize relevant links
			for (Link link : queueLinks) {
				this.relevantLinks.add(link.getId());
			}
			for (Link link : stackLinks) {
				this.relevantLinks.add(link.getId());
			}
		}
	}
	
	@Override
	public void reset(int iteration) {

		// initialize and reset the information from the previous iteration
		
		queueLink2signal.clear();
		transitVehicles.clear();
		transitDrivers.clear();
		train2route.clear();
		terminal2waitingPersons.clear();
		terminal2trains.clear();
		train2terminal.clear();
		train2passengers.clear();
		person2legCounter.clear();
		person2destinationStopLink.clear();
		person2nextTrainRouteDescription.clear();
		link2vehicles.clear();
		vehicle2person.clear();	
		terminal2queueStatus.clear();
		
		// initialize queue status
		for (TransitStopFacility terminal : this.scenario.getTransitSchedule().getFacilities().values()) {
			this.terminal2queueStatus.put(terminal.getId(), QueueStatus.Active);
		}
		
	}

	@Override
	public void notifyMobsimInitialized(MobsimInitializedEvent e) {
		Netsim mobsim = (Netsim) e.getQueueSimulation() ;
		for (Id<TransitStopFacility> stopId : scenario.getTransitSchedule().getFacilities().keySet()) {
			for (Link link : this.terminal2queueLinks.get(stopId)) {
				SignalizeableItem signalLink = (SignalizeableItem) mobsim.getNetsimNetwork().getNetsimLink(link.getId()) ;
				signalLink.setSignalized(true);
				signalLink.setSignalStateAllTurningMoves(SignalGroupState.GREEN);
				this.queueLink2signal.put(link.getId(), signalLink);
			}
		}
	}
	
	@Override
	public void notifyMobsimBeforeSimStep(MobsimBeforeSimStepEvent event) {
		for (Id<TransitStopFacility> stopFacilityId : this.terminal2trains.keySet()) {
			
			if (this.terminal2trains.get(stopFacilityId).isEmpty()) {
				activateQueue(stopFacilityId, event.getSimulationTime());
				
			} else {
				
				// check if there is an agent who wants to get out	
				// check if there is an agent in the stack who wants to get into the train				
				if (isThereATrainWithAlightingAgents(stopFacilityId) || isTheStackWithBoardingAgents(stopFacilityId)) {
					// deactivate the queue			
					deactivateQueue(stopFacilityId, event.getSimulationTime());
					
				} else {
					// activate the queue		
					activateQueue(stopFacilityId, event.getSimulationTime());
				}			
			}
		}
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitVehicles.add(event.getVehicleId());
		transitDrivers.add(event.getDriverId());
		train2route.put(event.getVehicleId(), event.getTransitRouteId());
		
		// initialize
		this.train2passengers.put(event.getVehicleId(), new HashSet<>());
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		if (transitVehicles.contains(event.getVehicleId())) {
			// transit vehicle arrives at a facility
			
			this.train2terminal.put(event.getVehicleId(), event.getFacilityId());
						
			// update the information about which train is at which terminal
			if (this.terminal2trains.get(event.getFacilityId()) == null) {
				this.terminal2trains.put(event.getFacilityId(), new HashSet<>());
			}
			this.terminal2trains.get(event.getFacilityId()).add(event.getVehicleId());
			
			// check if we have to update the queue
			int trainsAtTerminal = terminal2trains.get(event.getFacilityId()).size();
			
			if (trainsAtTerminal == 1) {
				// everything OK				
						
			} else {
				log.info("There are " + trainsAtTerminal + " trains at terminal " + event.getFacilityId().toString() +
						" at time " + Time.writeTime(event.getTime(), Time.TIMEFORMAT_HHMMSS));			
			}				
		}
	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		if (transitVehicles.contains(event.getVehicleId())) {
			// transit vehicle departs at a facility
			
			this.train2terminal.remove(event.getVehicleId());
			
			// update the information about which train is at which terminal
			if (this.terminal2trains.get(event.getFacilityId()) == null) {
				throw new RuntimeException("Train departs without arriving. Aborting...");
			}
			this.terminal2trains.get(event.getFacilityId()).remove(event.getVehicleId());
			
			// TODO: check if the train has departed too late

		}
	}
	
	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		vehicleLeavesLink(event.getVehicleId(), event.getLinkId());
	}
	
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		vehicleLeavesLink(event.getVehicleId(), event.getLinkId());
	}

	private void vehicleLeavesLink(Id<Vehicle> vehicleId, Id<Link> linkId) {
		if (this.transitVehicles.contains(vehicleId) || !this.relevantLinks.contains(linkId)) {
			// ignore transit vehicles here and also ignore links that are not relevant
		} else {
			this.link2vehicles.get(linkId).remove(vehicleId);
		}
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		vehicleEntersLink(event.getVehicleId(), event.getLinkId());
	}
	
	@Override
	public void handleEvent(LinkEnterEvent event) {
		vehicleEntersLink(event.getVehicleId(), event.getLinkId());
	}
	
	private void vehicleEntersLink(Id<Vehicle> vehicleId, Id<Link> linkId) {
		if (this.transitVehicles.contains(vehicleId) || !this.relevantLinks.contains(linkId)) {
			// ignore transit vehicles here and also ignore links that are not relevant
		} else {
			if (this.link2vehicles.get(linkId) == null) {
				Set<Id<Vehicle>> vehicles = new HashSet<>();
				vehicles.add(vehicleId);
				this.link2vehicles.put(linkId, vehicles);
			}
			this.link2vehicles.get(linkId).add(vehicleId);
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (this.transitVehicles.contains(event.getVehicleId())) {
			// transit vehicle
			
			if (!this.transitDrivers.contains(event.getPersonId())) {
				// not the transit driver
				
				// remove the person from our passenger tracker
				this.train2passengers.get(event.getVehicleId()).remove(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		
		if (this.transitVehicles.contains(event.getVehicleId())) {
			// transit vehicle
			
			if (!this.transitDrivers.contains(event.getPersonId())) {
				// not the transit driver
				
				// add the person to our passenger tracker
				this.train2passengers.get(event.getVehicleId()).add(event.getPersonId());
				
				// find out where the agent wants to get off the train
				int currentLegNr = this.person2legCounter.get(event.getPersonId());
				Plan selectedPlan = this.scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
				Leg currentLeg = TripStructureUtils.getLegs(selectedPlan).get(currentLegNr);
				
				if (!currentLeg.getMode().equals(TransportMode.pt)) {
					throw new RuntimeException("Expecting a pt leg at position " + currentLegNr + " for agent " + event.getPersonId() + " : " + currentLeg.toString() );
				}
				
				Id<Link> destinationLink = currentLeg.getRoute().getEndLinkId();
				this.person2destinationStopLink.put(event.getPersonId(), destinationLink);
				
				// a person entering a transit vehicle is no longer waiting, update this information
				Id<TransitStopFacility> stopId = this.train2terminal.get(event.getVehicleId());
				this.terminal2waitingPersons.get(stopId).remove(event.getPersonId());
			}
		} else {
			// carKV mode etc.
			vehicle2person.put(event.getVehicleId(), event.getPersonId());
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (this.person2legCounter.get(event.getPersonId()) == null) {
			this.person2legCounter.put(event.getPersonId(), 0);
		} else {
			int legCount = this.person2legCounter.get(event.getPersonId());
			this.person2legCounter.put(event.getPersonId(), legCount + 1);
		}
		
		if (event.getLegMode().startsWith(carKVLegPrefix)) {
			int currentLegNr = this.person2legCounter.get(event.getPersonId());
			Plan selectedPlan = this.scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
			List<Leg> legs = TripStructureUtils.getLegs(selectedPlan);
			
			int nextLegNr = currentLegNr + 1;
			
			if (legs.size() > nextLegNr) {			
				// there is a following leg (the carKV leg was the access leg)
				
				Leg nextLeg = TripStructureUtils.getLegs(selectedPlan).get(nextLegNr);	
				if (nextLeg.getMode().equals(TransportMode.pt)) {
					this.person2nextTrainRouteDescription.put(event.getPersonId(), nextLeg.getRoute().getRouteDescription());
				} else {
					log.warn("A leg with mode " + carKVLegPrefix + "... is expected to be followed by a pt leg. "
							+ "Agent " + event.getPersonId() + " - Leg nr. " + currentLegNr + " nextLeg: " + nextLeg.toString());
					log.warn("Needs to be checked!!");
				}
				
			} else {
				// there is no following leg (the carKV leg was the egress leg)
				// remove the person information, will be re-computed in the next person departure event
				this.person2nextTrainRouteDescription.remove(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		if (this.terminal2waitingPersons.get(event.getWaitingAtStopId()) == null) {
			this.terminal2waitingPersons.put(event.getWaitingAtStopId(), new HashSet<>());
		}
		this.terminal2waitingPersons.get(event.getWaitingAtStopId()).add(event.getPersonId());
	}
	
	private void activateQueue(Id<TransitStopFacility> facilityId, double time) {
		if (this.terminal2queueStatus.get(facilityId) == QueueStatus.Active) {
			// nothing to do
			
		} else {
			this.terminal2queueStatus.put(facilityId, QueueStatus.Active);

			// log.debug("++++ Activate queue for " + facilityId + " at time " + Time.writeTime(time, Time.TIMEFORMAT_HHMMSS) + ": " + time);
			
			for (Link link : this.terminal2queueLinks.get(facilityId)) {
				
				this.queueLink2signal.get(link.getId()).setSignalStateAllTurningMoves(SignalGroupState.GREEN);
			}			
		}	
	}
	
	private void deactivateQueue(Id<TransitStopFacility> facilityId, double time) {
		
		if (this.terminal2queueStatus.get(facilityId) == QueueStatus.Deactive) {
			// nothing to do
			
		} else {
			this.terminal2queueStatus.put(facilityId, QueueStatus.Deactive);
			
			// log.debug("---- Deactivate queue for " + facilityId + " at time " + Time.writeTime(time, Time.TIMEFORMAT_HHMMSS) + ": " + time);
						
			for (Link link : this.terminal2queueLinks.get(facilityId)) {
				
				this.queueLink2signal.get(link.getId()).setSignalStateAllTurningMoves(SignalGroupState.RED);					
			}	
		}
	}
	
	

	private boolean isTheStackWithBoardingAgents(Id<TransitStopFacility> stopFacilityId) {
		
		// (1) check the vehicles/persons on the links
		Set<Link> stackLinks = this.terminal2stackLinks.get(stopFacilityId);			
		for (Link link : stackLinks) {
			if (this.link2vehicles.get(link.getId()) == null) {
				// no information for that link
			} else {
				// there are vehicles on that link we need to check
				
				for (Id<Vehicle> vehicleId : this.link2vehicles.get(link.getId())) {
					Id<Person> personId = this.vehicle2person.get(vehicleId);
					if (agentWantsToBoardATrainWhichIsCurrentlyAtTheStop(stopFacilityId, personId)) {
						// This agent who is in the stack, in particular on one of the links between the queue and the transit stop,
						// wants to board one of the trains which is currently at the terminal.
						
						return true;
					}
				}
			}
		}
		
		// (2) also check the agents who have left the link and are still waiting to board the train
		if (this.terminal2waitingPersons.get(stopFacilityId) == null) {
			// no agent is waiting a the terminal
			
		} else {
			// there are agents waiting at the terminal we have to check
			
			for (Id<Person> personId : this.terminal2waitingPersons.get(stopFacilityId)) {
				if (agentWantsToBoardATrainWhichIsCurrentlyAtTheStop(stopFacilityId, personId)) {
					// This agent who is in the stack, in particular on one of the links between the queue and the transit stop,
					// wants to board one of the trains which is currently at the terminal.
					
					return true;
				}
			}
		}
		
		return false;
	}

	private boolean agentWantsToBoardATrainWhichIsCurrentlyAtTheStop(Id<TransitStopFacility> stopFacilityId, Id<Person> personId) {
		
		for (Id<Vehicle> train : this.terminal2trains.get(stopFacilityId)) {
			Id<TransitRoute> trainRouteId = this.train2route.get(train);
			String nextTrainRouteDescription = this.person2nextTrainRouteDescription.get(personId);
			
			if (nextTrainRouteDescription == null) {
				log.debug("There is no information for agent " + personId);
				
			} else {
				
				log.debug("Agent " + personId + " has a next leg.");
				
				if (nextTrainRouteDescription.contains("\"" + trainRouteId.toString() + "\"")) {
					// The agent wants to board one of the trains which is currently at the terminal.
					
					// check if the agent is able to board the train --> check if the train has available capacities
					if (this.train2passengers.get(train).size() == this.train2capacity.get(train)) {
						log.debug("The agent cannot board train " + train + " at stop " + stopFacilityId);
						
					} else {
						log.debug("The agent is about to board train " + train + " at stop " + stopFacilityId);
						return true;
					}
					
				} else {
					log.debug("The agent does not want to board train " + train + " at stop " + stopFacilityId);
				}
			}
		}

		return false;
	}

	private boolean isThereATrainWithAlightingAgents(Id<TransitStopFacility> stopFacilityId) {
		Id<Link> stopFacilityLink = this.scenario.getTransitSchedule().getFacilities().get(stopFacilityId).getLinkId();
		
		for (Id<Vehicle> train : this.terminal2trains.get(stopFacilityId)) {
			for (Id<Person> person : this.train2passengers.get(train)) {
				Id<Link> destinationStopLinkOfThatPerson = this.person2destinationStopLink.get(person);
				
				if (stopFacilityLink.toString().equals(destinationStopLinkOfThatPerson.toString())) {
					// This agent who is on the train wants to alight at the current transit stop.
					
					return true;
				}
			}

		}
		return false;
	}
	
	private Set<Link> getQueueLinks(Id<TransitStopFacility> facilityId) {
		
		Set<Link> links = new HashSet<>();
		
		// This is hard-coded and should be changed if the naming syntax changes.
		String idPrefix = facilityId + "_" + facilityId + "_IN1_carKV_";
		
		for (Link link : this.scenario.getNetwork().getLinks().values()) {
			if (link.getId().toString().startsWith(idPrefix)) {
				links.add(link);
			}
		}
		
		if (links.isEmpty()) throw new RuntimeException("Could not find the truck-stack queue for terminal " + facilityId.toString());
		
		return links;
	}
	
	private Set<Link> getStackLinks(Id<TransitStopFacility> facilityId) {
		
		Set<Link> links = new HashSet<>();
		
		// This is hard-coded and should be changed if the naming syntax changes.
		String idPrefix1 = facilityId + "_" + facilityId + "_OUT1_carKV_";
		String idSuffix1 = "-" + facilityId + "_IN";
		
		for (Link link : this.scenario.getNetwork().getLinks().values()) {
			if (link.getId().toString().startsWith(idPrefix1) &&
					link.getId().toString().endsWith(idSuffix1)) {
				links.add(link);
			}
		}
		
		// This is hard-coded and should be changed if the naming syntax changes.
		String id2 = facilityId + "_" + facilityId + "_IN-" + facilityId + "_OUT";
		
		for (Link link : this.scenario.getNetwork().getLinks().values()) {
			if (link.getId().toString().equals(id2)) {
				links.add(link);
			}
		}
		
		if (links.isEmpty() || links.size() < 2) throw new RuntimeException("Expecting 2 stack links. "
				+ "Could not find all stack links for terminal " + facilityId.toString());
		
		return links;
	}
	
	public Map<Id<TransitStopFacility>, Set<Id<Vehicle>>> getTerminal2trains() {
		return terminal2trains;
	}

}
