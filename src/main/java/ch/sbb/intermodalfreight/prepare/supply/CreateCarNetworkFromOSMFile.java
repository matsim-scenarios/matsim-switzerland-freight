package ch.sbb.intermodalfreight.prepare.supply;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.OsmNetworkReader;


/**
 * 
 * Creates a road network file from an OSM input file.
 * 
 * Requires the following preparation:
 * 
 * 1) Download an osm file, e.g. from https://download.geofabrik.de/
 * 
 * 2) Process the osm data with osmosis
 * osmosis --rb file=switzerland-latest.osm.pbf  --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction --used-node --wx switzerland.osm * 
 *
 *
 * TODO: Uses the old OSM network reader which is deprecated. Should be adapted to the new OSM reader...
 *
 * @author ikaddoura
 *
 */
public class CreateCarNetworkFromOSMFile {
	
	private final static Logger log = LogManager.getLogger(CreateCarNetworkFromOSMFile.class);
	
	private final String INPUT_OSMFILE ;
	private final String outputDir;
	private final String networkCS ;

	private Network network = null;
	private String outnetworkPrefix ;
		
	public static void main(String[] args) throws NumberFormatException, IOException {
		
		String rootDirectory = "path-to-root-directory";
		
		String osmfile = rootDirectory + "switzerland.osm";
		
		String prefix = "ch-network_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String outDir = rootDirectory;

		String crs = "EPSG:2056";
		CreateCarNetworkFromOSMFile networkCreator = new CreateCarNetworkFromOSMFile(osmfile, crs , outDir, prefix);

		boolean keepPaths = false;
		boolean clean = true;
		boolean simplify = false;
				
		networkCreator.createNetwork(keepPaths, simplify, clean);
		networkCreator.writeNetwork();
	}
	
	public CreateCarNetworkFromOSMFile(String inputOSMFile, String networkCoordinateSystem, String outputDir, String prefix) {
		this.INPUT_OSMFILE = inputOSMFile;
		this.networkCS = networkCoordinateSystem;
		this.outputDir = outputDir.endsWith("/")?outputDir:outputDir+"/";
		this.outnetworkPrefix = prefix;
				
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDir);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("--- set the coordinate system for network to be created to " + this.networkCS + " ---");
	}

	public void writeNetwork(){
		String outNetwork = this.outputDir+outnetworkPrefix+"_network.xml.gz";
		log.info("Writing network to " + outNetwork);
		new NetworkWriter(network).write(outNetwork);
		log.info("... done.");
	}
	
	public void createNetwork(boolean keepPaths, boolean doSimplify, boolean doCleaning){
		CoordinateTransformation ct =
			 TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, networkCS);
		
		if(this.network == null) {
			Config config = ConfigUtils.createConfig();
			Scenario scenario = ScenarioUtils.createScenario(config);
			network = scenario.getNetwork();

			log.info("start parsing from osm file " + INPUT_OSMFILE);
	
			OsmNetworkReader networkReader = new OsmNetworkReader(network,ct, true, true);
						
			if (keepPaths) {
				networkReader.setKeepPaths(true);
			} else {
				networkReader.setKeepPaths(false);
			}

			networkReader.parse(INPUT_OSMFILE);
			log.info("finished parsing osm file");
		}	
		
		if (doSimplify){
			outnetworkPrefix += "_simplified";
			log.info("number of nodes before simplifying:" + network.getNodes().size());
			log.info("number of links before simplifying:" + network.getLinks().size());
			log.info("start simplifying the network");
			/*
			 * simplify network: merge links that are shorter than the given threshold
			 */

			NetworkSimplifier simp = new NetworkSimplifier();
			simp.setMergeLinkStats(false);
			simp.run(network);
			
			log.info("number of nodes after simplifying:" + network.getNodes().size());
			log.info("number of links after simplifying:" + network.getLinks().size());
		}
		
		if (doCleaning){
			
			// minimum 1 lane
			for (Link link : network.getLinks().values()) {
	        	if (link.getNumberOfLanes() < 1.0) {
	        		link.setNumberOfLanes(1.0);
	        	}
	        }
			
			// remove links with zero capacity
			Set<Id<Link>> linksToRemove = new HashSet<>();
	        for (Link link : network.getLinks().values()) {
	        	if (link.getNumberOfLanes() == 0. || link.getCapacity() == 0.) {
	        		linksToRemove.add(link.getId());
	        	}
	        }
	        for (Id<Link> link : linksToRemove) {
	        	network.removeLink(link);
	        }
			
			log.info("number of nodes before cleaning:" + network.getNodes().size());
			log.info("number of links before cleaning:" + network.getLinks().size());
			log.info("attempt to clean the network");
			
			/*
			 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
			 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
			 */
			new NetworkCleaner().run(network);
			
			log.info("number of nodes after cleaning:" + network.getNodes().size());
			log.info("number of links after cleaning:" + network.getLinks().size());
			
		}
	}
}
