package com.betterbank.classify;

/**
 * Where an item sits in a production chain: gathered ({@link #RAW}), processed into
 * something that is still an input ({@link #INTERMEDIATE}), or a usable end product
 * ({@link #FINISHED}).
 *
 * <p>This is the attribute the Ironman scheme is built on.
 */
public enum MaterialStage
{
	NONE,
	RAW,
	INTERMEDIATE,
	FINISHED
}
