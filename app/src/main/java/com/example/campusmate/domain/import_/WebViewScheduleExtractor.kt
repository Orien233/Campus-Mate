package com.example.campusmate.domain.import_

import android.webkit.WebSettings
import android.webkit.WebView

/** Extracts page HTML with evaluateJavascript; no addJavascriptInterface is used. */
class WebViewScheduleExtractor {
    fun prepare(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
    }

    fun extractHtml(webView: WebView, onResult: (String?) -> Unit) {
        webView.evaluateJavascript(
            "(function(){return document.documentElement.outerHTML;})();"
        ) { value ->
            onResult(value?.trim('"')?.replace("\\u003C", "<")?.replace("\\n", "\n"))
        }
    }
}
