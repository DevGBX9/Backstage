/*
 * This file is part of the Backstage project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Backstage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Backstage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Backstage.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.devgbx9.backstage.sim;

import com.devgbx9.backstage.Backstage;
import com.devgbx9.backstage.net.BackstagePackets;
import com.devgbx9.backstage.state.ActiveChunk;
import com.devgbx9.backstage.state.BackstageWorldState;
import com.devgbx9.backstage.state.WorldStateStore;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hooks the world lifecycle: record the close time, catch up on the next open, track active chunks. */
public final class BackstageEvents
{
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** The loading screen needs a couple of ticks to reach the client before the server thread freezes. */
	private static final long OVERLAY_LEAD_MILLIS = 100L;

	private record Dwell(String dimension, int x, int z, int ticks)
	{
	}

	private record PendingSimulation(long ticks, long runAtMillis)
	{
	}

	private static final Map<UUID, Dwell> DWELLS = new HashMap<>();

	private static WorldStateStore store;
	private static BackstageWorldState state;
	private static PendingSimulation pending;

	private BackstageEvents()
	{
	}

	public static void register()
	{
		ServerLifecycleEvents.SERVER_STARTED.register(BackstageEvents::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(BackstageEvents::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(BackstageEvents::onServerTick);
	}

	public static BackstageWorldState state()
	{
		return state;
	}

	public static WorldStateStore store()
	{
		return store;
	}

	/**
	 * Asks for a manual catch-up run. The run itself starts a couple of ticks later, so the loading
	 * screen reaches the clients before the server thread gets busy.
	 *
	 * @return false when another run is already on its way
	 */
	public static boolean requestSimulation(MinecraftServer server, long ticks)
	{
		if (pending != null || SimulationEngine.isRunning())
		{
			return false;
		}
		pending = new PendingSimulation(ticks, System.currentTimeMillis() + OVERLAY_LEAD_MILLIS);
		BackstagePackets.broadcast(server, true, ticks);
		return true;
	}

	private static String now()
	{
		return LocalDateTime.now().format(TIME_FORMAT);
	}

	private static void onServerStarted(MinecraftServer server)
	{
		DWELLS.clear();
		pending = null;
		try
		{
			store = WorldStateStore.open(server);
			state = store.load();

			long nowMillis = System.currentTimeMillis();
			long gapMillis = 0L;
			if (state.isPrimed() && nowMillis > state.lastClosedEpochMillis)
			{
				gapMillis = nowMillis - state.lastClosedEpochMillis;
				if (gapMillis > BackstageConfig.MAX_TRUSTED_GAP_MILLIS)
				{
					Backstage.LOGGER.warn("[Backstage] Ignoring a {} gap, the system clock probably changed",
							SimulationEngine.formatTicks(BackstageConfig.ticksFor(gapMillis)));
					gapMillis = 0L;
				}
			}

			state.lastGapMillis = gapMillis;
			state.lastOpenedAt = now();
			long requested = BackstageConfig.ticksFor(gapMillis);
			state.lastRequestedTicks = requested;
			store.save(state);

			Backstage.LOGGER.info("[Backstage] World opened. Last close: {}. Offline for {} -> {} ticks",
					state.lastClosedAt.isEmpty() ? "never" : state.lastClosedAt,
					SimulationEngine.formatTicks(requested), requested);

			if (requested > 0L)
			{
				SimulationEngine.Result result = SimulationEngine.run(server, requested, state);
				state.lastSimulatedTicks = result.executedTicks();
				state.totalSimulatedTicks += result.executedTicks();
				state.lastSimulatedAtMillis = System.currentTimeMillis();

				// the gap is consumed: the next close starts a fresh cycle
				state.lastClosedEpochMillis = 0L;
				store.save(state);
			}
		}
		catch (Throwable t)
		{
			Backstage.LOGGER.error("[Backstage] Failed to handle world start", t);
		}
	}

	private static void onServerStopping(MinecraftServer server)
	{
		try
		{
			if (store == null || state == null)
			{
				return;
			}
			state.lastClosedEpochMillis = System.currentTimeMillis();
			state.lastClosedAt = now();
			store.save(state);
			Backstage.LOGGER.info("[Backstage] World closed at {}", state.lastClosedAt);
		}
		catch (Throwable t)
		{
			Backstage.LOGGER.error("[Backstage] Failed to record the world close time", t);
		}
		finally
		{
			DWELLS.clear();
			pending = null;
			store = null;
			state = null;
		}
	}

	private static void onServerTick(MinecraftServer server)
	{
		if (state == null || store == null)
		{
			return;
		}

		if (pending != null && System.currentTimeMillis() >= pending.runAtMillis())
		{
			PendingSimulation request = pending;
			pending = null;
			runSimulation(server, request.ticks());
		}

		boolean changed = false;
		for (ServerLevel level : server.getAllLevels())
		{
			for (ServerPlayer player : level.players())
			{
				changed |= trackDwell(player, level);
			}
		}
		if (changed)
		{
			store.save(state);
		}
	}

	private static void runSimulation(MinecraftServer server, long ticks)
	{
		try
		{
			SimulationEngine.Result result = SimulationEngine.run(server, ticks, state);
			state.lastRequestedTicks = result.requestedTicks();
			state.lastSimulatedTicks = result.executedTicks();
			state.totalSimulatedTicks += result.executedTicks();
			state.lastSimulatedAtMillis = System.currentTimeMillis();
			store.save(state);

			server.getPlayerList().broadcastSystemMessage(Component.literal(String.format(
					"[Backstage] Done: %d ticks in %d ms (%s ticks/s)%s",
					result.executedTicks(), result.wallMillis(),
					String.format("%.0f", result.ticksPerSecond()),
					result.completed() ? "" : ", partial (budget reached)")), false);
		}
		catch (Throwable t)
		{
			Backstage.LOGGER.error("[Backstage] Manual simulation crashed", t);
		}
		finally
		{
			BackstagePackets.broadcast(server, false, 0L);
		}
	}

	/** Marks a chunk active once its player stayed in it for five consecutive minutes. */
	private static boolean trackDwell(ServerPlayer player, ServerLevel level)
	{
		String dimension = level.dimension().identifier().toString();
		int x = player.chunkPosition().x();
		int z = player.chunkPosition().z();
		UUID id = player.getUUID();

		Dwell previous = DWELLS.get(id);
		int ticks = 1;
		if (previous != null && previous.dimension().equals(dimension) && previous.x() == x && previous.z() == z)
		{
			ticks = previous.ticks() + 1;
		}
		DWELLS.put(id, new Dwell(dimension, x, z, ticks));

		if (ticks == BackstageConfig.DWELL_TICKS && state.addActiveChunk(new ActiveChunk(dimension, x, z)))
		{
			Backstage.LOGGER.info("[Backstage] Chunk {} became active (player {} stayed 5 minutes)", new ActiveChunk(dimension, x, z), player.getName().getString());
			return true;
		}
		return false;
	}
}
