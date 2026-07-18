/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import me.him188.ani.app.platform.Context
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.utils.ktor.UrlHelpers
import me.him188.ani.utils.platform.annotations.TestOnly

interface WebViewVideoExtractor {
    sealed class Instruction {
        /**
         * 继续加载这个链接
         */
        data object LoadPage : Instruction()

        /**
         * 已经找到资源, 停止加载
         */
        data object FoundResource : Instruction()

        data object Continue : Instruction()
    }

    suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource?

    companion object {
        const val DEFAULT_TIMEOUT = 8_000L
    }
}

data class WebResource(
    val url: String
)

internal const val DOM_MEDIA_URL_MESSAGE_PREFIX = "__ani_dom_media_url__:"
internal const val DOM_MEDIA_URL_JAVASCRIPT_INTERFACE = "AniDomMediaUrlScanner"
internal const val INLINE_SCRIPT_URL_MESSAGE_PREFIX = "__ani_inline_script_url__:"
internal const val INLINE_SCRIPT_URL_JAVASCRIPT_INTERFACE = "AniInlineScriptUrlScanner"

internal const val INLINE_SCRIPT_URL_MAX_SCRIPT_LENGTH = 256 * 1024
internal const val INLINE_SCRIPT_URL_MAX_SCRIPT_SCANS = 512
internal const val INLINE_SCRIPT_URL_MAX_CANDIDATES_PER_SCRIPT = 64
internal const val INLINE_SCRIPT_URL_MAX_CANDIDATES = 256
internal const val INLINE_SCRIPT_URL_MAX_LENGTH = 4 * 1024

/**
 * Converts DOM attribute values to absolute URLs and suppresses repeats for one extraction.
 *
 * DOM implementations normally expose an absolute value through an element's `src` property,
 * but normalizing again here also covers platform bridges that report the raw attribute value.
 */
internal class DomMediaUrlCollector {
    private val seenUrls = mutableSetOf<String>()

    fun collect(baseUrl: String, rawUrl: String): String? {
        val normalized = rawUrl.trim().takeIf { it.isNotEmpty() } ?: return null
        val absoluteUrl = UrlHelpers.computeAbsoluteUrlOrNull(baseUrl, normalized) ?: return null
        return absoluteUrl.takeIf(seenUrls::add)
    }
}

/**
 * Normalizes URL candidates found in inline scripts and bounds matcher invocations per extraction.
 *
 * The page-side scanner already limits the amount of script text and candidate count it processes.
 * These checks form a second boundary at the native bridge, where input must still be treated as
 * untrusted.
 */
internal class InlineScriptUrlCollector(
    private val maxCandidates: Int = INLINE_SCRIPT_URL_MAX_CANDIDATES,
    private val maxUrlLength: Int = INLINE_SCRIPT_URL_MAX_LENGTH,
) {
    init {
        require(maxCandidates > 0)
        require(maxUrlLength > 0)
    }

    private val seenUrls = mutableSetOf<String>()

    fun collect(baseUrl: String, rawUrl: String): String? {
        if (seenUrls.size >= maxCandidates) return null
        if (rawUrl.length.toLong() > maxUrlLength.toLong() * 2) return null

        val normalized = rawUrl
            .trim()
            .replace("\\/", "/")
            .trimEnd(',', ';', ')', ']', '}', '\\')
        if (normalized.isEmpty() || normalized.length > maxUrlLength) return null
        if (
            !normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val absoluteUrl = UrlHelpers.computeAbsoluteUrlOrNull(baseUrl, normalized) ?: return null
        if (absoluteUrl.length > maxUrlLength) return null
        return absoluteUrl.takeIf(seenUrls::add)
    }
}

/**
 * Installs a per-document scanner for media and iframe `src` attributes.
 *
 * [reportUrlStatement] is JavaScript that can use the local `url` variable to bridge the value
 * back to the platform implementation.
 */
internal fun domMediaUrlScannerScript(reportUrlStatement: String): String = """
    (function() {
        const scannerKey = "__aniDomMediaUrlScanner";
        const previousScanner = window[scannerKey];
        if (previousScanner && typeof previousScanner.stop === "function") {
            previousScanner.stop();
        }

        const selector = "iframe[src],video[src],source[src],audio[src]";
        const seenUrls = new Set();
        let observer = null;
        let stopped = false;

        function reportElement(element) {
            if (stopped || !element || typeof element.matches !== "function" || !element.matches(selector)) {
                return;
            }
            const url = element.src;
            if (!url || seenUrls.has(url)) {
                return;
            }
            seenUrls.add(url);
            try {
                $reportUrlStatement
            } catch (error) {
                console.warn("[AniDomMediaUrlScanner] Failed to report URL:", error);
            }
        }

        function scan(root) {
            if (!root) {
                return;
            }
            reportElement(root);
            if (typeof root.querySelectorAll === "function") {
                root.querySelectorAll(selector).forEach(reportElement);
            }
        }

        function start() {
            if (stopped) {
                return;
            }
            scan(document);
            if (!document.documentElement) {
                return;
            }
            observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    if (mutation.type === "attributes") {
                        reportElement(mutation.target);
                    } else {
                        mutation.addedNodes.forEach(scan);
                    }
                });
            });
            observer.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ["src"]
            });
        }

        function onReady() {
            start();
        }

        window[scannerKey] = {
            stop: function() {
                stopped = true;
                document.removeEventListener("DOMContentLoaded", onReady);
                if (observer) {
                    observer.disconnect();
                    observer = null;
                }
            }
        };

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", onReady, { once: true });
        } else {
            start();
        }
    })();
""".trimIndent()

internal fun stopDomMediaUrlScannerScript(): String = """
    (function() {
        const scannerKey = "__aniDomMediaUrlScanner";
        const scanner = window[scannerKey];
        if (scanner && typeof scanner.stop === "function") {
            scanner.stop();
        }
        delete window[scannerKey];
    })();
""".trimIndent()

/**
 * Installs a bounded per-document observer for URL candidates in inline `script` text.
 *
 * The script never evaluates the text or reads external script responses. It only reports strings
 * that already look like absolute HTTP(S) URLs. [reportUrlStatement] may use the local `url`
 * variable to bridge a candidate back to the platform implementation.
 */
internal fun inlineScriptUrlScannerScript(reportUrlStatement: String): String = """
    (function() {
        const scannerKey = "__aniInlineScriptUrlScanner";
        const previousScanner = window[scannerKey];
        if (previousScanner && typeof previousScanner.stop === "function") {
            previousScanner.stop();
        }

        const selector = "script:not([src])";
        const maxScriptLength = $INLINE_SCRIPT_URL_MAX_SCRIPT_LENGTH;
        const maxScriptScans = $INLINE_SCRIPT_URL_MAX_SCRIPT_SCANS;
        const maxCandidatesPerScript = $INLINE_SCRIPT_URL_MAX_CANDIDATES_PER_SCRIPT;
        const maxCandidates = $INLINE_SCRIPT_URL_MAX_CANDIDATES;
        const maxCandidateLength = ${INLINE_SCRIPT_URL_MAX_LENGTH * 2};
        const urlPattern = /https?:\\?\/\\?\/[^\s"'<>`]+/gi;
        const seenUrls = new Set();
        const lastFingerprints = new WeakMap();
        let scannedScriptCount = 0;
        let reportedCandidateCount = 0;
        let observer = null;
        let stopped = false;

        function stop() {
            stopped = true;
            document.removeEventListener("DOMContentLoaded", onReady);
            if (observer) {
                observer.disconnect();
                observer = null;
            }
        }

        function reportCandidate(rawUrl) {
            if (stopped || !rawUrl || rawUrl.length > maxCandidateLength) {
                return;
            }
            const normalizedUrl = rawUrl.replace(/\\\//g, "/");
            if (seenUrls.has(normalizedUrl)) {
                return;
            }
            seenUrls.add(normalizedUrl);
            reportedCandidateCount += 1;
            const url = rawUrl;
            try {
                $reportUrlStatement
            } catch (error) {
                console.warn("[AniInlineScriptUrlScanner] Failed to report URL:", error);
            }
            if (reportedCandidateCount >= maxCandidates) {
                stop();
            }
        }

        function fingerprint(text) {
            let hash = 2166136261;
            for (let index = 0; index < text.length; index += 1) {
                hash ^= text.charCodeAt(index);
                hash = Math.imul(hash, 16777619);
            }
            return text.length + ":" + (hash >>> 0);
        }

        function scanScript(script) {
            if (
                stopped ||
                !script ||
                typeof script.matches !== "function" ||
                !script.matches(selector)
            ) {
                return;
            }
            if (scannedScriptCount >= maxScriptScans) {
                stop();
                return;
            }

            const text = (script.textContent || "").slice(0, maxScriptLength);
            const currentFingerprint = fingerprint(text);
            if (lastFingerprints.get(script) === currentFingerprint) {
                return;
            }
            lastFingerprints.set(script, currentFingerprint);
            scannedScriptCount += 1;

            urlPattern.lastIndex = 0;
            let candidateCount = 0;
            let match;
            while (!stopped && candidateCount < maxCandidatesPerScript && (match = urlPattern.exec(text)) !== null) {
                candidateCount += 1;
                reportCandidate(match[0]);
            }
            if (scannedScriptCount >= maxScriptScans) {
                stop();
            }
        }

        function scan(root) {
            if (stopped || !root) {
                return;
            }
            if (root.nodeType === Node.TEXT_NODE) {
                const ownerScript = root.parentElement && root.parentElement.closest(selector);
                scanScript(ownerScript);
                return;
            }
            scanScript(root);
            if (typeof root.querySelectorAll === "function") {
                root.querySelectorAll(selector).forEach(scanScript);
            }
        }

        function start() {
            if (stopped) {
                return;
            }
            scan(document);
            if (stopped || !document.documentElement) {
                return;
            }
            observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    if (mutation.type === "characterData" || mutation.type === "attributes") {
                        scan(mutation.target);
                    } else {
                        scanScript(mutation.target);
                        mutation.addedNodes.forEach(scan);
                    }
                });
            });
            observer.observe(document.documentElement, {
                childList: true,
                subtree: true,
                characterData: true,
                attributes: true,
                attributeFilter: ["src"]
            });
        }

        function onReady() {
            start();
        }

        window[scannerKey] = { stop: stop };
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", onReady, { once: true });
        } else {
            start();
        }
    })();
""".trimIndent()

internal fun stopInlineScriptUrlScannerScript(): String = """
    (function() {
        const scannerKey = "__aniInlineScriptUrlScanner";
        const scanner = window[scannerKey];
        if (scanner && typeof scanner.stop === "function") {
            scanner.stop();
        }
        delete window[scannerKey];
    })();
""".trimIndent()

expect fun WebViewVideoExtractor(
    proxyConfig: ProxyConfig?,
    videoResolverSettings: VideoResolverSettings,
): WebViewVideoExtractor

@TestOnly
class TestWebViewVideoExtractor(
    private val urls: (pageUrl: String) -> List<String>,
) : WebViewVideoExtractor {
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource {
        urls(pageUrl).forEach {
            if (resourceMatcher(it) is Instruction.FoundResource) {
                return WebResource(it)
            }
        }
        throw IllegalStateException("No match found")
    }
}
