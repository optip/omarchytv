package com.omarchytv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** v2.14: screensaver mode "web": URL resolution, the URL rule, the pixel shift and the config defaults. */
public class WebSaverTest {

    private static final List<LauncherConfig.WebApp> APPS = Arrays.asList(
            new LauncherConfig.WebApp("energy", "Energy", "http://10.5.10.56:8080/x.html", "", "desktop"),
            new LauncherConfig.WebApp("x", "X", "https://x.com", "", "mobile"),
            new LauncherConfig.WebApp("public", "Public", "http://8.8.8.8/x.html", "", "mobile"));

    private static String url(String webapp, String url) {
        LauncherConfig.WebApp t = WebSaver.target(webapp, url, APPS);
        return t == null ? "" : t.url;
    }

    @Test
    public void webappWinsAndKeepsItsUa() {
        assertEquals("http://10.5.10.56:8080/x.html", url("energy", "https://example.com/other"));
        assertEquals("http://10.5.10.56:8080/x.html", url("Energy", ""));   // same slug rule as the webapps ids
        assertEquals("desktop", WebSaver.target("energy", null, APPS).ua);
        assertEquals("https://x.com", url("x", null));
    }

    @Test
    public void missingWebappUsesTheUrlKey() {
        assertEquals("https://example.com/dash", url("nothere", "https://example.com/dash"));
        assertEquals("http://10.5.10.56:8080/x.html", url("", " http://10.5.10.56:8080/x.html "));
        assertEquals("", WebSaver.target(null, "https://example.com/dash", APPS).ua);   // the WebView's own UA
        assertEquals("https://example.com/dash", WebSaver.target("energy", "https://example.com/dash", null).url);
    }

    @Test
    public void nothingGivesEmpty() {
        assertEquals("", url("", ""));
        assertEquals("", url(null, null));
        assertEquals("", url("nothere", ""));
        assertNull(WebSaver.target("energy", "", null));   // no webapps list, no url
    }

    @Test
    public void theLanHttpRule() {
        assertEquals("http://10.1.2.3/d.html", url(null, "http://10.1.2.3/d.html"));
        assertEquals("http://192.168.1.20:8080/", url(null, "http://192.168.1.20:8080/"));
        assertEquals("https://dash.example.com/", url(null, "https://dash.example.com/"));
        assertEquals("", url(null, "http://8.8.8.8/d.html"));
        assertEquals("", url(null, "http://dash.local/d.html"));
        assertEquals("", url(null, "http://example.com/d.html"));
        assertEquals("", url(null, "file:///sdcard/x.html"));
        assertEquals("", url(null, "javascript:alert(1)"));
        assertEquals("", url("public", ""));   // the webapp exists, but its URL is not allowed
    }

    @Test
    public void shiftStaysWithin8dpAndOrbits() {
        for (float density : new float[]{1f, 2f, 3f}) {
            float[] start = WebSaver.shiftOffset(0, density);
            for (int s = 0; s < 32; s++) {
                float[] o = WebSaver.shiftOffset(s, density), n = WebSaver.shiftOffset(s + 1, density);
                assertTrue(Math.abs(o[0]) <= 8 * density && Math.abs(o[1]) <= 8 * density);
                assertTrue("step " + s + " moves", o[0] != n[0] || o[1] != n[1]);
            }
            assertArrayEquals(start, WebSaver.shiftOffset(8, density));
            assertArrayEquals(start, WebSaver.shiftOffset(-8, density));
            assertArrayEquals(WebSaver.shiftOffset(7, density), WebSaver.shiftOffset(-1, density));
        }
        assertEquals(12f, WebSaver.shiftOffset(0, 2f)[0], 0f);
    }

    private static void assertArrayEquals(float[] a, float[] b) {
        org.junit.Assert.assertArrayEquals(a, b, 0f);
    }

    @Test
    public void sameOriginOnly() {
        assertTrue(WebSaver.sameOrigin("http://10.5.10.56:8080/x.html", "http://10.5.10.56:8080/rest/items"));
        assertTrue(WebSaver.sameOrigin("https://Dash.example.com/a", "https://dash.example.com:443/b"));
        assertTrue(WebSaver.sameOrigin("http://10.0.0.1/a", "http://10.0.0.1:80/b"));
        assertFalse(WebSaver.sameOrigin("http://10.5.10.56:8080/x.html", "http://10.5.10.56:8081/x.html"));
        assertFalse(WebSaver.sameOrigin("http://10.5.10.56/x.html", "https://10.5.10.56/x.html"));
        assertFalse(WebSaver.sameOrigin("http://10.5.10.56/x.html", "http://10.5.10.57/x.html"));
        assertFalse(WebSaver.sameOrigin("http://10.5.10.56/x.html", "intent://x#Intent;end"));
        assertFalse(WebSaver.sameOrigin("http://10.5.10.56/x.html", "not a url"));
    }

    /** Existing configs keep their art and mode; mode "web" stays "web". */
    @Test
    public void configDefaults() throws Exception {
        try {
            String[][] cases = {
                    // screensaver JSON, art, mode
                    {null, "aquarium", "aquarium"},
                    {"{}", "aquarium", "aquarium"},
                    {"{\"art\":\"omarchy\"}", "omarchy", "logo"},
                    {"{\"art\":\"aquarium-classic\"}", "aquarium-classic", "aquarium"},
                    {"{\"mode\":\"logo\"}", "omarchy-tv", "logo"},
                    {"{\"mode\":\"aquarium\"}", "aquarium", "aquarium"},
                    {"{\"mode\":\"logo\",\"art\":\"aquarium\"}", "aquarium", "aquarium"},
                    {"{\"mode\":\"web\",\"webapp\":\"energy\"}", "omarchy-tv", "web"},
                    {"{\"mode\":\"web\",\"art\":\"aquarium\"}", "aquarium", "web"},
                    {"{\"mode\":\"web\",\"art\":\"aquarium-classic\"}", "aquarium-classic", "web"},
            };
            for (String[] c : cases) {
                JSONObject j = new JSONObject();
                if (c[0] != null) j.put("screensaver", new JSONObject(c[0]));
                LauncherConfig cfg = new LauncherConfig(null, j, 1L);
                assertEquals(c[0], c[1], cfg.screensaverArt);
                assertEquals(c[0], c[2], cfg.screensaverMode);
                assertTrue(c[0], cfg.screensaverShift);
                assertNull(c[0], c[0] != null && c[0].contains("web") ? null : WebSaver.target(cfg));
            }
            JSONObject j = new JSONObject("{\"webapps\":[{\"id\":\"energy\",\"name\":\"Energy\",\"url\":\"http://10.5.10.56:8080/x.html\",\"ua\":\"desktop\"}],"
                    + "\"screensaver\":{\"mode\":\"web\",\"webapp\":\"energy\",\"url\":\"https://example.com/\",\"shift\":false}}");
            LauncherConfig cfg = new LauncherConfig(null, j, 2L);
            assertFalse(cfg.screensaverShift);
            assertNotNull(WebSaver.target(cfg));
            assertEquals("http://10.5.10.56:8080/x.html", WebSaver.target(cfg).url);
            j.getJSONObject("screensaver").put("mode", "logo");
            assertNull(WebSaver.target(new LauncherConfig(null, j, 3L)));   // only mode "web" shows a page
        } finally {
            Theme.resolve(null, new JSONObject().put("name", "tokyo-night"));
        }
    }
}
