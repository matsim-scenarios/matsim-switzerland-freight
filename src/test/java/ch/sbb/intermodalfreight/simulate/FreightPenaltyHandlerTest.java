/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author ikaddoura
 *
 */
public class FreightPenaltyHandlerTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public final void testLegWasDuringNight() {
		Assert.assertEquals(false, FreightPenaltyHandler.legWasDuringNight(8 * 3600., 10 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(21 * 3600., 24 * 3600 + 9 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(8 * 3600., 24 * 3600. + 23 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(22 * 3600., 23 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(3 * 3600., 4 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(3 * 3600., 6 * 3600.));
		Assert.assertEquals(false, FreightPenaltyHandler.legWasDuringNight(20 * 3600., 21 * 3600.));

		// start on second day
		Assert.assertEquals(false, FreightPenaltyHandler.legWasDuringNight(24 * 3600. + 19 * 3600., 24 * 3600. + 20 * 3600.));
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(24 * 3600. + 19 * 3600., 24 * 3600. + 23 * 3600.));	
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(24 * 3600. + 4 * 3600., 24 * 3600. + 9 * 3600.));	
		
		// some crazy cases
		Assert.assertEquals(true, FreightPenaltyHandler.legWasDuringNight(6 * 3600., 2 * 24 * 3600 + 17 * 3600.));
	}	
	
}
