/**
 * 
 */
package ch.sbb.intermodalfreight;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import ch.sbb.intermodalfreight.utils.config.Project;

/**
 * 
 * @author ikaddoura
 *
 */
public class RunExampleIT {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;
	
	/**
	 * Tests the run example.
	 */
	@Test
	public final void testRunExample() {

		try {
			
			// Read the project settings from src/test/resources/project.properties
			Project project = new Project();
			
			// Copy the required files to the test output directory and run the example from there...
			
			String inputDirectory0 = "scenarios/01_original_data/";
			FileUtils.copyFile(new File(inputDirectory0 + "cargo_train_schedule.xlsx"), new File(project.getDirectory().getOriginalDataPath() + "/cargo_train_schedule.xlsx"));
			FileUtils.copyFile(new File(inputDirectory0 + "network.xml.gz"), new File(project.getDirectory().getOriginalDataPath() + "/network.xml.gz"));
			FileUtils.copyFile(new File(inputDirectory0 + "terminal_distances.csv"), new File(project.getDirectory().getOriginalDataPath() + "/terminal_distances.csv"));
			FileUtils.copyFile(new File(inputDirectory0 + "terminals.csv"), new File(project.getDirectory().getOriginalDataPath() + "/terminals.csv"));
			
			FileUtils.copyFile(new File("scenarios/03_matsim_input/v001/plans.xml.gz"), new File(project.getDirectory().getMatsimInputPath() + "/plans.xml.gz"));
			FileUtils.copyFile(new File("scenarios/04_matsim_config/config_r001.xml"), new File(project.getDirectory().getMatsimConfigFilePath().toString()));

			RunExample example = new RunExample();
			example.run(project);
			
		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();			
		}	
	}
	
	/**
	 * Tests a setup similar to the run example but with just 4 agents and only 1 iteration.
	 */
	@Test
	public final void testRunExampleWith4Agents() {

		try {
			
			// Read the project settings from src/test/resources/project.properties
			Project project = new Project();
		 			
			// Copy the required files to the test output directory
			String inputDirectory0 = "scenarios/01_original_data/";
			FileUtils.copyFile(new File(inputDirectory0 + "cargo_train_schedule.xlsx"), new File(project.getDirectory().getOriginalDataPath() + "/cargo_train_schedule.xlsx"));
			FileUtils.copyFile(new File(inputDirectory0 + "network.xml.gz"), new File(project.getDirectory().getOriginalDataPath() + "/network.xml.gz"));
			FileUtils.copyFile(new File(inputDirectory0 + "terminal_distances.csv"), new File(project.getDirectory().getOriginalDataPath() + "/terminal_distances.csv"));
			FileUtils.copyFile(new File(inputDirectory0 + "terminals.csv"), new File(project.getDirectory().getOriginalDataPath() + "/terminals.csv"));
			
			FileUtils.copyFile(new File(utils.getClassInputDirectory() + "config.xml"), new File(project.getDirectory().getMatsimConfigFilePath().toString()));
			FileUtils.copyFile(new File(utils.getClassInputDirectory() + "plans_4agents.xml"), new File(project.getDirectory().getMatsimInputPath() + "/plans_4agents.xml"));
			
			RunExample example = new RunExample();
			example.run(project);
			
			// Test the scores
			
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			new PopulationReader(scenario).readFile("test/output/ch/sbb/intermodalFreight/RunExampleIT/05_matsim_output/rTest/rTest.output_plans.xml.gz");
			Assert.assertEquals("Scores have changed.", 300.11163215607803, scenario.getPopulation().getPersons().get(Id.createPersonId("container_0_car")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 489.2102394530788, scenario.getPopulation().getPersons().get(Id.createPersonId("container_0_pt")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", 473.2490557805454, scenario.getPopulation().getPersons().get(Id.createPersonId("container_1_car")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);
			Assert.assertEquals("Scores have changed.", -2765.101665525193, scenario.getPopulation().getPersons().get(Id.createPersonId("container_1_pt")).getSelectedPlan().getScore(), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();			
		}	
	}

}
