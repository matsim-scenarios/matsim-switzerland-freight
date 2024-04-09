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
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;

import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.CarRoutingApproach;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;

/**
 * 
 * Runs the intermodal freight scenario. Configures the config, scenario and controler.
 * 
 * @author ikaddoura
 *
 */
public class RunIntermodalFreightScenario {
	private static final Logger log = LogManager.getLogger(RunIntermodalFreightScenario.class );

	public static void main(String[] args) {
		
		if ( args==null || args.length==0 || args[0]==null ){
			args = new String[] { "C:/devsbb/tmp/ibg/v019/config_test.xml" };
		}
		
		log.info("Arguments:");
		for (String arg : args) {
			log.info( arg );
		}
		log.info("---");
				
		Config config = prepareConfig(args);
		Scenario scenario = prepareScenario(config);
		Controler controler = prepareControler(scenario);
		
		controler.run();
	}
	
	/**
	 * Prepares the config for the intermodal freight simulation.
	 * 
	 * @param args
	 * @return
	 */
	public static Config prepareConfig(String[] args) {
		
		Config config = ConfigUtils.loadConfig(args, new IntermodalFreightConfigGroup());
		// config.controler().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );		
		
		if (config.controller().getFirstIteration() != 0) {
			throw new RuntimeException("The simulation is expected to start with iteration 0, otherwise the initialization of desired arrival times does not work. Aborting...");
		}
		
		if (config.qsim().getEndTime().seconds() > (24 * 3600. * 3)) {
			throw new RuntimeException("In the current version, the supply only accounts for one day plus the next day (= maximum 48h). Aborting...");
		}
		
		if (config.qsim().isUsingFastCapacityUpdate() == true) {
			throw new RuntimeException("Signals don't work with fast capacity update. Needs to be set to 'false'. Aborting...");
		}
		
		if (config.qsim().getEndTime().seconds() != config.travelTimeCalculator().getMaxTime()) {
			log.warn("Setting travel time max time to the qsim end time: " + Time.writeTime(config.qsim().getEndTime().seconds(), Time.TIMEFORMAT_HHMMSS));
			config.travelTimeCalculator().setMaxTime((int) config.qsim().getEndTime().seconds());
		}
		 
		return config;
	}

	/**
	 * Prepares the scenario for the intermodal freight simulation.
	 * 
	 * @param config
	 * @return
	 */
	public static Scenario prepareScenario(Config config) {
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}

	/**
	 * Prepares the controler for the intermodal freight simulation. Adds several customized modules which replace MATSim's default behavior.
	 * 
	 * @param scenario
	 * @return
	 */
	public static Controler prepareControler( Scenario scenario ) {
		
		Controler controler = new Controler(scenario) ;
		
		IntermodalFreightConfigGroup ifCfg = (IntermodalFreightConfigGroup) scenario.getConfig().getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);
		
		// To use the deterministic pt simulation (Part 1 of 2):
        controler.addOverridingModule(new SBBTransitModule());
        
        // To use the deterministic pt simulation (Part 2 of 2):
        controler.configureQSimComponents(components -> {
        	new SBBTransitEngineQSimModule().configure(components);
        });
        
        // replace activity scoring
        controler.setScoringFunctionFactory(new FreightScoringFunctionFactory(scenario));
        
		TerminalQueueDeactivationEventHandler queueHandler = new TerminalQueueDeactivationEventHandler(scenario);
		
        // write departure and arrival times of initial iteration into person attributes
        controler.addOverridingModule(new AbstractModule() {	
			@Override
			public void install() {
				
				// use our own analysis main mode identifier
				this.bind(AnalysisMainModeIdentifier.class).to(IntermodalFreightAnalysisMainModeIdentifier.class);
				
				// store initial arrival times and write into person plan (if not provided in the attributes)
				FreightStoreInitialTimes initialTimes = new FreightStoreInitialTimes(scenario);
				this.addEventHandlerBinding().toInstance(initialTimes);
				this.addControlerListenerBinding().toInstance(initialTimes);
				
				// a penalty when using the car mode at night
				FreightPenaltyHandler nightPenaltyHandler = new FreightPenaltyHandler(scenario);
				this.addEventHandlerBinding().toInstance(nightPenaltyHandler);
				
				// handle the queues...
				this.bind(TerminalQueueDeactivationEventHandler.class).toInstance(queueHandler);
				this.addEventHandlerBinding().toInstance(queueHandler);
				this.addMobsimListenerBinding().toInstance(queueHandler);		
				
				if (ifCfg.getCarRoutingApproach() == CarRoutingApproach.Default) {
					// nothing to do
					
				} else if (ifCfg.getCarRoutingApproach() == CarRoutingApproach.Freespeed) {
					// use the freespeed for routing, because this is how trucks tend to do their routing; congestion effects will be ignored which is fine
					// because we don't want to have trucks on some mountain roads
					
					for (String mode : controler.getConfig().routing().getNetworkModes()) {
						addTravelDisutilityFactoryBinding(mode).toInstance(new FreespeedTravelTimeDisutilityFactory(mode, controler.getConfig().scoring()));
					}
				} else {
					throw new RuntimeException("Unknown car routing approach. Aborting...");
				}
				
			}
		});
        
        
        // required for the queue handling
        controler.addOverridingQSimModule(new SignalsQSimModule());
        
        // required for the terminal-specific loading/unloading times per container
        controler.addOverridingQSimModule(new ContainerTransitEngineModule());
        
		return controler;
	}

	
}
