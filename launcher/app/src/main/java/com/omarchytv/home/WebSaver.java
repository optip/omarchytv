package com.omarchytv.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * v2.14: screensaver mode "web". The saver shows one web page, for example an energy dashboard on the LAN.
 * omarchytv.json "screensaver": {"mode": "web", "webapp": "energy"} uses the url and ua of that "webapps" entry.
 * {"mode": "web", "url": "http://10.5.10.56:8080/x.html"} uses the URL directly. The webapp wins when it exists.
 * The URL rule is the rule of the remote panes and web apps ({@link RemotePanes#urlAllowed}). {@link TteSaver}
 * shows the native saver until the page is loaded. It keeps the native saver when the page fails.
 */
final class WebSaver {

    /** Calls from the WebView, on the main thread. */
    interface Listener {
        void loaded();

        void failed(String why);
    }

    /** Time between two pixel-shift steps. */
    static final long SHIFT_MS = 60_000;
    /** Slow orbit of 8 positions, in dp. Each value is in the range -6..6, so in +-8 dp. */
    private static final int[][] ORBIT_DP = {{6, 0}, {4, 4}, {0, 6}, {-4, 4}, {-6, 0}, {-4, -4}, {0, -6}, {4, -4}};

    private WebSaver() {
    }

    /** The page to show, or null when the mode is not "web" or the URL is missing or not allowed. */
    static LauncherConfig.WebApp target(LauncherConfig cfg) {
        if (!"web".equals(cfg.screensaverMode)) return null;
        return target(cfg.screensaverWebapp, cfg.screensaverUrl, cfg.webapps);
    }

    /**
     * Plain Java (unit tested). The "webapps" entry whose id is {@code slug(webapp)} wins. Else the direct
     * {@code url} applies (ua "" = the WebView's own UA). The result is null when its URL is not allowed.
     */
    static LauncherConfig.WebApp target(String webapp, String url, List<LauncherConfig.WebApp> apps) {
        LauncherConfig.WebApp t = null;
        if (webapp != null && !webapp.trim().isEmpty() && apps != null) {
            String id = LauncherConfig.slug(webapp.trim());
            for (LauncherConfig.WebApp w : apps) {
                if (id.equals(w.id)) {
                    t = w;
                    break;
                }
            }
        }
        if (t == null && url != null && !url.trim().isEmpty()) t = new LauncherConfig.WebApp("", "", url.trim(), "", "");
        return t != null && RemotePanes.urlAllowed(t.url) ? t : null;
    }

    /** Plain Java (unit tested): the pixel shift for a step, in px. Step 8 is step 0 again. */
    static float[] shiftOffset(int step, float density) {
        int[] o = ORBIT_DP[Math.floorMod(step, ORBIT_DP.length)];
        return new float[]{o[0] * density, o[1] * density};
    }

    /** Plain Java (unit tested): same scheme, host and port (default ports included). */
    static boolean sameOrigin(String a, String b) {
        try {
            URI x = new URI(a), y = new URI(b);
            String sx = lower(x.getScheme()), sy = lower(y.getScheme());
            return !sx.isEmpty() && sx.equals(sy) && x.getHost() != null
                    && lower(x.getHost()).equals(lower(y.getHost())) && port(x) == port(y);
        } catch (Exception e) {
            return false;
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static int port(URI u) {
        if (u.getPort() >= 0) return u.getPort();
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : "http".equalsIgnoreCase(u.getScheme()) ? 80 : -1;
    }

    /** scheme://host:port of a URL, for the log (the path and the query can hold keys). */
    static String origin(String url) {
        try {
            URI u = new URI(url);
            return lower(u.getScheme()) + "://" + u.getHost() + ":" + port(u);
        } catch (Exception e) {
            return "?";
        }
    }

    /** The WebView for the page. The caller loads {@code t.url}. It does not take focus or keys. */
    @SuppressLint("SetJavaScriptEnabled")
    static WebView make(Context c, LauncherConfig.WebApp t, Listener l) {
        WebView w = new WebView(c);
        w.setBackgroundColor(Color.BLACK);
        WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setUseWideViewPort(true);        // the page's <meta name=viewport content="width=1920">
        ws.setLoadWithOverviewMode(true);   // a 1920 px page fits a 1080p or a 4K WebView
        ws.setSupportZoom(false);
        ws.setSupportMultipleWindows(false);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        if (t.ua != null && !t.ua.isEmpty()) ws.setUserAgentString(WebAppActivity.userAgent(t.ua, ws.getUserAgentString()));
        w.setFocusable(false);
        w.setFocusableInTouchMode(false);
        w.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        final String home = t.url;
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TteSaver.TAG, "salvaschermo web console: " + m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
                }
                return true;
            }

            // No dialogs: a dialog window would take the keys that close the saver.
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                r.cancel();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                r.cancel();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
                r.cancel();
                return true;
            }

            @Override
            public boolean onJsBeforeUnload(WebView v, String url, String msg, JsResult r) {
                r.cancel();
                return true;
            }
        });
        w.setWebViewClient(new WebViewClient() {
            private boolean mainError;

            /** The saver never leaves the page's origin. */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                String to = req.getUrl().toString();
                boolean ok = sameOrigin(home, to) && RemotePanes.urlAllowed(to);
                if (!ok) Log.w(TteSaver.TAG, "salvaschermo web: navigazione bloccata verso " + origin(to));
                return !ok;
            }

            /** http:// only to a literal LAN address: the rule of WebAppActivity, for every request. */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!"http".equals(u.getScheme()) || LanHttpDataSource.allowed(u)) return null;
                return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame() || mainError) return;
                mainError = true;
                l.failed("errore " + err.getErrorCode() + " " + err.getDescription());
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse res) {
                if (!req.isForMainFrame() || mainError || res.getStatusCode() < 400) return;
                mainError = true;
                l.failed("HTTP " + res.getStatusCode());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mainError && url != null && !url.startsWith("about:")) l.loaded();
            }

            /** Keep the app alive when the page's renderer stops (API 26+). */
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail d) {
                mainError = true;
                l.failed("renderer terminato");
                return true;
            }
        });
        return w;
    }
}
