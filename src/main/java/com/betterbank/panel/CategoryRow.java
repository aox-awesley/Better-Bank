package com.betterbank.panel;

import com.betterbank.classify.Category;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One category in the editor: name, colour, and the controls for it.
 *
 * <p>Uses RuneLite's {@link ColorScheme} and {@link FontManager} throughout so the panel
 * matches every other plugin sidebar rather than looking hand-rolled.
 */
class CategoryRow extends JPanel
{
	interface Actions
	{
		void rename(String categoryId);

		void recolour(String categoryId);

		void move(String categoryId, int delta);

		void toggleHidden(String categoryId);

		void toggleExpanded(String categoryId);
	}

	CategoryRow(Category category, int itemCount, boolean hidden, boolean expanded, Actions actions)
	{
		setLayout(new BorderLayout(4, 0));
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel swatch = new JLabel("■");
		swatch.setForeground(category.colourRgb() == null
			? ColorScheme.LIGHT_GRAY_COLOR : new Color(category.colourRgb()));
		swatch.setToolTipText("Set colour");
		swatch.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		add(swatch, BorderLayout.WEST);

		final JLabel name = new JLabel(category.name() + "  (" + itemCount + ")");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(hidden ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		name.setToolTipText(hidden ? "Hidden - click the eye to show" : category.id());
		add(name, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.add(small(expanded ? "▼" : "▶", "Show assigned items",
			() -> actions.toggleExpanded(category.id())));
		buttons.add(small("↑", "Move up", () -> actions.move(category.id(), -1)));
		buttons.add(small("↓", "Move down", () -> actions.move(category.id(), 1)));
		buttons.add(small(hidden ? "○" : "●", hidden ? "Show" : "Hide",
			() -> actions.toggleHidden(category.id())));
		buttons.add(small("✎", "Rename", () -> actions.rename(category.id())));
		add(buttons, BorderLayout.EAST);

		// The swatch is a click target too.
		swatch.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				actions.recolour(category.id());
			}
		});
	}

	private static JButton small(String text, String tooltip, Runnable onClick)
	{
		final JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(22, 18));
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(Color.WHITE);
		button.setBorder(BorderFactory.createEmptyBorder());
		button.addActionListener(e -> onClick.run());
		return button;
	}

	/** A read-only line for one assigned item, with an unassign control. */
	static JPanel assignedItem(String itemName, Runnable onUnassign)
	{
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(2, 18, 2, 6));

		final JLabel label = new JLabel(itemName);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);

		final JButton remove = new JButton("✕");
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setToolTipText("Unassign - let the rules decide again");
		remove.setPreferredSize(new Dimension(20, 16));
		remove.setFocusPainted(false);
		remove.setBackground(ColorScheme.DARK_GRAY_COLOR);
		remove.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		remove.setBorder(BorderFactory.createEmptyBorder());
		remove.addActionListener(e -> onUnassign.run());
		row.add(remove, BorderLayout.EAST);

		final JPanel wrapper = new JPanel(new GridLayout(1, 1));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(row);
		return wrapper;
	}
}
