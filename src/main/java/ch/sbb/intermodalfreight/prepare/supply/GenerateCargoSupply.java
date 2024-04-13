/**
 * 
 */
package ch.sbb.intermodalfreight.prepare.supply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleType.DoorOperationMode;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;


/**
 * 
 * A preparation class which generates the cargo supply.
 * 
 * @author Ihab Kaddoura
 *
 */
public class GenerateCargoSupply {
	private static final Logger log = LogManager.getLogger(GenerateCargoSupply.class);
	
	private final String carAccessibleAttributePrefix = "accessible_";
	private final double railCraneGap = 500.;
	private final double xCoordGapRailInOut = 500.;
	private final double numberOfLanesCarRailLink = 20.;
	private final double numberOfLanesTerminalLink = 10.;
	private final double numberOfLanesTerminalConnectionLinks = 100.;
	private final double largeLinkCapacity = 3600.;
	private final double linksPerTerminal = 3.;

	private final int simulatedDays;
	private final double distanceEachTerminalLink;
	private final double speedEachTerminalLink;
	
	private final Scenario scenario;
	private final Network carOnlyNetwork;
	private final Map<String, Terminal> terminals = new HashMap<>();	
	private final TransitSchedule schedule;
	private final Vehicles vehicles;
	private final Network network;
	private final TransitScheduleFactory sf;
	private final VehiclesFactory vf;
    private final NetworkFactory nf;
    
	private final List<NetworkChangeEvent> networkChangeEvents = new ArrayList<>();

	/**
	 * @param scenario
	 * @param originalCarNetwork a car only network
	 * @param distanceTerminalCraneLinks the distance of the terminal links
	 * @param craneTravelTime the free speed travel time to pass the terminal's crane links
	 * @param simulatedDays the number of simulated days, e.g. three days in order to use the second day as the representative day
	 */
	public GenerateCargoSupply(Scenario scenario, Network originalCarNetwork, double distanceTerminalCraneLinks, double craneTravelTime, int simulatedDays) {
		this.scenario = scenario;
		this.carOnlyNetwork = originalCarNetwork;
		this.simulatedDays = simulatedDays;
		
		// compute distance and speed for each terminal link
		this.distanceEachTerminalLink = distanceTerminalCraneLinks / linksPerTerminal;
		this.speedEachTerminalLink = distanceEachTerminalLink / (craneTravelTime / linksPerTerminal );
		
		schedule = scenario.getTransitSchedule();
        vehicles = scenario.getTransitVehicles();
        network = scenario.getNetwork();
        sf = schedule.getFactory();
		vf = vehicles.getFactory();
        nf = network.getFactory();

		// check if car only network contains modes other than car or ride
		Set<String> carModes = new HashSet<>();
		carModes.add("car");
		carModes.add("ride");
		for (Link link : carOnlyNetwork.getLinks().values()) {
			for (String mode : link.getAllowedModes()) {
				if (!carModes.contains(mode)) {
					throw new RuntimeException("Car only network seems to be multimodal. Aborting..."
							+ "[" + link.getId() + ": " + link.getAllowedModes().toString() + "]");
				}
			}
		}
	}

	private Link addLink(String name, double length, Node fromNode, Node toNode, Set<String> modes, double capacity, double freespeed, double lanes) {
		
		Link link = this.scenario.getNetwork().getFactory().createLink(Id.create(name + "_" + fromNode.getId().toString() + "-" + toNode.getId().toString(), Link.class), fromNode, toNode);
        link.setAllowedModes(modes);
        link.setLength(length);
        link.setFreespeed(freespeed);
        link.setCapacity(capacity);
        link.setNumberOfLanes(lanes);
        this.scenario.getNetwork().addLink(link);
        
        return link;
	}
    
    private static Node getNearestNode(Network network, final Coord coord) {
        Node nearestNode = NetworkUtils.getNearestNode((network),coord);
        if ( nearestNode == null ) {
            throw new RuntimeException("Could not find a nearest node. Aborting...") ;
        }

        if ( nearestNode.getInLinks().isEmpty() || nearestNode.getOutLinks().isEmpty() ) {
        	
        	int extensionRadius = 1000;
        	for (int radius = extensionRadius; radius <= 1000000; radius = radius + extensionRadius) {
        		for (Node node : NetworkUtils.getNearestNodes(network, coord, radius)) {
        			 if ( node.getInLinks().isEmpty() || node.getOutLinks().isEmpty() ) {
         	        	// skip (we need both ingoing Links and outgoing Links)
         	        } else {
         	        	return node;
         	        }
        		}
        	}
        	throw new RuntimeException("The nearest node has either no ingoing or no outgoing links. [node = " + nearestNode.getId() + "] Couldn't find any other node around the coordinate. Maybe increase the maximum radius?"  ) ;
        }

        return nearestNode;
    }

	/**
	 * 
	 * Creates the terminal (transit stop + access/egress links) and connects the terminal to the road network.
	 * 
	 * @param terminal
	 */
	public void addTerminalAndConnectToRoadNetwork(Terminal terminal) {
		
        // create the terminal link: (t_IN)<#####>(t_OUT)
   
		Node tIn = nf.createNode(Id.create(terminal.getName() + "_IN", Node.class), new Coord(terminal.getCoord().getX() - xCoordGapRailInOut , terminal.getCoord().getY()));
        Node tOut = nf.createNode(Id.create(terminal.getName() + "_OUT", Node.class), terminal.getCoord());
        network.addNode(tIn);
        network.addNode(tOut);
        
        Set<String> modesTerminalLink = new HashSet<>();
        modesTerminalLink.add("rail");
        for (String kvMode : terminal.getMode2terminalCapacity().keySet()) {
            modesTerminalLink.add(kvMode);
        }
        
        Link terminalLink = addLink(terminal.getName(), distanceEachTerminalLink, tIn, tOut, modesTerminalLink, largeLinkCapacity, speedEachTerminalLink, numberOfLanesCarRailLink);
        
        // create transit stop and put it on the link
        
       	TransitStopFacility stop = sf.createTransitStopFacility(Id.create(terminal.getName(), TransitStopFacility.class), terminal.getCoord(), false);
    	stop.setLinkId(terminalLink.getId());
    	
    	if (terminal.getMode2terminalCapacity().size() > 1) {
    		log.warn("There are several modes defined for terminal " + terminal.getName() + ". "
    				+ " Should be revised once we need this functionality. For now, we are using the minimum capacity for the train-stack queue.");
    	}
    	
    	// use the minimum capacity given for that terminal
    	double terminalCapacity = Double.MAX_VALUE;
    	for (Double capacity : terminal.getMode2terminalCapacity().values()) {
    		if (capacity < terminalCapacity) {
    			terminalCapacity = capacity;
    		}
    	}
    	
    	// the capacity is given in containers per hour, and here we need the time consumption per container
    	stop.getAttributes().putAttribute("accessTime", 3600./terminalCapacity);
    	stop.getAttributes().putAttribute("egressTime", 3600./terminalCapacity);
    	
    	// define access / egress modes for intermodal router
    	for (String kvMode : terminal.getMode2terminalCapacity().keySet()) {
        	stop.getAttributes().putAttribute(carAccessibleAttributePrefix + kvMode, 1);
        }
    	
        schedule.addStopFacility(stop);
        
        // store terminal information
        terminal.setTerminalLink(terminalLink);
        terminal.setStop(stop);
        
        // connect the terminal link to road network using a link queue as crane
        
        for (String kvMode : terminal.getMode2terminalCapacity().keySet()) {
            connectToRoadNetwork(terminal.getName(), terminalLink, terminal.getMode2terminalCapacity().get(kvMode), terminal.getMode2operatingTimes().get(kvMode).getFirst(), terminal.getMode2operatingTimes().get(kvMode).getSecond(), kvMode);
        }
                
	}
	
	/**
	 * 
	 * Creates the links shown below to connect a terminal to the road network.
	 * 
	 * 
	 * 	####(XA)*****>(XB)####
	 *        A        |
	 *		__|________|
	 *	    | |
	 *		| L________
	 *		|		   |
	 *		V          |
	 *		(XA1)--->(XB1)					     
	 *        A  	  |
	 *		  |       |
	 *		  L__   __|
	 *           | |
	 *		     | V
	 * (1)------ (2) --------- (3)
	 *        
	 *        
	 *        --- road
	 *        ### rail
	 *		  *** rail and road
	 *        
	 * 
	 * @param name
	 * @param railHubLink
	 * @param containersPerHour
	 * @param from
	 * @param to
	 * @param carModeKV
	 */
	private void connectToRoadNetwork(String name, Link railHubLink, Double containersPerHour, double from, double to, String carModeKV) {
	
		// crane link XA1->XB1
		
        LineSegment ls = new LineSegment(railHubLink.getFromNode().getCoord().getX(), railHubLink.getFromNode().getCoord().getY(), railHubLink.getToNode().getCoord().getX(), railHubLink.getToNode().getCoord().getY()); 
        
    	Coordinate hubXB1Coordinate = ls.pointAlongOffset(1, -1 * railCraneGap);
    	Coordinate hubXA1Coordinate = ls.pointAlongOffset(0, -1 * railCraneGap);
    	Coord hubXB1Coord = new Coord(hubXB1Coordinate.x, hubXB1Coordinate.y);
    	Coord hubXA1Coord = new Coord(hubXA1Coordinate.x, hubXA1Coordinate.y);	

    	Node xB1 = nf.createNode(Id.create(railHubLink.getToNode().getId().toString() + "1_" + carModeKV, Node.class), hubXB1Coord);
    	network.addNode(xB1);
    	Node xA1 = nf.createNode(Id.create(railHubLink.getFromNode().getId().toString() + "1_" + carModeKV, Node.class), hubXA1Coord);
    	network.addNode(xA1);
    	
    	Link craneLink = addLink(name, distanceEachTerminalLink, xA1, xB1, new HashSet<>(Arrays.asList(carModeKV)), containersPerHour, speedEachTerminalLink, numberOfLanesTerminalLink);
    	
    	for (int day = 0; day < simulatedDays; day++) {
    		
    		double daySecondsToAdd = day * 24 * 3600.;
    		
    		if (from > 0.) {
        		
        		{
    	    		// 0 till service start
    	    		NetworkChangeEvent networkChangeStart = new NetworkChangeEvent(0. + daySecondsToAdd);
    	    		networkChangeStart.addLink(craneLink);
    	    		networkChangeStart.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
    	    		networkChangeStart.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
    				this.networkChangeEvents.add(networkChangeStart);
    				
    				// at service start time: set back to original values
    				NetworkChangeEvent networkChangeSetBack = new NetworkChangeEvent(from + daySecondsToAdd);
    				networkChangeSetBack.addLink(craneLink);
    				networkChangeSetBack.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, craneLink.getCapacity() / 3600.));
    				networkChangeSetBack.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, craneLink.getNumberOfLanes()));
    				this.networkChangeEvents.add(networkChangeSetBack);
        		}
        	}
        	
        	if (to < 24 * 3600.) {
        		// service end time
        		NetworkChangeEvent networkChangeStart = new NetworkChangeEvent(to + daySecondsToAdd);
        		networkChangeStart.addLink(craneLink);
        		networkChangeStart.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
        		networkChangeStart.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
    			this.networkChangeEvents.add(networkChangeStart);
        	}
    	}
    	
    	// connect parallel links    	
    	addLink(name, distanceEachTerminalLink, railHubLink.getToNode(), xA1, new HashSet<>(Arrays.asList(carModeKV)), largeLinkCapacity, speedEachTerminalLink, numberOfLanesTerminalConnectionLinks);
    	addLink(name, distanceEachTerminalLink, xB1, railHubLink.getFromNode(), new HashSet<>(Arrays.asList(carModeKV)), largeLinkCapacity, speedEachTerminalLink, numberOfLanesTerminalConnectionLinks);
        
    	// connect to original car only road network
    	
        Node nearestNodeXA2 = getNearestNode(carOnlyNetwork, xA1.getCoord()); 
        addLink(name, NetworkUtils.getEuclideanDistance(nearestNodeXA2.getCoord(), xA1.getCoord()), nearestNodeXA2, xA1, new HashSet<>(Arrays.asList(carModeKV)), largeLinkCapacity, 13.8889, numberOfLanesTerminalConnectionLinks);            
        
        Node nearestNodeXB2 = getNearestNode(carOnlyNetwork, xB1.getCoord()); 
        addLink(name, NetworkUtils.getEuclideanDistance(xB1.getCoord(), nearestNodeXB2.getCoord()), xB1, nearestNodeXB2, new HashSet<>(Arrays.asList(carModeKV)), largeLinkCapacity, 13.8889, numberOfLanesTerminalConnectionLinks);            
	}
	
	/**
	 * 
	 * Adds a cargo train connection in which the units is set to 1.0
	 * 
	 * @param routeCounter a unique integer value
	 * @param transitLine name of the transit line, used to create IDs
	 * @param transitRoute name of the transit route, used to create IDs
	 * @param routeInfos a correctly sorted list which contains the route stop infos
	 * @param vehicleCapacity train capacity in number of containers that fit into the train
	 * @param relation2distance (optional) a map which contains the distances between the terminals; if null the euclidean distance will be used
	 */
	public TransitLine addCargoConnection(int routeCounter, String transitLine, String transitRoute, List<RouteStopInfo> routeInfos, int vehicleCapacity, Map<String, Integer> relation2distance) {
		Double units = 1.0;
		return this.addCargoConnection(routeCounter, transitLine, transitRoute, units, routeInfos, vehicleCapacity, relation2distance);
	}

	/**
	 * 
	 * Adds a cargo train connection.
	 * 
	 * @param routeCounter a unique integer value
	 * @param transitLine name of the transit line, used to create IDs
	 * @param transitRoute name of the transit route, used to create IDs
	 * @param units train units, e.g. 1.0 for a full train, 0.5 for a half train
	 * @param routeInfos a correctly sorted list which contains the route stop infos
	 * @param vehicleCapacity train capacity in number of containers that fit into the train
	 * @param relation2distance (optional) a map which contains the distances between the terminals; if null the euclidean distance will be used
	 */
	public TransitLine addCargoConnection(int routeCounter, String transitLine, String transitRoute, Double units, List<RouteStopInfo> routeInfos, int vehicleCapacity, Map<String, Integer> relation2distance) {
		
		if (routeInfos.size() < 2) throw new RuntimeException("At least two route stops required. Aborting...");
		if (units == null || units > 1.0 || units == 0.) throw new RuntimeException("Check units value: " + units);

		log.info("Adding cargo connection...");
		
		List<Id<Link>> railLinks = new ArrayList<>();     
		RouteStopInfo previousStop = null;
		for (RouteStopInfo stop : routeInfos) {
			log.info("Stop: " + stop.getTransitStop().getId());
			if (previousStop == null) {
				// add first terminal
				railLinks.add(stop.getLink().getId());
			} else {
				// not the first terminal
				// add connecting link
				Link connectingLink = addLink(transitLine + "_" + transitRoute + "_" + routeCounter,
						getDistance(relation2distance, previousStop, stop),
						previousStop.getLink().getToNode(),
						stop.getLink().getFromNode(),
						new HashSet<>(Arrays.asList("rail")),
						largeLinkCapacity,
						13.8889,
						1);
				railLinks.add(connectingLink.getId());
				// add terminal
				railLinks.add(stop.getLink().getId());
			}
			previousStop = stop;
		}
		
        List<TransitRouteStop> stops = new ArrayList<>();
		int stopCounter = 0;
		double firstTrainStartTime = Double.NaN;
		
		double latestDepartureArrivalTimeOffset = 0.;
		for (RouteStopInfo info : routeInfos) {
			
			if (stopCounter == 0) {
				// first stop, this is the actual start time when the vehicle arrives at the first stop, e.g. 5.00 a.m., 
				firstTrainStartTime = info.gettArrival();
				
				// compute the offsets
				
				// at the first stop, we want the vehicle to arrive some time before the departure
				double arrivalOffset = info.gettArrival() - firstTrainStartTime;
				latestDepartureArrivalTimeOffset = arrivalOffset; 

		        double departureOffset = info.gettDeparture() - firstTrainStartTime;
		        if (departureOffset < latestDepartureArrivalTimeOffset) {
					departureOffset = departureOffset + 24 * 3600.;
				}
				stops.add(sf.createTransitRouteStopBuilder(info.getTransitStop()).arrivalOffset(arrivalOffset).departureOffset(departureOffset).build());
				latestDepartureArrivalTimeOffset = departureOffset; 
				
			} else if (stopCounter == routeInfos.size() - 1) {
				// last stop, we don't need a departure
				double arrivalOffset = info.gettArrival() - firstTrainStartTime;
				if (arrivalOffset < latestDepartureArrivalTimeOffset) {
					arrivalOffset = arrivalOffset + 24 * 3600.;
				}
		        stops.add(sf.createTransitRouteStopBuilder(info.getTransitStop()).arrivalOffset(arrivalOffset).build());
		        latestDepartureArrivalTimeOffset = arrivalOffset;
		        
			} else {
				// intermediate stop
				double arrivalOffset = info.gettArrival() - firstTrainStartTime;
				if (arrivalOffset < latestDepartureArrivalTimeOffset) {
					arrivalOffset = arrivalOffset + 24 * 3600.;
				}
				latestDepartureArrivalTimeOffset = arrivalOffset;
				double departureOffset = info.gettDeparture() - firstTrainStartTime;
				if (departureOffset < latestDepartureArrivalTimeOffset) {
					departureOffset = departureOffset + 24 * 3600.;
				}
				latestDepartureArrivalTimeOffset = departureOffset;
		        stops.add(sf.createTransitRouteStopBuilder(info.getTransitStop()).arrivalOffset(arrivalOffset).departureOffset(departureOffset).build());
			}
			stopCounter++;
		}
		
		VehicleType vehType = addVehicleType("cargoTrain_" + transitLine + "_" + transitRoute + "_" + routeCounter, (int) (vehicleCapacity * units));
        
		NetworkRoute networkRoute = RouteUtils.createLinkNetworkRouteImpl(railLinks.get(0), railLinks.subList(1, railLinks.size() - 1), railLinks.get(railLinks.size() - 1));
        TransitRoute route = sf.createTransitRoute(Id.create(transitLine + "_" + transitRoute + "_" + routeCounter, TransitRoute.class), networkRoute, stops, "rail");
    	
    	// generate departures for all days
		for (int day = 0; day < simulatedDays; day++) {
			
			// create a new vehicle for each day
			Vehicle veh = vf.createVehicle(Id.create("cargoTrain_" + transitLine + "_" + transitRoute + "_" + routeCounter + "_" + day, Vehicle.class), vehType);
	        vehicles.addVehicle(veh);
			
			double routeDepartureDay = day * 24 * 3600 + firstTrainStartTime;
			
			Departure departure = sf.createDeparture(Id.create(day, Departure.class), routeDepartureDay);
	        departure.setVehicleId(veh.getId());
	        route.addDeparture(departure);
		}
		
		Id<TransitLine> lineId = Id.create(transitLine, TransitLine.class);
		TransitLine line;
		if (schedule.getTransitLines().get(lineId) == null) {
			line = sf.createTransitLine(Id.create(transitLine, TransitLine.class));
			schedule.addTransitLine(line);
		} else {
			line = schedule.getTransitLines().get(lineId);
		}
        line.addRoute(route);
        
        return line;
        		
	}
	
	private double getDistance(Map<String, Integer> relation2distance, RouteStopInfo previousStop, RouteStopInfo stop) {
		if (relation2distance == null) {
			return NetworkUtils.getEuclideanDistance(previousStop.getTransitStop().getCoord(), stop.getTransitStop().getCoord());
		} else {
			String from = previousStop.getTransitStop().getId().toString();
			String to = stop.getTransitStop().getId().toString();
			String relation = from + "_" + to;
			if (relation2distance.get(relation) == null) {
				throw new RuntimeException("No distance stored for given relation. Aborting... " + relation);
			} else {
				return relation2distance.get(relation);
			}
		}
	}
	
	private VehicleType addVehicleType(String name, int vehicleCapacity) {
		VehicleType vehType = vf.createVehicleType(Id.create("cargoTrain_" + name, VehicleType.class));
        VehicleCapacity vehCapacity = vehType.getCapacity();
        vehCapacity.setSeats(vehicleCapacity);
        VehicleUtils.setDoorOperationMode(vehType, DoorOperationMode.serial);
        vehicles.addVehicleType(vehType);
		return vehType;
	}

	/**
	 * @return the terminals
	 */
	public Map<String, Terminal> getTerminals() {
		return this.terminals;
	}

	/**
	 * @return the networkChangeEvents
	 */
	public List<NetworkChangeEvent> getNetworkChangeEvents() {		
		return networkChangeEvents;
	}
    
	final double additionalSeconds = 60.;

	public void addHGVnightRestriction() {
		for (Link link : scenario.getNetwork().getLinks().values()) {
    		if (link.getAllowedModes().contains("car")) {
				
				NetworkChangeEvent networkChangeEventInitial = new NetworkChangeEvent(0. + additionalSeconds);
    			networkChangeEventInitial.addLink(link);
    			networkChangeEventInitial.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
    			networkChangeEventInitial.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0));
				this.networkChangeEvents.add(networkChangeEventInitial);
    	    	
				for (int day = 0; day < simulatedDays; day++) {

    				double restrictionEndTime = day * 24 * 3600. + 5 * 3600. + additionalSeconds;
    				NetworkChangeEvent networkChangeEventEnd = new NetworkChangeEvent(restrictionEndTime);
    				networkChangeEventEnd.addLink(link);
    				networkChangeEventEnd.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, link.getFlowCapacityPerSec()));
    				networkChangeEventEnd.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, link.getNumberOfLanes()));
    				this.networkChangeEvents.add(networkChangeEventEnd);
    				
    				double restrictionStartTime = day * 24 * 3600. + 22 * 3600. + additionalSeconds;
    				NetworkChangeEvent networkChangeEventStart = new NetworkChangeEvent(restrictionStartTime);
    				networkChangeEventStart.addLink(link);
    				networkChangeEventStart.setFlowCapacityChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0.));
    				networkChangeEventStart.setLanesChange(new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, 0));
    				this.networkChangeEvents.add(networkChangeEventStart);
    	    	}
   			}
   		}
	}

}
