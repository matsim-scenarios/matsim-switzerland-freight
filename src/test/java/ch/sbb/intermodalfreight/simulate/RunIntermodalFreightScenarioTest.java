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
package ch.sbb.intermodalfreight.simulate;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author ikaddoura
 *
 */
public class RunIntermodalFreightScenarioTest {

	@RegisterExtension public MatsimTestUtils utils = new MatsimTestUtils() ;

	/**
	 * Base setup:
	 * - corridor with 3 terminals
	 * - several KV containers and road only containers traveling from one side to the other side
	 * - one iteration
	 *
	 */
	@Test
	public final void testCorridorOneIteration() {

		try {
			String [] args = {utils.getClassInputDirectory() + "config.xml",
				  "--config:controler.outputDirectory", utils.getOutputDirectory(),
				  "--config:controler.lastIteration", "0"
			} ;
			Config config = RunIntermodalFreightScenario.prepareConfig(args);

			config.network().setInputFile("cargoNetwork.xml.gz");
			config.network().setChangeEventsInputFile("cargoTerminalNetworkChangeEvents.xml.gz");
			config.network().setTimeVariantNetwork(true);

			config.transit().setTransitScheduleFile("cargoTransitSchedule.xml.gz");
			config.transit().setVehiclesFile("cargoTransitVehicles.xml.gz");
			config.plans().setInputFile("plans.xml");

			Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
			Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
			controler.run();

			// car score
			Assertions.assertEquals("car", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan()));
			Assertions.assertEquals(579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, not delayed
			Assertions.assertEquals("carKV_TEU---walk---pt---walk---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
//			Assertions.assertEquals("carKV_TEU---pt---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
			Assertions.assertEquals(831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, delayed
			Assertions.assertEquals("carKV_TEU---walk---pt---walk---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan()));
//			Assertions.assertEquals("Modes have changed.", "carKV_TEU---pt---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan()));
			Assertions.assertEquals(-1168.3264397832252, scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assertions.fail();
		}
	}

	/**
	 * Now, we increase the departure/arrival tolerance compared to testCorridorOneIteration.
	 *
	 * Container1 still uses KV and arrives delayed compared to the road only connection, but within the tolerance.
	 * Therefore, the score increases.
	 *
	 */
	@Test
	public final void testCorridorOneIterationIncreasedTolerance() {

		try {
			String [] args = {utils.getClassInputDirectory() + "config.xml",
				  "--config:controler.outputDirectory", utils.getOutputDirectory(),
				  "--config:controler.lastIteration", "0"
			} ;
			Config config = RunIntermodalFreightScenario.prepareConfig(args);

			IntermodalFreightConfigGroup ifCfg = (IntermodalFreightConfigGroup) config.getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);
			ifCfg.setTolerance(5 * 3600.);

			config.network().setInputFile("cargoNetwork.xml.gz");
			config.network().setChangeEventsInputFile("cargoTerminalNetworkChangeEvents.xml.gz");
			config.network().setTimeVariantNetwork(true);

			config.transit().setTransitScheduleFile("cargoTransitSchedule.xml.gz");
			config.transit().setVehiclesFile("cargoTransitVehicles.xml.gz");
			config.plans().setInputFile("plans.xml");

			Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
			Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
			controler.run();

			// car score
			Assertions.assertEquals( "car", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan()));
			Assertions.assertEquals(579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, not delayed
			Assertions.assertEquals("carKV_TEU---walk---pt---walk---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
//			Assertions.assertEquals("carKV_TEU---pt---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
			Assertions.assertEquals(831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, no longer delayed
			Assertions.assertEquals( "carKV_TEU---walk---pt---walk---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan()));
//			Assertions.assertEquals( "carKV_TEU---pt---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan()));
			Assertions.assertEquals(831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assertions.fail();
		}
	}

	/**
	 * Now, we increase the number of iterations compared to testCorridorOneIteration.
     * The delayed container should switch to car.
	 *
	 */
	@Test
	public final void testCorridorMultipleIterations() {

		try {
			String [] args = {utils.getClassInputDirectory() + "config.xml",
				  "--config:controler.outputDirectory", utils.getOutputDirectory(),
				  "--config:controler.lastIteration", "10"
			} ;
			Config config = RunIntermodalFreightScenario.prepareConfig(args);

			config.network().setInputFile("cargoNetwork.xml.gz");
			config.network().setChangeEventsInputFile("cargoTerminalNetworkChangeEvents.xml.gz");
			config.network().setTimeVariantNetwork(true);

			config.transit().setTransitScheduleFile("cargoTransitSchedule.xml.gz");
			config.transit().setVehiclesFile("cargoTransitVehicles.xml.gz");
			config.plans().setInputFile("plans.xml");

			Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
			Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
			controler.run();

			// car score
			Assertions.assertEquals("car", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan()));
			Assertions.assertEquals(579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container0")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, not delayed
			Assertions.assertEquals("carKV_TEU---walk---pt---walk---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
//			Assertions.assertEquals("carKV_TEU---pt---carKV_TEU", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan()));
			Assertions.assertEquals(831.6735602167748, scenario.getPopulation().getPersons().get(Id.createPersonId("container1")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

			// rail container, delayed which eventually switches to car
			Assertions.assertEquals("car", getLegModes(scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan()));
			Assertions.assertEquals(579.65, scenario.getPopulation().getPersons().get(Id.createPersonId("container2")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			LogManager.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assertions.fail();
		}
	}

	private String getLegModes(Plan selectedPlan) {
		String modes = "";
		for (PlanElement pE : selectedPlan.getPlanElements()) {
			if (pE instanceof Leg) {
				Leg leg = (Leg) pE;
				modes = modes + leg.getMode() + "---";
			}
		}

		modes = modes.substring(0, modes.length() - 3);

		return modes;
	}

}
