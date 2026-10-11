package com.devtools;

import java.awt.Color;
import net.runelite.api.NPC;
import net.runelite.client.util.Text;

public class NpcHighlightEntry
{
	public String match = "";
	public int color = 0x00FFFF;
	public int ticks = 4;

	Color toColor()
	{
		return new Color(color);
	}

	boolean matches(NPC npc)
	{
		if (npc == null || match == null)
		{
			return false;
		}

		String trimmed = match.trim();
		if (trimmed.isEmpty())
		{
			return false;
		}

		if (isNumeric(trimmed))
		{
			try
			{
				return npc.getId() == Integer.parseInt(trimmed);
			}
			catch (NumberFormatException ex)
			{
				return false;
			}
		}

		String name = npc.getName();
		if (name == null || name.equals("null"))
		{
			return false;
		}

		return Text.removeTags(name).toLowerCase().contains(trimmed.toLowerCase());
	}

	private static boolean isNumeric(String value)
	{
		for (int i = 0; i < value.length(); i++)
		{
			if (!Character.isDigit(value.charAt(i)))
			{
				return false;
			}
		}
		return !value.isEmpty();
	}
}
