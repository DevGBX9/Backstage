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

package com.devgbx9.backstage.client;

import com.devgbx9.backstage.net.BackstagePackets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/** Puts the loading screen up while the server catches up, and takes it down afterwards. */
public class BackstageClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		ClientPlayNetworking.registerGlobalReceiver(BackstagePackets.SimulationStatus.TYPE,
				(payload, context) -> context.client().execute(() ->
				{
					Minecraft client = context.client();
					if (payload.running())
					{
						client.gui.setScreen(new BackstageLoadingScreen(payload.ticks()));
					}
					else if (client.gui.screen() instanceof BackstageLoadingScreen)
					{
						client.gui.setScreen(null);
					}
				}));
	}
}
