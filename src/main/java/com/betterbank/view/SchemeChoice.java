package com.betterbank.view;

import com.betterbank.classify.BuiltInSchemes;
import com.betterbank.classify.Scheme;
import net.runelite.api.gameval.SpriteID;

/**
 * The schemes selectable in config - all seven from SPEC §4.
 *
 * <p>Each constant holds one immutable {@link Scheme} instance, built once: the renderer
 * reads this on every bank redraw, on the client thread.
 *
 * <p>Icons come from the game's own {@code ICON_*} sprite family - the small, uniform set the
 * game already uses for pickable icons. They are referenced <b>by id</b>, so a resource pack
 * that overrides those ids is picked up automatically with no work here.
 */
public enum SchemeChoice
{
	SKILLER("Skiller", BuiltInSchemes.skiller(), SpriteID.ICON_TOOLS),
	IRONMAN("Ironman", BuiltInSchemes.ironman(), SpriteID.ICON_IRON_STANDARD),
	MERCHANT("Merchant", BuiltInSchemes.merchant(), SpriteID.ICON_COINS),
	PVMER("PvMer", BuiltInSchemes.pvmer(), SpriteID.ICON_SWORDS),
	PKER("PKer", BuiltInSchemes.pker(), SpriteID.ICON_SKULL),
	QUESTING("Questing", BuiltInSchemes.questing(), SpriteID.ICON_COMPASS),
	COLLECTION_LOG("Collection Log", BuiltInSchemes.collectionLog(), SpriteID.ICON_CROWN);

	private final String label;
	private final Scheme scheme;
	private final int spriteId;

	SchemeChoice(String label, Scheme scheme, int spriteId)
	{
		this.label = label;
		this.scheme = scheme;
		this.spriteId = spriteId;
	}

	public Scheme scheme()
	{
		return scheme;
	}

	/** A game sprite id, so resource packs apply without the plugin bundling any image. */
	public int spriteId()
	{
		return spriteId;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
