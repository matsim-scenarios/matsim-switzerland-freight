/**
 * 
 */
package ch.sbb.intermodalfreight.prepare.supply;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.io.NetworkChangeEventsParser;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleWriter;

import ch.sbb.intermodalfreight.utils.config.Project;

/**
 * 
 * An example how to generate the cargo supply.
 * 
 * @author ikaddoura
 *
 */
public class RunGenerateCargoSupply {
	
	private static final Logger log = LogManager.getLogger(RunGenerateCargoSupply.class);

	public static void main(String[] args) throws IOException {	
		Project project = new Project();
		RunGenerateCargoSupply runGenerateCargoSupply = new RunGenerateCargoSupply();
		runGenerateCargoSupply.run(project);
	}

	/**
	 * 
	 * Generates the required MATSim supply input files (transit schedule, transit network, transit vehicles) based on the given properties.
	 * 
	 * @param project
	 * @throws IOException
	 */
	public void run(Project project) throws IOException {
		
		final String outputDir = project.getDirectory().getMatsimInputPath().toString() + "/";

		final String onlyCarNetwork = project.getDirectory().getOriginalDataPath() + "/" + project.getMatsimInput().getSupply().getNetwork();
		final String networkChangeEventsFile = project.getDirectory().getOriginalDataPath() + "/" + project.getMatsimInput().getSupply().getNetworkChangeEvents();
		final String inputScheduleXLSX = project.getDirectory().getOriginalDataPath() + "/" + project.getMatsimInput().getSupply().getSchedule();
		final String inputTerminalsFile = project.getDirectory().getOriginalDataPath() + "/" + project.getMatsimInput().getSupply().getTerminals();
		final String inputDistancesCSV = project.getDirectory().getOriginalDataPath() + "/" + project.getMatsimInput().getSupply().getDistances();
		
	    final String sheetName = project.getMatsimInput().getSupply().getSheet();
		final int cargoTrainCapacityTEU = project.getMatsimInput().getSupply().getTrainCapacity();
		final double distanceTerminalCraneLinks = project.getMatsimInput().getSupply().getCraneLinkLength();
		final double craneTravelTime = project.getMatsimInput().getSupply().getCraneTravelTime();
		final int simulatedDays = project.getMatsimInput().getSimulatedDays();
		final String linksToRemoveFromOriginalCarNetworkAsCommaSeparatedString = project.getMatsimInput().getSupply().getLinksToRemoveFromNetwork();
		final double arrivalDepartureOffsetFirstStop = project.getMatsimInput().getSupply().getArrivalDepartureOffsetFirstStop();
		
		List<Id<Link>> linksToRemoveFromOriginalCarNetwork = new ArrayList<>();
		if (linksToRemoveFromOriginalCarNetworkAsCommaSeparatedString == null | linksToRemoveFromOriginalCarNetworkAsCommaSeparatedString == "") {
			// nothing to do
		} else {
			for (String linkId : linksToRemoveFromOriginalCarNetworkAsCommaSeparatedString.split(",")) {
				linksToRemoveFromOriginalCarNetwork.add(Id.createLinkId(linkId));
			}
		}
		
		log.info("Writing into " + outputDir);
								
		try {
			Files.createDirectories(Paths.get(outputDir));
		} catch (IOException e) {
			log.error("Failed to create directories", e);
		}
		

		// ################################################################################################################
		
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("CH1903plus_LV95");
		config.network().setInputFile(onlyCarNetwork);
		config.network().setInputCRS("CH1903plus_LV95");
		Scenario scenario = ScenarioUtils.loadScenario(config);		
        Network originalCarNetwork = ScenarioUtils.loadScenario(config).getNetwork();
        
		GenerateCargoSupply supply = new GenerateCargoSupply(scenario, originalCarNetwork, distanceTerminalCraneLinks, craneTravelTime, simulatedDays);
		
        // first read and add the terminals		
		Map<String,Terminal> terminals = new TerminalsFileReader(inputTerminalsFile).getName2terminal();
		for (Terminal terminal : terminals.values()) {
			supply.addTerminalAndConnectToRoadNetwork(terminal);
		}
		
		// then read the schedule xlsx and add the transit lines, routes and departures	
		List<RouteInfo> routeInfos = new CargoScheduleReader(inputScheduleXLSX, sheetName, terminals, arrivalDepartureOffsetFirstStop).getRouteInfos();
		Map<String, Integer> relation2distance = new TerminalDistanceReader().getTerminalDistances(inputDistancesCSV);
		
		int routeCounter = 0;
		for (RouteInfo routeInfo : routeInfos) {
	    	log.info("Route info: " + routeInfo.toString());
	    	
	    	String transitLine = routeInfo.getLine();
	    	String transitRoute = routeInfo.getRoute();
			List<RouteStopInfo> routeStopInfos = routeInfo.getRouteStopInfos();
	    	
			supply.addCargoConnection(routeCounter, transitLine, transitRoute, routeStopInfos, cargoTrainCapacityTEU, relation2distance);
			
			routeCounter++;
	    }
			
		// see if we have to adjust the network
		if (project.getMatsimInput().getSupply().isNetworkNightHGVRestriction()) {
			log.info("Creating network change events to account for the night ban of HGV.");
			supply.addHGVnightRestriction();
		}
		
		// Allow rail access/egress mode wherever the car mode is allowed.
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> modes = new HashSet<>();
				for (String mode : link.getAllowedModes()) {
					modes.add(mode);
					modes.add(KVModes.CAR_KV_CONTAINER);
				}
				link.setAllowedModes(modes);
			}
		}
				
		// merge with existing network change events
		List<NetworkChangeEvent> changeEvents = new ArrayList<>();
		if (networkChangeEventsFile == null || networkChangeEventsFile.endsWith("null") || networkChangeEventsFile.equals("")) {
			log.info("There are no network change events given as input file.");
			
		} else {
			log.info("Reading input network change events...");
			new NetworkChangeEventsParser(scenario.getNetwork(), changeEvents).readFile(networkChangeEventsFile);
			log.info("Reading input network change events... Done. Number of network change events: " + changeEvents.size());
		}
		
		log.info("Adding network change events from supply generation (terminal closing times and night ban of HGV...");
		changeEvents.addAll(supply.getNetworkChangeEvents());
		log.info("New total number of network change events: " + changeEvents.size());
		
		// remove some links
		for (Id<Link> linkId : linksToRemoveFromOriginalCarNetwork) {
			scenario.getNetwork().removeLink(linkId);
		 }
		
		// make sure we don't have any network change events affecting the links we removed from the network
		List<NetworkChangeEvent> changeEventsToRemove = new ArrayList<>();
		for (NetworkChangeEvent nce : changeEvents) {
			for (Link linkInNCE : nce.getLinks()) {
				for (Id<Link> linkIdToRemove : linksToRemoveFromOriginalCarNetwork) {
					if (linkInNCE.getId().toString().equals(linkIdToRemove.toString())) {
						changeEventsToRemove.add(nce);
					}
		        }
			}
		}
		
		log.info("Number of change events to remove: " + changeEventsToRemove.size());
		for (NetworkChangeEvent nceToRemove : changeEventsToRemove) {
			changeEvents.remove(nceToRemove);
		}
		log.info("New total number of network change events: " + changeEvents.size());
				
		new NetworkChangeEventsWriter().write(outputDir + "cargoNetworkChangeEvents.xml.gz", changeEvents);		
		new NetworkWriter(scenario.getNetwork()).write(outputDir + "cargoNetwork.xml.gz");
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputDir + "cargoTransitSchedule.xml.gz");
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputDir + "cargoTransitVehicles.xml.gz");
		
		log.info("Done.");
	}

}
