package com.devtools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Deque;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Dev Tools",
	description = "Development overlays, NPC recording, and timing tools for plugin work",
	tags = {"debug", "dev", "tools"},
	internalName = "dev-tools"
)
public class DevToolsPlugin extends Plugin implements KeyListener
{
	private static final String HIGHLIGHTS_KEY = "npcHighlights";
	private static final Type HIGHLIGHT_LIST_TYPE = new TypeToken<ArrayList<NpcHighlightEntry>>()
	{
	}.getType();
	private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final int[] PRAYER_BOOK_WIDGETS = {
		InterfaceID.Prayerbook.PRAYER1, InterfaceID.Prayerbook.PRAYER2, InterfaceID.Prayerbook.PRAYER3,
		InterfaceID.Prayerbook.PRAYER4, InterfaceID.Prayerbook.PRAYER5, InterfaceID.Prayerbook.PRAYER6,
		InterfaceID.Prayerbook.PRAYER7, InterfaceID.Prayerbook.PRAYER8, InterfaceID.Prayerbook.PRAYER9,
		InterfaceID.Prayerbook.PRAYER10, InterfaceID.Prayerbook.PRAYER11, InterfaceID.Prayerbook.PRAYER12,
		InterfaceID.Prayerbook.PRAYER13, InterfaceID.Prayerbook.PRAYER14, InterfaceID.Prayerbook.PRAYER15,
		InterfaceID.Prayerbook.PRAYER16, InterfaceID.Prayerbook.PRAYER17, InterfaceID.Prayerbook.PRAYER18,
		InterfaceID.Prayerbook.PRAYER19, InterfaceID.Prayerbook.PRAYER20, InterfaceID.Prayerbook.PRAYER21,
		InterfaceID.Prayerbook.PRAYER22, InterfaceID.Prayerbook.PRAYER23, InterfaceID.Prayerbook.PRAYER24,
		InterfaceID.Prayerbook.PRAYER25, InterfaceID.Prayerbook.PRAYER26, InterfaceID.Prayerbook.PRAYER27,
		InterfaceID.Prayerbook.PRAYER28, InterfaceID.Prayerbook.PRAYER29, InterfaceID.Prayerbook.PRAYER30
	};
	private static final int[] PRAYER_ORB_WIDGETS = {
		InterfaceID.Orbs.PRAYERBUTTON,
		InterfaceID.OrbsNomap.PRAYERBUTTON,
		InterfaceID.OrbsOsm.PRAYERBUTTON,
		InterfaceID.OrbsOsmNomap.PRAYERBUTTON
	};
	private static final int[] DEFAULT_HIGHLIGHT_COLORS = {
		0x00FFFF, 0xFF8800, 0xFF44AA, 0x44FF88, 0x8888FF
	};

	@Inject
	private Client client;

	@Inject
	private DevToolsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	@Inject
	private DevToolsPanel panel;

	@Inject
	private DevToolsOverlay overlay;

	private NavigationButton navButton;
	private volatile List<NpcHighlightEntry> highlights = new ArrayList<>();
	private final Map<Integer, NPC> trackedNpcs = new HashMap<>();
	private final Map<Integer, Integer> remainingTicks = new HashMap<>();
	private final Map<Integer, Integer> lastAnimations = new HashMap<>();
	private final Set<Integer> resetThisTick = new HashSet<>();

	private volatile boolean recording;
	private volatile String recordFilter = "";
	private NPC recordedNpc;
	private int lastRecordedAnim = Integer.MIN_VALUE;
	private int lastRecordedAnimStartTick = -1;
	private int lastRelevantAnimId = -1;
	private int lastRelevantAnimTick = -1;
	private final List<String> fullLog = new ArrayList<>();
	private final Set<Projectile> loggedProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());

	private boolean prayKeyHeld;
	private boolean prayToggleOn;
	private Widget togglePrayWidget;
	private boolean prayKeyDown;

	@Override
	protected void startUp()
	{
		loadHighlights();
		panel.init();
		overlayManager.add(overlay);

		navButton = NavigationButton.builder()
			.tooltip("Dev Tools")
			.icon(createIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		keyManager.registerKeyListener(this);

		if (client != null && client.getGameState() == GameState.LOGGED_IN)
		{
			scanNpcs();
		}

		log.debug("Dev Tools started");
	}

	@Override
	protected void shutDown()
	{
		stopRecording();
		stopPrayFlick();
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(overlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		trackedNpcs.clear();
		remainingTicks.clear();
		lastAnimations.clear();
		loggedProjectiles.clear();
		log.debug("Dev Tools stopped");
	}

	@Provides
	DevToolsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DevToolsConfig.class);
	}

	List<NpcHighlightEntry> getHighlights()
	{
		return highlights;
	}

	Collection<NPC> getTrackedNpcs()
	{
		return trackedNpcs.values();
	}

	boolean isRecording()
	{
		return recording;
	}

	void setRecordFilter(String filter)
	{
		this.recordFilter = filter == null ? "" : filter;
	}

	NpcHighlightEntry findHighlight(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}
		for (NpcHighlightEntry entry : highlights)
		{
			if (entry != null && entry.matches(npc))
			{
				return entry;
			}
		}
		return null;
	}

	int getRemainingTicks(NPC npc)
	{
		if (npc == null)
		{
			return 0;
		}
		Integer remaining = remainingTicks.get(npc.getIndex());
		if (remaining != null)
		{
			return remaining;
		}
		NpcHighlightEntry entry = findHighlight(npc);
		return entry == null ? 0 : entry.ticks;
	}

	void startRecording(String name)
	{
		recordFilter = name == null ? "" : name;
		recording = true;
		recordedNpc = null;
		lastRecordedAnim = Integer.MIN_VALUE;
		lastRecordedAnimStartTick = currentTick();
		lastRelevantAnimId = -1;
		lastRelevantAnimTick = -1;
		loggedProjectiles.clear();
		SwingUtilities.invokeLater(() ->
		{
			panel.setRecordingState(true);
			panel.showNoNpc();
		});
	}

	void stopRecording()
	{
		if (recording)
		{
			flushHeldAnimation();
		}
		recording = false;
		recordedNpc = null;
		SwingUtilities.invokeLater(() -> panel.setRecordingState(false));
	}

	void clearLog()
	{
		fullLog.clear();
		SwingUtilities.invokeLater(() ->
		{
			panel.setLogLines(fullLog);
			panel.setStatus("Log cleared");
		});
	}

	void exportLog()
	{
		try
		{
			Filepath dir = getPluginDirectory();
			if (dir != null && !dir.exists())
			{
				dir.createDirectories();
			}
			String filename = "devtools-npc-log-" + LocalDateTime.now().format(EXPORT_TIME) + ".txt";
			Filepath file = dir.join(filename);
			try (BufferedWriter writer = file.openBufferedWriter())
			{
				for (String line : fullLog)
				{
					writer.write(line);
					writer.newLine();
				}
			}
			SwingUtilities.invokeLater(() -> panel.setStatus("Exported " + filename));
		}
		catch (IOException ex)
		{
			log.debug("Failed to export log", ex);
			SwingUtilities.invokeLater(() -> panel.setStatus("Export failed"));
		}
	}

	void addHighlight()
	{
		NpcHighlightEntry entry = new NpcHighlightEntry();
		entry.color = DEFAULT_HIGHLIGHT_COLORS[highlights.size() % DEFAULT_HIGHLIGHT_COLORS.length];
		List<NpcHighlightEntry> next = new ArrayList<>(highlights);
		next.add(entry);
		highlights = next;
		saveHighlights();
		SwingUtilities.invokeLater(panel::rebuildHighlightRows);
	}

	void removeHighlight(NpcHighlightEntry entry)
	{
		List<NpcHighlightEntry> next = new ArrayList<>(highlights);
		next.remove(entry);
		highlights = next;
		saveHighlights();
		SwingUtilities.invokeLater(panel::rebuildHighlightRows);
	}

	void saveHighlights()
	{
		configManager.setConfiguration(DevToolsConfig.GROUP, HIGHLIGHTS_KEY, gson.toJson(highlights));
	}

	private void loadHighlights()
	{
		String json = configManager.getConfiguration(DevToolsConfig.GROUP, HIGHLIGHTS_KEY);
		if (json == null || json.isEmpty())
		{
			highlights = new ArrayList<>();
			return;
		}
		try
		{
			List<NpcHighlightEntry> loaded = gson.fromJson(json, HIGHLIGHT_LIST_TYPE);
			highlights = loaded == null ? new ArrayList<>() : loaded;
		}
		catch (RuntimeException ex)
		{
			log.debug("Failed to load highlight list", ex);
			highlights = new ArrayList<>();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event == null || event.getGameState() == null)
		{
			return;
		}
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			trackedNpcs.clear();
			remainingTicks.clear();
			lastAnimations.clear();
			recordedNpc = null;
			stopPrayFlick();
			return;
		}
		scanNpcs();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event == null || event.getNpc() == null)
		{
			return;
		}
		trackNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event == null || event.getNpc() == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		int index = npc.getIndex();
		trackedNpcs.remove(index);
		remainingTicks.remove(index);
		lastAnimations.remove(index);
		if (recordedNpc == npc)
		{
			recordedNpc = null;
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (event == null || event.getNpc() == null)
		{
			return;
		}
		trackNpc(event.getNpc());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		updateHighlightCountdowns();
		updateRecording();
		flickPrayerIfNeeded();
		resetThisTick.clear();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event == null || event.getActor() == null)
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			handleHighlightAnimation(npc);
			if (recording && isRecordedNpc(npc))
			{
				handleRecordedAnimation(npc);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!recording || event == null || client == null)
		{
			return;
		}

		Actor actor = event.getActor();
		Player player = client.getLocalPlayer();
		if (actor == null || player == null || actor != player)
		{
			return;
		}

		NPC npc = resolveRecordedNpc();
		if (npc == null || !isInteractingWithPlayer(npc, player))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat == null)
		{
			return;
		}

		int tick = currentTick();
		int damage = hitsplat.getAmount();
		if (lastRelevantAnimTick >= 0)
		{
			int delta = tick - lastRelevantAnimTick;
			appendLog("Player received hitsplat at tick " + tick + " (" + damage + "), "
				+ delta + " ticks after animation " + lastRelevantAnimId);
		}
		else
		{
			appendLog("Player received hitsplat at tick " + tick + " (" + damage + ")");
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (!recording || event == null)
		{
			return;
		}
		Projectile projectile = event.getProjectile();
		if (projectile == null || loggedProjectiles.contains(projectile))
		{
			return;
		}

		NPC npc = resolveRecordedNpc();
		Actor source = projectile.getSourceActor();
		if (npc == null || source == null || source != npc)
		{
			return;
		}

		loggedProjectiles.add(projectile);
		appendLog("Projectile " + projectile.getId() + " spawned at tick " + currentTick());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event == null || !DevToolsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("oneTickPrayEnabled".equals(event.getKey()) && (config == null || !config.oneTickPrayEnabled()))
		{
			stopPrayFlick();
		}
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (e == null || !prayFeatureEnabled())
		{
			return;
		}
		Keybind bind = config.oneTickPrayHotkey();
		if (bind != null && bind.getKeyCode() == KeyEvent.VK_BACK_QUOTE
			&& (e.getKeyChar() == '`' || e.getKeyChar() == '~'))
		{
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e == null || !prayFeatureEnabled())
		{
			return;
		}
		Keybind bind = config.oneTickPrayHotkey();
		if (bind == null || !bind.matches(e))
		{
			return;
		}
		e.consume();
		if (prayKeyDown)
		{
			return;
		}
		prayKeyDown = true;
		clientThread.invoke(this::onPrayHotkeyPressed);
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (e == null || config == null)
		{
			return;
		}
		Keybind bind = config.oneTickPrayHotkey();
		if (bind == null || !bind.matches(e))
		{
			return;
		}
		e.consume();
		prayKeyDown = false;
		clientThread.invoke(this::onPrayHotkeyReleased);
	}

	private void onPrayHotkeyPressed()
	{
		if (!prayFeatureEnabled())
		{
			return;
		}
		if (config.oneTickPrayMode() == OneTickPrayMode.TOGGLE)
		{
			prayToggleOn = !prayToggleOn;
			if (prayToggleOn)
			{
				togglePrayWidget = findHoveredPrayerWidget();
			}
			else
			{
				togglePrayWidget = null;
			}
		}
		else
		{
			prayKeyHeld = true;
		}
	}

	private void onPrayHotkeyReleased()
	{
		prayKeyHeld = false;
	}

	private void flickPrayerIfNeeded()
	{
		if (!prayFeatureEnabled())
		{
			return;
		}

		boolean active = config.oneTickPrayMode() == OneTickPrayMode.TOGGLE ? prayToggleOn : prayKeyHeld;
		if (!active)
		{
			return;
		}

		flickHoveredPrayer();
		clientThread.invokeLater(this::flickHoveredPrayer);
	}

	private void flickHoveredPrayer()
	{
		if (!prayFeatureEnabled() || client == null)
		{
			return;
		}

		Widget widget;
		if (config.oneTickPrayMode() == OneTickPrayMode.TOGGLE)
		{
			widget = togglePrayWidget;
			if (widget == null || widget.isHidden())
			{
				widget = findHoveredPrayerWidget();
				togglePrayWidget = widget;
			}
		}
		else
		{
			widget = findHoveredPrayerWidget();
		}

		if (widget == null || widget.isHidden())
		{
			return;
		}

		String option = "";
		int identifier = 1;
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (int i = 0; i < actions.length; i++)
			{
				if (actions[i] != null && !actions[i].isEmpty())
				{
					identifier = i + 1;
					option = actions[i];
					break;
				}
			}
		}

		String target = widget.getName() == null ? "" : widget.getName();
		client.menuAction(-1, widget.getId(), MenuAction.CC_OP, identifier, -1, option, target);
	}

	private Widget findHoveredPrayerWidget()
	{
		if (client == null)
		{
			return null;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		for (int id : PRAYER_BOOK_WIDGETS)
		{
			Widget widget = client.getWidget(id);
			if (containsMouse(widget, mouse))
			{
				return widget;
			}
		}

		for (int id : PRAYER_ORB_WIDGETS)
		{
			Widget widget = client.getWidget(id);
			if (containsMouse(widget, mouse))
			{
				return widget;
			}
		}

		Widget quickPrayers = client.getWidget(InterfaceID.Quickprayer.BUTTONS);
		if (quickPrayers != null && !quickPrayers.isHidden())
		{
			Widget[] children = quickPrayers.getChildren();
			if (children != null)
			{
				for (Widget child : children)
				{
					if (containsMouse(child, mouse))
					{
						return child;
					}
				}
			}
			if (containsMouse(quickPrayers, mouse))
			{
				return quickPrayers;
			}
		}

		return null;
	}

	private static boolean containsMouse(Widget widget, Point mouse)
	{
		if (widget == null || mouse == null || widget.isHidden())
		{
			return false;
		}
		Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.contains(mouse.getX(), mouse.getY());
	}

	private boolean prayFeatureEnabled()
	{
		return config != null && config.oneTickPrayEnabled();
	}

	private void stopPrayFlick()
	{
		prayKeyHeld = false;
		prayToggleOn = false;
		togglePrayWidget = null;
		prayKeyDown = false;
	}

	private void scanNpcs()
	{
		trackedNpcs.clear();
		if (client == null)
		{
			return;
		}
		List<NPC> npcs = client.getNpcs();
		if (npcs == null)
		{
			return;
		}
		for (NPC npc : npcs)
		{
			trackNpc(npc);
		}
	}

	private void trackNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		trackedNpcs.put(npc.getIndex(), npc);
		lastAnimations.putIfAbsent(npc.getIndex(), npc.getAnimation());
		if (findHighlight(npc) != null)
		{
			remainingTicks.putIfAbsent(npc.getIndex(), getConfiguredTicks(npc));
		}
	}

	private void updateHighlightCountdowns()
	{
		List<Integer> stale = new ArrayList<>();
		for (Map.Entry<Integer, NPC> entry : trackedNpcs.entrySet())
		{
			NPC npc = entry.getValue();
			int index = entry.getKey();
			if (npc == null || findHighlight(npc) == null)
			{
				stale.add(index);
				continue;
			}

			remainingTicks.putIfAbsent(index, getConfiguredTicks(npc));
			int anim = npc.getAnimation();
			Integer last = lastAnimations.get(index);
			if (last != null && last == -1 && anim != -1)
			{
				remainingTicks.put(index, getConfiguredTicks(npc));
				resetThisTick.add(index);
			}
			else if (!resetThisTick.contains(index))
			{
				int current = remainingTicks.getOrDefault(index, getConfiguredTicks(npc));
				remainingTicks.put(index, Math.max(0, current - 1));
			}
			lastAnimations.put(index, anim);
		}
		for (Integer index : stale)
		{
			remainingTicks.remove(index);
		}
	}

	private void handleHighlightAnimation(NPC npc)
	{
		if (npc == null || findHighlight(npc) == null)
		{
			return;
		}
		int index = npc.getIndex();
		int anim = npc.getAnimation();
		Integer last = lastAnimations.get(index);
		if (last != null && last == -1 && anim != -1)
		{
			remainingTicks.put(index, getConfiguredTicks(npc));
			resetThisTick.add(index);
		}
		lastAnimations.put(index, anim);
	}

	private int getConfiguredTicks(NPC npc)
	{
		NpcHighlightEntry entry = findHighlight(npc);
		return entry == null ? 0 : Math.max(0, entry.ticks);
	}

	private void updateRecording()
	{
		if (!recording)
		{
			return;
		}

		NPC npc = resolveRecordedNpc();
		if (npc == null)
		{
			SwingUtilities.invokeLater(panel::showNoNpc);
			return;
		}

		if (lastRecordedAnim == Integer.MIN_VALUE)
		{
			lastRecordedAnim = npc.getAnimation();
			lastRecordedAnimStartTick = currentTick();
			if (lastRecordedAnim != -1)
			{
				lastRelevantAnimId = lastRecordedAnim;
				lastRelevantAnimTick = currentTick();
			}
		}

		pruneProjectiles();
		final String name = npcName(npc);
		final int id = npc.getId();
		final int anim = npc.getAnimation();
		final String pos = formatWorldPoint(npc.getWorldLocation());
		final String projectiles = formatProjectiles(npc);
		final String prayer = overheadPrayer(npc);

		SwingUtilities.invokeLater(() -> panel.showNpcInfo(name, id, anim, pos, projectiles, prayer));
	}

	private NPC resolveRecordedNpc()
	{
		if (!recording)
		{
			return null;
		}
		if (recordedNpc != null && trackedNpcs.containsValue(recordedNpc) && nameMatches(recordedNpc, recordFilter))
		{
			return recordedNpc;
		}
		for (NPC npc : trackedNpcs.values())
		{
			if (nameMatches(npc, recordFilter))
			{
				recordedNpc = npc;
				return npc;
			}
		}
		recordedNpc = null;
		return null;
	}

	private boolean isRecordedNpc(NPC npc)
	{
		return npc != null && resolveRecordedNpc() == npc;
	}

	private void handleRecordedAnimation(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		int anim = npc.getAnimation();
		int tick = currentTick();
		if (lastRecordedAnim != Integer.MIN_VALUE && anim != lastRecordedAnim)
		{
			int held = tick - lastRecordedAnimStartTick;
			if (held > 0)
			{
				appendLog("Same animation held for " + held + " ticks");
			}
			appendLog("Animation changed to " + anim + " at tick " + tick);
			lastRecordedAnim = anim;
			lastRecordedAnimStartTick = tick;
		}
		if (anim != -1)
		{
			lastRelevantAnimId = anim;
			lastRelevantAnimTick = tick;
		}
	}

	private void flushHeldAnimation()
	{
		if (lastRecordedAnim == Integer.MIN_VALUE)
		{
			return;
		}
		int held = currentTick() - lastRecordedAnimStartTick;
		if (held > 0)
		{
			appendLog("Same animation held for " + held + " ticks");
		}
	}

	private void pruneProjectiles()
	{
		if (client == null)
		{
			loggedProjectiles.clear();
			return;
		}
		Deque<Projectile> projectiles = client.getProjectiles();
		if (projectiles == null)
		{
			loggedProjectiles.clear();
			return;
		}
		Set<Projectile> live = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Projectile projectile : projectiles)
		{
			if (projectile != null)
			{
				live.add(projectile);
			}
		}
		loggedProjectiles.retainAll(live);
	}

	private String formatProjectiles(NPC npc)
	{
		if (client == null || npc == null)
		{
			return "none";
		}
		Deque<Projectile> projectiles = client.getProjectiles();
		if (projectiles == null)
		{
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (Projectile projectile : projectiles)
		{
			if (projectile == null)
			{
				continue;
			}
			Actor source = projectile.getSourceActor();
			if (source != npc)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(projectile.getId());
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}

	private static String overheadPrayer(NPC npc)
	{
		if (npc == null)
		{
			return "none";
		}
		int[] archives = npc.getOverheadArchiveIds();
		short[] sprites = npc.getOverheadSpriteIds();
		if (archives == null || sprites == null)
		{
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		int n = Math.min(archives.length, sprites.length);
		for (int i = 0; i < n; i++)
		{
			if (archives[i] == -1)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append("sprite ").append(sprites[i]);
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}

	private static boolean nameMatches(NPC npc, String filter)
	{
		if (npc == null || filter == null)
		{
			return false;
		}
		String trimmed = filter.trim();
		if (trimmed.isEmpty())
		{
			return false;
		}
		String name = npc.getName();
		if (name == null || name.equals("null"))
		{
			return false;
		}
		return Text.removeTags(name).toLowerCase().contains(trimmed.toLowerCase());
	}

	private static String npcName(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return "?";
		}
		return Text.removeTags(npc.getName());
	}

	private static String formatWorldPoint(WorldPoint point)
	{
		if (point == null)
		{
			return "?";
		}
		return point.getX() + ", " + point.getY() + ", " + point.getPlane();
	}

	private boolean isInteractingWithPlayer(NPC npc, Player player)
	{
		if (npc == null || player == null)
		{
			return false;
		}
		Actor npcTarget = npc.getInteracting();
		Actor playerTarget = player.getInteracting();
		return npcTarget == player || playerTarget == npc;
	}

	private int currentTick()
	{
		return client == null ? 0 : client.getTickCount();
	}

	private void appendLog(String line)
	{
		if (line == null)
		{
			return;
		}
		fullLog.add(line);
		List<String> snapshot = new ArrayList<>(fullLog);
		SwingUtilities.invokeLater(() -> panel.setLogLines(snapshot));
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16; x++)
		{
			for (int y = 0; y < 16; y++)
			{
				boolean border = x == 0 || y == 0 || x == 15 || y == 15;
				boolean glyph = (x >= 4 && x <= 11 && (y == 4 || y == 11))
					|| (y >= 4 && y <= 11 && (x == 4 || x == 11))
					|| (x >= 7 && x <= 8 && y >= 7 && y <= 8);
				if (border)
				{
					image.setRGB(x, y, 0xFF1E88E5);
				}
				else if (glyph)
				{
					image.setRGB(x, y, 0xFFFFFFFF);
				}
				else
				{
					image.setRGB(x, y, 0xFF1565C0);
				}
			}
		}
		return image;
	}
}
