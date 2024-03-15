/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.intermodalfreight.simulate;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
* @author ikaddoura
*/

public class IntermodalFreightConfigGroup extends ReflectiveConfigGroup {
	
	public static final String GROUP_NAME = "intermodalFreight" ;
	
	public IntermodalFreightConfigGroup() {
		super(GROUP_NAME);
	}
	
	public enum TerminalCapacityApproach { WithoutCapacityReduction, WithCapacityReduction };
	public enum CarRoutingApproach { Default, Freespeed };

	private double arrivingLateUtility = -1000.;
	private double departingEarlyUtiliy = -1000.;
	private double arrivingRightOnTimeUtility = 1000.;
	private double tolerance = 3600.;
	private double carPenaltyNight = -1000.;
	private double departureUtility = -2000.;
	private double arrivalUtility = 2000.;
	private double additionalToleranceForImportExport = 0.;
	private TerminalCapacityApproach terminalCapacityApproach = TerminalCapacityApproach.WithoutCapacityReduction;
	private CarRoutingApproach carRoutingApproach = CarRoutingApproach.Freespeed;
	
	@StringGetter( "tolerance" )
	public double getTolerance() {
		return tolerance;
	}
	
	@StringSetter( "tolerance" )
	public void setTolerance(double tolerance) {
		this.tolerance = tolerance;
	}

	@StringGetter( "arrivingLateUtility" )
	public double getArrivingLateUtility() {
		return arrivingLateUtility;
	}

	@StringSetter( "arrivingLateUtility" )
	public void setArrivingLateUtility(double arrivingLateUtility) {
		this.arrivingLateUtility = arrivingLateUtility;
	}

	@StringGetter( "departingEarlyUtiliy" )
	public double getDepartingEarlyUtiliy() {
		return departingEarlyUtiliy;
	}

	@StringSetter( "departingEarlyUtiliy" )
	public void setDepartingEarlyUtiliy(double departingEarlyUtiliy) {
		this.departingEarlyUtiliy = departingEarlyUtiliy;
	}

	@StringGetter( "arrivingRightOnTimeUtility" )
	public double getArrivingRightOnTimeUtility() {
		return arrivingRightOnTimeUtility;
	}

	@StringSetter( "arrivingRightOnTimeUtility" )
	public void setArrivingRightOnTimeUtility(double arrivingRightOnTimeUtility) {
		this.arrivingRightOnTimeUtility = arrivingRightOnTimeUtility;
	}

	@StringGetter( "carPenaltyNight" )
	public double getCarPenaltyNight() {
		return carPenaltyNight;
	}

	@StringSetter( "carPenaltyNight" )
	public void setCarPenaltyNight(double carPenaltyNight) {
		this.carPenaltyNight = carPenaltyNight;
	}

	@StringGetter( "departureUtility" )
	public double getDepartureUtility() {
		return departureUtility;
	}

	@StringSetter( "departureUtility" )
	public void setDepartureUtility(double departureUtility) {
		this.departureUtility = departureUtility;
	}

	@StringGetter( "arrivalUtility" )
	public double getArrivalUtility() {
		return arrivalUtility;
	}

	@StringSetter( "arrivalUtility" )
	public void setArrivalUtility(double arrivalUtility) {
		this.arrivalUtility = arrivalUtility;
	}

	@StringGetter( "additionalToleranceForImportExport" )
	public double getAdditionalToleranceForImportExport() {
		return additionalToleranceForImportExport;
	}

	@StringSetter( "additionalToleranceForImportExport" )
	public void setAdditionalToleranceForImportExport(double additionalToleranceForImportExport) {
		this.additionalToleranceForImportExport = additionalToleranceForImportExport;
	}

	@StringGetter( "terminalCapacityApproach" )
	public TerminalCapacityApproach getTerminalCapacityApproach() {
		return terminalCapacityApproach;
	}

	@StringSetter( "terminalCapacityApproach" )
	public void setTerminalCapacityApproach(TerminalCapacityApproach terminalCapacityApproach) {
		this.terminalCapacityApproach = terminalCapacityApproach;
	}

	@StringGetter( "carRoutingApproach" )
	public CarRoutingApproach getCarRoutingApproach() {
		return carRoutingApproach;
	}

	@StringSetter( "carRoutingApproach" )
	public void setCarRoutingApproach(CarRoutingApproach carRoutingApproach) {
		this.carRoutingApproach = carRoutingApproach;
	}
	
	
}

