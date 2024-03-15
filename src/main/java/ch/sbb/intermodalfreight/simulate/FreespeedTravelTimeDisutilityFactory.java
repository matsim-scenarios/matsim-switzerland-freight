package ch.sbb.intermodalfreight.simulate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

/**
 * A simple travel disutility factory which computes the travel disutility based on the freespeed and the distance.
 * 
 * @author ikaddoura
 *
 */
public class FreespeedTravelTimeDisutilityFactory implements TravelDisutilityFactory {
	private static final Logger log = LogManager.getLogger( FreespeedTravelTimeDisutilityFactory.class ) ;

	private final double scaledMarginalUtilityOfTraveling;
	private final double scaledMarginalUtilityOfPerforming;
	private final double scaledMarginalUtilityOfDistance;
	
	public FreespeedTravelTimeDisutilityFactory(String mode, PlanCalcScoreConfigGroup planCalcScore) {
		
		log.info("Using the freespeed travel time and distance based costs for mode " + mode);
		
		this.scaledMarginalUtilityOfTraveling = planCalcScore.getModes().get(mode).getMarginalUtilityOfTraveling() / 3600.0;
		this.scaledMarginalUtilityOfPerforming = planCalcScore.getPerforming_utils_hr() / 3600.0;
		this.scaledMarginalUtilityOfDistance = planCalcScore.getModes().get(mode).getMonetaryDistanceRate() * planCalcScore.getMarginalUtilityOfMoney();
	}

	@Override
	public TravelDisutility createTravelDisutility(TravelTime timeCalculator) {
		return new FreespeedTravelTimeAndDisutility(scaledMarginalUtilityOfTraveling, scaledMarginalUtilityOfPerforming, scaledMarginalUtilityOfDistance);
	}

}
