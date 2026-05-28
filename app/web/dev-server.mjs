#!/usr/bin/env node
import {createServer} from 'node:http';
import {createReadStream, existsSync, statSync} from 'node:fs';
import {extname, join, normalize, resolve} from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const require = createRequire(import.meta.url);
const root = resolve(__dirname, 'build/kotlin-webpack/wasmJs/developmentExecutable');
const port = Number(process.env.PORT || 8080);
const isolationHeaders = {
	'cross-origin-opener-policy': 'same-origin',
	'cross-origin-embedder-policy': 'require-corp',
};

const mimeTypes = {
	'.html': 'text/html; charset=utf-8',
	'.js': 'text/javascript; charset=utf-8',
	'.mjs': 'text/javascript; charset=utf-8',
	'.wasm': 'application/wasm',
	'.json': 'application/json; charset=utf-8',
	'.cvr': 'application/octet-stream',
	'.worker': 'text/javascript; charset=utf-8',
};

function sendText(res, status, text) {
	res.writeHead(status, {...isolationHeaders, 'content-type': 'text/plain; charset=utf-8'});
	res.end(text);
}

function sendJson(res, status, value) {
	res.writeHead(status, {
		...isolationHeaders,
		'content-type': 'application/json; charset=utf-8',
		'cache-control': 'no-store',
		'access-control-allow-origin': '*',
	});
	res.end(JSON.stringify(value));
}

async function handleProxy(req, res, requestUrl) {
	if (req.method === 'OPTIONS') {
		res.writeHead(204, {
			...isolationHeaders,
			'access-control-allow-origin': '*',
			'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
			'access-control-allow-headers': req.headers['access-control-request-headers'] || '*',
			'access-control-max-age': '86400',
		});
		res.end();
		return;
	}
	const raw = requestUrl.searchParams.get('url');
	if (!raw) return sendText(res, 400, 'Missing url');
	let target;
	try {
		target = new URL(raw);
	} catch {
		return sendText(res, 400, 'Invalid url');
	}
	if (target.protocol !== 'https:' && target.protocol !== 'http:') {
		return sendText(res, 403, 'Proxy target is not allowed');
	}

	const headers = new Headers();
	const referer = requestUrl.searchParams.get('referer');
	for (const [name, value] of Object.entries(req.headers)) {
		if (!value) continue;
		const lower = name.toLowerCase();
		if (['host', 'origin', 'referer', 'connection', 'content-length'].includes(lower)) continue;
		headers.set(name, Array.isArray(value) ? value.join(', ') : value);
	}
	if (referer) headers.set('referer', referer);
	if (!headers.has('user-agent')) {
		headers.set(
				'user-agent',
				'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
		);
	}
	if (!headers.has('accept-language')) headers.set('accept-language', 'zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7');
	if (!headers.has('accept')) headers.set('accept', '*/*');
	if (!headers.has('referer')) headers.set('referer', `${target.origin}/`);

	const body = ['GET', 'HEAD'].includes(req.method || 'GET') ? undefined : req;
	try {
		const upstream = await fetch(target, {
			method: req.method,
			headers,
			body,
			duplex: body ? 'half' : undefined
		});
		const responseHeaders = {};
		upstream.headers.forEach((value, key) => {
			const lower = key.toLowerCase();
			if (['content-encoding', 'content-length', 'transfer-encoding', 'connection'].includes(lower)) return;
			responseHeaders[key] = value;
		});
		responseHeaders['access-control-allow-origin'] = '*';
		responseHeaders['cache-control'] = responseHeaders['cache-control'] || 'no-store';
		const contentType = upstream.headers.get('content-type') || '';
		const isPlaylist = contentType.includes('mpegurl') || target.pathname.toLowerCase().endsWith('.m3u8');
		if (isPlaylist && upstream.body) {
			const playlist = await upstream.text();
			responseHeaders['content-type'] = responseHeaders['content-type'] || 'application/vnd.apple.mpegurl; charset=utf-8';
			res.writeHead(upstream.status, {...isolationHeaders, ...responseHeaders});
			res.end(rewritePlaylist(playlist, target, referer));
			return;
		}
		res.writeHead(upstream.status, {...isolationHeaders, ...responseHeaders});
		if (upstream.body) {
			for await (const chunk of upstream.body) res.write(chunk);
		}
		res.end();
	} catch (error) {
		sendText(res, 502, `Proxy request failed: ${error && error.message ? error.message : error}`);
	}
}

let browserContextPromise;

async function handleBrowserResolve(req, res, requestUrl) {
	if (req.method === 'OPTIONS') {
		res.writeHead(204, {
			...isolationHeaders,
			'access-control-allow-origin': '*',
			'access-control-allow-methods': 'GET,OPTIONS',
			'access-control-allow-headers': req.headers['access-control-request-headers'] || '*',
			'access-control-max-age': '86400',
		});
		res.end();
		return;
	}
	if (req.method !== 'GET') return sendJson(res, 405, {ok: false, error: 'Method not allowed'});

	const raw = requestUrl.searchParams.get('url');
	if (!raw) return sendJson(res, 400, {ok: false, error: 'Missing url'});

	let target;
	try {
		target = new URL(raw);
	} catch {
		return sendJson(res, 400, {ok: false, error: 'Invalid url'});
	}
	if (target.protocol !== 'https:' && target.protocol !== 'http:') {
		return sendJson(res, 403, {ok: false, error: 'Resolver target is not allowed'});
	}

	let page;
	const startedAt = Date.now();
	const timeoutMillis = Math.max(
			3000,
			Math.min(Number(requestUrl.searchParams.get('timeout') || process.env.ANIMEKO_BROWSER_RESOLVER_TIMEOUT || 20000), 60000),
	);
	const candidates = [];
	const seen = new Set();

	const addCandidate = (url, source, details = {}) => {
		if (!url || seen.has(url)) return;
		if (!isMediaCandidateUrl(url) && !isLikelyPlayerUrl(url) && !details.contentType) return;
		seen.add(url);
		candidates.push({
			url,
			source,
			contentType: details.contentType || '',
			referer: cleanReferer(details.referer) || raw,
		});
	};

	try {
		const context = await getBrowserResolverContext();
		page = await context.newPage();
		await page.setExtraHTTPHeaders({
			'accept-language': 'zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7',
		});

		page.on('request', (request) => {
			const headers = request.headers();
			addCandidate(request.url(), 'request', {referer: headers.referer || headers.referrer || raw});
		});
		page.on('response', async (response) => {
			const url = response.url();
			const headers = response.headers();
			const contentType = headers['content-type'] || '';
			if (isMediaContentType(contentType) || isMediaCandidateUrl(url)) {
				let referer = raw;
				try {
					const requestHeaders = await response.request().allHeaders();
					referer = requestHeaders.referer || requestHeaders.referrer || raw;
				} catch {
					// best effort only
				}
				addCandidate(url, 'response', {contentType, referer});
			}
		});

		await page.goto(raw, {
			waitUntil: 'domcontentloaded',
			referer: `${target.origin}/`,
			timeout: Math.min(timeoutMillis, 20000),
		}).catch(() => undefined);

		// A number of web players only start requesting the media playlist after a
		// user gesture. Click the viewport center once; ignore pages that block it.
		await page.mouse.click(640, 360).catch(() => undefined);

		const deadline = Date.now() + timeoutMillis;
		while (Date.now() < deadline) {
			if (candidates.some((candidate) => isMediaCandidateUrl(candidate.url) || isMediaContentType(candidate.contentType))) break;
			await page.waitForTimeout(500);
		}

		// Some players place the resolved URL in DOM attributes/scripts after JS
		// execution instead of requesting it immediately.
		const domUrls = await page.evaluate(() => {
			const html = document.documentElement?.outerHTML || '';
			return Array.from(new Set(
					(html.match(/https?:\\?\/\\?\/[^"'\\\s<>]+/gi) || [])
							.map((value) => value.replaceAll('\\/', '/')),
			)).slice(0, 200);
		}).catch(() => []);
		for (const url of domUrls) addCandidate(url, 'dom', {referer: page.url() || raw});

		sendJson(res, 200, {
			ok: true,
			url: raw,
			finalUrl: page.url(),
			elapsedMillis: Date.now() - startedAt,
			candidates,
		});
	} catch (error) {
		sendJson(res, 502, {
			ok: false,
			url: raw,
			elapsedMillis: Date.now() - startedAt,
			error: error && error.message ? error.message : String(error),
		});
	} finally {
		if (page) await page.close().catch(() => undefined);
	}
}

function cleanReferer(value) {
	return (value || '').split(',')[0].trim();
}

async function getBrowserResolverContext() {
	if (!browserContextPromise) {
		browserContextPromise = (async () => {
			const playwright = loadPlaywright();
			const launchOptions = {
				headless: process.env.ANIMEKO_BROWSER_RESOLVER_HEADLESS !== 'false',
				viewport: {width: 1280, height: 720},
				userAgent:
						'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
			};
			const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ||
					(existsSync('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome')
							? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
							: undefined);
			if (executablePath) launchOptions.executablePath = executablePath;
			return playwright.chromium.launchPersistentContext(
					process.env.ANIMEKO_BROWSER_RESOLVER_PROFILE || '/tmp/animeko-web-browser-resolver-profile',
					launchOptions,
			);
		})();
	}
	return browserContextPromise;
}

function loadPlaywright() {
	const candidates = [
		process.env.PLAYWRIGHT_PACKAGE_PATH,
		'playwright',
		'playwright-core',
		'/Users/him188/.npm/_npx/9833c18b2d85bc59/node_modules/playwright',
	].filter(Boolean);
	for (const candidate of candidates) {
		try {
			return require(candidate);
		} catch {
			// try next candidate
		}
	}
	throw new Error('Playwright is not installed. Set PLAYWRIGHT_PACKAGE_PATH or run `npm i -D playwright`.');
}

function isMediaCandidateUrl(url) {
	const lower = url.toLowerCase();
	const path = lower.split('?')[0];
	return path.endsWith('.m3u8') ||
			path.endsWith('.mp4') ||
			lower.includes('.m3u8') ||
			lower.includes('m3u8') ||
			lower.includes('.mp4');
}

function isLikelyPlayerUrl(url) {
	const lower = url.toLowerCase();
	return lower.includes('player') || lower.includes('play') || lower.includes('m3u8');
}

function isMediaContentType(contentType) {
	const lower = (contentType || '').toLowerCase();
	return lower.includes('mpegurl') ||
			lower.includes('application/x-mpegurl') ||
			lower.includes('application/vnd.apple.mpegurl') ||
			lower.startsWith('video/');
}

function proxiedUrl(url, referer) {
	const refererParam = referer ? `&referer=${encodeURIComponent(referer)}` : '';
	return `/__animeko_proxy?url=${encodeURIComponent(url)}${refererParam}`;
}

function rewritePlaylist(playlist, baseUrl, referer) {
	const nextReferer = referer || baseUrl.toString();
	return playlist.split(/\r?\n/).map((line) => {
		if (!line.trim()) return line;
		if (line.startsWith('#')) {
			return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${proxiedUrl(new URL(uri, baseUrl).toString(), nextReferer)}"`);
		}
		return proxiedUrl(new URL(line.trim(), baseUrl).toString(), nextReferer);
	}).join('\n');
}

function serveStatic(req, res, requestUrl) {
	let pathname = decodeURIComponent(requestUrl.pathname);
	if (pathname === '/') pathname = '/index.html';
	const safePath = normalize(pathname).replace(/^([/\\])+/, '');
	let file = resolve(join(root, safePath));
	if (!file.startsWith(root)) return sendText(res, 403, 'Forbidden');
	if (!existsSync(file) || statSync(file).isDirectory()) {
		file = resolve(join(root, 'index.html'));
	}
	if (!existsSync(file)) return sendText(res, 404, `Missing ${file}`);
	res.writeHead(200, {
		...isolationHeaders,
		'content-type': mimeTypes[extname(file)] || 'application/octet-stream'
	});
	createReadStream(file).pipe(res);
}

createServer((req, res) => {
	const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
	if (requestUrl.pathname === '/__animeko_proxy') {
		handleProxy(req, res, requestUrl);
	} else if (requestUrl.pathname === '/__animeko_browser_resolve') {
		handleBrowserResolve(req, res, requestUrl);
	} else {
		serveStatic(req, res, requestUrl);
	}
}).listen(port, () => {
	console.log(`Animeko Web dev server: http://localhost:${port}`);
	console.log(`Serving ${root}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
	process.on(signal, async () => {
		if (browserContextPromise) {
			const context = await browserContextPromise.catch(() => null);
			await context?.close().catch(() => undefined);
		}
		process.exit(0);
	});
}
