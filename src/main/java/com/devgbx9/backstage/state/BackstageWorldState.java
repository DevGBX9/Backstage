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

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod remembers about a single world.
 * Stored in {@code <world>/backstage/state.json}.
 */
public class BackstageWorldState
{
	public static final int CURRENT_SCHEMA = 1;

	@SerializedName("schema")
	public int schema = CURRENT_SCHEMA;

	/** Wall clock time (epoch millis) at which the world was last closed. 0 if unknown. */
	@SerializedName("last_closed_epoch_millis")
	public long lastClosedEpochMillis = 0L;

	/** Human readable local time of the last close, e.g. {@code 2026-11-19 14:03:27}. */
	@SerializedName("last_closed_at")
	public String lastClosedAt = "";

	/** Human readable local time at which the world was opened again. */
	@SerializedName("last_opened_at")
	public String lastOpenedAt = "";

	/** How long the world was closed before it was opened again, in milliseconds. */
	@SerializedName("last_gap_millis")
	public long lastGapMillis = 0L;

	/** Ticks that were requested for the last catch-up run. */
	@SerializedName("last_requested_ticks")
	public long lastRequestedTicks = 0L;

	/** Ticks that were actually simulated for the last catch-up run. */
	@SerializedName("last_simulated_ticks")
	public long lastSimulatedTicks = 0L;

	/** Wall clock time (epoch millis) at which the last catch-up run finished. */
	@SerializedName("last_simulated_at_millis")
	public long lastSimulatedAtMillis = 0L;

	/** Lifetime total of simulated ticks. */
	@SerializedName("total_simulated_ticks")
	public long totalSimulatedTicks = 0L;

	@SerializedName("active_chunks")
	public List<ActiveChunk> activeChunks = new ArrayList<>();

	public boolean isPrimed()
	{
		return this.lastClosedEpochMillis > 0L;
	}

	public boolean hasActiveChunk(ActiveChunk chunk)
	{
		return this.activeChunks.contains(chunk);
	}

	public boolean addActiveChunk(ActiveChunk chunk)
	{
		if (this.hasActiveChunk(chunk) || this.activeChunks.size() >= 4096)
		{
			return false;
		}
		this.activeChunks.add(chunk);
		return true;
	}

	public boolean removeActiveChunk(ActiveChunk chunk)
	{
		return this.activeChunks.remove(chunk);
	}

	/** Drop obviously broken data so a hand edited file can never crash the world load. */
	public BackstageWorldState sanitized()
	{
		if (this.schema <= 0)
		{
			this.schema = CURRENT_SCHEMA;
		}
		if (this.activeChunks == null)
		{
			this.activeChunks = new ArrayList<>();
		}
		this.activeChunks.removeIf(chunk -> chunk == null || !chunk.isValid());
		return this;
	}
}
