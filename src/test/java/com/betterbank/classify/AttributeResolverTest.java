package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The layering contract: bundled override beats runtime derivation beats name pattern.
 */
public class AttributeResolverTest
{
	private static OverrideTable overrides(String json) throws IOException
	{
		return OverrideTable.fromJson(new Gson(),
			new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void namePatternsApplyWhenNothingElseKnows() throws IOException
	{
		final AttributeResolver r = new AttributeResolver(OverrideTable.empty(),
			new FakeItemMetadata().named(1, "Yew logs"));
		assertTrue(r.resolve(1).skills().contains(SkillType.WOODCUTTING));
		assertEquals(MaterialStage.RAW, r.resolve(1).material());
	}

	@Test
	public void runtimeDerivationBeatsNamePatterns() throws IOException
	{
		// "Ring of forging" ends in nothing special, but the client says it is a ring.
		final AttributeResolver r = new AttributeResolver(OverrideTable.empty(),
			new FakeItemMetadata().equipment(1, "Ring of forging", 12));
		assertEquals(EquipmentSlot.RING, r.resolve(1).slot());
	}

	@Test
	public void bundledOverrideBeatsRuntimeDerivation() throws IOException
	{
		// The client calls a dragon pickaxe a weapon. The override says it is a tool, and the
		// override is what a Merchant needs to see.
		final AttributeResolver r = new AttributeResolver(
			overrides("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"Dragon pickaxe\",\"tool\":true}}}"),
			new FakeItemMetadata().equipment(1, "Dragon pickaxe", 3));
		final ItemAttributes a = r.resolve(1);
		assertTrue("override must win", a.tool());
		assertEquals("runtime slot still applies where the override is silent",
			EquipmentSlot.WEAPON, a.slot());
	}

	@Test
	public void overrideIsPartialAndDoesNotWipeOtherLayers() throws IOException
	{
		final AttributeResolver r = new AttributeResolver(
			overrides("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"Coal\",\"skills\":[\"MINING\"]}}}"),
			new FakeItemMetadata().put(1, RuntimeItem.builder("Coal").stackable(false).build()));
		final ItemAttributes a = r.resolve(1);
		assertTrue(a.skills().contains(SkillType.MINING));
		assertTrue("runtime tradeable survives the override", a.tradeable());
	}

	@Test
	public void clientNameIsPreferredOverTheOverrideName() throws IOException
	{
		// The override file's name field is documentation; the client is the source of truth.
		final AttributeResolver r = new AttributeResolver(
			overrides("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"stale name\"}}}"),
			new FakeItemMetadata().named(1, "Actual name"));
		assertEquals("Actual name", r.resolve(1).name());
	}

	@Test
	public void overrideNameIsUsedWhenTheClientHasNoDefinition() throws IOException
	{
		final AttributeResolver r = new AttributeResolver(
			overrides("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"Coal\",\"skills\":[\"MINING\"]}}}"),
			ItemMetadata.empty());
		assertEquals("Coal", r.resolve(1).name());
		assertTrue(r.resolve(1).skills().contains(SkillType.MINING));
	}

	@Test
	public void completelyUnknownItemResolvesToNull()
	{
		final AttributeResolver r = new AttributeResolver(OverrideTable.empty(), ItemMetadata.empty());
		assertNull(r.resolve(999));
	}

	@Test
	public void resultsAreCached()
	{
		final int[] calls = {0};
		final ItemMetadata counting = itemId ->
		{
			calls[0]++;
			return RuntimeItem.builder("Yew logs").build();
		};
		final AttributeResolver r = new AttributeResolver(OverrideTable.empty(), counting);
		r.resolve(1);
		r.resolve(1);
		r.resolve(1);
		assertEquals("attributes do not change; look them up once", 1, calls[0]);
		r.invalidate();
		r.resolve(1);
		assertEquals(2, calls[0]);
	}

	@Test
	public void unknownEnumInAnOverrideFailsLoudly()
	{
		try
		{
			overrides("{\"formatVersion\":1,\"items\":{\"1\":{\"name\":\"X\",\"slot\":\"TROUSERS\"}}}");
			org.junit.Assert.fail("a typo in the data must fail the load");
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
			overrides("{\"formatVersion\":99,\"items\":{}}");
			org.junit.Assert.fail("unsupported formatVersion must be rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("formatVersion"));
		}
	}

	@Test
	public void bundledOverrideTableLoads() throws IOException
	{
		// Size is guarded in BundledDataTest, which excludes the generated pet block.
		final OverrideTable table = OverrideTable.bundled(new Gson());
		assertFalse(table.itemIds().isEmpty());
	}
}
