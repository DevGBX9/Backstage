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

package com.devgbx9.backstage.mixins;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens the block event queue of a level to the simulation.
 * <p>
 * Pistons, dispensers, droppers, note blocks and chests do their work through block events, and
 * vanilla only drains that queue while it ticks the whole level. A catch-up run has to drain it
 * itself, otherwise a piston that was powered during the simulation never moves.
 */
@Mixin(ServerLevel.class)
public interface ServerLevelInvoker
{
	@Invoker("runBlockEvents")
	void backstage$runBlockEvents();
}
