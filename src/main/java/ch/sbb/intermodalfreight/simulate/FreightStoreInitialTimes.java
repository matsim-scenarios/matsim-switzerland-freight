/**
 * 
 */
package ch.sbb.intermodalfreight.simulate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;

import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.DesiredArrivalTimeApproach;

/**
 * 
 * Assumes that the departure and arrival times in the initial iteration are the desired arrival and departure times and writes them into the person attributes.
 * If the person attributes already contain the desired arrival and departure time attributes, the values will not be updated and this code does essentially nothing.
 * 
 * @author ikaddoura
 *
 */
public class FreightStoreInitialTimes implements IterationEndsListener, ActivityEndEventHandler, ActivityStartEventHandler {

	private static final Logger log = LogManager.getLogger(FreightStoreInitialTimes.class);

	private final Scenario scenario;

	private int iterationCounter = 0;

	public FreightStoreInitialTimes(Scenario scenario) {
		this.scenario = scenario;
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		
		if (iterationCounter == 0) {
			
			if (!event.getActType().contains("interaction")) {
				
				if (!event.getActType().contains("freight")) throw new RuntimeException("Expecting a freight activity. Aborting..." + event.toString());
				
				Person person = this.scenario.getPopulation().getPersons().get(event.getPersonId());	
				ScoringParameters params = new SubpopulationScoringParameters( scenario ).getScoringParameters(person);
				ActivityUtilityParameters actParams = params.utilParams.get(event.getActType());

				if (actParams.isScoreAtAll()) {
					if (person.getAttributes().getAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME) == null) {
						person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME, event.getTime());		
					} else {
						log.warn("Using the initial departure times from the input plans file and not writing them again into the person attributes.");
					}
				}
			}
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		
		// only do the following in the initial iteration because we assume that the initial iteration defines the desired departure and arrival times
		if (iterationCounter == 0) {
			
			if (!event.getActType().contains("interaction")) {
				
				if (!event.getActType().contains("freight")) throw new RuntimeException("Expecting a freight activity. Aborting..." + event.toString());
				
				Person person = this.scenario.getPopulation().getPersons().get(event.getPersonId());	
				ScoringParameters params = new SubpopulationScoringParameters( scenario ).getScoringParameters(person);
				ActivityUtilityParameters actParams = params.utilParams.get(event.getActType());
				IntermodalFreightConfigGroup imfcg = (IntermodalFreightConfigGroup) scenario.getConfig().getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);

				if (actParams.isScoreAtAll()) {
					if (person.getAttributes().getAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME) == null) {
						double arrivalTime = event.getTime();
						
						if (imfcg.getDesiredArrivalTimeApproach() == DesiredArrivalTimeApproach.UseArrivalTimeFromInitialIteration) {
							// do not change the arrival time
						} else if (imfcg.getDesiredArrivalTimeApproach() == DesiredArrivalTimeApproach.UseArrivalTimeFromInitialIterationAndMoveDayArrivalsToMorning) {
							
							int desiredArrivalDay = (int) (arrivalTime / (24. * 3600));
							
							double initialDepartureTime = (double) person.getAttributes().getAttribute(FreightActivityScoring.INITIAL_DEPARTURE_TIME);
							int desiredDepartureDay = (int) (initialDepartureTime / (24. * 3600));
							
							if (desiredArrivalDay == desiredDepartureDay) {
								// desired departure and arrival on same day. no need to shift the desired arrival time.
							} else {
								
								// shift the desired arrival time to the morning
								arrivalTime = 6. * 3600 + 24. * 3600 * desiredArrivalDay;
							}
														
						} else {
							throw new RuntimeException("Unknown desired arrival time appproach: " + imfcg.getDesiredArrivalTimeApproach());
						}
						
						person.getAttributes().putAttribute(FreightActivityScoring.INITIAL_ARRIVAL_TIME, arrivalTime);

					} else {
						log.warn("Using the initial arrival times from the input plans file and not writing them again into the person attributes.");
					}
				}
			}
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		iterationCounter++;
	}
}


