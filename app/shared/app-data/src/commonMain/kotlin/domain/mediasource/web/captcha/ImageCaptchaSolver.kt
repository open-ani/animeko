/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaDetector
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.isSearchCooldownPage
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.appendText
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Html
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

/** 平台侧图片验证码识别入口。识别器只负责“图片字节 -> 四位数字”。 */
fun interface ImageCaptchaRecognizer {
    suspend fun recognize(sample: ImageCaptchaSample): String?
}

object UnsupportedImageCaptchaRecognizer : ImageCaptchaRecognizer {
    override suspend fun recognize(sample: ImageCaptchaSample): String? = null
}

data class ImageCaptchaSample(
    val bytes: ByteArray,
    val mediaType: String,
    val sourceUrl: String,
)

data class ImageCaptchaSampleCollectionResult(
    val savedCount: Int,
    val outputDirectory: String,
)

/**
 * MacCMS 图片验证码后台协议。该策略不创建浏览器，Android/Desktop/iOS 共用同一实现。
 *
 * Cookie 由 [SolveContext.http] 所使用的 WebSourceCookieJar 维护，因此不再复制旧 coordinator
 * 中三套不同的 Cookie/重定向实现。
 */
class MacCmsImageCaptchaSolver(
    private val recognizer: ImageCaptchaRecognizer,
) : CaptchaSolver {
    override val id: String = "maccms-image-captcha"

    override fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean {
        if (recognizer === UnsupportedImageCaptchaRecognizer) return false
        return reason.kind == WebCaptchaKind.Image || host in KNOWN_MACCMS_IMAGE_CAPTCHA_HOSTS
    }

    override suspend fun attempt(ctx: SolveContext): SolveOutcome {
        val session = MacCmsSession(ctx)
        return solveImageCaptcha(
            request = ctx.request,
            recognizer = recognizer,
            captureSample = session::captureSample,
            submitAnswer = session::submitAnswer,
            evaluate = ctx.evaluate,
            onSolved = ctx.retainSolvedPage,
        )
    }
}

/** 使用 WebView/JCEF DOM 的通用图片验证码策略。 */
class BrowserImageCaptchaSolver(
    private val recognizer: ImageCaptchaRecognizer,
) : CaptchaSolver {
    override val id: String = "browser-image-captcha"

    override fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean {
        return recognizer !== UnsupportedImageCaptchaRecognizer && reason.kind == WebCaptchaKind.Image
    }

    override suspend fun attempt(ctx: SolveContext): SolveOutcome {
        val browser = try {
            ctx.acquireBrowser()
        } catch (_: UnsupportedOperationException) {
            return SolveOutcome.Unsupported
        }
        browser.navigate(ctx.request.pageUrl)
        if (!browser.awaitImageCaptchaPage(ctx.request)) {
            return SolveOutcome.Failed(BlockReason.Captcha(ctx.request.kind))
        }
        return solveImageCaptcha(
            request = ctx.request,
            recognizer = recognizer,
            captureSample = { previousBytes ->
                previousBytes?.let { browser.captureRefreshedImageCaptchaSample(it) }
                    ?: browser.captureImageCaptchaSample()
            },
            submitAnswer = { answer ->
                browser.executeJavaScript(buildSubmitImageCaptchaScript(answer))
                browser.awaitSolvedPage(ctx.request, ctx.evaluate)
            },
            evaluate = ctx.evaluate,
            onSolved = ctx.retainSolvedPage,
        )
    }
}

private suspend fun solveImageCaptcha(
    request: SolveRequest,
    recognizer: ImageCaptchaRecognizer,
    captureSample: suspend (previousBytes: ByteArray?) -> ImageCaptchaSample?,
    submitAnswer: suspend (answer: String) -> LoadedPage?,
    evaluate: suspend (LoadedPage) -> PageVerdict<*>,
    onSolved: (LoadedPage) -> Unit,
    maxAttempts: Int = IMAGE_CAPTCHA_MAX_ATTEMPTS,
): SolveOutcome {
    var previousSampleBytes: ByteArray? = null
    var lastReason: BlockReason? = BlockReason.Captcha(request.kind)
    repeat(maxAttempts) { attempt ->
        val sample = captureSample(previousSampleBytes)
        if (sample == null) {
            logger.warn { "Could not capture a changed image captcha for ${request.pageUrl}" }
            return@repeat
        }
        previousSampleBytes = sample.bytes
        val answer = recognizer.recognize(sample)
            ?.trim()
            ?.takeIf(::isValidImageCaptchaAnswer)
        if (answer == null) {
            logger.warn { "Recognizer returned no valid answer for ${sample.sourceUrl}" }
            return@repeat
        }

        logger.debug { "Submitting image captcha attempt ${attempt + 1}/$maxAttempts for ${request.pageUrl}" }
        val solvedPage = submitAnswer(answer)
        if (solvedPage != null) {
            val verdict = evaluate(solvedPage)
            if (isSuccessfulSolve(solvedPage, verdict, request)) {
                onSolved(solvedPage)
                logger.info { "Solved image captcha on attempt ${attempt + 1}/$maxAttempts for ${request.pageUrl}" }
                return SolveOutcome.Solved
            }
            lastReason = (verdict as? PageVerdict.Blocked)?.reason
        }
    }
    return SolveOutcome.Failed(lastReason)
}

private class MacCmsSession(
    private val ctx: SolveContext,
) {
    private data class Response(
        val finalUrl: String,
        val statusCode: Int,
        val contentType: String?,
        val bytes: ByteArray,
    )

    private var effectiveOrigin = ctx.request.pageUrl.originOrNull()
    private var effectivePageUrl = ctx.request.pageUrl
    private var requestIndex = 0

    suspend fun captureSample(previousBytes: ByteArray?): ImageCaptchaSample? {
        val origin = effectiveOrigin ?: return null
        repeat(IMAGE_CAPTCHA_HTTP_REFRESH_ATTEMPTS) {
            val sourceUrl = "$origin/index.php/verify/index.html?_ani_captcha=${currentTimeMillis()}-${requestIndex++}"
            val response = execute(sourceUrl, post = false)
            if (response != null && response.statusCode in 200..299 && response.bytes.isNotEmpty()) {
                val changed = previousBytes == null || !response.bytes.contentEquals(previousBytes)
                if (changed) {
                    return ImageCaptchaSample(
                        bytes = response.bytes,
                        mediaType = response.contentType
                            ?.substringBefore(';')
                            ?.trim()
                            ?.ifBlank { null }
                            ?: "application/octet-stream",
                        sourceUrl = response.finalUrl,
                    )
                }
            }
            delay(IMAGE_CAPTCHA_HTTP_REFRESH_INTERVAL)
        }
        return null
    }

    suspend fun submitAnswer(answer: String): LoadedPage? {
        val origin = effectiveOrigin ?: return null
        val response = execute(
            "$origin/index.php/ajax/verify_check?type=search&verify=$answer",
            post = true,
        ) ?: return null
        val code = runCatching {
            Json.parseToJsonElement(response.bytes.decodeToString())
                .jsonObject["code"]
                ?.jsonPrimitive
                ?.intOrNull
        }.getOrNull()
        if (response.statusCode !in 200..299 || code != 1) return null
        return execute(effectivePageUrl, post = false)?.let {
            LoadedPage(
                finalUrl = it.finalUrl,
                html = it.bytes.decodeToString(),
                status = it.statusCode,
            )
        }
    }

    private suspend fun execute(url: String, post: Boolean): Response? {
        return try {
            ctx.http.use {
                val statement = if (post) {
                    preparePost(url) {
                        accept(ContentType.Any)
                        header(HttpHeaders.Referrer, effectivePageUrl)
                        header("X-Requested-With", "XMLHttpRequest")
                        header(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
                        setBody(ByteArray(0))
                    }
                } else {
                    prepareGet(url) {
                        accept(ContentType.Any)
                        header(HttpHeaders.Referrer, effectivePageUrl)
                    }
                }
                statement.execute { response ->
                    Response(
                        finalUrl = response.request.url.toString(),
                        statusCode = response.status.value,
                        contentType = response.headers[HttpHeaders.ContentType],
                        bytes = response.body(),
                    )
                }
            }.also { adoptRedirectOrigin(url, it.finalUrl) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Image captcha background request failed: ${if (post) "POST" else "GET"} $url" }
            null
        }
    }

    private fun adoptRedirectOrigin(requestUrl: String, finalUrl: String) {
        val requested = runCatching { Url(requestUrl) }.getOrNull() ?: return
        val final = runCatching { Url(finalUrl) }.getOrNull() ?: return
        if (requested.encodedPath != final.encodedPath) return
        val redirectedOrigin = finalUrl.originOrNull() ?: return
        if (redirectedOrigin == effectiveOrigin) return
        effectiveOrigin = redirectedOrigin
        effectivePageUrl = URLBuilder(effectivePageUrl).apply {
            protocol = final.protocol
            host = final.host
            port = final.port
        }.buildString()
    }
}

internal suspend fun collectImageCaptchaSamplesToDirectory(
    browser: CaptchaBrowser,
    request: SolveRequest,
    count: Int,
    outputDirectory: String,
): ImageCaptchaSampleCollectionResult {
    require(count in 1..MAX_IMAGE_CAPTCHA_SAMPLE_COUNT)
    require(outputDirectory.isNotBlank())
    val directory = Path(outputDirectory).inSystem
    withContext(Dispatchers.Default) { directory.createDirectories() }
    browser.navigate(request.pageUrl)
    if (!browser.awaitImageCaptchaPage(request)) {
        return ImageCaptchaSampleCollectionResult(0, directory.absolutePath)
    }
    var previousBytes: ByteArray? = null
    var savedCount = 0
    repeat(count) { index ->
        val sample = previousBytes?.let { browser.captureRefreshedImageCaptchaSample(it) }
            ?: browser.captureImageCaptchaSample()
            ?: return@repeat
        val capturedAt = currentTimeMillis()
        val fileName = "${request.mediaSourceId.toFileNameSegment()}-$capturedAt-${index.toString().padStart(4, '0')}.${sample.fileExtension()}"
        withContext(Dispatchers.Default) {
            directory.resolve(fileName).writeBytes(sample.bytes)
            directory.resolve(IMAGE_CAPTCHA_MANIFEST_FILE).appendText(
                Json.encodeToString(
                    ImageCaptchaSampleManifestEntry(
                        fileName,
                        request.mediaSourceId,
                        request.pageUrl,
                        sample.sourceUrl,
                        sample.mediaType,
                        capturedAt,
                    ),
                ) + "\n",
            )
        }
        savedCount++
        previousBytes = sample.bytes
    }
    return ImageCaptchaSampleCollectionResult(savedCount, directory.absolutePath)
}

private suspend fun CaptchaBrowser.awaitImageCaptchaPage(request: SolveRequest): Boolean {
    repeat(IMAGE_CAPTCHA_PAGE_POLL_COUNT) {
        delay(IMAGE_CAPTURE_POLL_INTERVAL)
        val page = currentPage() ?: return@repeat
        val detected = WebCaptchaDetector.detect(page.finalUrl, page.html)
        if (detected == WebCaptchaKind.Image ||
            (request.kind == WebCaptchaKind.Image && page.html.contains("/verify/", ignoreCase = true))
        ) {
            return true
        }
    }
    return false
}

private suspend fun CaptchaBrowser.awaitSolvedPage(
    request: SolveRequest,
    evaluate: suspend (LoadedPage) -> PageVerdict<*>,
): LoadedPage? {
    repeat(IMAGE_CAPTCHA_VALIDATION_POLL_COUNT) {
        delay(IMAGE_CAPTCHA_VALIDATION_POLL_INTERVAL)
        val page = currentPage() ?: return@repeat
        if (isSuccessfulSolve(page, evaluate(page), request)) return page
    }
    return null
}

private suspend fun CaptchaBrowser.captureRefreshedImageCaptchaSample(previousBytes: ByteArray): ImageCaptchaSample? {
    repeat(IMAGE_CAPTCHA_SAMPLE_REFRESH_ATTEMPTS) {
        executeJavaScript(REFRESH_IMAGE_CAPTCHA_SCRIPT)
        delay(IMAGE_CAPTCHA_SAMPLE_REFRESH_INTERVAL)
        val sample = captureImageCaptchaSample() ?: return@repeat
        if (!sample.bytes.contentEquals(previousBytes)) return sample
    }
    return null
}

private suspend fun CaptchaBrowser.captureImageCaptchaSample(): ImageCaptchaSample? {
    executeJavaScript(CAPTURE_IMAGE_CAPTCHA_SCRIPT)
    repeat(IMAGE_CAPTURE_POLL_COUNT) {
        delay(IMAGE_CAPTURE_POLL_INTERVAL)
        val page = currentPage() ?: return@repeat
        parseImageCaptchaSample(page.html)?.let { return it }
        if (hasFailedImageCaptchaCapture(page.html)) return null
    }
    return null
}

private fun isSuccessfulSolve(page: LoadedPage, verdict: PageVerdict<*>, request: SolveRequest): Boolean {
    if (verdict is PageVerdict.Ok) return true
    if (verdict !is PageVerdict.EmptyContent || request.expectation !is PageExpectation.SearchResults) return false
    if (!page.isSearchResultLocationFor(request)) return false
    if (WebCaptchaDetector.detect(page.finalUrl, page.html) != null) return false
    val document = runCatching { Html.parse(page.html) }.getOrNull() ?: return false
    return !document.isSearchCooldownPage() && document.isExplicitEmptySearchResultPage()
}

private fun LoadedPage.isSearchResultLocationFor(request: SolveRequest): Boolean {
    val requested = runCatching { Url(request.pageUrl) }.getOrNull() ?: return false
    val actual = runCatching { Url(finalUrl) }.getOrNull() ?: return false
    val requestedPath = requested.encodedPath.trimEnd('/').ifBlank { "/" }
    val actualPath = actual.encodedPath.trimEnd('/').ifBlank { "/" }
    if (requestedPath != "/" && actualPath == "/") return false
    return requestedPath == actualPath ||
        (requestedPath.contains("search", true) && actualPath.contains("search", true))
}

private fun Document.isExplicitEmptySearchResultPage(): Boolean {
    val normalized = text().replace(Regex("\\s+"), " ").trim().lowercase()
    return EMPTY_SEARCH_RESULT_MARKERS.any { it in normalized }
}

internal fun isValidImageCaptchaAnswer(answer: String): Boolean {
    return answer.length == 4 && answer.all { it in '0'..'9' }
}

@OptIn(ExperimentalEncodingApi::class)
private fun parseImageCaptchaSample(html: String): ImageCaptchaSample? {
    val marker = runCatching { Html.parse(html) }.getOrNull()
        ?.select("#$IMAGE_CAPTCHA_MARKER_ID")
        ?.firstOrNull()
        ?: return null
    if (marker.attr("data-state") != "ready") return null
    val dataUrl = marker.attr("data-image")
    val separator = dataUrl.indexOf(',')
    if (!dataUrl.startsWith("data:") || separator < 0) return null
    val metadata = dataUrl.substring(5, separator)
    if (!metadata.endsWith(";base64")) return null
    val bytes = runCatching { Base64.decode(dataUrl.substring(separator + 1)) }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    return ImageCaptchaSample(
        bytes,
        metadata.removeSuffix(";base64").ifBlank { "application/octet-stream" },
        marker.attr("data-source"),
    )
}

private fun hasFailedImageCaptchaCapture(html: String): Boolean {
    return runCatching { Html.parse(html) }.getOrNull()
        ?.select("#$IMAGE_CAPTCHA_MARKER_ID")
        ?.firstOrNull()
        ?.attr("data-state") == "failed"
}

private fun buildSubmitImageCaptchaScript(answer: String): String = """
    (function() {
      document.getElementById('$IMAGE_CAPTCHA_MARKER_ID')?.remove();
      const selectors = ['input[name="verify"]','input[name="verifycode"]','input[name="verify_code"]',
        'input[name="captcha"]','input[name="captcha_code"]','input[name="code"]',
        'input[autocomplete="one-time-code"]','input[id*="verify" i]','input[id*="captcha" i]',
        'input[class*="verify" i]','input[class*="captcha" i]'];
      const visible = Array.from(document.querySelectorAll(
        'input:not([type]),input[type="text"],input[type="tel"],input[type="number"]'
      )).filter(e => { const s = getComputedStyle(e); return !e.disabled && s.display !== 'none' && s.visibility !== 'hidden'; });
      const input = document.querySelector(selectors.join(',')) || (visible.length === 1 ? visible[0] : null);
      if (!input) return;
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
      if (setter) setter.call(input, '$answer'); else input.value = '$answer';
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      const submit = document.querySelector('.verify-submit,[data-type="search"]') ||
        input.form?.querySelector('button[type="submit"],input[type="submit"],button:not([type])');
      if (submit) submit.click(); else if (input.form?.requestSubmit) input.form.requestSubmit();
      else if (input.form) input.form.submit();
    })();
""".trimIndent()

private val CAPTURE_IMAGE_CAPTCHA_SCRIPT = """
    (function() {
      let marker = document.getElementById('$IMAGE_CAPTCHA_MARKER_ID');
      if (!marker) { marker = document.createElement('meta'); marker.id = '$IMAGE_CAPTCHA_MARKER_ID'; document.documentElement.appendChild(marker); }
      marker.setAttribute('data-state', 'loading'); marker.removeAttribute('data-image'); marker.removeAttribute('data-source');
      const image = document.querySelector('img.ds-verify-img,img[src*="/verify/"],img[src*="captcha"],img[id*="captcha"],img[class*="captcha"]');
      if (!image) { marker.setAttribute('data-state', 'failed'); return; }
      const source = image.currentSrc || image.src;
      fetch(source, { credentials: 'include', cache: 'no-store' }).then(r => { if (!r.ok) throw new Error(); return r.blob(); })
        .then(blob => { const reader = new FileReader(); reader.onload = function() { marker.setAttribute('data-source', source); marker.setAttribute('data-image', String(reader.result || '')); marker.setAttribute('data-state', 'ready'); }; reader.onerror = function() { marker.setAttribute('data-state', 'failed'); }; reader.readAsDataURL(blob); })
        .catch(() => marker.setAttribute('data-state', 'failed'));
    })();
""".trimIndent()

private val REFRESH_IMAGE_CAPTCHA_SCRIPT = """
    (function() {
      const image = document.querySelector('img.ds-verify-img,img[src*="/verify/"],img[src*="captcha"],img[id*="captcha"],img[class*="captcha"]');
      if (image) { image.click(); const source = image.getAttribute('src') || ''; image.setAttribute('src', source + (source.includes('?') ? '&' : '?') + '_ani_captcha=' + Date.now()); }
    })();
""".trimIndent()

private fun String.originOrNull(): String? = runCatching {
    val url = Url(this)
    URLBuilder().apply {
        protocol = url.protocol
        host = url.host
        port = url.port
    }.buildString().trimEnd('/')
}.getOrNull()

private fun ImageCaptchaSample.fileExtension(): String = when (mediaType.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

private fun String.toFileNameSegment(): String = map {
    if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_'
}.joinToString("").ifBlank { "captcha" }

@Serializable
private data class ImageCaptchaSampleManifestEntry(
    val file: String,
    val mediaSourceId: String,
    val pageUrl: String,
    val sourceUrl: String,
    val mediaType: String,
    val capturedAtMillis: Long,
)

private val logger = logger("ImageCaptchaSolver")
private val KNOWN_MACCMS_IMAGE_CAPTCHA_HOSTS = setOf("acgfta.com", "cycani.org", "youknow.tv")
private val EMPTY_SEARCH_RESULT_MARKERS = listOf(
    "什么都没有", "什麼都沒有", "暂无数据", "暫無資料", "暂无相关", "暫無相關",
    "没有找到", "沒有找到", "搜索结果为空", "搜索結果為空", "no results", "no matches",
)
private const val IMAGE_CAPTCHA_MARKER_ID = "ani-image-captcha-sample"
private const val IMAGE_CAPTCHA_MAX_ATTEMPTS = 3
private const val IMAGE_CAPTCHA_HTTP_REFRESH_ATTEMPTS = 10
private const val IMAGE_CAPTCHA_PAGE_POLL_COUNT = 100
private const val IMAGE_CAPTURE_POLL_COUNT = 30
private const val IMAGE_CAPTCHA_VALIDATION_POLL_COUNT = 30
private const val IMAGE_CAPTCHA_SAMPLE_REFRESH_ATTEMPTS = 10
private const val MAX_IMAGE_CAPTCHA_SAMPLE_COUNT = 10_000
private const val IMAGE_CAPTCHA_MANIFEST_FILE = "manifest.jsonl"
private val IMAGE_CAPTCHA_HTTP_REFRESH_INTERVAL = 250.milliseconds
private val IMAGE_CAPTURE_POLL_INTERVAL = 100.milliseconds
private val IMAGE_CAPTCHA_VALIDATION_POLL_INTERVAL = 200.milliseconds
private val IMAGE_CAPTCHA_SAMPLE_REFRESH_INTERVAL = 250.milliseconds
