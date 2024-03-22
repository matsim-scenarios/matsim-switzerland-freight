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

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.ScenarioConfigGroup;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ActivityUtilityParameters.ZeroUtilityComputation;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author ikaddoura
 *
 */
public class FreightActivityScoringTest {
	
	@Test
	public final void testScoresFixAmountsOnly() {

		PlanCalcScoreConfigGroup scoringConfigGroup = new PlanCalcScoreConfigGroup();
		Map<String, ActivityUtilityParameters> activityParams = new HashMap<>();
		
		{
			ActivityUtilityParameters.Builder factory = new ActivityUtilityParameters.Builder();
			factory.setType("freight_origin");
			factory.setPriority(1.0);
			factory.setTypicalDuration_s(8.0 * 3600);
			factory.setScoreAtAll(true);
			ZeroUtilityComputation zeroUtilityComputation = new ActivityUtilityParameters.SameAbsoluteScore();
			factory.setZeroUtilityComputation(zeroUtilityComputation);
			ActivityUtilityParameters actUtilParams = factory.build();
			activityParams.put("freight_origin", actUtilParams);
		}
		
		{
			ActivityUtilityParameters.Builder factory = new ActivityUtilityParameters.Builder();
			factory.setType("freight_destination");
			factory.setPriority(1.0);
			factory.setTypicalDuration_s(8.0 * 3600);
			factory.setScoreAtAll(true);
			ZeroUtilityComputation zeroUtilityComputation = new ActivityUtilityParameters.SameAbsoluteScore();
			factory.setZeroUtilityComputation(zeroUtilityComputation);
			ActivityUtilityParameters actUtilParams = factory.build();
			activityParams.put("freight_destination", actUtilParams);
		}
		
		ScoringParameters scoringParameters = new ScoringParameters.Builder(scoringConfigGroup, scoringConfigGroup.getScoringParameters(null), activityParams, new ScenarioConfigGroup()).build();
		
		IntermodalFreightConfigGroup intermodalFreightConfigGroup = new IntermodalFreightConfigGroup();
		intermodalFreightConfigGroup.setTolerance(0.);
		intermodalFreightConfigGroup.setAdditionalToleranceForImportExport(0.);
		intermodalFreightConfigGroup.setArrivingRightOnTimeUtility(0.1);
		intermodalFreightConfigGroup.setArrivingLateUtility(-100.);
		intermodalFreightConfigGroup.setDepartingEarlyUtiliy(-1000);
		
		{
			
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart early and arrive too late
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20. * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -1100., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart early and arrive right on time
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -999.9, scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart right on time and arrive too late
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -100., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart right on time and arrive right on time
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0.1, scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			// no person attributes
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));

			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20. * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			// no person attributes
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));

			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
	}
	
	@Test
	public final void testScoresWithTimeDependentPenalties() {

		PlanCalcScoreConfigGroup scoringConfigGroup = new PlanCalcScoreConfigGroup();
		Map<String, ActivityUtilityParameters> activityParams = new HashMap<>();
		
		{
			ActivityUtilityParameters.Builder factory = new ActivityUtilityParameters.Builder();
			factory.setType("freight_origin");
			factory.setPriority(1.0);
			factory.setTypicalDuration_s(8.0 * 3600);
			factory.setScoreAtAll(true);
			ZeroUtilityComputation zeroUtilityComputation = new ActivityUtilityParameters.SameAbsoluteScore();
			factory.setZeroUtilityComputation(zeroUtilityComputation);
			ActivityUtilityParameters actUtilParams = factory.build();
			activityParams.put("freight_origin", actUtilParams);
		}
		
		{
			ActivityUtilityParameters.Builder factory = new ActivityUtilityParameters.Builder();
			factory.setType("freight_destination");
			factory.setPriority(1.0);
			factory.setTypicalDuration_s(8.0 * 3600);
			factory.setScoreAtAll(true);
			ZeroUtilityComputation zeroUtilityComputation = new ActivityUtilityParameters.SameAbsoluteScore();
			factory.setZeroUtilityComputation(zeroUtilityComputation);
			ActivityUtilityParameters actUtilParams = factory.build();
			activityParams.put("freight_destination", actUtilParams);
		}
		
		ScoringParameters scoringParameters = new ScoringParameters.Builder(scoringConfigGroup, scoringConfigGroup.getScoringParameters(null), activityParams, new ScenarioConfigGroup()).build();
		
		IntermodalFreightConfigGroup intermodalFreightConfigGroup = new IntermodalFreightConfigGroup();
		intermodalFreightConfigGroup.setTolerance(0.);
		intermodalFreightConfigGroup.setAdditionalToleranceForImportExport(0.);
		intermodalFreightConfigGroup.setArrivingRightOnTimeUtility(0.1);
		intermodalFreightConfigGroup.setArrivingLateUtility(-100.);
		intermodalFreightConfigGroup.setDepartingEarlyUtiliy(-1000);
		intermodalFreightConfigGroup.setDepartingEarlyUtilityPerHour(-10.);
		intermodalFreightConfigGroup.setArrivingLateUtilityPerHour(-5.);
		
		{
			
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart early and arrive too late
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20. * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -1120., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart early and arrive right on time
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -1009.9, scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart right on time and arrive too late
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", -110., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, 9 * 3600.);
			person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, 18 * 3600.);
			
			// depart right on time and arrive right on time
			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0.1, scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			// no person attributes
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));

			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(8 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(20. * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
		
		{
			// no person attributes
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));

			Activity firstActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_origin", new Coord(0., 0.));
			firstActivity.setEndTime(9.1 * 3600.);
			Activity lastActivity = PopulationUtils.getFactory().createActivityFromCoord("freight_destination", new Coord(0., 0.));
			lastActivity.setStartTime(17.9 * 3600.);
			
			FreightActivityScoring scoring = new FreightActivityScoring(person, scoringParameters, intermodalFreightConfigGroup);
			scoring.handleFirstActivity(firstActivity);
			scoring.handleLastActivity(lastActivity);
			
			Assert.assertEquals("Wrong score.", 0., scoring.getScore(), MatsimTestUtils.EPSILON);
		}
	}	
	
}
