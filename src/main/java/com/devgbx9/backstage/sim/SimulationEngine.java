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

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Fast forwards the world by running real, vanilla block ticks, but only on the chunks that the
 * mod considers active.
 * <p>
 * Instead of ticking whole levels (which would advance the entire world) every simulated tick does
 * exactly what vanilla does for the active chunks: random ticks through {@link ServerLevel#tickChunk},
 * scheduled block and fluid ticks, block entities and entities. Everything else is left untouched,
 * so a one hour catch-up costs about the same as ticking two chunks for one hour, not a whole world.
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
	 * Runs {@code ticks} simulated ticks as fast as the machine allows.
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
		boolean previousAutoSave = server.setAutoSave(false);
		List<ForcedChunk> forced = new ArrayList<>();
		List<LevelPlan> plans = new ArrayList<>();
		long startNanos = System.nanoTime();
		long executed = 0L;
		long gameTimeBefore = gameTime(server);
		boolean completed = false;

		try
		{
			plans = prepare(server, state, forced);

			if (plans.isEmpty())
			{
				Backstage.LOGGER.warn("[Backstage] No active chunk could be loaded, nothing to simulate");
			}
			else
			{
				Backstage.LOGGER.info("[Backstage] Simulating {} ticks ({}) on {} active chunk(s) in {} dimension(s)",
						target, formatTicks(target), countChunks(plans), plans.size());

				long budgetNanos = BackstageConfig.MAX_WALL_MILLIS * 1_000_000L;
				for (long i = 0L; i < target; i++)
				{
					for (LevelPlan plan : plans)
					{
						tickPlan(plan, i);
					}
					// the day and night cycle lives in the server clocks, one call per simulated tick keeps it in sync
					server.clockManager().tick();
					executed++;
					if ((i & (BackstageConfig.BUDGET_CHECK_INTERVAL - 1)) == 0 && System.nanoTime() - startNanos > budgetNanos)
					{
						Backstage.LOGGER.warn("[Backstage] Wall clock budget of {} ms reached after {} ticks, stopping",
								BackstageConfig.MAX_WALL_MILLIS, executed);
						break;
					}
				}

				// let the chunk system digest everything the simulation scheduled before the player continues
				for (LevelPlan plan : plans)
				{
					plan.level().getChunkSource().tick(() -> true, false);
				}
				completed = executed >= target;
			}
		}
		catch (Throwable t)
		{
			Backstage.LOGGER.error("[Backstage] Catch-up simulation crashed after {} ticks", executed, t);
		}
		finally
		{
			releaseForcedChunks(forced);
			server.setAutoSave(previousAutoSave);
			running = false;
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

	private static long countChunks(List<LevelPlan> plans)
	{
		long total = 0L;
		for (LevelPlan plan : plans)
		{
			total += plan.chunks().size();
		}
		return total;
	}

	/** One tick of a single dimension, limited to its active chunks. */
	private static void tickPlan(LevelPlan plan, long tickIndex)
	{
		ServerLevel level = plan.level();

		long gameTime = level.getLevelData().getGameTime() + 1L;
		((ServerLevelData) level.getLevelData()).setGameTime(gameTime);

		int randomTickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
		for (LevelChunk chunk : plan.chunks())
		{
			level.tickChunk(chunk, randomTickSpeed);
		}

		level.getBlockTicks().tick(gameTime, BackstageConfig.MAX_SCHEDULED_TICKS, plan.blockTicker());
		level.getFluidTicks().tick(gameTime, BackstageConfig.MAX_SCHEDULED_TICKS, plan.fluidTicker());

		for (LevelChunk chunk : plan.chunks())
		{
			tickBlockEntities(level, chunk);
		}

		tickEntities(level, plan, tickIndex);
	}

	private static void tickBlockEntities(ServerLevel level, LevelChunk chunk)
	{
		if (chunk.getBlockEntities().isEmpty())
		{
			return;
		}
		for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values()))
		{
			if (blockEntity.isRemoved())
			{
				continue;
			}
			tickBlockEntity(level, blockEntity, blockEntity.getBlockState());
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void tickBlockEntity(ServerLevel level, BlockEntity blockEntity, BlockState state)
	{
		BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(level, (BlockEntityType) blockEntity.getType());
		if (ticker != null)
		{
			ticker.tick(level, blockEntity.getBlockPos(), state, blockEntity);
		}
	}

	private static void tickEntities(ServerLevel level, LevelPlan plan, long tickIndex)
	{
		if (tickIndex % BackstageConfig.ENTITY_REFRESH_TICKS == 0L)
		{
			plan.entities().clear();
			level.getEntities(EntityTypeTest.forClass(Entity.class),
					entity -> plan.positions().contains(entity.chunkPosition().pack()), plan.entities());
		}
		for (Entity entity : plan.entities())
		{
			if (entity.isRemoved() || entity instanceof ServerPlayer)
			{
				continue;
			}
			if (!plan.positions().contains(entity.chunkPosition().pack()) || entity.getVehicle() != null)
			{
				continue;
			}
			level.tickNonPassenger(entity);
		}
	}

	/** Copies the vanilla private handlers, they are not reachable from outside the level. */
	private static void tickBlock(ServerLevel level, BlockPos pos, Block type)
	{
		BlockState state = level.getBlockState(pos);
		if (state.is(type))
		{
			state.tick(level, pos, level.getRandom());
		}
	}

	private static void tickFluid(ServerLevel level, BlockPos pos, Fluid type)
	{
		BlockState state = level.getBlockState(pos);
		if (state.getFluidState().is(type))
		{
			state.getFluidState().tick(level, pos, state);
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

	private record LevelPlan(ServerLevel level, List<LevelChunk> chunks, Set<Long> positions, List<Entity> entities,
			BiConsumer<BlockPos, Block> blockTicker, BiConsumer<BlockPos, Fluid> fluidTicker)
	{
	}

	/**
	 * Force loads exactly the active chunks and resolves them to real {@link LevelChunk}s.
	 * Anything that cannot be resolved is skipped instead of quietly ticking the whole world.
	 */
	private static List<LevelPlan> prepare(MinecraftServer server, BackstageWorldState state, List<ForcedChunk> forced)
	{
		Map<ServerLevel, List<ActiveChunk>> byLevel = new LinkedHashMap<>();
		for (ActiveChunk chunk : state.activeChunks)
		{
			ServerLevel level = resolveLevel(server, chunk);
			if (level == null)
			{
				Backstage.LOGGER.warn("[Backstage] Ignoring active chunk in unknown dimension {}", chunk);
				continue;
			}
			byLevel.computeIfAbsent(level, ignored -> new ArrayList<>()).add(chunk);
		}

		List<LevelPlan> plans = new ArrayList<>();
		for (Map.Entry<ServerLevel, List<ActiveChunk>> entry : byLevel.entrySet())
		{
			ServerLevel level = entry.getKey();
			for (ActiveChunk chunk : entry.getValue())
			{
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

			// pushes the new tickets through the distance manager and loads the chunks right away
			level.getChunkSource().tick(() -> true, false);

			List<LevelChunk> chunks = new ArrayList<>();
			Set<Long> positions = new HashSet<>();
			for (ActiveChunk chunk : entry.getValue())
			{
				try
				{
					ChunkAccess access = level.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true);
					if (access instanceof LevelChunk levelChunk)
					{
						chunks.add(levelChunk);
						positions.add(levelChunk.getPos().pack());
					}
				}
				catch (Throwable t)
				{
					Backstage.LOGGER.warn("[Backstage] Could not load {}", chunk, t);
				}
			}

			if (chunks.isEmpty())
			{
				continue;
			}
			plans.add(new LevelPlan(level, chunks, positions, new ArrayList<>(),
					(pos, block) -> tickBlock(level, pos, block),
					(pos, fluid) -> tickFluid(level, pos, fluid)));
		}
		return plans;
	}

	private static void releaseForcedChunks(List<ForcedChunk> forced)
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
