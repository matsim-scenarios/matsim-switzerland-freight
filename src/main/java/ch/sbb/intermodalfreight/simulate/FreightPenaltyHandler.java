package ch.sbb.intermodalfreight.simulate;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;

import com.google.inject.Inject;

/**
 * Throws person score events in the following situations:
 * 
 * - when an agent departs (a penalty, e.g. -1000)
 * - when an agent arrives (a compensation of the previous penalty, e.g. +1000)
 * This works as an (additional) agent stuck penalty if the agent does not arrive at the trip destination.
 * Depending on the scoring parameters, the default stuck penalty may be too low.
 * 
 * - when an agent which uses the car mode is traveling at night (the night is hard-coded from 10 p.m. till 5 a.m.)
 * 
 * @author ikaddoura
 *
 */
public class FreightPenaltyHandler implements PersonDepartureEventHandler, PersonArrivalEventHandler{

	@Inject
	private EventsManager eventsManager;
	
	private final IntermodalFreightConfigGroup ifCfg;
	
	private Map<Id<Person>, Double> person2departureTime = new HashMap<>();
	

	@Override
	public void reset(int iteration) {
		this.person2departureTime.clear();
	}

	public FreightPenaltyHandler(Scenario scenario) {
		this.ifCfg = (IntermodalFreightConfigGroup) scenario.getConfig().getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		
		eventsManager.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), ifCfg.getArrivalUtility(), "arrival_utility"));
		
		if (this.person2departureTime.get(event.getPersonId()) != null) {
			double departureTime = this.person2departureTime.get(event.getPersonId());
			double arrivalTime = event.getTime();
			
			if (legWasDuringNight(departureTime, arrivalTime)) {
				eventsManager.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), ifCfg.getCarPenaltyNight(), "car_night_22-5_penalty"));
			}
			
		}
	}

	public static boolean legWasDuringNight(double departureTime, double arrivalTime) {
		int departureDay = (int) (departureTime / (24. * 3600));
		int arrivalDay = (int) (arrivalTime / (24. * 3600.));
				
		if (departureDay != arrivalDay) {
			return true;
			
		} else {
			// departure and arrival on same day
			// now check if the departure or arrival time is in the early morning or late evening
			
			double depTimeOfDay = departureTime - departureDay * 24 * 3600.;
			double arrivalTimeOfDay = arrivalTime - arrivalDay * 24 * 3600.;
			
			if (depTimeOfDay < 5 * 3600. || depTimeOfDay > 22 * 3600. ||
					arrivalTimeOfDay < 5 * 3600. || arrivalTimeOfDay > 22 * 3600.) {
				return true;
				
			} else {
				return false;
			}
			
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		
		eventsManager.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), ifCfg.getDepartureUtility(), "departure_utility"));
		
		if (event.getLegMode().equals(TransportMode.car)) {
			this.person2departureTime.put(event.getPersonId(), event.getTime());
		}
	}

}
