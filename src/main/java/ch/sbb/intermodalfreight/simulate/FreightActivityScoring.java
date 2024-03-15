/**
 * 
 */
package ch.sbb.intermodalfreight.simulate;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;


/**
 * 
 * A simplistic activity scoring approach which consists of
 *  - a penalty (fixed amount) for arriving too late
 *  - a penalty (fixed amount) for departing too early
 *  - a reward (fixed amount) for arriving right on time or early
 * 
 * @author ikaddoura
 *
 */
public class FreightActivityScoring implements org.matsim.core.scoring.SumScoringFunction.ActivityScoring {

	public final static String INITIAL_ARRIVAL_TIME = "initialArrival";
	public final static String INITIAL_DEPARTURE_TIME = "initialDeparture";
	private final static String ATTRIBUTE_IMPORT = "Import";
	private final static String ATTRIBUTE_EXPORT = "Export";
	
	private final Person person;
	private final ScoringParameters params;
	private final IntermodalFreightConfigGroup ifCfg;
	
	private double score = 0.;

	public FreightActivityScoring(Person person, ScoringParameters parameters, IntermodalFreightConfigGroup ifCfg) {
		this.person = person;
		this.params = parameters;
		this.ifCfg = ifCfg;
	}

	@Override
	public void finish() {
		// nothing to do here!
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public void handleFirstActivity(Activity act) {
		this.score += calcActScore(0., act.getEndTime().seconds(), act);
	}
	
	@Override
	public void handleLastActivity(Activity act) {
		this.score += calcActScore(act.getStartTime().seconds(), 24 * 3600., act);
	}

	@Override
	public void handleActivity(Activity act) {
		this.score += calcActScore(act.getStartTime().seconds(), act.getEndTime().seconds(), act);
	}

	private double calcActScore(double arrival, double departure, Activity act) {
		
		double scoreTmp = 0.;
		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
				
		if (actParams.isScoreAtAll()) {
			
			Double desiredArrival = (Double) person.getAttributes().getAttribute(INITIAL_ARRIVAL_TIME);
			Double desiredDeparture = (Double) person.getAttributes().getAttribute(INITIAL_DEPARTURE_TIME);
			
			if (arrival == 0.) {
				// origin activity
				if (desiredDeparture != null) {
					double earlierDeparture = desiredDeparture - departure;		
					if (earlierDeparture > (getTolerance(person))) {
						// departing earlier --> penalty
						scoreTmp += ifCfg.getDepartingEarlyUtiliy();
					} else {
						// departing later or within tolerance --> ok
					}
				}		
			} else if (departure == 24 * 3600.) {
				// destination activity
				if (desiredArrival != null) {
					double lateArrival = arrival - desiredArrival;
					if (lateArrival > getTolerance(person)) {
						// arriving later --> penalty
						scoreTmp += ifCfg.getArrivingLateUtility();
					} else {
						// arriving earlier or within tolerance --> ok
						scoreTmp += ifCfg.getArrivingRightOnTimeUtility();
					}
				}	
			} else {
				throw new RuntimeException("Freight plans should only consist of a freight origin and destination activity. Aborting... "
						+ act.getType() 
						+ " arrival = " + arrival + " departure = " + departure);
			}
		}
		
		return scoreTmp;
	}

	private double getTolerance(Person person) {
		double tolerance = ifCfg.getTolerance();
		
		Object transportTypeObject = person.getAttributes().getAttribute("transport_type");
		
		if (transportTypeObject != null) {
			String transportType = (String) transportTypeObject;
			if (transportType.equalsIgnoreCase(ATTRIBUTE_EXPORT) || transportType.equalsIgnoreCase(ATTRIBUTE_IMPORT)) {
				// increase tolerance
				tolerance = tolerance + ifCfg.getAdditionalToleranceForImportExport();
			}
		}
		
		return tolerance;
	}

}
