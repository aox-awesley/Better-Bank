package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers loading and parsing the bundled table. A plain Gson is used here because there is
 * no injector in a unit test; the table registers no type adapters, so this is the same
 * parse the plugin performs with RuneLite's injected Gson.
 */
public class AttributeTableTest
{
	private static AttributeTable bundled() throws IOException
	{
		return AttributeTable.bundled(new Gson());
	}

	private static AttributeTable parse(String json) throws IOException
	{
		return AttributeTable.fromJson(new Gson(),
			new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void bundledTableLoadsFromTheJar() throws IOException
	{
		final AttributeTable table = bundled();
		assertTrue("expected a few hundred items, got " + table.size(), table.size() >= 300);
	}

	@Test
	public void bundledTableKnowsAboutSharks() throws IOException
	{
		final ItemAttributes shark = bundled().get(385);
		assertNotNull(shark);
		assertEquals("Shark", shark.name());
		assertEquals(ConsumableClass.FOOD, shark.consumable());
		assertEquals(MaterialStage.FINISHED, shark.material());
		assertTrue(shark.skills().contains(SkillType.COOKING));
		assertFalse(shark.equippable());
	}

	@Test
	public void unknownItemIsAbsentRatherThanEmpty() throws IOException
	{
		final AttributeTable table = bundled();
		assertNull(table.get(Integer.MAX_VALUE));
		assertFalse(table.contains(Integer.MAX_VALUE));
	}

	@Test
	public void skillOrderIsPreservedNotSortedByEnumOrdinal() throws IOException
	{
		// Raw shark is FISHING then COOKING. COOKING has the lower ordinal, so an EnumSet
		// would silently reorder these and file every raw fish under Cooking.
		final Iterator<SkillType> skills = bundled().get(383).skills().iterator();
		assertEquals(SkillType.FISHING, skills.next());
		assertEquals(SkillType.COOKING, skills.next());
	}

	@Test
	public void defaultsApplyToOmittedFields() throws IOException
	{
		final ItemAttributes a = parse("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"X\"}}}").get(1);
		assertTrue(a.tradeable());
		assertFalse(a.members());
		assertFalse(a.stackable());
		assertFalse(a.equippable());
		assertEquals(ConsumableClass.NONE, a.consumable());
		assertEquals(MaterialStage.NONE, a.material());
		assertTrue(a.skills().isEmpty());
	}

	@Test
	public void explicitFalseOverridesATrueDefault() throws IOException
	{
		final ItemAttributes a =
			parse("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"X\",\"tradeable\":false}}}").get(1);
		assertFalse(a.tradeable());
	}

	@Test
	public void unknownEnumValueFailsLoudly()
	{
		try
		{
			parse("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"X\",\"slot\":\"TROUSERS\"}}}");
			fail("expected a typo in the data to fail the load, not be silently dropped");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("Malformed"));
		}
	}

	@Test
	public void wrongFormatVersionIsRejected()
	{
		try
		{
			parse("{\"formatVersion\":99,\"items\":{}}");
			fail("expected an unsupported formatVersion to be rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("formatVersion"));
		}
	}

	@Test
	public void attributesAreImmutable() throws IOException
	{
		try
		{
			bundled().get(385).skills().add(SkillType.SLAYER);
			fail("attributes must not be mutable by callers");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}
}
