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

/** Tunables of the catch-up simulation. */
public final class BackstageConfig
{
	private BackstageConfig()
	{
	}

	/** Minecraft runs at a fixed 20 ticks per second. */
	public static final int TICKS_PER_SECOND = 20;

	/** Length of a single game tick in milliseconds. */
	public static final long TICK_MILLIS = 50L;

	/** A chunk becomes active after a player stayed in it for this many ticks (5 minutes). */
	public static final int DWELL_TICKS = 5 * 60 * TICKS_PER_SECOND;

	/** Never simulate more than this many ticks in a single catch-up run (24 in-game hours). */
	public static final long MAX_TICKS_PER_RUN = 24L * 60L * 60L * TICKS_PER_SECOND;

	/** Hard wall clock budget for a single catch-up run, so world loading can never hang forever. */
	public static final long MAX_WALL_MILLIS = 30_000L;

	/** How often (in ticks) the loop checks the wall clock budget. */
	public static final int BUDGET_CHECK_INTERVAL = 64;

	/** Upper bound of scheduled block or fluid ticks processed in a single simulated tick, like vanilla. */
	public static final int MAX_SCHEDULED_TICKS = 65536;

	/** How often (in simulated ticks) the engine rescans the entities of the active chunks. */
	public static final int ENTITY_REFRESH_TICKS = 20;

	/** Ticks longer than this are considered the result of a system clock change and ignored. */
	public static final long MAX_TRUSTED_GAP_MILLIS = 30L * 24L * 60L * 60L * 1000L;

	public static long ticksFor(long gapMillis)
	{
		if (gapMillis <= 0L)
		{
			return 0L;
		}
		return gapMillis / TICK_MILLIS;
	}
}
