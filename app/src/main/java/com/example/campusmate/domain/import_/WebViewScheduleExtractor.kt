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
     * Polls for a schedule table or course cards to appear (useful for SPA/dynamic pages), then
     * extracts a smaller HTML snippet to improve local and AI parsing quality.
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
            onResult(sanitizeHtml(decodeJsResult(value)))
        }
    }

    private fun sanitizeHtml(html: String?): String? {
        val value = html?.trim().orEmpty()
        if (value.isBlank()) return null
        return value
            .replace(
                Regex("(?is)<(script|style|noscript|iframe)\\b[^>]*>.*?</\\1\\s*>"),
                ""
            )
            .take(MAX_HTML_LENGTH)
            .takeIf { it.isNotBlank() }
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
                var cards = document.querySelectorAll('[data-weekday],[data-day],[data-course],[class*="course"],[class*="lesson"],[class*="schedule"]');
                function scoreText(text) {
                  text = (text || "").trim();
                  if (!text) return 0;
                  var score = 0;
                  if (/(星期|周)\s*[一二三四五六日天1-7]|\b(mon|tue|wed|thu|fri|sat|sun)\b/i.test(text)) score += 3;
                  if (/第?\s*\d{1,2}\s*节?(?:\s*[-~—至到]\s*\d{1,2}\s*节?)?/.test(text)) score += 3;
                  if (/第?\s*\d{1,2}\s*[-~—至到]\s*\d{1,2}\s*周|单周|双周/.test(text)) score += 1;
                  return score;
                }
                function scoreTable(t) {
                  var text = (t.innerText || "").trim();
                  if (!text) return 0;
                  var score = scoreText(text);
                  if (text.indexOf("[" ) >= 0 && text.indexOf("]") >= 0) score += 1; // e.g. [08:00-09:50]
                  // Prefer larger tables.
                  score += Math.min(3, Math.floor(text.length / 300));
                  return score;
                }
                var best = 0;
                for (var i=0;i<tables.length;i++){
                  best = Math.max(best, scoreTable(tables[i]));
                }
                for (var j=0;j<cards.length;j++){
                  best = Math.max(best, scoreText(cards[j].innerText || ""));
                }
                return best >= 5 ? "1" : "0";
              } catch (e) {
                return "0";
              }
            })();
        """.trimIndent()
    }

    private fun buildExtractBestHtmlScript(): String {
        // Extract the best-matching schedule table or card collection; otherwise return full page HTML.
        return """
            (function(){
              try {
                var tables = document.querySelectorAll('table');
                var cards = document.querySelectorAll('[data-weekday],[data-day],[data-course],[class*="course"],[class*="lesson"],[class*="schedule"]');
                function scoreText(text) {
                  text = (text || "").trim();
                  if (!text) return 0;
                  var score = 0;
                  if (/(星期|周)\s*[一二三四五六日天1-7]|\b(mon|tue|wed|thu|fri|sat|sun)\b/i.test(text)) score += 3;
                  if (/第?\s*\d{1,2}\s*节?(?:\s*[-~—至到]\s*\d{1,2}\s*节?)?/.test(text)) score += 3;
                  if (/第?\s*\d{1,2}\s*[-~—至到]\s*\d{1,2}\s*周|单周|双周/.test(text)) score += 1;
                  return score;
                }
                function scoreTable(t) {
                  var text = (t.innerText || "").trim();
                  if (!text) return 0;
                  var score = scoreText(text);
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
                function clean(node) {
                  var clone = node.cloneNode(true);
                  var remove = clone.querySelectorAll('script,style,noscript,iframe,input,textarea,select');
                  for (var j=remove.length-1;j>=0;j--) remove[j].remove();
                  return clone;
                }
                if (bestTable && bestScore >= 5) {
                  return clean(bestTable).outerHTML.slice(0, 64000);
                }
                var cardHtml = [];
                var seen = {};
                for (var j=0;j<cards.length;j++) {
                  var card = cards[j];
                  var cardText = (card.innerText || "").trim();
                  if (scoreText(cardText) < 5 || seen[cardText]) continue;
                  seen[cardText] = true;
                  cardHtml.push(clean(card).outerHTML);
                  if (cardHtml.join("").length >= 64000) break;
                }
                if (cardHtml.length > 0) {
                  return '<div data-campusmate-card-schedule="true">' + cardHtml.join("") + '</div>';
                }
                var root = document.body || document.documentElement;
                return root ? clean(root).outerHTML.slice(0, 64000) : "";
              } catch (e) {
                return "";
              }
            })();
        """.trimIndent()
    }

    companion object {
        private const val MAX_HTML_LENGTH = 64_000
    }
}
