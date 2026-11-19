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

package com.devgbx9.backstage;

import com.devgbx9.backstage.command.BackstageCommand;
import com.devgbx9.backstage.sim.BackstageEvents;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Backstage implements ModInitializer
{
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final String MOD_ID = "backstage";
	public static String MOD_VERSION = "unknown";
	public static String MOD_NAME = "unknown";

	@Override
	public void onInitialize()
	{
		ModMetadata metadata = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(RuntimeException::new).getMetadata();
		MOD_NAME = metadata.getName();
		MOD_VERSION = metadata.getVersion().getFriendlyString();

		BackstageEvents.register();
		BackstageCommand.register();

		LOGGER.info("[Backstage] {} {} loaded", MOD_NAME, MOD_VERSION);
	}
}
