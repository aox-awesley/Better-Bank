package com.betterbank.classify;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The bundled item&rarr;attributes table (SPEC §7 module 1).
 *
 * <p>Loaded from a JSON resource inside the jar. Deployed plugins run from inside a jar and
 * are not unpacked, so this always reads through {@code getResourceAsStream} - never
 * {@code getResource}.
 */
public final class AttributeTable
{
	public static final String RESOURCE_PATH = "/com/betterbank/item-attributes.json";

	/**
	 * The only format version this build understands. Bump deliberately, and only alongside
	 * a migration - the table ships inside the jar, but user data keyed off it does not.
	 */
	public static final int SUPPORTED_FORMAT_VERSION = 1;

	private final Map<Integer, ItemAttributes> byItemId;

	private AttributeTable(Map<Integer, ItemAttributes> byItemId)
	{
		this.byItemId = Collections.unmodifiableMap(byItemId);
	}

	/** Loads the table bundled with the plugin. */
	public static AttributeTable bundled(Gson gson) throws IOException
	{
		try (InputStream in = AttributeTable.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (in == null)
			{
				throw new IOException("Bundled attribute table missing: " + RESOURCE_PATH);
			}
			return fromJson(gson, in);
		}
	}

	public static AttributeTable fromJson(Gson gson, InputStream in) throws IOException
	{
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			final FileDto dto = gson.fromJson(reader, FileDto.class);
			if (dto == null)
			{
				throw new IOException("Attribute table is empty");
			}
			if (dto.formatVersion != SUPPORTED_FORMAT_VERSION)
			{
				throw new IOException("Unsupported attribute table formatVersion "
					+ dto.formatVersion + " (expected " + SUPPORTED_FORMAT_VERSION + ")");
			}
			if (dto.items == null)
			{
				throw new IOException("Attribute table has no \"items\"");
			}

			final Map<Integer, ItemAttributes> parsed = new LinkedHashMap<>(dto.items.size());
			for (Map.Entry<String, ItemDto> e : dto.items.entrySet())
			{
				final int itemId;
				try
				{
					itemId = Integer.parseInt(e.getKey());
				}
				catch (NumberFormatException nfe)
				{
					throw new IOException("Attribute table key is not an item id: " + e.getKey());
				}
				parsed.put(itemId, e.getValue().toAttributes(itemId));
			}
			return new AttributeTable(parsed);
		}
		catch (JsonParseException ex)
		{
			throw new IOException("Malformed attribute table", ex);
		}
	}

	/** Builds a table directly, for tests and for future runtime-derived overlays. */
	public static AttributeTable of(Map<Integer, ItemAttributes> items)
	{
		return new AttributeTable(new LinkedHashMap<>(items));
	}

	/** @return the item's attributes, or null if the table does not cover this item. */
	public ItemAttributes get(int itemId)
	{
		return byItemId.get(itemId);
	}

	public boolean contains(int itemId)
	{
		return byItemId.containsKey(itemId);
	}

	public int size()
	{
		return byItemId.size();
	}

	public Set<Integer> itemIds()
	{
		return byItemId.keySet();
	}

	// ---- JSON shape ----------------------------------------------------------------

	private static final class FileDto
	{
		int formatVersion;
		Map<String, ItemDto> items;
	}

	/**
	 * Mirrors one entry in the JSON. Every field is a boxed/nullable type so an absent field
	 * is distinguishable from a present-but-default one, and defaults live in exactly one
	 * place ({@link ItemAttributes.Builder}).
	 */
	private static final class ItemDto
	{
		String name;
		String slot;
		List<String> styles;
		List<String> skills;
		String consumable;
		String material;
		Boolean tradeable;
		Boolean members;
		Boolean stackable;
		Boolean noted;
		Boolean questItem;
		Boolean clueReward;
		Boolean achievement;
		Boolean tool;
		Boolean rune;
		Boolean currency;
		Boolean teleport;

		ItemAttributes toAttributes(int itemId)
		{
			final ItemAttributes.Builder b = ItemAttributes.builder(name == null ? "" : name);
			if (slot != null)
			{
				b.slot(parse(EquipmentSlot.class, slot, itemId, "slot"));
			}
			if (styles != null && !styles.isEmpty())
			{
				final CombatStyle[] v = new CombatStyle[styles.size()];
				for (int i = 0; i < v.length; i++)
				{
					v[i] = parse(CombatStyle.class, styles.get(i), itemId, "styles");
				}
				b.styles(v);
			}
			if (skills != null && !skills.isEmpty())
			{
				final SkillType[] v = new SkillType[skills.size()];
				for (int i = 0; i < v.length; i++)
				{
					v[i] = parse(SkillType.class, skills.get(i), itemId, "skills");
				}
				b.skills(v);
			}
			if (consumable != null)
			{
				b.consumable(parse(ConsumableClass.class, consumable, itemId, "consumable"));
			}
			if (material != null)
			{
				b.material(parse(MaterialStage.class, material, itemId, "material"));
			}
			if (tradeable != null)
			{
				b.tradeable(tradeable);
			}
			if (members != null)
			{
				b.members(members);
			}
			if (stackable != null)
			{
				b.stackable(stackable);
			}
			if (noted != null)
			{
				b.noted(noted);
			}
			if (questItem != null)
			{
				b.questItem(questItem);
			}
			if (clueReward != null)
			{
				b.clueReward(clueReward);
			}
			if (achievement != null)
			{
				b.achievement(achievement);
			}
			if (tool != null)
			{
				b.tool(tool);
			}
			if (rune != null)
			{
				b.rune(rune);
			}
			if (currency != null)
			{
				b.currency(currency);
			}
			if (teleport != null)
			{
				b.teleport(teleport);
			}
			return b.build();
		}

		/**
		 * Fails loudly on an unrecognised value. Gson would silently yield null for an
		 * unknown enum constant, which would turn a typo in the data into an item that is
		 * quietly wrong rather than a build that fails.
		 */
		private static <E extends Enum<E>> E parse(Class<E> type, String value, int itemId, String field)
		{
			try
			{
				return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex)
			{
				throw new JsonParseException("Item " + itemId + " has unknown " + field
					+ " \"" + value + "\"");
			}
		}
	}
}
