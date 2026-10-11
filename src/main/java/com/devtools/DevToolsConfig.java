package com.devtools;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(DevToolsConfig.GROUP)
public interface DevToolsConfig extends Config
{
	String GROUP = "devtools-plugin";

	@ConfigSection(
		name = "Debug 1-tick pray",
		description = "Local-only prayer flick helper. Disabled by default.",
		position = 0
	)
	String oneTickPraySection = "oneTickPraySection";

	@ConfigItem(
		keyName = "oneTickPrayEnabled",
		name = "Enable 1-tick pray",
		description = "Automatically activating prayers may violate Jagex third-party client rules and is for local debugging only.",
		warning = "Automatically activating prayers may violate Jagex third-party client rules and is for local debugging only.",
		section = oneTickPraySection,
		position = 0
	)
	default boolean oneTickPrayEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "oneTickPrayHotkey",
		name = "1-tick pray hotkey",
		description = "Hold or toggle 1-tick flicking of the prayer under the mouse. Default is backtick (`).",
		section = oneTickPraySection,
		position = 1
	)
	default Keybind oneTickPrayHotkey()
	{
		return new Keybind(KeyEvent.VK_BACK_QUOTE, 0);
	}

	@ConfigItem(
		keyName = "oneTickPrayMode",
		name = "1-tick pray mode",
		description = "Hold: flick while the hotkey is held. Toggle: press once to start, press again to stop.",
		section = oneTickPraySection,
		position = 2
	)
	default OneTickPrayMode oneTickPrayMode()
	{
		return OneTickPrayMode.HOLD;
	}
}
