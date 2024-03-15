/**
 * 
 */
package ch.sbb.intermodalfreight.simulate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.ScoreEventScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;

/**
 * 
 * Replaces the default activity scoring by our freight-specific scoring approach.
 * 
 * @author ikaddoura
 *
 */
public class FreightScoringFunctionFactory implements ScoringFunctionFactory {
	private static final Logger log = LogManager.getLogger(FreightScoringFunctionFactory.class);

	private final Config config;
	private final Network network;
	private final ScoringParametersForPerson params;
	private final IntermodalFreightConfigGroup ifCfg;

	public FreightScoringFunctionFactory(Scenario scenario) {
		this.config = scenario.getConfig();
		this.params = new SubpopulationScoringParameters( scenario );
		this.network = scenario.getNetwork();
		
		this.ifCfg = (IntermodalFreightConfigGroup) scenario.getConfig().getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);

		log.info("FreightScoringFunctionFactory initialized.");
	}
	
	@Override
	public ScoringFunction createNewScoringFunction(Person person) {
		final ScoringParameters parameters = params.getScoringParameters( person );

		SumScoringFunction sumScoringFunction = new SumScoringFunction();
		sumScoringFunction.addScoringFunction(new FreightActivityScoring( person, parameters, ifCfg ));
		sumScoringFunction.addScoringFunction(new CharyparNagelLegScoring( parameters , this.network, config.transit().getTransitModes() ));
		sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring( parameters ));
		sumScoringFunction.addScoringFunction(new CharyparNagelAgentStuckScoring( parameters ));
		sumScoringFunction.addScoringFunction(new ScoreEventScoring());
		return sumScoringFunction;
	}

}
