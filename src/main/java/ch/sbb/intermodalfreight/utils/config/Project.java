package ch.sbb.intermodalfreight.utils.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Project configuration access
 *
 * @author munterfi
 */
public final class Project {
    private static final Logger log = LogManager.getLogger(Project.class);
    private static final String PROPERTIES_FILE = "/project.properties";
    private static final String VERSION_FILE = "version.json";
    private static final String ROOT_PATH_KEY = "project.root.path";
    private static final String OVERWRITE_KEY = "project.overwrite";
    private static final String DATA_VERSION_KEY = "project.data.version";
    private final Properties properties;
    private boolean overwrite;
    private final Directory directory;
    private final MatsimInput matsimInput;
    private final MatsimRun matsimRun;
    private final String dataVersion;
    private Writer writer;

    public Project() {
        try {
            properties = PropertyReader.load(PROPERTIES_FILE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties", e);
        }
        directory = new Directory();
        matsimInput = new MatsimInput();
        matsimRun = new MatsimRun();
        overwrite = Boolean.parseBoolean(properties.getProperty(OVERWRITE_KEY));
        dataVersion = properties.getProperty(DATA_VERSION_KEY);
        checkArgument(dataVersion.startsWith("d"), "Invalid data version. It should start with 'd'");
    }

    public Directory getDirectory() {
        return directory;
    }

    public MatsimInput getMatsimInput() {
        return matsimInput;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public Properties getProperties() {
        return properties;
    }

    public MatsimRun getMatsimRun() {
        return matsimRun;
    }

    public String getDataVersion() {
        return dataVersion;
    }

    public void writeMatsimInputVersionFile() {
        try {
            ObjectMapper objectMapper = JsonMapper.builder().configure(SerializationFeature.INDENT_OUTPUT, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("dataVersion", dataVersion);
            jsonMap.put("createdBy", System.getProperty("user.name"));
            jsonMap.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            jsonMap.put("matsimInput", matsimInput);
            String jsonContent = objectMapper.writeValueAsString(jsonMap);
            Path filePath = getDirectory().getMatsimInputPath().resolve(VERSION_FILE);
            log.info("Writing matsim input version file to {}", filePath);
            try (BufferedWriter writer = (this.writer != null) ? new BufferedWriter(
                    this.writer) : Files.newBufferedWriter(filePath)) {
                writer.write(jsonContent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }

    /**
     * Ensures that the specified directory exists.
     * <p>
     * If the directory exists and overwriting is not allowed, a {@link RuntimeException} is thrown. If overwriting is
     * allowed, the method may perform additional actions based on the specific implementation.
     * <p>
     * If the directory does not exist, it is created along with any necessary parent directories.
     *
     * @param path The {@link Path} representing the directory to ensure.
     * @throws RuntimeException If an error occurs during the operation or if overwriting is not allowed and the
     *                          directory already exists.
     */
    public void ensureDirectoryExists(Path path) {
        System.out.println(overwrite);
        try {
            if (Files.exists(path) && Files.isDirectory(path)) {
                handleExistingDirectory(path);
            } else {
                log.info("Creating directory {}", path);
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error ensuring directory " + path, e);
        }
    }

    private void handleExistingDirectory(Path path) {
        if (!overwrite) {
            throw new RuntimeException("Directory " + path + " already exists, and overwriting is not allowed.");
        }
        log.warn("Directory already exists, and overwriting is allowed. Overwriting...");
    }

    void setWriter(Writer writer) {
        this.writer = writer;
    }

    public class MatsimInput {
        private static final String MATSIM_INPUT_VERSION_KEY = "matsim.input.version";
        private static final String SIMULATED_DAYS_KEY = "matsim.input.simulated.days";
        private final Supply supply;
        private final String version;
        private final int simulatedDays;

        private MatsimInput() {
            supply = new Supply();
            version = properties.getProperty(MATSIM_INPUT_VERSION_KEY);
            simulatedDays = Integer.parseInt(properties.getProperty(SIMULATED_DAYS_KEY));
            checkArgument(version.startsWith("v"), "Invalid MatsimInput version. It should start with 'v'");
        }

        public Supply getSupply() {
            return supply;
        }

        public String getVersion() {
            return version;
        }

        public int getSimulatedDays() {
            return simulatedDays;
        }

    }

    public class Supply {
        private static final String SUPPLY_CRANE_LINK_LENGTH_KEY = "matsim.input.supply.crane.link.length";
        private static final String SUPPLY_CRANE_TRAVEL_TIME_KEY = "matsim.input.supply.crane.travel.time";
        private static final String SUPPLY_FIRST_ARRIVAL_OFFSET = "matsim.input.supply.first.arrival.offset";
        private static final String SUPPLY_INPUT_NETWORK_KEY = "matsim.input.supply.network";
        private static final String SUPPLY_INPUT_LINKS_TO_REMOVE_FROM_NETWORK_KEY = "matsim.input.supply.links.to.remove.from.network";
        private static final String SUPPLY_INPUT_NETWORK_CHANGE_EVENTS_KEY = "matsim.input.supply.network.change.events";
        private static final String SUPPLY_INPUT_SCHEDULE_KEY = "matsim.input.supply.schedule";
        private static final String SUPPLY_INPUT_SHEET_KEY = "matsim.input.supply.sheet";
        private static final String SUPPLY_INPUT_TERMINALS_KEY = "matsim.input.supply.terminals";
        private static final String SUPPLY_INPUT_DISTANCES_KEY = "matsim.input.supply.distances";
        private static final String SUPPLY_TRAIN_CAPACITY_KEY = "matsim.input.supply.train.capacity";
        private static final String SUPPLY_NETWORK_NIGHT_HGV_RESTRICTION_KEY = "matsim.input.supply.network.night.hgv.restriction";

        private final double craneLinkLength;
        private final double craneTravelTime;
        private final double arrivalDepartureOffsetFirstStop;
        private final String network;
        private final String linksToRemoveFromNetwork;
        private final String networkChangeEvents;
        private final String schedule;
        private final String sheet;
        private final String distances;
        private final String terminals;
        private final int trainCapacity;
        private final boolean networkNightHGVRestriction;

        private Supply() {
            craneLinkLength = Double.parseDouble(properties.getProperty(SUPPLY_CRANE_LINK_LENGTH_KEY));
            craneTravelTime = Double.parseDouble(properties.getProperty(SUPPLY_CRANE_TRAVEL_TIME_KEY));
            arrivalDepartureOffsetFirstStop = Double.parseDouble(properties.getProperty(SUPPLY_FIRST_ARRIVAL_OFFSET));
            network = properties.getProperty(SUPPLY_INPUT_NETWORK_KEY);
            linksToRemoveFromNetwork = properties.getProperty(SUPPLY_INPUT_LINKS_TO_REMOVE_FROM_NETWORK_KEY);
            networkChangeEvents = properties.getProperty(SUPPLY_INPUT_NETWORK_CHANGE_EVENTS_KEY);
            schedule = properties.getProperty(SUPPLY_INPUT_SCHEDULE_KEY);
            sheet = properties.getProperty(SUPPLY_INPUT_SHEET_KEY);
            distances = properties.getProperty(SUPPLY_INPUT_DISTANCES_KEY);
            terminals = properties.getProperty(SUPPLY_INPUT_TERMINALS_KEY);
            trainCapacity = Integer.parseInt(properties.getProperty(SUPPLY_TRAIN_CAPACITY_KEY));
            networkNightHGVRestriction = Boolean.parseBoolean(
                    properties.getProperty(SUPPLY_NETWORK_NIGHT_HGV_RESTRICTION_KEY));

            checkArgument(craneLinkLength > 0, "Invalid crane link length. It should be greater than 0.");
            checkArgument(craneTravelTime > 0, "Invalid crane travel time. It should be greater than 0.");
            checkArgument(trainCapacity > 0, "Invalid train capacity. It should be greater than 0.");
            checkArgument(arrivalDepartureOffsetFirstStop >= 0,
                    "Invalid first arrival offset. Should be equals or greater than 0.");
        }

        public double getCraneLinkLength() {
            return craneLinkLength;
        }

        public double getCraneTravelTime() {
            return craneTravelTime;
        }

        public double getArrivalDepartureOffsetFirstStop() {
            return arrivalDepartureOffsetFirstStop;
        }

        public String getNetwork() {
            return network;
        }

        public String getLinksToRemoveFromNetwork() {
            return linksToRemoveFromNetwork;
        }

        public String getNetworkChangeEvents() {
            return networkChangeEvents;
        }

        public String getSchedule() {
            return schedule;
        }

        public String getSheet() {
            return sheet;
        }

        public String getDistances() {
            return distances;
        }

        public String getTerminals() {
            return terminals;
        }

        public int getTrainCapacity() {
            return trainCapacity;
        }

        public boolean isNetworkNightHGVRestriction() {
            return networkNightHGVRestriction;
        }
    }

    public class MatsimRun {
        private static final String RUN_ID_KEY = "matsim.run.id";
        private final String id;

        private MatsimRun() {
            id = properties.getProperty(RUN_ID_KEY);
            checkArgument(id.startsWith("r"), "Invalid run ID: " + id + ". It should start with 'r'");
        }

        public String getId() {
            return id;
        }

    }

    public class Directory {
        private static final String ORIGINAL_DATA = "01_original_data";
        private static final String PROCESSED_DATA = "02_processed_data";
        private static final String MATSIM_INPUT = "03_matsim_input";
        private static final String MATSIM_CONFIG = "04_matsim_config";
        private static final String MATSIM_OUTPUT = "05_matsim_output";
        private static final String ANALYSIS_RESULTS = "06_analysis_results";
        private static final String DOCS = "07_docs";
        private final Path rootPath;
        private final Path originalDataPath;
        private final Path processedDataPath;
        private final Path matsimInputPath;
        private final Path matsimConfigFilePath;
        private final Path matsimOutputPath;
        private final Path analysisResultsPath;
        private final Path docsPath;

        private Directory() {
            String dataVersion = properties.getProperty(Project.DATA_VERSION_KEY);
            String inputVersion = properties.getProperty(MatsimInput.MATSIM_INPUT_VERSION_KEY);
            String runId = properties.getProperty(MatsimRun.RUN_ID_KEY);
            rootPath = Path.of(properties.getProperty(Project.ROOT_PATH_KEY));
            originalDataPath = rootPath.resolve(ORIGINAL_DATA);
            processedDataPath = rootPath.resolve(PROCESSED_DATA).resolve(dataVersion);
            matsimInputPath = rootPath.resolve(MATSIM_INPUT).resolve(inputVersion);
            matsimConfigFilePath = rootPath.resolve(MATSIM_CONFIG).resolve(String.format("config_%s.xml", runId));
            matsimOutputPath = rootPath.resolve(MATSIM_OUTPUT).resolve(runId);
            analysisResultsPath = rootPath.resolve(ANALYSIS_RESULTS).resolve(runId);
            docsPath = rootPath.resolve(DOCS);
        }

        public Path getOriginalDataPath() {
            return originalDataPath;
        }

        public Path getProcessedDataPath() {
            return processedDataPath;
        }

        public Path getMatsimInputPath() {
            return matsimInputPath;
        }

        public Path getMatsimConfigFilePath() {
            return matsimConfigFilePath;
        }

        public Path getMatsimOutputPath() {
            return matsimOutputPath;
        }

        public Path getAnalysisResultsPath() {
            return analysisResultsPath;
        }

        public Path getDocsPath() {
            return docsPath;
        }

    }

}
