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
import com.devgbx9.backstage.state.ActiveChunk;
import com.devgbx9.backstage.state.BackstageWorldState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast forwards the world by running real server ticks.
 * <p>
 * Only the chunks marked as active are force loaded before the run, so the rest of the world
 * is not even present in memory while the simulation happens.
 */
public final class SimulationEngine
{
	private SimulationEngine()
	{
	}

	public record Result(long requestedTicks, long executedTicks, long wallMillis, boolean completed)
	{
		public boolean simulatedSomething()
		{
			return this.executedTicks > 0L;
		}

		public double ticksPerSecond()
		{
			return this.wallMillis <= 0L ? 0.0D : (this.executedTicks * 1000.0D) / this.wallMillis;
		}
	}

	private static boolean running = false;

	public static boolean isRunning()
	{
		return running;
	}

	/**
	 * Runs {@code ticks} server ticks as fast as the machine allows.
	 *
	 * @return what actually happened, including the number of ticks really executed
	 */
	public static Result run(MinecraftServer server, long ticks, BackstageWorldState state)
	{
		if (ticks <= 0L)
		{
			return new Result(0L, 0L, 0L, true);
		}
		if (running)
		{
			Backstage.LOGGER.warn("[Backstage] A catch-up run is already in progress, skipping");
			return new Result(ticks, 0L, 0L, false);
		}

		long target = Math.min(ticks, BackstageConfig.MAX_TICKS_PER_RUN);
		if (target < ticks)
		{
			Backstage.LOGGER.warn("[Backstage] Capping the catch-up run at {} ticks ({} requested)", target, ticks);
		}

		running = true;
		List<ForcedChunk> forced = forceActiveChunks(server, state);
		boolean previousAutoSave = server.setAutoSave(false);
		long startNanos = System.nanoTime();
		long executed = 0L;
		long gameTimeBefore = gameTime(server);
		boolean completed = false;

		try
		{
			Backstage.LOGGER.info("[Backstage] Simulating {} ticks ({}) on {} active chunk(s)",
					target, formatTicks(target), knownActiveChunkCount(server, state));

			long budgetNanos = BackstageConfig.MAX_WALL_MILLIS * 1_000_000L;
			for (long i = 0L; i < target; i++)
			{
				tickLevels(server);
				executed++;
				if (i % BackstageConfig.BUDGET_CHECK_INTERVAL == 0 && System.nanoTime() - startNanos > budgetNanos)
				{
					Backstage.LOGGER.warn("[Backstage] Wall clock budget of {} ms reached after {} ticks, stopping",
							BackstageConfig.MAX_WALL_MILLIS, executed);
					break;
				}
			}
			completed = executed >= target;
		}
		catch (Throwable t)
		{
			Backstage.LOGGER.error("[Backstage] Catch-up simulation crashed after {} ticks", executed, t);
		}
		finally
		{
			running = false;
			releaseForcedChunks(server, forced);
			server.setAutoSave(previousAutoSave);
		}

		long wallMillis = (System.nanoTime() - startNanos) / 1_000_000L;
		long gameTimeAfter = gameTime(server);
		Result result = new Result(target, executed, wallMillis, completed);
		Backstage.LOGGER.info("[Backstage] Simulated {} ticks in {} ms ({} ticks/s), world tick counter {} -> {} (+{}), {}",
				result.executedTicks(), result.wallMillis(),
				String.format("%.0f", result.ticksPerSecond()),
				gameTimeBefore, gameTimeAfter, gameTimeAfter - gameTimeBefore,
				result.completed() ? "completed" : "partial");
		return result;
	}

	/**
	 * Ticks the worlds directly instead of ticking the server.
	 * An empty server pauses its worlds after a while (emptyTicks), which would silently skip the
	 * whole catch-up, and nothing server level (players, network, autosave) matters during it anyway.
	 */
	private static void tickLevels(MinecraftServer server)
	{
		for (ServerLevel level : server.getAllLevels())
		{
			level.tick(() -> true);
		}
	}

	private static long gameTime(MinecraftServer server)
	{
		try
		{
			return server.overworld().getLevelData().getGameTime();
		}
		catch (Throwable t)
		{
			return -1L;
		}
	}

	private record ForcedChunk(ServerLevel level, int x, int z)
	{
	}

	private static long knownActiveChunkCount(MinecraftServer server, BackstageWorldState state)
	{
		return state.activeChunks.stream().filter(chunk -> resolveLevel(server, chunk) != null).count();
	}

	private static List<ForcedChunk> forceActiveChunks(MinecraftServer server, BackstageWorldState state)
	{
		List<ForcedChunk> forced = new ArrayList<>();
		for (ActiveChunk chunk : state.activeChunks)
		{
			ServerLevel level = resolveLevel(server, chunk);
			if (level == null)
			{
				continue;
			}
			try
			{
				if (level.setChunkForced(chunk.x, chunk.z, true))
				{
					forced.add(new ForcedChunk(level, chunk.x, chunk.z));
				}
			}
			catch (Throwable t)
			{
				Backstage.LOGGER.warn("[Backstage] Could not force load {}", chunk, t);
			}
		}
		return forced;
	}

	private static void releaseForcedChunks(MinecraftServer server, List<ForcedChunk> forced)
	{
		for (ForcedChunk chunk : forced)
		{
			try
			{
				chunk.level().setChunkForced(chunk.x(), chunk.z(), false);
			}
			catch (Throwable t)
			{
				Backstage.LOGGER.warn("[Backstage] Could not release forced chunk {}, {}", chunk.x(), chunk.z(), t);
			}
		}
	}

	public static ServerLevel resolveLevel(MinecraftServer server, ActiveChunk chunk)
	{
		for (ServerLevel level : server.getAllLevels())
		{
			if (level.dimension().identifier().toString().equals(chunk.dimension))
			{
				return level;
			}
		}
		return null;
	}

	public static String formatTicks(long ticks)
	{
		long seconds = ticks / BackstageConfig.TICKS_PER_SECOND;
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long secs = seconds % 60L;
		if (hours > 0L)
		{
			return hours + "h " + minutes + "m " + secs + "s";
		}
		if (minutes > 0L)
		{
			return minutes + "m " + secs + "s";
		}
		return secs + "s";
	}
}
