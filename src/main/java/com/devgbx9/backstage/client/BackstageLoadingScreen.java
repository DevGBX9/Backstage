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

import com.devgbx9.backstage.sim.SimulationEngine;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A plain world loading screen shown while the server fast forwards the active chunks. */
public class BackstageLoadingScreen extends Screen
{
	private final long ticks;

	public BackstageLoadingScreen(long ticks)
	{
		super(Component.literal("[Backstage] Simulating the world..."));
		this.ticks = ticks;
	}

	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		graphics.centeredText(this.font, this.title, centerX, centerY - 20, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Component.literal("Catching up on " + SimulationEngine.formatTicks(this.ticks)
						+ " of world time. This can take a moment."),
				centerX, centerY, 0xFFA0A0A0);
	}
}
