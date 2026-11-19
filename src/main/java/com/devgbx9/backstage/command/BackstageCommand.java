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

package com.devgbx9.backstage.command;

import com.devgbx9.backstage.Backstage;
import com.devgbx9.backstage.sim.BackstageConfig;
import com.devgbx9.backstage.sim.BackstageEvents;
import com.devgbx9.backstage.sim.SimulationEngine;
import com.devgbx9.backstage.state.ActiveChunk;
import com.devgbx9.backstage.state.BackstageWorldState;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import com.mojang.brigadier.arguments.IntegerArgumentType;

public final class BackstageCommand
{
	private BackstageCommand()
	{
	}

	public static void register()
	{
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("backstage")
						.executes(context -> status(context.getSource()))
						.then(Commands.literal("trust").executes(context -> trust(context.getSource(), true)))
						.then(Commands.literal("untrust").executes(context -> trust(context.getSource(), false)))
						.then(Commands.literal("status").executes(context -> status(context.getSource())))
						.then(Commands.literal("list").executes(context -> list(context.getSource())))
						.then(Commands.literal("simulate")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
										.executes(context -> simulate(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
				));
	}

	private static int status(CommandSourceStack source)
	{
		BackstageWorldState state = BackstageEvents.state();
		if (state == null)
		{
			source.sendFailure(Component.literal("[Backstage] No world state loaded yet"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("[Backstage] Active chunks: " + state.activeChunks.size()), false);
		source.sendSuccess(() -> Component.literal("[Backstage] Last close: " + (state.lastClosedAt.isEmpty() ? "never" : state.lastClosedAt)), false);
		source.sendSuccess(() -> Component.literal("[Backstage] Last open: " + (state.lastOpenedAt.isEmpty() ? "never" : state.lastOpenedAt)), false);
		source.sendSuccess(() -> Component.literal("[Backstage] Last gap: " + SimulationEngine.formatTicks(BackstageConfig.ticksFor(state.lastGapMillis))), false);
		source.sendSuccess(() -> Component.literal("[Backstage] Last simulation: " + state.lastSimulatedTicks + " of " + state.lastRequestedTicks + " ticks"), false);
		source.sendSuccess(() -> Component.literal("[Backstage] Total simulated: " + state.totalSimulatedTicks + " ticks"), false);
		return 1;
	}

	private static int list(CommandSourceStack source)
	{
		BackstageWorldState state = BackstageEvents.state();
		if (state == null)
		{
			source.sendFailure(Component.literal("[Backstage] No world state loaded yet"));
			return 0;
		}
		if (state.activeChunks.isEmpty())
		{
			source.sendSuccess(() -> Component.literal("[Backstage] No active chunks. Stay 5 minutes in a chunk or use /backstage trust"), false);
			return 1;
		}
		for (ActiveChunk chunk : state.activeChunks)
		{
			source.sendSuccess(() -> Component.literal("[Backstage] - " + chunk), false);
		}
		return 1;
	}

	private static int trust(CommandSourceStack source, boolean trust)
	{
		BackstageWorldState state = BackstageEvents.state();
		if (state == null)
		{
			source.sendFailure(Component.literal("[Backstage] No world state loaded yet"));
			return 0;
		}
		ServerPlayer player = source.getPlayer();
		if (player == null)
		{
			source.sendFailure(Component.literal("[Backstage] This command needs a player"));
			return 0;
		}
		ActiveChunk chunk = new ActiveChunk(
				source.getLevel().dimension().identifier().toString(),
				player.chunkPosition().x(),
				player.chunkPosition().z()
		);
		boolean changed = trust ? state.addActiveChunk(chunk) : state.removeActiveChunk(chunk);
		if (changed && BackstageEvents.store() != null)
		{
			BackstageEvents.store().save(state);
		}
		if (trust)
		{
			source.sendSuccess(() -> Component.literal((changed ? "[Backstage] Chunk is now active: " : "[Backstage] Chunk was already active: ") + chunk), false);
		}
		else
		{
			source.sendSuccess(() -> Component.literal((changed ? "[Backstage] Chunk is no longer active: " : "[Backstage] Chunk was not active: ") + chunk), false);
		}
		return changed ? 1 : 0;
	}

	private static int simulate(CommandSourceStack source, int seconds)
	{
		BackstageWorldState state = BackstageEvents.state();
		if (state == null)
		{
			source.sendFailure(Component.literal("[Backstage] No world state loaded yet"));
			return 0;
		}
		if (SimulationEngine.isRunning())
		{
			source.sendFailure(Component.literal("[Backstage] A simulation is already running"));
			return 0;
		}
		long ticks = (long) seconds * BackstageConfig.TICKS_PER_SECOND;
		source.sendSuccess(() -> Component.literal("[Backstage] Simulating " + SimulationEngine.formatTicks(ticks) + " (" + ticks + " ticks)..."), false);
		SimulationEngine.Result result = SimulationEngine.run(source.getServer(), ticks, state);
		state.lastRequestedTicks = result.requestedTicks();
		state.lastSimulatedTicks = result.executedTicks();
		state.totalSimulatedTicks += result.executedTicks();
		state.lastSimulatedAtMillis = System.currentTimeMillis();
		if (BackstageEvents.store() != null)
		{
			BackstageEvents.store().save(state);
		}
		source.sendSuccess(() -> Component.literal(String.format("[Backstage] Done: %d ticks in %d ms (%s ticks/s)%s",
				result.executedTicks(), result.wallMillis(),
				String.format("%.0f", result.ticksPerSecond()),
				result.completed() ? "" : ", partial (budget reached)")), false);
		Backstage.LOGGER.info("[Backstage] Manual simulation by {}: {} ticks", source.getTextName(), result.executedTicks());
		return 1;
	}
}
