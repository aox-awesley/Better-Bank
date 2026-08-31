package com.betterbank.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The clamp that keeps the scheme switcher out of the item grid.
 *
 * <p>The bug this pins: the old clamp compared a coordinate local to the switcher's parent
 * against {@code START_X}, an offset inside the <i>grid</i> widget. Those are sibling widgets
 * with independent origins, so the comparison was meaningless and the icon landed on the
 * first item. Everything here is in one space - canvas - which is what makes it decidable.
 */
public class SwitcherClampTest
{
	private static final int ICON = 20;
	private static final int MARGIN = 4;

	/** Right edge of the icon, given a clamped left edge. */
	private static int rightEdge(int x)
	{
		return x + ICON;
	}

	@Test
	public void iconStaysLeftOfTheGridWhenTheCauldronSitsUnderIt()
	{
		// The real failure shape: the strip runs from the parent edge (480) to the grid (531,
		// i.e. START_X across), and the cauldron reports a canvas x inside the grid.
		final int x = BankCategoryRenderer.clampLeftOfGrid(560, 480, 531);
		assertTrue("right edge " + rightEdge(x) + " must clear grid at 531", rightEdge(x) <= 531);
		assertEquals(531 - ICON - MARGIN, x);
	}

	@Test
	public void aStripTooNarrowForTheIconPinsToTheParentEdge()
	{
		// Nothing can fit; sitting at the parent edge is the best available, and is at least
		// deterministic rather than drifting over the grid.
		assertEquals(480, BankCategoryRenderer.clampLeftOfGrid(520, 480, 500));
	}

	@Test
	public void aPositionAlreadyClearOfTheGridIsLeftAlone()
	{
		assertEquals(484, BankCategoryRenderer.clampLeftOfGrid(484, 480, 600));
	}

	@Test
	public void neverGoesLeftOfItsOwnParent()
	{
		// A grid so far left there is no room: pinned to the parent edge rather than off it.
		assertEquals(480, BankCategoryRenderer.clampLeftOfGrid(400, 480, 490));
		assertEquals(480, BankCategoryRenderer.clampLeftOfGrid(481, 480, 300));
	}

	@Test
	public void unknownGridPositionOnlyAppliesTheParentBound()
	{
		assertEquals(700, BankCategoryRenderer.clampLeftOfGrid(700, 480, Integer.MIN_VALUE));
		assertEquals(480, BankCategoryRenderer.clampLeftOfGrid(100, 480, Integer.MIN_VALUE));
	}

	// ---- vertical placement, from the real session's numbers -------------------------

	@Test
	public void placementMatchesTheObservedGoodHorizontalAndFixesTheVertical()
	{
		// Straight from the log: parentCanvas=(21,41), gridLeftCanvas=73. Horizontal was
		// already right at local x=6; vertical was bottom+96, which landed mid-grid.
		final int gridTop = 45;
		final int[] local = BankCategoryRenderer.leftStripPlacement(21, 41, 73, gridTop);

		assertEquals("horizontal was already correct and must not regress", 6, local[0]);
		assertEquals("level with the top of the grid, not floating mid-column",
			gridTop - 41, local[1]);
	}

	@Test
	public void iconSitsBesideTheFirstRowNotHalfwayDownTheColumn()
	{
		final int parentY = 41;
		for (int gridTop = parentY; gridTop < parentY + 400; gridTop += 23)
		{
			final int[] local = BankCategoryRenderer.leftStripPlacement(21, parentY, 73, gridTop);
			assertEquals(gridTop - parentY, local[1]);
		}
	}

	@Test
	public void verticalNeverGoesAboveTheParentsOwnTop()
	{
		// A grid reported above the parent (mid-relayout) must not push the icon off the top.
		final int[] local = BankCategoryRenderer.leftStripPlacement(21, 41, 73, 10);
		assertEquals(0, local[1]);
	}

	@Test
	public void placementIsAlwaysInsideTheStripAndBelowTheParentTop()
	{
		for (int parentX = 0; parentX <= 400; parentX += 37)
		{
			for (int parentY = 0; parentY <= 400; parentY += 41)
			{
				for (int gridLeft = parentX + 30; gridLeft <= parentX + 200; gridLeft += 29)
				{
					for (int gridTop = parentY - 50; gridTop <= parentY + 300; gridTop += 53)
					{
						final int[] local =
							BankCategoryRenderer.leftStripPlacement(parentX, parentY, gridLeft, gridTop);
						assertTrue("x local must be >= 0", local[0] >= 0);
						assertTrue("y local must be >= 0", local[1] >= 0);
						assertTrue("right edge must clear the grid",
							parentX + local[0] + ICON <= gridLeft);
					}
				}
			}
		}
	}

	@Test
	public void clearsTheGridAcrossAWideRangeOfLayouts()
	{
		for (int parent = 0; parent <= 800; parent += 40)
		{
			for (int grid = parent; grid <= parent + 400; grid += 37)
			{
				for (int desired = parent - 100; desired <= grid + 200; desired += 29)
				{
					final int x = BankCategoryRenderer.clampLeftOfGrid(desired, parent, grid);
					assertTrue("left of parent: " + x, x >= parent);
					// It can only overlap the grid when the strip is too narrow to fit the icon,
					// in which case being pinned to the parent edge is the best available.
					if (grid - parent >= ICON + MARGIN)
					{
						assertTrue("overlaps grid: right=" + rightEdge(x) + " grid=" + grid,
							rightEdge(x) <= grid);
					}
				}
			}
		}
	}
}
