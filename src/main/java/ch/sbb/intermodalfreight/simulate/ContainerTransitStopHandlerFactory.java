/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandler;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandlerFactory;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

public class ContainerTransitStopHandlerFactory implements TransitStopHandlerFactory {
	
	@Inject
	private TerminalQueueDeactivationEventHandler queueEventHandler;
	
	@Inject
	private Scenario scenario;

	@Override
	public TransitStopHandler createTransitStopHandler(Vehicle vehicle) {
		return new ContainerTransitStopHandler(vehicle, queueEventHandler, scenario);
	}

}
