package com.betterbank.store;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Import must be strict and all-or-nothing, with a message a user can act on. */
public class SchemeTransferTest
{
	private static final Gson GSON = new Gson();

	private static SchemeTransfer.Result parse(String text)
	{
		return SchemeTransfer.parse(GSON, text);
	}

	private static String exportSample()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("armour").name("Armor").order(1);
		custom.edit("flip-list").added(true).name("Flip list");
		final Map<Integer, String> assignments = new LinkedHashMap<>();
		assignments.put(385, "consumables");
		assignments.put(995, "flip-list");
		return SchemeTransfer.export(GSON, "merchant", custom, assignments);
	}

	@Test
	public void exportThenImportRoundTrips()
	{
		final SchemeTransfer.Result result = parse(exportSample());
		assertTrue(result.error(), result.ok());
		assertEquals("merchant", result.schemeId());
		assertEquals("Armor", result.customization().byId().get("armour").name());
		assertTrue(result.customization().byId().get("flip-list").added());
		assertEquals("consumables", result.assignments().get(385));
		assertEquals("flip-list", result.assignments().get(995));
	}

	@Test
	public void emptyInputIsRejectedClearly()
	{
		assertFalse(parse(null).ok());
		assertFalse(parse("   ").ok());
		assertTrue(parse("").error().contains("Nothing to import"));
	}

	@Test
	public void nonJsonIsRejectedAsNotAScheme()
	{
		final SchemeTransfer.Result result = parse("this is not json {{{");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("not valid JSON"));
	}

	@Test
	public void foreignJsonIsRejectedOnTheFormatMarker()
	{
		final SchemeTransfer.Result result = parse("{\"hello\":\"world\"}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("not a Better Bank scheme"));
	}

	@Test
	public void newerFormatVersionSaysToUpdate()
	{
		final SchemeTransfer.Result result = parse(
			"{\"format\":\"better-bank/scheme\",\"version\":99,\"scheme\":\"merchant\"}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("newer version"));
	}

	@Test
	public void unknownSchemeIsNamedInTheError()
	{
		final SchemeTransfer.Result result = parse(
			"{\"format\":\"better-bank/scheme\",\"version\":1,\"scheme\":\"speedrunner\"}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("speedrunner"));
	}

	@Test
	public void missingSchemeIdIsRejected()
	{
		assertFalse(parse("{\"format\":\"better-bank/scheme\",\"version\":1}").ok());
	}

	@Test
	public void editingACategoryTheSchemeDoesNotHaveIsRejected()
	{
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\",\"categories\":[{\"id\":\"herblore\",\"name\":\"Herbs\"}]}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("herblore"));
	}

	@Test
	public void addedCategoriesAreAllowedAndBecomeAssignable()
	{
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\",\"categories\":[{\"id\":\"mine\",\"name\":\"Mine\",\"added\":true}],"
			+ "\"assignments\":{\"385\":\"mine\"}}");
		assertTrue(result.error(), result.ok());
		assertEquals("mine", result.assignments().get(385));
	}

	@Test
	public void assignmentToAnUndefinedCategoryIsRejected()
	{
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\",\"assignments\":{\"385\":\"nonsense\"}}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("nonsense"));
	}

	@Test
	public void nonNumericItemIdIsRejected()
	{
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\",\"assignments\":{\"shark\":\"consumables\"}}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("not an item id"));
	}

	@Test
	public void categoryWithoutAnIdIsRejected()
	{
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\",\"categories\":[{\"name\":\"Nameless\"}]}");
		assertFalse(result.ok());
		assertTrue(result.error(), result.error().contains("no id"));
	}

	@Test
	public void aMalformedImportYieldsNothingAtAllRatherThanAPartialScheme()
	{
		// The valid half must not survive the invalid half.
		final SchemeTransfer.Result result = parse("{\"format\":\"better-bank/scheme\",\"version\":1,"
			+ "\"scheme\":\"merchant\","
			+ "\"categories\":[{\"id\":\"armour\",\"name\":\"Armor\"}],"
			+ "\"assignments\":{\"385\":\"consumables\",\"995\":\"nonsense\"}}");
		assertFalse(result.ok());
		org.junit.Assert.assertNull("nothing may be handed back", result.customization());
		org.junit.Assert.assertNull("nothing may be handed back", result.assignments());
	}

	@Test
	public void anEmptySchemeExportsAndImportsCleanly()
	{
		final String text = SchemeTransfer.export(GSON, "skiller", new SchemeCustomization(),
			new LinkedHashMap<>());
		final SchemeTransfer.Result result = parse(text);
		assertTrue(result.error(), result.ok());
		assertTrue(result.customization().isEmpty());
		assertTrue(result.assignments().isEmpty());
	}
}
