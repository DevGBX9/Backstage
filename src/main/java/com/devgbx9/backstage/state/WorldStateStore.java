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

package com.devgbx9.backstage.state;

import com.devgbx9.backstage.Backstage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes {@code <world>/backstage/state.json}. */
public final class WorldStateStore
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FOLDER = "backstage";
	private static final String FILE_NAME = "state.json";

	private final Path file;

	private WorldStateStore(Path file)
	{
		this.file = file;
	}

	public static WorldStateStore open(MinecraftServer server) throws IOException
	{
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
		Files.createDirectories(dir);
		return new WorldStateStore(dir.resolve(FILE_NAME));
	}

	public Path file()
	{
		return this.file;
	}

	public BackstageWorldState load()
	{
		if (!Files.isRegularFile(this.file))
		{
			return new BackstageWorldState();
		}
		try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8))
		{
			BackstageWorldState state = GSON.fromJson(reader, BackstageWorldState.class);
			return state == null ? new BackstageWorldState() : state.sanitized();
		}
		catch (Exception e)
		{
			Backstage.LOGGER.warn("[Backstage] Failed to read {}, starting from a clean state", this.file, e);
			return new BackstageWorldState();
		}
	}

	public void save(BackstageWorldState state)
	{
		try
		{
			Files.createDirectories(this.file.getParent());
			Path tmp = this.file.resolveSibling(FILE_NAME + ".tmp");
			Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
			try
			{
				Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException atomicUnsupported)
			{
				Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (Exception e)
		{
			Backstage.LOGGER.error("[Backstage] Failed to write {}", this.file, e);
		}
	}
}
