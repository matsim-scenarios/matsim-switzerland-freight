package ch.sbb.intermodalfreight.simulate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.pt.PTPassengerAgent;
import org.matsim.core.mobsim.qsim.pt.PassengerAccessEgress;
import org.matsim.core.scenario.ScenarioUtils;
import org.mockito.MockMakers;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import ch.sbb.intermodalfreight.simulate.IntermodalFreightConfigGroup.TerminalCapacityApproach;

/**
 * Regression tests for the TEU-equivalent boarding capacity gate in
 * {@link ContainerTransitStopHandler}.
 *
 * Background: MATSim core rebuilds the list of entering passengers on every simulated second
 * (see PassengerAccessEgressImpl.calculateStopTimeAndTriggerBoarding) and caps it only by the
 * RAW passenger count. Once train capacity is weighted by container size, a train can be full
 * in TEU terms while still raw-count-empty, so the same waiting containers keep reappearing in
 * that list. If handleTransitStop() keeps reporting a non-zero stop time in that situation, the
 * train never departs - core departs a transit vehicle only when the stop time is 0.0.
 *
 * These tests reproduce that core behaviour by rebuilding the entering list on every step, and
 * assert that the train always reaches a departure decision within a bounded number of steps.
 *
 * @author nicdul
 */
public class ContainerTransitStopHandlerCapacityTest {

	/**
	 * Upper bound on simulated seconds spent at the stop before we call it a deadlock. The
	 * fixed handler needs a handful of steps (open doors / board / close doors); the buggy
	 * version never terminates, so any generous bound exposes it.
	 */
	private static final int MAX_STEPS = 100;

	private static final Id<Vehicle> TRAIN_ID = Id.createVehicleId("train1");

	private Scenario scenario;
	private TransitStopFacility stop;
	private Vehicle train;
	private MobsimVehicle mobsimTrain;
	private TerminalQueueDeactivationEventHandler queueHandler;
	private PassengerAccessEgress accessEgress;

	@BeforeEach
	public void setUp() {
		Config config = ConfigUtils.createConfig();
		IntermodalFreightConfigGroup ifCfg = new IntermodalFreightConfigGroup();
		// WithoutCapacityReduction keeps the access/egress time factor at 1.0, so one container
		// boards per simulated second and the step accounting stays easy to reason about.
		ifCfg.setTerminalCapacityApproach(TerminalCapacityApproach.WithoutCapacityReduction);
		config.addModule(ifCfg);
		this.scenario = ScenarioUtils.createScenario(config);

		this.stop = this.scenario.getTransitSchedule().getFactory().createTransitStopFacility(
				Id.create("terminal1", TransitStopFacility.class), new Coord(0, 0), false);
		this.stop.setLinkId(Id.createLinkId("link1"));
		this.stop.getAttributes().putAttribute("accessTime", 1.0);
		this.stop.getAttributes().putAttribute("egressTime", 1.0);

		// Door operation mode defaults to serial, which is the only mode the handler supports.
		VehicleType trainType = VehicleUtils.createVehicleType(Id.create("trainType", VehicleType.class));
		this.train = this.scenario.getTransitVehicles().getFactory().createVehicle(TRAIN_ID, trainType);

		this.mobsimTrain = mock(MobsimVehicle.class);
		when(this.mobsimTrain.getId()).thenReturn(TRAIN_ID);

		// The subclass mock maker is required here: TerminalQueueDeactivationEventHandler
		// implements a dozen MATSim event-handler interfaces, and Mockito's default inline
		// mock maker fails to instrument that whole hierarchy ("Could not modify all classes").
		// Only hasRemainingCapacityFor() is exercised, so a constructor-less subclass is enough.
		this.queueHandler = mock(TerminalQueueDeactivationEventHandler.class,
				withSettings().mockMaker(MockMakers.SUBCLASS));
		this.accessEgress = mock(PassengerAccessEgress.class);
	}

	/**
	 * The train is already full in TEU-equivalent terms when it arrives, so none of the waiting
	 * containers fits. The train must still reach a departure decision instead of holding the
	 * stop forever.
	 */
	@Test
	public void trainDepartsWhenNoWaitingContainerFits() {
		// nothing fits, whatever the agent
		when(this.queueHandler.hasRemainingCapacityFor(any(), any())).thenReturn(false);

		ContainerTransitStopHandler handler = newHandler();
		List<PTPassengerAgent> waiting = List.of(passenger("feu1"), passenger("feu2"));

		int steps = runUntilDeparture(handler, waiting, new HashSet<>());

		assertTrue(steps < MAX_STEPS,
				"Train never departed: still at the stop after " + MAX_STEPS + " simulated seconds "
						+ "even though no waiting container fits into its remaining TEU capacity.");
		verify(this.accessEgress, never()).handlePassengerEntering(any(), any(), any(), anyDouble());
	}

	/**
	 * The train has room for two containers and three are waiting. It must board exactly two and
	 * then depart, leaving the third for a later departure - rather than holding the doors open
	 * because a waiting container it can no longer carry keeps reappearing in the boarding list.
	 */
	@Test
	public void trainDepartsOnceItIsFullAndLeavesTheRestBehind() {
		PTPassengerAgent first = passenger("teu1");
		PTPassengerAgent second = passenger("teu2");
		PTPassengerAgent third = passenger("teu3");

		Set<Id<Person>> boarded = new HashSet<>();
		// room for exactly two containers
		when(this.queueHandler.hasRemainingCapacityFor(any(), any())).thenAnswer(inv -> boarded.size() < 2);
		when(this.accessEgress.handlePassengerEntering(any(), any(), any(), anyDouble())).thenAnswer(inv -> {
			boarded.add(((PTPassengerAgent) inv.getArgument(0)).getId());
			return true;
		});

		ContainerTransitStopHandler handler = newHandler();
		int steps = runUntilDeparture(handler, List.of(first, second, third), boarded);

		assertTrue(steps < MAX_STEPS,
				"Train never departed after filling up: still at the stop after " + MAX_STEPS
						+ " simulated seconds with one container left waiting.");
		assertEquals(2, boarded.size(), "Expected exactly two containers to board a train with room for two.");
		assertTrue(boarded.contains(first.getId()) && boarded.contains(second.getId()),
				"Expected the two head-of-queue containers to board, but got: " + boarded);
	}

	/**
	 * An oversized container (a FEU needing 2 slots) sits at the head of the FIFO boarding queue
	 * and does not fit, while a smaller TEU behind it does. The smaller one must still get on
	 * instead of the whole stop stalling behind the oversized one.
	 */
	@Test
	public void smallerContainerBoardsPastAnOversizedOneAtTheHeadOfTheQueue() {
		// The ids are resolved up front: calling getId() on a mock inside a matcher expression
		// would corrupt Mockito's matcher stack.
		Id<Person> oversizedId = Id.createPersonId("feu1");
		Id<Person> fittingId = Id.createPersonId("teu1");
		PTPassengerAgent oversized = passenger("feu1");
		PTPassengerAgent fitting = passenger("teu1");

		Set<Id<Person>> boarded = new HashSet<>();
		when(this.queueHandler.hasRemainingCapacityFor(any(), eq(oversizedId))).thenReturn(false);
		when(this.queueHandler.hasRemainingCapacityFor(any(), eq(fittingId))).thenReturn(true);
		when(this.accessEgress.handlePassengerEntering(any(), any(), any(), anyDouble())).thenAnswer(inv -> {
			boarded.add(((PTPassengerAgent) inv.getArgument(0)).getId());
			return true;
		});

		ContainerTransitStopHandler handler = newHandler();
		int steps = runUntilDeparture(handler, List.of(oversized, fitting), boarded);

		assertTrue(steps < MAX_STEPS, "Train never departed with an oversized container at the head of the queue.");
		assertEquals(Set.of(fittingId), boarded,
				"Expected the smaller container to board past the oversized one, but got: " + boarded);
	}

	private ContainerTransitStopHandler newHandler() {
		return new ContainerTransitStopHandler(this.train, this.queueHandler, this.scenario);
	}

	/**
	 * Drives the handler second by second until it reports a stop time of 0.0, which is what makes
	 * MATSim core let the transit vehicle depart.
	 *
	 * The entering list is rebuilt on every step from the agents that have not boarded yet, which
	 * is exactly what core does - and is the reason the naive "stop boarding when the next
	 * container does not fit" approach deadlocked.
	 *
	 * @return the number of steps taken; {@value #MAX_STEPS} means the train never departed
	 */
	private int runUntilDeparture(ContainerTransitStopHandler handler, List<PTPassengerAgent> waiting,
			Set<Id<Person>> boarded) {
		double now = 0.0;
		for (int step = 0; step < MAX_STEPS; step++) {
			List<PTPassengerAgent> entering = new ArrayList<>();
			for (PTPassengerAgent agent : waiting) {
				if (!boarded.contains(agent.getId())) {
					entering.add(agent);
				}
			}

			double stopTime = handler.handleTransitStop(this.stop, now, new ArrayList<>(), entering,
					this.accessEgress, this.mobsimTrain);
			if (stopTime == 0.0) {
				return step;
			}
			now += stopTime;
		}
		return MAX_STEPS;
	}

	private PTPassengerAgent passenger(String id) {
		PTPassengerAgent agent = mock(PTPassengerAgent.class);
		when(agent.getId()).thenReturn(Id.createPersonId(id));
		return agent;
	}

}
