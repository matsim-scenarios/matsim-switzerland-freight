/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package ch.sbb.intermodalfreight.prepare.supply;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.io.NetworkChangeEventsParser;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.MatsimVehicleWriter;

import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup;
import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.TerminalCapacityApproach;
import ch.sbb.intermodalfreight.simulate.RunIntermodalFreightScenario;

/**
 * 
 * In this test class, the rail cargo supply is generated from code.
 * Some additional input files are provided as input (network, network change events) which are then extended by rail infrastructure and terminals.
 * The modified network as well as the newly generated transit schedule and transit vehicle files are written into the test output directory.
 * In some tests, additional input files (plans, config) are provided and copied from the test input directory to the test output directory and a simulation run is started from there.
 * After running the simulation the scores are tested. If the scores change there might be changes in the supply preparation.
 *
 * In this test, the demand consist of a single container type.
 * 
 * @author ikaddoura
 *
 */
public class AddCargoSupplyTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;
	
	/**
	 * Only adds a single cargo connection without running the simulation.
	 */
	@Test
	public final void testAddCargoConnectionNoSimulation() {

		try {
			
			System.out.println("Running test...");
			
			Config config = ConfigUtils.createConfig();
			config.network().setInputFile(utils.getClassInputDirectory() + "carOnlyNetwork.xml");
			Scenario scenario = ScenarioUtils.loadScenario(config);
						
	        Network originalCarNetwork = ScenarioUtils.loadScenario(config).getNetwork();
	        

	        Map<String, Terminal> terminals = new HashMap<>();
	                
			terminals.put("t1", new Terminal("t1", "t1", "t1",
					new Coord(0., -8000.),
					Map.of(KVModes.CAR_KV_CONTAINER, 40.),
					Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(0., 24 * 3600.)))
					);
			
	        terminals.put("t2", new Terminal("t2", "t2", "t2",
	        		new Coord(50000., -8000.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, 40.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(3 * 3600., 20 * 3600.)))
	        		);
	        
	        terminals.put("t3", new Terminal("t3", "t3", "t3",
	        		new Coord(120000., -8000.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, 6.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(0 * 3600., 24 * 3600.)))
	        		);

	        // first add the terminals
			GenerateCargoSupply supply = new GenerateCargoSupply(scenario, originalCarNetwork, 600., 216., 3);
			for (Terminal terminal : terminals.values()) {
				supply.addTerminalAndConnectToRoadNetwork(terminal);
			}
			
			// then add the transit lines, routes and departures
			
			{
				// first direction
				String transitLine = "line1";
				String transitRoute = "route1";
				
				String t1 = "t1";			
				double t1Arrival = 21 * 3600.;
				double t1Departure = 23 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 0.5 * 3600.;
				double t2Departure = 2.5 * 3600.;
				
				String t3 = "t3";
				double t3Arrival = 5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				TransitLine line = supply.addCargoConnection(0, transitLine, transitRoute, routeInfos, 40, null);
				
				for (TransitRoute route : line.getRoutes().values()) {
					System.out.println("Line: " + line.getId() + " Route: " + route.getId());
					for (TransitRouteStop stop : route.getStops()) {
						if (stop.getArrivalOffset().isDefined()) System.out.println("Arrival offset: " + Time.writeTime(stop.getArrivalOffset().seconds(), Time.TIMEFORMAT_HHMMSS));
						if (stop.getDepartureOffset().isDefined()) System.out.println("Departure offset: " + Time.writeTime(stop.getDepartureOffset().seconds(), Time.TIMEFORMAT_HHMMSS));
					}	
					
					for (Departure dep : route.getDepartures().values()) {
						System.out.println("Departure: " + Time.writeTime(dep.getDepartureTime(), Time.TIMEFORMAT_HHMMSS));
					}
					
				}
				
				TransitRoute route = line.getRoutes().get(Id.create("line1_route1_0", TransitRoute.class));
				
				Assert.assertEquals("Schedule has changed.", 0., route.getStops().get(0).getArrivalOffset().seconds(), MatsimTestUtils.EPSILON);
				Assert.assertEquals("Schedule has changed.", 7200., route.getStops().get(0).getDepartureOffset().seconds(), MatsimTestUtils.EPSILON);
				Assert.assertEquals("Schedule has changed.", 3 * 3600. + 0.5 * 3600., route.getStops().get(1).getArrivalOffset().seconds(), MatsimTestUtils.EPSILON);
				Assert.assertEquals("Schedule has changed.", 5 * 3600. + 0.5 * 3600., route.getStops().get(1).getDepartureOffset().seconds(), MatsimTestUtils.EPSILON);
				Assert.assertEquals("Schedule has changed.", 8 * 3600., route.getStops().get(2).getArrivalOffset().seconds(), MatsimTestUtils.EPSILON);
			}	
			
		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}

	@Test
	public final void testAddCargoConnectionAndRunSimulation1() {

		try {
			
			System.out.println("Running test...");
			
			Config config = ConfigUtils.createConfig();
			config.network().setInputFile(utils.getClassInputDirectory() + "carOnlyNetwork.xml");
			Scenario scenario = ScenarioUtils.loadScenario(config);
						
	        Network originalCarNetwork = ScenarioUtils.loadScenario(config).getNetwork();
	        

	        Map<String, Terminal> terminals = new HashMap<>();
	                
			terminals.put("t1", new Terminal("t1", "t1", "t1",
					new Coord(0., -8000.),
					Map.of(KVModes.CAR_KV_CONTAINER, 40.),
					Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(0., 24 * 3600.)))
					);
			
	        terminals.put("t2", new Terminal("t2", "t2", "t2",
	        		new Coord(50000., -8000.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, 40.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(3 * 3600., 20 * 3600.)))
	        		);
	        
	        terminals.put("t3", new Terminal("t3", "t3", "t3",
	        		new Coord(120000., -8000.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, 6.),
	        		Map.of(KVModes.CAR_KV_CONTAINER, new Tuple<Double, Double>(0 * 3600., 24 * 3600.)))
	        		);

	        // first add the terminals
			GenerateCargoSupply supply = new GenerateCargoSupply(scenario, originalCarNetwork, 600., 216., 3);
			for (Terminal terminal : terminals.values()) {
				supply.addTerminalAndConnectToRoadNetwork(terminal);
			}
			
			// then add the transit lines, routes and departures
			
			{
				// first direction
				String transitLine = "line1";
				String transitRoute = "route1";
				
				String t1 = "t1";			
				double t1Arrival = -1 * 3600.;
				double t1Departure = 2 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 8.5 * 3600.;
				double t2Departure = 9 * 3600.;
				
				String t3 = "t3";
				double t3Arrival = 9.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(0, transitLine, transitRoute, routeInfos, 40, null);
			}
			
			{
				// first direction, a later train
				String transitLine = "line1";
				String transitRoute = "route1b";
				
				String t1 = "t1";			
				double t1Arrival = 14.5 * 3600.;
				double t1Departure = 15 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 15.5 * 3600.;
				double t2Departure = 16 * 3600.;
				
				String t3 = "t3";
				double t3Arrival = 13.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(0, transitLine, transitRoute, routeInfos, 40, null);
			}
			
			{
				// second direction
				String transitLine = "line1";
				String transitRoute = "route2";
				
				String t1 = "t3";			
				double t1Arrival = 17.5 * 3600.;
				double t1Departure = 18 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 18.5 * 3600.;
				double t2Departure = 19 * 3600.;
				
				String t3 = "t1";
				double t3Arrival = 21.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(1, transitLine, transitRoute, routeInfos, 40, null);
			}
												
			String outputDir = utils.getOutputDirectory();
			
			// add all car KV modes as allowed modes to the network
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
			List<NetworkChangeEvent> changeEvents = new ArrayList<>() ;
			new NetworkChangeEventsParser(scenario.getNetwork(), changeEvents).readFile(utils.getClassInputDirectory() + "networkChangeEvents.xml");
			changeEvents.addAll(supply.getNetworkChangeEvents());
					
			// write all input files out
			new NetworkChangeEventsWriter().write(outputDir + "cargoTerminalNetworkChangeEvents.xml.gz", changeEvents);
			new NetworkWriter(scenario.getNetwork()).write(outputDir + "cargoNetwork.xml.gz");
			new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputDir + "cargoTransitSchedule.xml.gz");
			new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputDir + "cargoTransitVehicles.xml.gz");
			
			// now run the simulation and see if everything works
			
			Scenario scenarioForTesting = run();
			
			// uses train + arrives on time
			Assert.assertEquals("Scores have changed.", 780.2816162099284, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			// uses train + arrives on time, slightly reduced score because this container is at a later position in the queue and needs more time
			Assert.assertEquals("Scores have changed.", 774.976060654373, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container10")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);			
			
			// uses train + arrives on time, even more reduced score because this container is at a later position in the queue and needs more time
			Assert.assertEquals("Scores have changed.", 762.726060654373, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container15")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);			
					
			// uses train + arrives delayed (inflexible container)
			Assert.assertEquals("Scores have changed.", -1223.8433837900716, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container8_inflexible")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// road user traveling before road closure
			Assert.assertEquals("Scores have changed.", 549.2444444444445, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container9_road_before_closing_road")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// road user traveling during road closure, should be delayed (requires more time on the road than the other road users)
			Assert.assertEquals("Scores have changed.", -1473.2208333333333, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container10_road_affected_by_road_closure")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// road user traveling after road closure
			Assert.assertEquals("Scores have changed.", 549.2444444444445, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container11_road_after_road_closure")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
				
			// road user traveling at night should get a penalty
			Assert.assertEquals("Scores have changed.", -2450.7555555555555, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container12_night_penalty")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}

	@Test
	public final void testAddCargoConnectionAndRunSimulation2() {

		try {
			
			System.out.println("Running test...");
			
			Config config = ConfigUtils.createConfig();
			config.network().setInputFile(utils.getClassInputDirectory() + "carOnlyNetwork.xml");
			Scenario scenario = ScenarioUtils.loadScenario(config);
						
	        Network originalCarNetwork = ScenarioUtils.loadScenario(config).getNetwork();
	        Map<String, Terminal> terminals = new TerminalsFileReader(utils.getClassInputDirectory() + "terminals.csv").getName2terminal();

	        // first add the terminals
			GenerateCargoSupply supply = new GenerateCargoSupply(scenario, originalCarNetwork, 600., 216., 3);
			for (Terminal terminal : terminals.values()) {
				supply.addTerminalAndConnectToRoadNetwork(terminal);
			}
			
			// then add the transit lines, routes and departures
			
			{
				// first direction
				String transitLine = "line1";
				String transitRoute = "route1";
				
				String t1 = "t1";			
				double t1Arrival = -1 * 3600.;
				double t1Departure = 2 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 8.5 * 3600.;
				double t2Departure = 9 * 3600.;
				
				String t3 = "t3";
				double t3Arrival = 9.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(0, transitLine, transitRoute, routeInfos, 40, null);
			}
			
			{
				// first direction, a later train
				String transitLine = "line1";
				String transitRoute = "route1b";
				
				String t1 = "t1";			
				double t1Arrival = 14.5 * 3600.;
				double t1Departure = 15 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 15.5 * 3600.;
				double t2Departure = 16 * 3600.;
				
				String t3 = "t3";
				double t3Arrival = 13.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(0, transitLine, transitRoute, routeInfos, 40, null);
			}
			
			{
				// second direction
				String transitLine = "line1";
				String transitRoute = "route2";
				
				String t1 = "t3";			
				double t1Arrival = -1 * 3600.;
				double t1Departure = 2 * 3600.;
				
				String t2 = "t2";
				double t2Arrival = 8.5 * 3600.;
				double t2Departure = 9 * 3600.;
				
				String t3 = "t1";
				double t3Arrival = 9.5 * 3600.;
				double t3Departure = Double.NaN;
				
				List<RouteStopInfo> routeInfos = new ArrayList<>();
				RouteStopInfo stop1 = new RouteStopInfo(terminals.get(t1).getTerminalLink(), terminals.get(t1).getStop(), t1Arrival, t1Departure);
				RouteStopInfo stop2 = new RouteStopInfo(terminals.get(t2).getTerminalLink(), terminals.get(t2).getStop(), t2Arrival, t2Departure);
				RouteStopInfo stop3 = new RouteStopInfo(terminals.get(t3).getTerminalLink(), terminals.get(t3).getStop(), t3Arrival, t3Departure);

				routeInfos.add(stop1);
				routeInfos.add(stop2);
				routeInfos.add(stop3);
				
				supply.addCargoConnection(1, transitLine, transitRoute, routeInfos, 40, null);
			}
												
			String outputDir = utils.getOutputDirectory();
			
			// add all car KV modes as allowed modes to the network
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
			List<NetworkChangeEvent> changeEvents = new ArrayList<>() ;
			new NetworkChangeEventsParser(scenario.getNetwork(), changeEvents).readFile(utils.getClassInputDirectory() + "networkChangeEvents.xml");
			changeEvents.addAll(supply.getNetworkChangeEvents());
					
			// write all input files out
			new NetworkChangeEventsWriter().write(outputDir + "cargoTerminalNetworkChangeEvents.xml.gz", changeEvents);
			new NetworkWriter(scenario.getNetwork()).write(outputDir + "cargoNetwork.xml.gz");
			new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputDir + "cargoTransitSchedule.xml.gz");
			new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputDir + "cargoTransitVehicles.xml.gz");
			
			// now run the simulation and see if everything works
			
			Scenario scenarioForTesting = run();
			
			// uses train + arrives on time
			
			Assert.assertEquals("Scores have changed.", 796.94828287659, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			// uses train + arrives on time, slightly reduced score because this container is at a later position in the queue and needs more time
			Assert.assertEquals("Scores have changed.", 791.6427273210, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container10")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);			
			
			// road user traveling before road closure
			Assert.assertEquals("Scores have changed.", 549.2444444444445, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container9_road_before_closing_road")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// road user traveling during road closure, should be delayed (requires more time on the road than the other road users)
			Assert.assertEquals("Scores have changed.", -1473.2208333333333, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container10_road_affected_by_road_closure")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// road user traveling after road closure
			Assert.assertEquals("Scores have changed.", 549.2444444444445, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container11_road_after_road_closure")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
				
			// road user traveling at night should get a penalty
			Assert.assertEquals("Scores have changed.", -2450.7555555555555, scenarioForTesting.getPopulation().getPersons().get(Id.createPersonId("container12_night_penalty")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			
			
		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}

	/**
	 * runs the scenario and does the testing of scores
	 * @return 
	 * 
	 */
	private Scenario run() {
		// copy the config file and plans file from the test input directory into the test output directory and start a MATSim run from there...
		try {		
			FileUtils.copyFile(new File(utils.getClassInputDirectory() + "config.xml"), new File(utils.getOutputDirectory() + "config.xml"));
			FileUtils.copyFile(new File(utils.getClassInputDirectory() + "plans.xml"), new File(utils.getOutputDirectory() + "plans.xml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// now start a run and specifiy the input files (which should all be in the test output directory
		String [] args = {utils.getOutputDirectory() + "config.xml",
			  "--config:controler.outputDirectory", utils.getOutputDirectory() + "output"
			  } ;
		
		Config config = RunIntermodalFreightScenario.prepareConfig(args);
		config.network().setInputFile("cargoNetwork.xml.gz");
		config.network().setChangeEventsInputFile("cargoTerminalNetworkChangeEvents.xml.gz");
		config.transit().setTransitScheduleFile("cargoTransitSchedule.xml.gz");
		config.transit().setVehiclesFile("cargoTransitVehicles.xml.gz");
		config.plans().setInputFile("plans.xml");
		config.controler().setLastIteration(0);
		
		IntermodalFreightConfigGroup ifCfg = (IntermodalFreightConfigGroup) config.getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);
		ifCfg.setTerminalCapacityApproach(TerminalCapacityApproach.WithCapacityReduction);
		
		Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
		Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
		controler.run();

		return scenario;			
	}

}
