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

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** A chunk that the mod considers active, identified by dimension and chunk coordinates. */
public final class ActiveChunk
{
	@SerializedName("dimension")
	public String dimension;

	@SerializedName("x")
	public int x;

	@SerializedName("z")
	public int z;

	public ActiveChunk()
	{
	}

	public ActiveChunk(String dimension, int x, int z)
	{
		this.dimension = dimension;
		this.x = x;
		this.z = z;
	}

	public static ActiveChunk of(ResourceKey<Level> dimension, int x, int z)
	{
		return new ActiveChunk(dimension.identifier().toString(), x, z);
	}

	public boolean isValid()
	{
		return this.dimension != null && !this.dimension.isEmpty();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof ActiveChunk other))
		{
			return false;
		}
		return this.x == other.x && this.z == other.z && Objects.equals(this.dimension, other.dimension);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(this.dimension, this.x, this.z);
	}

	@Override
	public String toString()
	{
		return this.dimension + "[" + this.x + ", " + this.z + "]";
	}
}
