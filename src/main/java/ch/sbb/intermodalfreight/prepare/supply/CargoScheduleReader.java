package ch.sbb.intermodalfreight.prepare.supply;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * @author ikaddoura
 *
 */
public class CargoScheduleReader {
    private static final Logger log = LogManager.getLogger(CargoScheduleReader.class);
    
    private final List<RouteInfo> routeInfos;

	/**
	 * 
	 * Reads the schedule provided as xlsx file.
	 * 
	 * @param inputScheduleXLSX The schedule as xlsx file. For an example of how this file should look like, have a look into the test input directory.
	 * @param sheetName The xlsx sheet name.
	 * @param terminals The terminal information.
	 * @param arrivalDepartureOffsetFirstStop The first arrival departure offset time, i.e. how many seconds the train should arrive before departing at the initial stop.
	 * 
	 */
	public CargoScheduleReader(String inputScheduleXLSX, String sheetName, Map<String, Terminal> terminals, double arrivalDepartureOffsetFirstStop) {
		routeInfos = new ArrayList<>();

		log.info("Reading transit schedule...");
		
		try {
			
			FileInputStream fis = new FileInputStream(new File(inputScheduleXLSX));
			Workbook wb;
			wb = WorkbookFactory.create(fis);
			
			Sheet sheet = wb.getSheet(sheetName);
					    
		    Map<Integer, String> column2Header = new HashMap<>();
		    
		    int rowCounter = 0;
		    for (Row row : sheet) {
		    	log.info("-------");
		      	
		    	if (rowCounter == 0) {
		    		
		    		// store the headers
		    		String header = null;
		    		for (Cell cell : row) {
		    			if (cell.getCellType() == CellType.STRING) {
		    				header = cell.getRichStringCellValue().getString();
							column2Header.put(cell.getColumnIndex(), header);
							log.info(cell.getColumnIndex() + " -> " + header);
		    			} else if (cell.getCellType() == CellType.BLANK) {
		    				// merged cells -> use the previous header
							column2Header.put(cell.getColumnIndex(), header);
							log.info(cell.getColumnIndex() + " -> " + header);
		    			} else {
		    				throw new RuntimeException("Expecting only String Headers. Aborting...");
		    			}
		    		}
		    			    		
		    	} else if (rowCounter == 1) {
		    		// skip the second line (contains additional headers which we do not need)
		    		
		    	} else {
		    		
		    		String line = null;
			    	String route = null;
			    	Double units = null;
			    	Map<String,List<Double>> terminalFromHeader2Times = new HashMap<>();
			    			        
			        for (Cell cell : row) {

			        	if (cell.getCellType() == CellType.BLANK) {
			        		// skip cell
			        		continue;
			        	}
			        		
			        	String headerOfCurrentCell = column2Header.get(cell.getColumnIndex());
			        	log.info(headerOfCurrentCell);
			        	
			        	headerOfCurrentCell = fixEncodingIssues(headerOfCurrentCell);
			        	
			        	if (headerOfCurrentCell.equals("Linie")) {
			        		if (cell.getCellType() == CellType.STRING) {
			        			line = cell.getRichStringCellValue().getString();
			        			line = fixEncodingIssues(line);
			        		} else {
			        			throw new RuntimeException("Expecting the Zug column as String format. Aborting...");
			        		}
			        	} else if (headerOfCurrentCell.equals("Strecke")) {
			        		if (cell.getCellType() == CellType.STRING) {
			        			route = cell.getRichStringCellValue().getString();
			        			route = fixEncodingIssues(route);
			        		} else {
			        			throw new RuntimeException("Expecting the Strecke column as String format. Aborting...");
			        		}
			        	} else if (headerOfCurrentCell.equals("Richtung")) {
			        		// not required
			        	
			        	} else if (headerOfCurrentCell.equals("Zugeinheiten")) {
			        		if (cell.getCellType() == CellType.NUMERIC) {
			        			units = cell.getNumericCellValue();
			        			if (units == null || units > 1.0 || units == 0.) throw new RuntimeException("Invalid units value: " + units);

			        		} else {
			        			throw new RuntimeException("Expecting the Zugeinheiten column as Numeric format. Aborting...");
			        		}
			        	
			        	} else {
			        		// the header should be a terminal name
			        		
			        		// initialize
			        		if (terminalFromHeader2Times.get(headerOfCurrentCell) == null) {
				        		terminalFromHeader2Times.put(headerOfCurrentCell, new ArrayList<>());
			        		}
			        				        
			        		// now, store the times for the terminal
			        		if (cell.getCellType() == CellType.NUMERIC) {
			        			Date date = cell.getDateCellValue();
			            		double time = date.getHours() * 3600. + date.getMinutes() * 60 + date.getSeconds();
			            		terminalFromHeader2Times.get(headerOfCurrentCell).add(time);
			            		
			        		} else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
			        			Date date = cell.getDateCellValue();
			            		double time = date.getHours() * 3600. + date.getMinutes() * 60 + date.getSeconds();
			            		terminalFromHeader2Times.get(headerOfCurrentCell).add(time);
			            		
			        		} else {
			        			log.warn("Row number: " + row.getRowNum());
			        			throw new RuntimeException("Expecting the An/Ab column as Numeric or Formula/Numeric format. Aborting... cellType: " + cell.getCellType() + " / " + cell.getStringCellValue());
			        		}
			        	}
			        }
			        
			        RouteInfo routeInfo = new RouteInfo(line, route, units, terminalFromHeader2Times);
			        if (line != null && route != null) routeInfos.add(routeInfo);
		    	}
		        rowCounter++;
		    }
		    
		    wb.close();
		    
		    log.info("Route data:");
		    for (RouteInfo routeInfo : routeInfos) {
		    	log.info(routeInfo.toString());
		    }
		    
		    log.info("Reading transit schedule... Done.");
		    
		    log.info("Processing transit schedule...");
		    process(terminals, arrivalDepartureOffsetFirstStop);
		    log.info("Processing transit schedule... Done.");
		    
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	private String fixEncodingIssues(String route) {
		
		if (route.contains(" ")) {
			log.warn("Replace whitespace: " + route);
			route = route.replaceAll("\\s+","");
			log.warn(" --> " + route);
		}
		
		if (route.contains("ä") || route.contains("ö") || route.contains("ü")) {
			log.warn("Replace Umlaut: " + route);
			route = route.replaceAll("ä","ae").replaceAll("ö", "oe").replaceAll("ü", "ue");
			log.warn(" --> " + route);
		}
		
		if (route.contains("Ä") || route.contains("Ö") || route.contains("Ü")) {
			log.warn("Replace Umlaut: " + route);
			route = route.replaceAll("Ä","AE").replaceAll("Ö", "OE").replaceAll("Ü", "UE");
			log.warn(" --> " + route);
		}
		return route;
	}

	private void process(Map<String, Terminal> terminals, double arrivalDepartureOffsetFirstStop) {
		
		for (RouteInfo routeInfo : this.routeInfos) {
	    	log.info("Processing route info: " + routeInfo.toString());
	    	
	    	String transitRoute = routeInfo.getRoute();
	    	
			List<RouteStopInfo> routeStopInfos = new ArrayList<>();
			String[] terminalArray = transitRoute.split("-");
			int terminalCounter = 0;
	    	for (String terminalShort : terminalArray) {
	    		log.info(terminalShort);
	    		Terminal terminal = getTerminalViaShortName(terminals, terminalShort.trim());
	    		String terminalFromHeader = terminal.getHeader();
	    		
	    		double terminalArrival;
				double terminalDeparture;
				
	    		if (terminalCounter == 0) {
	    			// first terminal
	    			if (routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader) == null) {
	    				throw new RuntimeException("No times found for terminal. Aborting... Terminal: " + terminal.getName() + " / Route: " + routeInfo);
	    			}
	    			
	    			if (routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).size() != 1) {
	    				throw new RuntimeException("Expecting only one time information. Aborting... Terminal: " + terminal.getName() + " / Route: " + routeInfo);
	    			}
	    			
	    			terminalDeparture = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(0);
	    			
	    			// we also want the vehicle to arrive some time before the departure, was NaN in the previous version...
	    			terminalArrival = terminalDeparture - arrivalDepartureOffsetFirstStop;
	    			// if (terminalArrival < 0.) terminalArrival = 0.;
	    			
	    		} else if (terminalCounter == terminalArray.length - 1) {
	    			// last terminal
	    			if (routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).size() != 1) {
	    				throw new RuntimeException("Expecting only one time information. Aborting..." + terminal.getName());
	    			}
	    			terminalArrival = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(0);
	    			terminalDeparture = Double.NaN;
	    			
	    		} else {
	    			// intermediate terminals
	    			double time1;
	    			double time2;
	    			if (routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).size() != 3) {
	    				log.warn("Expecting arrival, departure and haltezeit for intermediate stops. " + terminal.getName() + " - " + transitRoute);
	    				log.warn("Assuming that the given time is the arrival time and assuming a fallback stop time of 30 minutes.");
	    				time1 = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(0);
	    				time2 = time1 + 1800.;
	    			} else {
	    				time1 = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(0);
		    			time2 = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(2);
	    			}
	    			
	    			double haltezeit = routeInfo.getTerminalFromHeader2Times().get(terminalFromHeader).get(1);
	    			
	    			if (time1 - time2 == haltezeit ||
	    					time1 - time2 == haltezeit - 24 * 3600.) {
	    				
	    				terminalArrival = time2;
	    				terminalDeparture = time1;
	    				
	    			} else {
	    				terminalArrival = time1;
	    				terminalDeparture = time2;	
	    			}
	    		}
	    		
	    		RouteStopInfo stop = new RouteStopInfo(terminal.getTerminalLink(), terminal.getStop(), terminalArrival, terminalDeparture);
				routeStopInfos.add(stop);
				
				terminalCounter++;
	    	}	 
	    	routeInfo.setRouteStopInfos(routeStopInfos);
	    }
	}
	
	private static Terminal getTerminalViaShortName(Map<String, Terminal> terminals, String terminalShort) {
		for (Terminal terminal : terminals.values()) {
			if (terminal.getShortName().equals(terminalShort)){
				return terminal;
			}
		}
		
		log.warn("Could not find a terminal for " + terminalShort);
		
		for (Terminal terminal : terminals.values()) {
			log.warn(terminal.getShortName());
		}
		
		throw new NullPointerException("Could not find terminal " + terminalShort + ". Aborting...");
	}

	/**
	 * Returns the route information which was read from the xlsx schedule file.
	 * 
	 * @return
	 */
	public List<RouteInfo> getRouteInfos() {
		return this.routeInfos;
	}

}
