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

import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.pt.PTPassengerAgent;
import org.matsim.core.mobsim.qsim.pt.PassengerAccessEgress;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandler;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.TerminalCapacityApproach;

/**
 * A slightly modified version of the default which uses the access and egress times from the terminal attributes instead of from the vehicle type.
 * 
 * @author ikaddoura based on the default version by aneumann
 * 
 */
public class ContainerTransitStopHandler implements TransitStopHandler {

	private boolean doorsOpen = false;
	private double passengersLeavingTimeFraction = 0.0;
	private double passengersEnteringTimeFraction = 0.0;
	
	private final VehicleType.DoorOperationMode doorOperationMode;
	
	private static final double openDoorsDuration = 1.0;
	private static final double closeDoorsDuration = 1.0;
	
	private TerminalQueueDeactivationEventHandler queueEventHandler;
	private IntermodalFreightConfigGroup ifCfg;
	
	ContainerTransitStopHandler(Vehicle vehicle, TerminalQueueDeactivationEventHandler queueEventHandler, Scenario scenario) {
		this.doorOperationMode = VehicleUtils.getDoorOperationMode(vehicle.getType());
		this.queueEventHandler = queueEventHandler;
		this.ifCfg = (IntermodalFreightConfigGroup) scenario.getConfig().getModules().get(IntermodalFreightConfigGroup.GROUP_NAME);
	}

	@Override
	public double handleTransitStop(TransitStopFacility stop, double now, List<PTPassengerAgent> leavingPassengers,
			List<PTPassengerAgent> enteringPassengers, PassengerAccessEgress handler, MobsimVehicle vehicle) {
		
		if(this.doorOperationMode == VehicleType.DoorOperationMode.parallel){
			throw new RuntimeException("Not supported. Set door operation mode to 'parallel.");
			
		} else if (this.doorOperationMode == VehicleType.DoorOperationMode.serial){
			return handleSerialStop(stop, now, leavingPassengers, enteringPassengers, handler, vehicle);
			
		} else {
			throw new RuntimeException("Not supported. Set door operation mode to 'parallel.");
		}		
	}
	
	private double handleSerialStop(TransitStopFacility stop, double now, List<PTPassengerAgent> leavingPassengers, 
			List<PTPassengerAgent> enteringPassengers, PassengerAccessEgress handler, MobsimVehicle vehicle) {
		
		double personEntersTime;
		double personLeavesTime;
		if (stop.getAttributes().getAttribute("accessTime") == null ||
				stop.getAttributes().getAttribute("egressTime") == null) {					
					throw new RuntimeException("There are no access/egress times provided in the transit stop facility attributes. "
							+ "Please add these parameters to the transit stop facility attributes in the schedule, "
							+ "e.g., 'accessTime = 1.0' and 'egressTime = 1.0'. Aborting...");
		
		} else {
						
			double accessEgreesTimeFactor = 1.;
			
			if (ifCfg.getTerminalCapacityApproach() == TerminalCapacityApproach.WithCapacityReduction) {
				Set<Id<Vehicle>> trainsAtTerminal = queueEventHandler.getTerminal2trains().get(stop.getId());	
				if (trainsAtTerminal != null && trainsAtTerminal.size() > 0) {
					accessEgreesTimeFactor = trainsAtTerminal.size();
				}
			} else if (ifCfg.getTerminalCapacityApproach() == TerminalCapacityApproach.WithoutCapacityReduction) {
				// nothing to do
				
			} else {
				throw new RuntimeException("Unknown terminal capacity approach. Aborting...");
			}
			
			personEntersTime = (double) stop.getAttributes().getAttribute("accessTime") * accessEgreesTimeFactor;
			personLeavesTime = (double) stop.getAttributes().getAttribute("egressTime") * accessEgreesTimeFactor;
		}
		
		double stopTime = 0.0;

		int cntEgress = leavingPassengers.size();
		int cntAccess = enteringPassengers.size();

		if (!this.doorsOpen) {
			// doors are closed

			if ((cntAccess > 0) || (cntEgress > 0)) {
				// case doors are shut, but passengers want to leave or enter
				// the veh
				this.doorsOpen = true;
				stopTime = openDoorsDuration; // Time to open doors
			} else {
				// case nobody wants to leave or enter the veh
				stopTime = 0.0;
			}

		} else {
			// doors are already open

			if ((cntAccess > 0) || (cntEgress > 0)) {
				// somebody wants to leave or enter the veh
				
				if (cntEgress > 0) {

					if (this.passengersLeavingTimeFraction < 1.0) {
						// next passenger can leave the veh

						while (this.passengersLeavingTimeFraction < 1.0) {
							if (leavingPassengers.size() == 0) {
								break;
							}

							if(handler.handlePassengerLeaving(leavingPassengers.get(0), vehicle, stop.getLinkId(), now)){
								leavingPassengers.remove(0);
								this.passengersLeavingTimeFraction += personLeavesTime;
							} else {
								break;
							}

						}

						this.passengersLeavingTimeFraction -= 1.0;
						stopTime = 1.0;

					} else {
						// still time needed to allow next passenger to leave
						this.passengersLeavingTimeFraction -= 1.0;
						stopTime = 1.0;
					}

				} else {
					this.passengersLeavingTimeFraction -= 1.0;
					this.passengersLeavingTimeFraction = Math.max(0, this.passengersLeavingTimeFraction);
					
					if (cntAccess > 0) {

						if (this.passengersEnteringTimeFraction < 1.0) {

							// next passenger can enter the veh

							while (this.passengersEnteringTimeFraction < 1.0) {
								if (enteringPassengers.size() == 0) {
									break;
								}

								if(handler.handlePassengerEntering(enteringPassengers.get(0), vehicle, stop.getId(), now)){
									enteringPassengers.remove(0);
									this.passengersEnteringTimeFraction += personEntersTime;
								} else {
									break;
								}

							}

							this.passengersEnteringTimeFraction -= 1.0;
							stopTime = 1.0;

						} else {
							// still time needed to allow next passenger to enter
							this.passengersEnteringTimeFraction -= 1.0;
							stopTime = 1.0;
						}

					} else {
						this.passengersEnteringTimeFraction -= 1.0;
						this.passengersEnteringTimeFraction = Math.max(0, this.passengersEnteringTimeFraction);
					}
				}				

			} else {

				// nobody left to handle

				if (this.passengersEnteringTimeFraction < 1.0 && this.passengersLeavingTimeFraction < 1.0) {
					// every passenger entered or left the veh so close and
					// leave

					this.doorsOpen = false;
					this.passengersEnteringTimeFraction = 0.0;
					this.passengersLeavingTimeFraction = 0.0;
					stopTime = closeDoorsDuration; // Time to shut the doors
				}

				// somebody is still leaving or entering the veh so wait again

				if (this.passengersEnteringTimeFraction >= 1) {
					this.passengersEnteringTimeFraction -= 1.0;
					stopTime = 1.0;
				}

				if (this.passengersLeavingTimeFraction >= 1) {
					this.passengersLeavingTimeFraction -= 1.0;
					stopTime = 1.0;
				}

			}

		}

		return stopTime;
	}

}
