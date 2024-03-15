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
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.MatsimVehicleWriter;

import ch.sbb.intermodalfreight.simulate.RunIntermodalFreightScenario;

/**
 * 
 * This test requires a car only network and network change events file to start with.
 * The car only network and network change events are then extended by rail infrastructure and terminals.
 * The modified network file as well as the newly generated transit schedule and transit vehicles files are written into the test output directory.
 * Then, additional input files (plans, config) are copied from the test input directory to the test output directory and a simulation run is started from there.
 * After running the simulation the scores are tested. If the scores change there might be changes in the supply preparation.
 * 
 * In this test, the demand consist of three different container agent types which move along separate queues at the terminal and then board the same train.
 * 
 * @author ikaddoura
 *
 */
public class AddCargoSupplyMultipleContainerTypesTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	/**
	 * 
	 * single iteration
	 * same capacity for all container types
	 * all terminals accessible by all container types
	 * 
	 */
	@Test
	public final void testSameCapacityOneIteration() {

		try {
			
			System.out.println("Running test...");
			
			Map<String, Terminal> terminals = new HashMap<>();
	        terminals.put("t1", new Terminal("t1", "t1", "t1",
	        		new Coord(0., -8000.),
	        		Map.of(KVModes.CAR_KV_TEU, 40.,
	        				KVModes.CAR_KV_SATTELAUFLIEGER, 40.,
	        				KVModes.CAR_KV_WECHSELBEHAELTER, 40.),
	        		Map.of(KVModes.CAR_KV_TEU, new Tuple<Double, Double>(0., 24 * 3600.),
	        				KVModes.CAR_KV_SATTELAUFLIEGER, new Tuple<Double, Double>(0., 24 * 3600.),
	        				KVModes.CAR_KV_WECHSELBEHAELTER, new Tuple<Double, Double>(0., 24 * 3600.)))
	        		);
	        
	        terminals.put("t2", new Terminal("t2", "t2", "t2",
	        		new Coord(50000., 0.),
	        		Map.of(KVModes.CAR_KV_TEU, 40.,
	        				KVModes.CAR_KV_SATTELAUFLIEGER, 40.,
	        				KVModes.CAR_KV_WECHSELBEHAELTER, 40.),
	        		Map.of(KVModes.CAR_KV_TEU, new Tuple<Double, Double>(10 * 3600., 18 * 3600.),
	        				KVModes.CAR_KV_SATTELAUFLIEGER, new Tuple<Double, Double>(10 * 3600., 18 * 3600.),
	        				KVModes.CAR_KV_WECHSELBEHAELTER, new Tuple<Double, Double>(10 * 3600., 18 * 3600.)))
	        		);
	        
	        terminals.put("t3", new Terminal("t3", "t3", "t3",
	        		new Coord(120000., -8000.),
	        		Map.of(KVModes.CAR_KV_TEU, 40.,
	        				KVModes.CAR_KV_SATTELAUFLIEGER, 40.,
	        				KVModes.CAR_KV_WECHSELBEHAELTER, 40.),
	        		Map.of(KVModes.CAR_KV_TEU, new Tuple<Double, Double>(8 * 3600., 18 * 3600.),
	        				KVModes.CAR_KV_SATTELAUFLIEGER, new Tuple<Double, Double>(8 * 3600., 18 * 3600.),
	        				KVModes.CAR_KV_WECHSELBEHAELTER, new Tuple<Double, Double>(8 * 3600., 18 * 3600.)))
	        		);
	        
			// extend the network and network change events and create a transit schedule and vehicles file
			createInputFiles(terminals);
			
			// now run the simulation with the previously generated input files and see if everything works
			Scenario scenario = run();
			
			// ######################################################################

			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container_sattelauflieger1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container_sattelauflieger2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container_wechselbehaelter1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container_wechselbehaelter2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			Assert.assertEquals("Scores have changed.", -1168.3264397832252, scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
				
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container3")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			
			Assert.assertEquals("Scores have changed.", 831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container4")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// ######################################################################
			
			
			Assert.assertEquals("Scores have changed.", 623.7505982615172, scenario.getPopulation().getPersons().get(Id.createPersonId("container5")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 623.7505982615172, scenario.getPopulation().getPersons().get(Id.createPersonId("container6")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 623.7505982615172, scenario.getPopulation().getPersons().get(Id.createPersonId("container7")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
		
			// Container 8 and 9 use the car only mode.
			Assert.assertEquals("Scores have changed.", 579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container8")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container9")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
				
		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}

	private void createInputFiles(Map<String, Terminal> terminals) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(utils.getClassInputDirectory() + "carOnlyNetwork.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
					
        Network originalCarNetwork = ScenarioUtils.loadScenario(config).getNetwork();

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
			double t1Arrival = 7.5 * 3600.;
			double t1Departure = 8 * 3600.;
			
			String t2 = "t2";
			double t2Arrival = 10 * 3600.;
			double t2Departure = 10.5 * 3600.;
			
			String t3 = "t3";
			double t3Arrival = 12 * 3600.;
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
			double t1Arrival = 12.5 * 3600.;
			double t1Departure = 13 * 3600.;
			
			String t2 = "t2";
			double t2Arrival = 15 * 3600.;
			double t2Departure = 15.5 * 3600.;
			
			String t3 = "t1";
			double t3Arrival = 17 * 3600.;
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
					modes.add(KVModes.CAR_KV_TEU);
					modes.add(KVModes.CAR_KV_SATTELAUFLIEGER);
					modes.add(KVModes.CAR_KV_WECHSELBEHAELTER);
				}
				link.setAllowedModes(modes);
			}
		}
		
		// merge with existing network change events
		List<NetworkChangeEvent> changeEvents = new ArrayList<>() ;
		NetworkChangeEventsParser parser = new NetworkChangeEventsParser(scenario.getNetwork(), changeEvents);
		parser.readFile(utils.getClassInputDirectory() + "networkChangeEvents.xml");
		changeEvents.addAll(supply.getNetworkChangeEvents());
				
		new NetworkChangeEventsWriter().write(outputDir + "cargoTerminalNetworkChangeEvents.xml.gz", changeEvents);
		
		new NetworkWriter(scenario.getNetwork()).write(outputDir + "cargoNetwork.xml.gz");
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputDir + "cargoTransitSchedule.xml.gz");
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputDir + "cargoTransitVehicles.xml.gz");
	}

	/**
	 * runs the scenario and returns the scenario
	 * 
	 */
	private Scenario run() {
		
		// copy the config file and plans file from the test input directory into the test output directory and start a MATSim run from there...
		// these files are not modified in the preprocessing
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
		
		Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
		Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
		controler.run();
		
		return scenario;
	}
}
