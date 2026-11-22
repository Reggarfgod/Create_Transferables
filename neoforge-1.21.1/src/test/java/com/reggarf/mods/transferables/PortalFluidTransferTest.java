package com.reggarf.mods.transferables;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.reggarf.mods.transferables.content.portal.fluids.PortalFluidTicker;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class PortalFluidTransferTest {

	@BeforeAll
	public static void setup() {
		try {
			Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
			field.setAccessible(true);
			field.setBoolean(null, true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testWaterTransferRespectsRateAndPreservesFluid() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(10000);

		FluidStack water = new FluidStack(Fluids.WATER, 5000);
		sourceTank.fill(water, IFluidHandler.FluidAction.EXECUTE);

		Assertions.assertEquals(5000, sourceTank.getFluidAmount());
		Assertions.assertEquals(0, destTank.getFluidAmount());

		// Test transfer at 32 mB/tick (corresponding to 64 RPM)
		int maxRate = 32;
		FluidStack transferred = PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), maxRate);

		Assertions.assertEquals(32, transferred.getAmount());
		Assertions.assertEquals(Fluids.WATER, transferred.getFluid());
		Assertions.assertEquals(4968, sourceTank.getFluidAmount());
		Assertions.assertEquals(32, destTank.getFluidAmount());

		// Total fluid conserved:
		Assertions.assertEquals(5000, sourceTank.getFluidAmount() + destTank.getFluidAmount());
	}

	@Test
	public void testLavaTransfer() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(10000);

		FluidStack lava = new FluidStack(Fluids.LAVA, 2000);
		sourceTank.fill(lava, IFluidHandler.FluidAction.EXECUTE);

		int maxRate = 16; // 32 RPM
		FluidStack transferred = PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), maxRate);

		Assertions.assertEquals(16, transferred.getAmount());
		Assertions.assertEquals(Fluids.LAVA, transferred.getFluid());
		Assertions.assertEquals(1984, sourceTank.getFluidAmount());
		Assertions.assertEquals(16, destTank.getFluidAmount());
		Assertions.assertEquals(2000, sourceTank.getFluidAmount() + destTank.getFluidAmount());
	}

	@Test
	public void testDestinationFullPreventsDrainAndLoss() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(1000);

		sourceTank.fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE);
		destTank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

		// Destination is full
		FluidStack transferred = PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), 32);

		Assertions.assertTrue(transferred.isEmpty());
		Assertions.assertEquals(5000, sourceTank.getFluidAmount());
		Assertions.assertEquals(1000, destTank.getFluidAmount());
	}

	@Test
	public void testPartialSpaceTransfersExactAmountWithoutLoss() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(1000);

		sourceTank.fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE);
		destTank.fill(new FluidStack(Fluids.WATER, 985), IFluidHandler.FluidAction.EXECUTE); // only 15 mB space left

		int maxRate = 64;
		FluidStack transferred = PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), maxRate);

		// Only 15 mB should be drained and filled
		Assertions.assertEquals(15, transferred.getAmount());
		Assertions.assertEquals(4985, sourceTank.getFluidAmount());
		Assertions.assertEquals(1000, destTank.getFluidAmount());
		Assertions.assertEquals(5985, sourceTank.getFluidAmount() + destTank.getFluidAmount());
	}

	@Test
	public void testSourceNearEmptyTransfersExactAvailable() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(10000);

		sourceTank.fill(new FluidStack(Fluids.WATER, 12), IFluidHandler.FluidAction.EXECUTE); // only 12 mB left

		int maxRate = 64;
		FluidStack transferred = PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), maxRate);

		Assertions.assertEquals(12, transferred.getAmount());
		Assertions.assertEquals(0, sourceTank.getFluidAmount());
		Assertions.assertEquals(12, destTank.getFluidAmount());
	}

	@Test
	public void testMultiTickTransferToCompletion() {
		FluidTank sourceTank = new FluidTank(10000);
		FluidTank destTank = new FluidTank(10000);

		sourceTank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

		int rate = 50; // 100 RPM
		int ticks = 0;
		while (!sourceTank.isEmpty() && ticks < 100) {
			PortalFluidTicker.transferFluid(List.of(sourceTank), List.of(destTank), rate);
			ticks++;
		}

		Assertions.assertEquals(20, ticks);
		Assertions.assertEquals(0, sourceTank.getFluidAmount());
		Assertions.assertEquals(1000, destTank.getFluidAmount());
	}
}
