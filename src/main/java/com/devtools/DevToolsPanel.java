package com.devtools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;

@Singleton
public class DevToolsPanel extends PluginPanel
{
	private static final int LOG_VIEW_LINES = 100;

	@Inject
	private DevToolsPlugin plugin;

	@Inject
	private ColorPickerManager colorPickerManager;

	private final JTextField npcNameField = new JTextField();
	private final JToggleButton recordButton = new JToggleButton("Start");
	private final JPanel infoPanel = new JPanel();
	private final JLabel infoName = valueLabel();
	private final JLabel infoId = valueLabel();
	private final JLabel infoAnim = valueLabel();
	private final JLabel infoPos = valueLabel();
	private final JLabel infoProj = valueLabel();
	private final JLabel infoPray = valueLabel();
	private final JTextArea logArea = new JTextArea();
	private final JLabel statusLabel = new JLabel(" ");
	private final JPanel highlightList = new JPanel();

	private boolean built;

	@Inject
	private DevToolsPanel()
	{
		super();
	}

	void init()
	{
		if (built)
		{
			return;
		}
		built = true;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		add(sectionHeader("NPC recording"));
		add(Box.createVerticalStrut(4));
		add(buildRecordingControls());
		add(Box.createVerticalStrut(8));
		add(buildInfoBlock());
		add(Box.createVerticalStrut(8));
		add(sectionHeader("Log"));
		add(Box.createVerticalStrut(4));
		add(buildLogBlock());
		add(Box.createVerticalStrut(8));
		add(buildHighlightHeader());
		add(Box.createVerticalStrut(4));
		add(highlightList);

		highlightList.setLayout(new BoxLayout(highlightList, BoxLayout.Y_AXIS));
		highlightList.setOpaque(false);
		highlightList.setAlignmentX(LEFT_ALIGNMENT);

		rebuildHighlightRows();
		showNoNpc();
	}

	private JPanel buildRecordingControls()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

		npcNameField.setToolTipText("NPC name (partial match)");
		styleField(npcNameField);
		npcNameField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				plugin.setRecordFilter(npcNameField.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				plugin.setRecordFilter(npcNameField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				plugin.setRecordFilter(npcNameField.getText());
			}
		});

		recordButton.setFocusable(false);
		recordButton.addActionListener(e ->
		{
			if (recordButton.isSelected())
			{
				plugin.startRecording(npcNameField.getText());
				recordButton.setText("Stop");
			}
			else
			{
				plugin.stopRecording();
				recordButton.setText("Start");
			}
		});

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttonRow.setOpaque(false);
		buttonRow.setAlignmentX(LEFT_ALIGNMENT);
		buttonRow.add(recordButton);

		panel.add(npcNameField);
		panel.add(Box.createVerticalStrut(4));
		panel.add(buttonRow);
		return panel;
	}

	private JPanel buildInfoBlock()
	{
		infoPanel.setLayout(new GridLayout(0, 1, 0, 2));
		infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		infoPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
		infoPanel.setAlignmentX(LEFT_ALIGNMENT);
		infoPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

		infoPanel.add(infoRow("Name", infoName));
		infoPanel.add(infoRow("ID", infoId));
		infoPanel.add(infoRow("Anim", infoAnim));
		infoPanel.add(infoRow("Pos", infoPos));
		infoPanel.add(infoRow("Proj", infoProj));
		infoPanel.add(infoRow("Pray", infoPray));
		return infoPanel;
	}

	private JPanel buildLogBlock()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);

		logArea.setEditable(false);
		logArea.setLineWrap(true);
		logArea.setWrapStyleWord(true);
		logArea.setFont(FontManager.getRunescapeSmallFont());
		logArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		logArea.setForeground(Color.WHITE);
		logArea.setBorder(new EmptyBorder(4, 4, 4, 4));

		JScrollPane scroll = new JScrollPane(logArea);
		scroll.setPreferredSize(new Dimension(PANEL_WIDTH, 180));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		JButton clear = new JButton("Clear log");
		clear.setFocusable(false);
		clear.addActionListener(e -> plugin.clearLog());
		JButton export = new JButton("Export");
		export.setFocusable(false);
		export.addActionListener(e -> plugin.exportLog());
		buttons.add(clear);
		buttons.add(export);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel south = new JPanel();
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		south.setOpaque(false);
		south.add(buttons);
		south.add(statusLabel);

		panel.add(scroll, BorderLayout.CENTER);
		panel.add(south, BorderLayout.SOUTH);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
		return panel;
	}

	private JPanel buildHighlightHeader()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		panel.add(sectionHeader("NPC highlights"), BorderLayout.WEST);

		JButton add = new JButton("+");
		add.setFocusable(false);
		add.setToolTipText("Add highlight");
		add.addActionListener(e -> plugin.addHighlight());
		panel.add(add, BorderLayout.EAST);
		return panel;
	}

	void rebuildHighlightRows()
	{
		highlightList.removeAll();
		List<NpcHighlightEntry> entries = plugin.getHighlights();
		if (entries != null)
		{
			for (NpcHighlightEntry entry : new ArrayList<>(entries))
			{
				highlightList.add(buildHighlightRow(entry));
				highlightList.add(Box.createVerticalStrut(4));
			}
		}
		highlightList.revalidate();
		highlightList.repaint();
	}

	private JPanel buildHighlightRow(NpcHighlightEntry entry)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 4, 4, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

		JTextField matchField = new JTextField(entry.match == null ? "" : entry.match);
		styleField(matchField);
		matchField.setToolTipText("NPC name or ID");
		matchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				entry.match = matchField.getText();
				plugin.saveHighlights();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				entry.match = matchField.getText();
				plugin.saveHighlights();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				entry.match = matchField.getText();
				plugin.saveHighlights();
			}
		});

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		controls.setOpaque(false);

		Color color = entry.toColor();
		ColorJButton colorButton = new ColorJButton("#" + ColorUtil.colorToHexCode(color).toUpperCase(), color);
		colorButton.setFocusable(false);
		colorButton.setPreferredSize(new Dimension(72, 22));
		colorButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				RuneliteColorPicker picker = colorPickerManager.create(
					DevToolsPanel.this,
					colorButton.getColor(),
					"Highlight colour",
					true);
				picker.setLocationRelativeTo(colorButton);
				picker.setOnColorChange(c ->
				{
					colorButton.setColor(c);
					colorButton.setText("#" + ColorUtil.colorToHexCode(c).toUpperCase());
					entry.color = c.getRGB() & 0xFFFFFF;
				});
				picker.setOnClose(c ->
				{
					entry.color = c.getRGB() & 0xFFFFFF;
					plugin.saveHighlights();
				});
				picker.setVisible(true);
			}
		});

		JSpinner ticks = new JSpinner(new SpinnerNumberModel(Math.max(0, entry.ticks), 0, 999, 1));
		ticks.setPreferredSize(new Dimension(56, 22));
		ticks.addChangeListener(e ->
		{
			Object value = ticks.getValue();
			if (value instanceof Integer)
			{
				entry.ticks = (Integer) value;
				plugin.saveHighlights();
			}
		});

		JButton remove = new JButton("X");
		remove.setFocusable(false);
		remove.setToolTipText("Remove");
		remove.addActionListener(e -> plugin.removeHighlight(entry));

		controls.add(colorButton);
		controls.add(new JLabel("Ticks"));
		controls.add(ticks);
		controls.add(remove);

		row.add(matchField);
		row.add(Box.createVerticalStrut(2));
		row.add(controls);
		return row;
	}

	void setRecordingState(boolean recording)
	{
		recordButton.setSelected(recording);
		recordButton.setText(recording ? "Stop" : "Start");
		if (!recording)
		{
			showNoNpc();
		}
	}

	void showNpcInfo(String name, int id, int anim, String pos, String projectiles, String prayer)
	{
		infoName.setText(name);
		infoId.setText(Integer.toString(id));
		infoAnim.setText(Integer.toString(anim));
		infoPos.setText(pos);
		infoProj.setText(projectiles);
		infoPray.setText(prayer);
		infoPanel.setVisible(true);
	}

	void showNoNpc()
	{
		infoName.setText(plugin != null && plugin.isRecording() ? "none in scene" : "not recording");
		infoId.setText("-");
		infoAnim.setText("-");
		infoPos.setText("-");
		infoProj.setText("-");
		infoPray.setText("-");
	}

	void setLogLines(List<String> lines)
	{
		if (lines == null || lines.isEmpty())
		{
			logArea.setText("");
			return;
		}

		int from = Math.max(0, lines.size() - LOG_VIEW_LINES);
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < lines.size(); i++)
		{
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			sb.append(lines.get(i));
		}
		logArea.setText(sb.toString());
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	void setStatus(String text)
	{
		statusLabel.setText(text == null ? " " : text);
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel infoRow(String key, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel keyLabel = new JLabel(key);
		keyLabel.setFont(FontManager.getRunescapeSmallFont());
		keyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(keyLabel, BorderLayout.WEST);
		row.add(value, BorderLayout.CENTER);
		return row;
	}

	private static JLabel valueLabel()
	{
		JLabel label = new JLabel("-");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	private static void styleField(JTextField field)
	{
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
	}
}
