package com.betterbank.panel;

import com.betterbank.BetterBankConfig;
import com.betterbank.classify.Category;
import com.betterbank.classify.Scheme;
import com.betterbank.store.OverrideStore;
import com.betterbank.store.SchemeCustomization;
import com.betterbank.store.SchemeCustomizer;
import com.betterbank.store.SchemeTransfer;
import com.betterbank.view.BankCategoryRenderer;
import com.betterbank.view.SchemeChoice;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The customization sidebar (SPEC §6).
 *
 * <p><b>Threading.</b> Every method here runs on the Swing EDT - which is not the client
 * thread, so store reads and writes are safe to do inline. The only thing that must hop
 * threads is redrawing the bank, which goes through the renderer's client-thread hop.
 * {@link ItemManager#getItemComposition} is the one client-touching call, used for item
 * names; it reads cached definitions and is why the panel never blocks.
 */
@Slf4j
@Singleton
public class BetterBankPanel extends PluginPanel
{
	private final OverrideStore store;
	private final BetterBankConfig config;
	private final ConfigManager configManager;
	private final BankCategoryRenderer renderer;
	private final ItemManager itemManager;
	private final Gson gson;

	private final JComboBox<SchemeChoice> schemePicker = new JComboBox<>(SchemeChoice.values());
	private final JPanel categories = new JPanel();
	private final JLabel status = new JLabel();

	/** Category ids whose assigned-items list is open. */
	private final List<String> expanded = new ArrayList<>();

	@Inject
	BetterBankPanel(OverrideStore store, BetterBankConfig config, ConfigManager configManager,
		BankCategoryRenderer renderer, ItemManager itemManager, Gson gson)
	{
		super(false);
		this.store = store;
		this.config = config;
		this.configManager = configManager;
		this.renderer = renderer;
		this.itemManager = itemManager;
		this.gson = gson;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		add(header(), BorderLayout.NORTH);

		categories.setLayout(new BoxLayout(categories, BoxLayout.Y_AXIS));
		categories.setBackground(ColorScheme.DARK_GRAY_COLOR);
		final JScrollPane scroll = new JScrollPane(categories,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		add(footer(), BorderLayout.SOUTH);
	}

	private JPanel header()
	{
		final JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		final JLabel title = new JLabel("Scheme");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		panel.add(title, BorderLayout.NORTH);

		schemePicker.setFocusable(false);
		schemePicker.setForeground(Color.WHITE);
		schemePicker.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		schemePicker.addActionListener(e ->
		{
			final SchemeChoice picked = (SchemeChoice) schemePicker.getSelectedItem();
			if (picked != null && picked != config.scheme())
			{
				configManager.setConfiguration(BetterBankConfig.GROUP, "scheme", picked);
				rebuild();
			}
		});
		panel.add(schemePicker, BorderLayout.CENTER);
		return panel;
	}

	private JPanel footer()
	{
		final JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(status, BorderLayout.NORTH);

		final JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(button("Add category", this::addCategory));
		buttons.add(button("Reset", this::resetScheme));
		buttons.add(button("Export", this::exportScheme));
		buttons.add(button("Import", this::importScheme));
		panel.add(buttons, BorderLayout.CENTER);
		return panel;
	}

	private static JButton button(String text, Runnable onClick)
	{
		final JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(Color.WHITE);
		button.addActionListener(e -> onClick.run());
		return button;
	}

	// ---- rendering -----------------------------------------------------------------

	/** Rebuilds the panel from the store. Safe to call from any thread. */
	public void refresh()
	{
		SwingUtilities.invokeLater(this::rebuildPanel);
	}

	private void rebuildPanel()
	{
		final SchemeChoice choice = config.scheme();
		if (schemePicker.getSelectedItem() != choice)
		{
			schemePicker.setSelectedItem(choice);
		}

		final Scheme scheme = activeScheme();
		final SchemeCustomization customization = store.customization(scheme.id());
		final Map<Integer, String> assignments = store.assignments(scheme.id());

		final Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
		for (Map.Entry<Integer, String> e : assignments.entrySet())
		{
			byCategory.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
		}

		categories.removeAll();
		for (Category category : scheme.categories())
		{
			final List<Integer> assigned =
				byCategory.getOrDefault(category.id(), new ArrayList<>());
			final boolean hidden = customization.byId().containsKey(category.id())
				&& customization.byId().get(category.id()).hidden();

			categories.add(new CategoryRow(category, assigned.size(), hidden,
				expanded.contains(category.id()), rowActions()));

			if (expanded.contains(category.id()))
			{
				if (assigned.isEmpty())
				{
					categories.add(note("No items assigned by hand."));
				}
				for (int itemId : assigned)
				{
					categories.add(CategoryRow.assignedItem(itemName(itemId),
						() -> unassign(scheme.id(), itemId)));
				}
			}
		}

		// Hidden categories are dropped from the scheme, so they need listing separately or
		// they would be unrecoverable from the panel.
		for (Map.Entry<String, com.betterbank.store.CategoryEdit> e : customization.byId().entrySet())
		{
			if (e.getValue().hidden() && !scheme.hasCategory(e.getKey()))
			{
				final String name = e.getValue().name() == null ? e.getKey() : e.getValue().name();
				categories.add(new CategoryRow(new Category(e.getKey(), name), 0, true,
					false, rowActions()));
			}
		}

		status.setText(store.isCustomized(scheme.id())
			? "<html>" + scheme.name() + " has unsaved-to-default edits.</html>"
			: "<html>" + scheme.name() + " is in its shipped state.</html>");

		categories.revalidate();
		categories.repaint();
	}

	private JLabel note(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(2, 18, 4, 6));
		return label;
	}

	private CategoryRow.Actions rowActions()
	{
		return new CategoryRow.Actions()
		{
			@Override
			public void rename(String categoryId)
			{
				BetterBankPanel.this.rename(categoryId);
			}

			@Override
			public void recolour(String categoryId)
			{
				BetterBankPanel.this.recolour(categoryId);
			}

			@Override
			public void move(String categoryId, int delta)
			{
				BetterBankPanel.this.move(categoryId, delta);
			}

			@Override
			public void toggleHidden(String categoryId)
			{
				BetterBankPanel.this.toggleHidden(categoryId);
			}

			@Override
			public void toggleExpanded(String categoryId)
			{
				if (!expanded.remove(categoryId))
				{
					expanded.add(categoryId);
				}
				rebuildPanel();
			}
		};
	}

	// ---- edits ---------------------------------------------------------------------

	private Scheme activeScheme()
	{
		final Scheme base = config.scheme().scheme();
		return SchemeCustomizer.apply(base, store.customization(base.id()));
	}

	private void rename(String categoryId)
	{
		final Scheme scheme = activeScheme();
		final Category category = scheme.category(categoryId);
		final String current = category == null ? categoryId : category.name();
		final String name = JOptionPane.showInputDialog(this, "Rename category:", current);
		if (name == null || name.trim().isEmpty() || name.equals(current))
		{
			return;
		}
		mutate(scheme.id(), c -> c.edit(categoryId).name(name.trim()));
	}

	private void recolour(String categoryId)
	{
		final Color chosen = JColorChooser.showDialog(this, "Category colour", Color.WHITE);
		if (chosen == null)
		{
			return;
		}
		mutate(activeScheme().id(), c -> c.edit(categoryId).colourRgb(chosen.getRGB() & 0xFFFFFF));
	}

	private void move(String categoryId, int delta)
	{
		final Scheme scheme = activeScheme();
		final List<String> order = new ArrayList<>();
		for (Category category : scheme.categories())
		{
			if (!Scheme.UNCATEGORIZED_ID.equals(category.id()))
			{
				order.add(category.id());
			}
		}
		final int from = order.indexOf(categoryId);
		final int to = from + delta;
		if (from < 0 || to < 0 || to >= order.size())
		{
			return;
		}
		order.remove(from);
		order.add(to, categoryId);

		mutate(scheme.id(), c ->
		{
			for (int i = 0; i < order.size(); i++)
			{
				c.edit(order.get(i)).order(i);
			}
		});
	}

	private void toggleHidden(String categoryId)
	{
		final Scheme scheme = activeScheme();
		final SchemeCustomization customization = store.customization(scheme.id());
		final boolean nowHidden = customization.byId().containsKey(categoryId)
			&& customization.byId().get(categoryId).hidden();
		mutate(scheme.id(), c -> c.edit(categoryId).hidden(!nowHidden));
	}

	private void addCategory()
	{
		final String name = JOptionPane.showInputDialog(this, "New category name:");
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		final String id = slug(name);
		final Scheme scheme = activeScheme();
		if (scheme.hasCategory(id))
		{
			JOptionPane.showMessageDialog(this, "That category already exists.",
				"Better Bank", JOptionPane.WARNING_MESSAGE);
			return;
		}
		mutate(scheme.id(), c -> c.edit(id).added(true).name(name.trim()));
	}

	/** A stable id from a display name, so renaming later does not orphan assignments. */
	static String slug(String name)
	{
		final String cleaned = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
		final String trimmed = cleaned.replaceAll("^-+|-+$", "");
		return trimmed.isEmpty() ? "category" : "user-" + trimmed;
	}

	private void unassign(String schemeId, int itemId)
	{
		store.clearAssignment(schemeId, itemId);
		rebuild();
	}

	private void mutate(String schemeId, java.util.function.Consumer<SchemeCustomization> edit)
	{
		final SchemeCustomization customization = store.customization(schemeId);
		edit.accept(customization);
		store.saveCustomization(schemeId, customization);
		rebuild();
	}

	// ---- reset, import, export -----------------------------------------------------

	private void resetScheme()
	{
		final Scheme scheme = activeScheme();
		if (!store.isCustomized(scheme.id()))
		{
			JOptionPane.showMessageDialog(this, scheme.name() + " is already in its shipped state.",
				"Better Bank", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int answer = JOptionPane.showConfirmDialog(this,
			"Discard all your edits to " + scheme.name() + " and return it to its shipped state?"
				+ "\nThis cannot be undone.",
			"Reset " + scheme.name(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		store.reset(scheme.id());
		rebuild();
	}

	private void exportScheme()
	{
		final Scheme scheme = activeScheme();
		final String text = SchemeTransfer.export(gson, scheme.id(),
			store.customization(scheme.id()), store.assignments(scheme.id()));
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
			new StringSelection(text), null);
		JOptionPane.showMessageDialog(this,
			scheme.name() + " copied to your clipboard.\nPaste it anywhere to share it.",
			"Better Bank", JOptionPane.INFORMATION_MESSAGE);
	}

	private void importScheme()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
		}
		catch (Exception ex)
		{
			log.debug("clipboard unavailable", ex);
			JOptionPane.showMessageDialog(this, "Could not read your clipboard.",
				"Better Bank", JOptionPane.ERROR_MESSAGE);
			return;
		}

		final SchemeTransfer.Result result = SchemeTransfer.parse(gson, text);
		if (!result.ok())
		{
			// Strict: nothing has been written, so there is nothing to undo.
			JOptionPane.showMessageDialog(this, result.error(),
				"Import failed", JOptionPane.ERROR_MESSAGE);
			return;
		}

		final int answer = JOptionPane.showConfirmDialog(this,
			"Replace your " + result.schemeId() + " customization with the imported one?"
				+ "\nYour current edits to that scheme will be discarded.",
			"Import scheme", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}

		store.reset(result.schemeId());
		store.saveCustomization(result.schemeId(), result.customization());
		for (Map.Entry<Integer, String> e : result.assignments().entrySet())
		{
			store.assign(result.schemeId(), e.getKey(), e.getValue());
		}
		rebuild();
	}

	// ---- helpers -------------------------------------------------------------------

	private String itemName(int itemId)
	{
		try
		{
			final String name = itemManager.getItemComposition(itemId).getName();
			return name == null || name.isEmpty() ? "Item " + itemId : name;
		}
		catch (RuntimeException ex)
		{
			return "Item " + itemId;
		}
	}

	/** Redraw the panel, and the bank if it is open. */
	private void rebuild()
	{
		rebuildPanel();
		renderer.requestRebuild();
	}
}
