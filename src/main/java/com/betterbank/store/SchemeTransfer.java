package com.betterbank.store;

import com.betterbank.classify.BuiltInSchemes;
import com.betterbank.classify.Scheme;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schemes as shareable text (SPEC §6).
 *
 * <p>Pure: no client, no config, no UI. Import is <b>all or nothing</b> - the whole payload
 * is validated and built before anything is returned, so a malformed share string can never
 * leave a half-applied scheme behind. Every rejection carries a reason a user can act on.
 */
public final class SchemeTransfer
{
	/** Marks the payload as ours, so pasting arbitrary JSON fails clearly. */
	static final String FORMAT = "better-bank/scheme";
	static final int FORMAT_VERSION = 1;

	private SchemeTransfer()
	{
	}

	/** A parsed, validated import, or the reason it was rejected. */
	public static final class Result
	{
		private final String error;
		private final String schemeId;
		private final SchemeCustomization customization;
		private final Map<Integer, String> assignments;

		private Result(String error, String schemeId, SchemeCustomization customization,
			Map<Integer, String> assignments)
		{
			this.error = error;
			this.schemeId = schemeId;
			this.customization = customization;
			this.assignments = assignments;
		}

		static Result failure(String message)
		{
			return new Result(message, null, null, null);
		}

		static Result success(String schemeId, SchemeCustomization customization,
			Map<Integer, String> assignments)
		{
			return new Result(null, schemeId, customization, assignments);
		}

		public boolean ok()
		{
			return error == null;
		}

		/** Why the import was rejected. Null on success. */
		public String error()
		{
			return error;
		}

		public String schemeId()
		{
			return schemeId;
		}

		public SchemeCustomization customization()
		{
			return customization;
		}

		public Map<Integer, String> assignments()
		{
			return assignments;
		}
	}

	// ---- export --------------------------------------------------------------------

	public static String export(Gson gson, String schemeId, SchemeCustomization customization,
		Map<Integer, String> assignments)
	{
		final Payload payload = new Payload();
		payload.format = FORMAT;
		payload.version = FORMAT_VERSION;
		payload.scheme = schemeId;
		payload.categories = customization == null
			? new ArrayList<>() : new ArrayList<>(customization.categories());
		payload.assignments = new LinkedHashMap<>();
		if (assignments != null)
		{
			for (Map.Entry<Integer, String> e : assignments.entrySet())
			{
				payload.assignments.put(Integer.toString(e.getKey()), e.getValue());
			}
		}
		return gson.toJson(payload);
	}

	// ---- import --------------------------------------------------------------------

	public static Result parse(Gson gson, String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return Result.failure("Nothing to import - paste an exported scheme first.");
		}

		final Payload payload;
		try
		{
			payload = gson.fromJson(text.trim(), Payload.class);
		}
		catch (JsonParseException | NumberFormatException ex)
		{
			return Result.failure("That is not a Better Bank scheme - it is not valid JSON.");
		}
		if (payload == null)
		{
			return Result.failure("That is not a Better Bank scheme - the text is empty.");
		}
		if (!FORMAT.equals(payload.format))
		{
			return Result.failure("That is not a Better Bank scheme"
				+ " (expected format \"" + FORMAT + "\").");
		}
		if (payload.version <= 0 || payload.version > FORMAT_VERSION)
		{
			return Result.failure("This scheme was exported by a newer version of Better Bank"
				+ " (format " + payload.version + "). Update the plugin to import it.");
		}
		if (payload.scheme == null || payload.scheme.trim().isEmpty())
		{
			return Result.failure("This scheme does not say which scheme it belongs to.");
		}

		final Scheme target = BuiltInSchemes.byId(payload.scheme);
		if (target == null)
		{
			return Result.failure("Unknown scheme \"" + payload.scheme + "\"."
				+ " It may come from a newer version of Better Bank.");
		}

		// Build the whole thing before returning any of it.
		final SchemeCustomization customization = new SchemeCustomization();
		final List<String> knownCategories = new ArrayList<>();
		for (com.betterbank.classify.Category category : target.categories())
		{
			knownCategories.add(category.id());
		}

		if (payload.categories != null)
		{
			for (CategoryEdit edit : payload.categories)
			{
				if (edit == null || edit.id() == null || edit.id().trim().isEmpty())
				{
					return Result.failure("This scheme contains a category with no id.");
				}
				if (!edit.added() && !knownCategories.contains(edit.id()))
				{
					return Result.failure("This scheme edits a category \"" + edit.id()
						+ "\" that " + target.name() + " does not have.");
				}
				customization.categories().add(edit);
				if (!knownCategories.contains(edit.id()))
				{
					knownCategories.add(edit.id());
				}
			}
		}

		final Map<Integer, String> assignments = new LinkedHashMap<>();
		if (payload.assignments != null)
		{
			for (Map.Entry<String, String> e : payload.assignments.entrySet())
			{
				final int itemId;
				try
				{
					itemId = Integer.parseInt(e.getKey().trim());
				}
				catch (NumberFormatException ex)
				{
					return Result.failure("This scheme has an assignment for \"" + e.getKey()
						+ "\", which is not an item id.");
				}
				final String categoryId = e.getValue();
				if (categoryId == null || categoryId.trim().isEmpty())
				{
					return Result.failure("Item " + itemId + " is assigned to nothing.");
				}
				if (!knownCategories.contains(categoryId))
				{
					return Result.failure("Item " + itemId + " is assigned to category \""
						+ categoryId + "\", which this scheme does not define.");
				}
				assignments.put(itemId, categoryId);
			}
		}

		return Result.success(payload.scheme, customization, assignments);
	}

	/** JSON shape. Kept flat and boring so a human can read and hand-edit a share string. */
	private static final class Payload
	{
		String format;
		int version;
		String scheme;
		List<CategoryEdit> categories;
		Map<String, String> assignments;
	}
}
