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
 *  - a penalty (fixed amount + delay-dependent) for arriving too late
 *  - a penalty (fixed amount + delay-dependent) for departing too early
 *  - a reward (fixed amount) for arriving right on time or early
 * 
 * @author ikaddoura
 *
 */
public class FreightActivityScoring implements org.matsim.core.scoring.SumScoringFunction.ActivityScoring {

	public final static String INITIAL_ARRIVAL_TIME = "initialArrival";
	public final static String INITIAL_DEPARTURE_TIME = "initialDeparture";
	
	// some hard-coded person attributes which trigger wider time tolerances for import/export
	private final static String ATTRIBUTE_TRANSPORT_TYPE_KEY = "transport_type";
	private final static String ATTRIBUTE_TRANSPORT_TYPE_IMPORT = "Import";
	private final static String ATTRIBUTE_TRANSPORT_TYPE_EXPORT = "Export";
	
	// some hard-coded person attributes which trigger wider time tolerances for empty containers
	private final static String ATTRIBUTE_COMMODITY_GROUP_KEY = "commodity_group";
	private final static int ATTRIBUTE_COMMODITY_GROUP_EMPTY_CONTAINER = -1;
		
	private final ScoringParameters params;
	private final IntermodalFreightConfigGroup ifCfg;
	private final Double desiredArrival;
	private final Double desiredDeparture;
	private final double tolerance;
	
	private double score = 0.;

	public FreightActivityScoring(Person person, ScoringParameters parameters, IntermodalFreightConfigGroup ifCfg) {
		this.params = parameters;
		this.ifCfg = ifCfg;	
		this.desiredArrival = (Double) person.getAttributes().getAttribute(INITIAL_ARRIVAL_TIME);
		this.desiredDeparture = (Double) person.getAttributes().getAttribute(INITIAL_DEPARTURE_TIME);
		this.tolerance = getTolerance(person);
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
		this.score += calcDepartureScore(act.getEndTime().seconds(), act);
	}
	
	@Override
	public void handleLastActivity(Activity act) {
		this.score += calcArrivalScore(act.getStartTime().seconds(), act);
	}

	@Override
	public void handleActivity(Activity act) {
		this.score += calcActScore(act.getStartTime().seconds(), act.getEndTime().seconds(), act);
	}

	private double calcActScore(double arrival, double departure, Activity act) {
		
		double scoreTmp = 0.;
		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
		if (actParams.isScoreAtAll()) {
			throw new RuntimeException("Freight plans should only consist of a freight origin and destination activity. Aborting... "
					+ " activity = "  + act.getType() 
					+ " arrival = " + arrival + " departure = " + departure);
		}
		
		return scoreTmp;
	}
	
	private double calcArrivalScore(double arrival, Activity act) {
		
		double scoreTmp = 0.;
		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
				
		if (actParams.isScoreAtAll()) {
			
			// destination activity
			if (desiredArrival != null) {
				double lateArrival = arrival - desiredArrival;
				if (lateArrival > tolerance) {
					// arriving later --> penalty
					// fix amount
					scoreTmp += ifCfg.getArrivingLateUtility();
					// delay-dependent amount
					double timeAfterToleranceSec = lateArrival - tolerance;
					scoreTmp += timeAfterToleranceSec * ifCfg.getArrivingLateUtilityPerHour() / 3600.;
					
				} else {
					// arriving earlier or within tolerance --> ok
					scoreTmp += ifCfg.getArrivingRightOnTimeUtility();
				}
			}
			
		}
		
		return scoreTmp;
	}
	
	private double calcDepartureScore(double departure, Activity act) {
		
		double scoreTmp = 0.;
		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
				
		if (actParams.isScoreAtAll()) {
			
			// origin activity
			if (desiredDeparture != null) {
				double earlierDeparture = desiredDeparture - departure;		
				if (earlierDeparture > tolerance) {
					// departing earlier --> penalty
					// fix amount
					scoreTmp += ifCfg.getDepartingEarlyUtiliy();
					// delay-specific amount
					double timeAfterToleranceSec = earlierDeparture - tolerance;
					scoreTmp += timeAfterToleranceSec * ifCfg.getDepartingEarlyUtilityPerHour() / 3600.;
				} else {
					// departing later or within tolerance --> ok
				}
			}
		}
		
		return scoreTmp;
	}

	private double getTolerance(Person person) {
		
		// base value
		double tolerance = ifCfg.getTolerance();
		
		// increase for import/export
		{
			Object transportTypeObject = person.getAttributes().getAttribute(ATTRIBUTE_TRANSPORT_TYPE_KEY);
			if (transportTypeObject != null) {
				String transportType = (String) transportTypeObject;
				if (transportType.equalsIgnoreCase(ATTRIBUTE_TRANSPORT_TYPE_EXPORT) || transportType.equalsIgnoreCase(ATTRIBUTE_TRANSPORT_TYPE_IMPORT)) {
					// increase tolerance
					tolerance = tolerance + ifCfg.getAdditionalToleranceForImportExport();
				}
			}
		}
		
		
		// increase for empty containers
		{
			Object commodityGroupObject = person.getAttributes().getAttribute(ATTRIBUTE_COMMODITY_GROUP_KEY);
			if (commodityGroupObject != null) {
				int commodityGroup = (int) commodityGroupObject;
				if (commodityGroup == ATTRIBUTE_COMMODITY_GROUP_EMPTY_CONTAINER) {
					// increase tolerance
					tolerance = tolerance + ifCfg.getAdditionalToleranceForEmptyContainers();
				}
			}
		}
		
		return tolerance;
	}

}
