package com.omarchytv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Home row: geometry.homeTiles is 5–8 (default 6). Tiles stay 16:9 and fit a 1920×1080 row.
 * The same box is used for app banners and web-app tiles. Left/Right stops at either end of the list.
 */
public class HomeTilesTest {
    @Test
    public void missingOrOutOfRangeUsesTheDefault() {
        assertEquals(6, LauncherConfig.homeTileCount(6, 6));
        assertEquals(6, LauncherConfig.homeTileCount(0, 6));
        assertEquals(6, LauncherConfig.homeTileCount(4, 6));
        assertEquals(6, LauncherConfig.homeTileCount(9, 6));
        assertEquals(7, LauncherConfig.homeTileCount(9, 7));   // bad value keeps a valid config.xml default
        assertEquals(6, LauncherConfig.homeTileCount(3, 99));  // a bad default still lands on 6
        for (int n = LauncherConfig.HOME_TILES_MIN; n <= LauncherConfig.HOME_TILES_MAX; n++) {
            assertEquals(n, LauncherConfig.homeTileCount(n, 6));
        }
    }

    @Test
    public void settingStepsThroughFiveToEight() {
        assertEquals(6, LauncherConfig.nextHomeTiles(5));
        assertEquals(7, LauncherConfig.nextHomeTiles(6));
        assertEquals(8, LauncherConfig.nextHomeTiles(7));
        assertEquals(5, LauncherConfig.nextHomeTiles(8));
        assertEquals(5, LauncherConfig.nextHomeTiles(0));
    }

    @Test
    public void everyCountFitsThe1080pRowAndKeepsAspect() {
        int prev = Integer.MAX_VALUE;
        for (int cols = 5; cols <= 8; cols++) {
            int[] box = HomeV29.homeTileSize(cols, HomeV29.HOME_ROW_INNER, HomeV29.HOME_ROW_GAP);
            int w = box[0], h = box[1];
            int used = cols * w + (cols - 1) * HomeV29.HOME_ROW_GAP;
            assertTrue(cols + " tiles fit the row", used <= HomeV29.HOME_ROW_INNER);
            assertTrue(cols + " leaves less than one tile of slack", HomeV29.HOME_ROW_INNER - used < w);
            assertEquals(w * 9 / 16, h);
            assertEquals(16d / 9d, w / (double) h, 0.02);
            assertTrue(cols + " is narrower than the previous count", w < prev);
            // Focus grows 5%. Only one tile is focused, so the growth has to stay inside the gap.
            assertTrue(cols + " focus scale stays in the gap", w * 0.05f < HomeV29.HOME_ROW_GAP);
            prev = w;
        }
        int[] six = HomeV29.homeTileSize(6, HomeV29.HOME_ROW_INNER, HomeV29.HOME_ROW_GAP);
        assertEquals(282, six[0]);
        assertEquals(158, six[1]);
    }

    @Test
    public void dockAndCompanionStayBelowThePanels() {
        for (int cols = 5; cols <= 8; cols++) {
            int top = 1080 - HomeV29.homeWindowExtent(cols);
            assertTrue(cols + " dock stays under the widget band", top > 560);
            assertTrue(cols + " companion sits above the dock",
                    HomeV29.companionBottomMargin(cols) >= HomeV29.homeWindowExtent(cols));
        }
        assertEquals(HomeV29.COMPANION_CLEAR_6, HomeV29.companionBottomMargin(6));
    }

    @Test
    public void dpadStopsAtEitherEndForEveryCount() {
        for (int visible = 5; visible <= 8; visible++) {
            int apps = 12;   // more tiles than fit: the rest scroll, they do not wrap
            assertEquals(0, HomeV29.homeRowStep(0, apps, -1));
            assertEquals(1, HomeV29.homeRowStep(0, apps, 1));
            assertEquals(visible - 1, HomeV29.homeRowStep(visible - 2, apps, 1));
            assertEquals(visible, HomeV29.homeRowStep(visible - 1, apps, 1));   // past the screen, still in the list
            assertEquals(apps - 1, HomeV29.homeRowStep(apps - 1, apps, 1));
            assertEquals(apps - 2, HomeV29.homeRowStep(apps - 1, apps, -1));
        }
        assertEquals(0, HomeV29.homeRowStep(0, 1, 1));
        assertEquals(0, HomeV29.homeRowStep(0, 1, -1));
        assertEquals(-1, HomeV29.homeRowStep(-1, 6, -1));   // no tile under the key: stay
        assertEquals(0, HomeV29.homeRowStep(-1, 6, 1));
    }
}
