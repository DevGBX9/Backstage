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

package com.devgbx9.backstage.net;

import com.devgbx9.backstage.Backstage;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells the client when a catch-up run starts and ends, so it can put a loading screen up instead of
 * showing the world standing still.
 * <p>
 * Clients without the mod are simply skipped, {@link ServerPlayNetworking#canSend} decides that.
 */
public final class BackstagePackets
{
	private BackstagePackets()
	{
	}

	public record SimulationStatus(boolean running, long ticks) implements CustomPacketPayload
	{
		public static final CustomPacketPayload.Type<SimulationStatus> TYPE =
				new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Backstage.MOD_ID, "simulation_status"));

		public static final StreamCodec<RegistryFriendlyByteBuf, SimulationStatus> CODEC = StreamCodec.of(
				(buf, payload) ->
				{
					buf.writeBoolean(payload.running());
					buf.writeVarLong(payload.ticks());
				},
				buf -> new SimulationStatus(buf.readBoolean(), buf.readVarLong())
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
		{
			return TYPE;
		}
	}

	public static void register()
	{
		PayloadTypeRegistry.clientboundPlay().register(SimulationStatus.TYPE, SimulationStatus.CODEC);
	}

	public static void broadcast(MinecraftServer server, boolean running, long ticks)
	{
		SimulationStatus payload = new SimulationStatus(running, ticks);
		for (ServerPlayer player : server.getPlayerList().getPlayers())
		{
			if (ServerPlayNetworking.canSend(player, SimulationStatus.TYPE))
			{
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
