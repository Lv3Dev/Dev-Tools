package com.devtools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

@Singleton
public class DevToolsOverlay extends Overlay
{
	private final Client client;
	private final DevToolsPlugin plugin;

	@Inject
	private DevToolsOverlay(Client client, DevToolsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.MED);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client == null || graphics == null)
		{
			return null;
		}

		List<NpcHighlightEntry> highlights = plugin.getHighlights();
		if (highlights == null || highlights.isEmpty())
		{
			return null;
		}

		for (NPC npc : plugin.getTrackedNpcs())
		{
			if (npc == null)
			{
				continue;
			}

			NpcHighlightEntry match = plugin.findHighlight(npc);
			if (match == null)
			{
				continue;
			}

			Color color = match.toColor();
			Polygon tilePoly = npc.getCanvasTilePoly();
			if (tilePoly != null)
			{
				Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 50);
				OverlayUtil.renderPolygon(graphics, tilePoly, color, fill, new BasicStroke(2));
			}

			int remaining = plugin.getRemainingTicks(npc);
			String text = Integer.toString(remaining);
			Point textLocation = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + 20);
			if (textLocation != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLocation, text, color);
			}
		}

		return null;
	}
}
