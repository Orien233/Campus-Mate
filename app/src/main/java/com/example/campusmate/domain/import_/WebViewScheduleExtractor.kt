package com.example.campusmate.domain.import_

import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject

/** Extracts page HTML with evaluateJavascript; no addJavascriptInterface is used. */
class WebViewScheduleExtractor {
    fun prepare(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
    }

    fun extractHtml(webView: WebView, onResult: (String?) -> Unit) {
        extractBestHtmlOnce(webView, onResult)
    }

    /**
     * Polls for a schedule table to appear (useful for SPA/dynamic pages), then extracts a smaller
     * HTML snippet (prefer the schedule table) to improve parsing quality.
     */
    fun extractHtmlWithWait(
        webView: WebView,
        timeoutMillis: Long = 8_000L,
        intervalMillis: Long = 250L,
        onResult: (String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val startAt = System.currentTimeMillis()

        fun tick() {
            if (System.currentTimeMillis() - startAt >= timeoutMillis) {
                extractBestHtmlOnce(webView, onResult)
                return
            }
            hasScheduleTable(webView) { hasTable ->
                if (hasTable == true) {
                    extractBestHtmlOnce(webView, onResult)
                } else {
                    handler.postDelayed({ tick() }, intervalMillis)
                }
            }
        }

        tick()
    }

    private fun hasScheduleTable(webView: WebView, onResult: (Boolean?) -> Unit) {
        val script = buildHasScheduleTableScript()
        webView.evaluateJavascript(script) { value ->
            val decoded = decodeJsResult(value)
            onResult(
                when (decoded?.trim()) {
                    "1", "true" -> true
                    "0", "false" -> false
                    else -> null
                }
            )
        }
    }

    private fun extractBestHtmlOnce(webView: WebView, onResult: (String?) -> Unit) {
        val script = buildExtractBestHtmlScript()
        webView.evaluateJavascript(script) { value ->
            onResult(decodeJsResult(value))
        }
    }

    private fun decodeJsResult(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw == "null") return null
        return try {
            // evaluateJavascript returns a JSON literal (usually a quoted string).
            JSONObject("{\"v\":$raw}").optString("v").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Best-effort fallback for non-JSON cases.
            raw.trim('"')
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
        }
    }

    private fun buildHasScheduleTableScript(): String {
        // Keep this script short and robust; do not depend on framework globals.
        return """
            (function(){
              try {
                var tables = document.querySelectorAll('table');
                if (!tables || tables.length === 0) return "0";
                function scoreTable(t) {
                  var text = (t.innerText || "").trim();
                  if (!text) return 0;
                  var score = 0;
                  if (text.indexOf("星期一") >= 0) score += 2;
                  if (text.indexOf("星期日") >= 0) score += 2;
                  if (text.indexOf("第1节") >= 0) score += 2;
                  if (text.indexOf("第2节") >= 0) score += 1;
                  if (text.indexOf("[" ) >= 0 && text.indexOf("]") >= 0) score += 1; // e.g. [08:00-09:50]
                  // Prefer larger tables.
                  score += Math.min(3, Math.floor(text.length / 300));
                  return score;
                }
                var best = 0;
                for (var i=0;i<tables.length;i++){
                  best = Math.max(best, scoreTable(tables[i]));
                }
                return best >= 4 ? "1" : "0";
              } catch (e) {
                return "0";
              }
            })();
        """.trimIndent()
    }

    private fun buildExtractBestHtmlScript(): String {
        // Extract the best-matching schedule table if present; otherwise return full page HTML.
        return """
            (function(){
              try {
                var tables = document.querySelectorAll('table');
                function scoreTable(t) {
                  var text = (t.innerText || "").trim();
                  if (!text) return 0;
                  var score = 0;
                  if (text.indexOf("星期一") >= 0) score += 2;
                  if (text.indexOf("星期日") >= 0) score += 2;
                  if (text.indexOf("第1节") >= 0) score += 2;
                  if (text.indexOf("第2节") >= 0) score += 1;
                  if (text.indexOf("[" ) >= 0 && text.indexOf("]") >= 0) score += 1;
                  score += Math.min(3, Math.floor(text.length / 300));
                  return score;
                }
                var bestTable = null;
                var bestScore = 0;
                for (var i=0;i<tables.length;i++){
                  var s = scoreTable(tables[i]);
                  if (s > bestScore) { bestScore = s; bestTable = tables[i]; }
                }
                if (bestTable && bestScore >= 4) {
                  return bestTable.outerHTML;
                }
                return document.documentElement.outerHTML;
              } catch (e) {
                return document.documentElement ? document.documentElement.outerHTML : "";
              }
            })();
        """.trimIndent()
    }
}
