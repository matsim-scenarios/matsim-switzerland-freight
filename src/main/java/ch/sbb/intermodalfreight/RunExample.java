package ch.sbb.intermodalfreight;

import java.io.IOException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;

import ch.sbb.intermodalfreight.prepare.supply.RunGenerateCargoSupply;
import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup;
import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.TerminalCapacityApproach;
import ch.sbb.intermodalfreight.simulate.RunIntermodalFreightScenario;
import ch.sbb.intermodalfreight.utils.config.Project;

/**
 * An example how to generate the cargo supply and run the simulation.
 *
 * @author ikaddoura
 */
public class RunExample {

    public static void main(String[] args) throws IOException {

        // Read the project settings
        Project project = new Project();

        RunExample example = new RunExample();
        example.run(project);
    }

    /**
     * An example how to generate the supply input files and start a simulation run.
     *
     * @param project the project configuration.
     * @throws IOException
     */
    public void run(Project project) throws IOException {

        // Generate the cargo supply input files
        RunGenerateCargoSupply runGenerateCargoSupply = new RunGenerateCargoSupply();
        runGenerateCargoSupply.run(project);

        // Adjust the config file and start the simulation run.
        String[] arguments = {project.getDirectory().getMatsimConfigFilePath().toString()};
        Config config = RunIntermodalFreightScenario.prepareConfig(arguments);
        config.controller().setRunId(project.getMatsimRun().getId());
        config.controller().setOutputDirectory(project.getDirectory().getMatsimOutputPath().toString());
        config.controller().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

        IntermodalFreightConfigGroup ifCfg = (IntermodalFreightConfigGroup) config.getModules()
                .get(IntermodalFreightConfigGroup.GROUP_NAME);
        ifCfg.setTerminalCapacityApproach(TerminalCapacityApproach.WithCapacityReduction);

        Scenario scenario = RunIntermodalFreightScenario.prepareScenario(config);
        Controler controler = RunIntermodalFreightScenario.prepareControler(scenario);
        controler.run();
    }

}
