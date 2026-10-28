package com.music.streamx.data.sources.module

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.define
import com.music.streamx.data.Http
import com.music.streamx.data.TrackLog
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Runs SpotiFLAC (`.sflx`) extensions inside the module sandbox.
 *
 * A SpotiFLAC extension is a ZIP holding `manifest.json` and `index.js`. The
 * script calls `registerExtension({...})` and expects that host's synchronous
 * globals — `http`, `fetch`, `storage`, `utils`, `file`, `log`. This object
 * provides those globals and wraps the registered extension in the
 * `searchTracks()` / `getTrackStreamUrl()` shape [ModuleSource] already speaks,
 * so a SpotiFLAC registry is just another module index past this point.
 *
 * Streaming is the adapted part. These extensions are written to *download*:
 * they either expose `getDownloadUrl()` or resolve a URL inside `download()` and
 * hand it to `file.download(url, path)`. Here `file.download` writes nothing; it
 * records the URL and headers it was given and reports success, and that URL is
 * what the player opens. A download that needs post-processing (segment
 * stitching, ffmpeg, decryption) produces nothing playable, so those calls mark
 * the capture unusable and the track falls through to the next source.
 */
internal object SflxHost {

    private const val TAG = "StreamX"

    /** Version reported to extensions that compare it against `minAppVersion`. */
    private const val HOST_VERSION = "5.0.0"

    /** Runtime features this host honours; a manifest requiring any other is skipped. */
    private val SUPPORTED_FEATURES = setOf("preparedContext@1")

    private const val MAX_SCRIPT = 8L * 1024 * 1024

    private val client by lazy {
        Http.client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Per-extension `storage`, shared by every engine in that extension's pool. */
    private val storage = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    class Package(val manifest: JSONObject, val script: String)

    /** Unpacks an `.sflx` archive, or throws saying why it cannot run here. */
    fun unpack(bytes: ByteArray): Package {
        var manifest: String? = null
        var script: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name != "manifest.json" && entry.name != "index.js") continue
                val text = zip.readBytes().also {
                    check(it.size <= MAX_SCRIPT) { "${entry.name} is too large" }
                }.decodeToString()
                if (entry.name == "manifest.json") manifest = text else script = text
            }
        }
        val parsed = JSONObject(requireNotNull(manifest) { "Extension has no manifest.json" })
        return Package(parsed, requireNotNull(script) { "Extension has no index.js" })
    }

    /** Why [manifest] cannot stream through this host, or null when it can. */
    fun unsupportedReason(manifest: JSONObject): String? {
        val types = manifest.optJSONArray("type")
        val provides = (0 until (types?.length() ?: 0)).map { types!!.optString(it) }
        if ("download_provider" !in provides) return "provides metadata only"
        val features = manifest.optJSONArray("requiredRuntimeFeatures")
        val missing = (0 until (features?.length() ?: 0)).map { features!!.optString(it) }
            .filterNot { it in SUPPORTED_FEATURES }
        return missing.takeIf { it.isNotEmpty() }?.let { "needs ${it.joinToString()}" }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ── Engine setup ─────────────────────────────────────────────────────

    /** Binds the host globals into [qjs] and evaluates [script] behind the adapter. */
    suspend fun install(qjs: QuickJs, moduleId: String, manifestJson: String, script: String) {
        bindNatives(qjs, moduleId)
        qjs.evaluate<String>("var __sflx_manifest = $manifestJson;\n$PRELUDE\n'ok'")
        qjs.evaluate<String>(
            """
            var __spine_iife_error = null;
            (function() {
                try {
                    $script
                } catch (e) {
                    __spine_iife_error = e && e.message ? e.message : String(e);
                }
            })();
            if (!__spine_iife_error && !__sflx_ext) __spine_iife_error = 'registerExtension was never called';
            var __spine_mod = __spine_iife_error ? {} : __sflx_adapter();
            'ok'
            """.trimIndent(),
        )
    }

    private fun bindNatives(qjs: QuickJs, moduleId: String) {
        val store = storage.getOrPut(moduleId) { ConcurrentHashMap() }
        qjs.define("__sflx") {
            function("request", sync { args ->
                request(
                    method = args.getOrNull(0)?.toString() ?: "GET",
                    url = args.getOrNull(1)?.toString().orEmpty(),
                    headersJson = args.getOrNull(2)?.toString() ?: "{}",
                    body = args.getOrNull(3)?.toString(),
                )
            })
            function("storageGet", sync { args -> store[args[0].toString()] })
            function("storageSet", sync { args ->
                val key = args[0].toString()
                val value = args.getOrNull(1)
                if (value == null) store.remove(key) else store[key] = value.toString()
                "true"
            })
            function("storageRemove", sync { args -> store.remove(args[0].toString()); "true" })
            function("log", sync { args ->
                val line = "[SFLX:$moduleId] ${args.getOrNull(1) ?: ""}"
                when (args.getOrNull(0)?.toString()) {
                    "error" -> TrackLog.e(TAG, line)
                    "warn" -> TrackLog.w(TAG, line)
                    else -> TrackLog.d(TAG, line)
                }
                "true"
            })
            function("digest", sync { args ->
                val algorithm = when (args[0].toString().lowercase()) {
                    "md5" -> "MD5"
                    "sha1" -> "SHA-1"
                    "sha512" -> "SHA-512"
                    else -> "SHA-256"
                }
                MessageDigest.getInstance(algorithm).digest(args[1].toString().toByteArray())
                    .joinToString("") { "%02x".format(it) }
            })
            function("hmac", sync { args ->
                val algorithm = when (args[0].toString().lowercase()) {
                    "sha1" -> "HmacSHA1"
                    "sha512" -> "HmacSHA512"
                    else -> "HmacSHA256"
                }
                val mac = Mac.getInstance(algorithm)
                mac.init(SecretKeySpec(args[1].toString().toByteArray(), algorithm))
                mac.doFinal(args[2].toString().toByteArray()).joinToString("") { "%02x".format(it) }
            })
            // Byte-level HMAC for extensions that pass byte arrays (TOTP and the like): hex in, hex out.
            function("hmacHex", sync { args ->
                val algorithm = if (args[0].toString().lowercase() == "sha256") "HmacSHA256" else "HmacSHA1"
                fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                val mac = Mac.getInstance(algorithm)
                mac.init(SecretKeySpec(bytes(args[1].toString()).takeIf { it.isNotEmpty() } ?: ByteArray(1), algorithm))
                mac.doFinal(bytes(args[2].toString())).joinToString("") { "%02x".format(it) }
            })
            function("timezone", sync { java.util.TimeZone.getDefault().id })
            function("base64Decode", sync { args ->
                String(Base64.getDecoder().decode(args[0].toString()), Charsets.ISO_8859_1)
            })
            function("base64Encode", sync { args ->
                Base64.getEncoder().encodeToString(args[0].toString().toByteArray(Charsets.ISO_8859_1))
            })
            function("sleep", sync { args ->
                Thread.sleep((args.getOrNull(0)?.toString()?.toDoubleOrNull() ?: 0.0).toLong().coerceIn(0, 5_000))
                "true"
            })
        }
    }

    private fun sync(block: (Array<Any?>) -> Any?) = object : FunctionBinding<Any?> {
        override fun invoke(args: Array<Any?>): Any? = block(args)
    }

    /** One blocking HTTP call, answered as JSON the prelude turns into a response object. */
    private fun request(method: String, url: String, headersJson: String, body: String?): String {
        val result = JSONObject()
        try {
            val builder = Request.Builder().url(url)
            val headers = runCatching { JSONObject(headersJson) }.getOrDefault(JSONObject())
            var contentType: String? = null
            for (key in headers.keys()) {
                val value = headers.optString(key)
                if (key.equals("content-type", ignoreCase = true)) contentType = value
                builder.header(key, value)
            }
            if (!headers.keys().asSequence().any { it.equals("user-agent", ignoreCase = true) }) {
                builder.header("User-Agent", USER_AGENT)
            }
            val upper = method.uppercase()
            // Bytes, not a String: OkHttp appends "; charset=utf-8" to a String body's
            // type, and some APIs (Spotify's client token) refuse anything but the exact value.
            val requestBody = (body ?: "").toByteArray().toRequestBody(
                (contentType ?: "application/json; charset=utf-8").toMediaTypeOrNull(),
            )
            when (upper) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                "DELETE" -> if (body == null) builder.delete() else builder.delete(requestBody)
                else -> builder.method(upper, requestBody)
            }
            client.newCall(builder.build()).execute().use { response ->
                result.put("statusCode", response.code)
                result.put("url", response.request.url.toString())
                result.put("body", response.body?.string().orEmpty())
                val responseHeaders = JSONObject()
                for (name in response.headers.names()) {
                    responseHeaders.put(name.lowercase(), response.headers(name).joinToString(", "))
                }
                result.put("headers", responseHeaders)
            }
        } catch (e: Exception) {
            result.put("statusCode", 0)
            result.put("body", "")
            result.put("error", e.message ?: e.javaClass.simpleName)
            result.put("headers", JSONObject())
        }
        return result.toString()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    // ── JS side ──────────────────────────────────────────────────────────

    private val PRELUDE = """
        var __sflx_ext = null;
        var __sflx_capture = null;
        var __sflx_tracks = {};
        function registerExtension(ext) { __sflx_ext = ext; }

        function __sflx_text(args) {
            return Array.prototype.map.call(args, function(a) {
                if (typeof a === 'string') return a;
                try { return JSON.stringify(a); } catch (e) { return String(a); }
            }).join(' ');
        }
        var log = {
            debug: function() { __sflx.log('debug', __sflx_text(arguments)); },
            info: function() { __sflx.log('info', __sflx_text(arguments)); },
            warn: function() { __sflx.log('warn', __sflx_text(arguments)); },
            error: function() { __sflx.log('error', __sflx_text(arguments)); }
        };

        function __sflx_response(raw) {
            var r = JSON.parse(raw);
            var headers = r.headers || {};
            Object.defineProperty(headers, 'get', {
                enumerable: false,
                value: function(k) { var v = headers[String(k).toLowerCase()]; return v === undefined ? null : v; }
            });
            r.headers = headers;
            r.status = r.statusCode;
            r.code = r.statusCode;
            r.ok = r.statusCode >= 200 && r.statusCode < 300;
            r.statusText = r.ok ? 'OK' : (r.error || 'Error');
            var body = r.body || '';
            r.text = function() { return body; };
            r.json = function() { return JSON.parse(body); };
            r.clone = function() { return r; };
            return r;
        }
        function __sflx_request(method, url, headers, body) {
            if (body !== undefined && body !== null && typeof body !== 'string') body = JSON.stringify(body);
            var h = headers;
            if (h && typeof h.forEach === 'function' && typeof h.entries === 'function') {
                var plain = {}; h.forEach(function(v, k) { plain[k] = v; }); h = plain;
            }
            return __sflx_response(__sflx.request(method, String(url), JSON.stringify(h || {}), body === undefined ? null : body));
        }
        var http = {
            get: function(url, headers) { return __sflx_request('GET', url, headers, null); },
            post: function(url, body, headers) { return __sflx_request('POST', url, headers, body); },
            put: function(url, body, headers) { return __sflx_request('PUT', url, headers, body); },
            delete: function(url, headers) { return __sflx_request('DELETE', url, headers, null); },
            request: function(url, options) {
                options = options || {};
                return __sflx_request(options.method || 'GET', url, options.headers, options.body);
            }
        };
        var fetch = function(url, options) {
            options = options || {};
            return __sflx_request(options.method || 'GET', url, options.headers, options.body);
        };

        var storage = {
            get: function(key) { var v = __sflx.storageGet(String(key)); return v === undefined ? null : v; },
            set: function(key, value) {
                __sflx.storageSet(String(key), value === undefined || value === null ? null :
                    (typeof value === 'string' ? value : JSON.stringify(value)));
                return true;
            },
            remove: function(key) { __sflx.storageRemove(String(key)); return true; }
        };
        var credentials = {
            get: function() { return null; }, set: function() { return false; },
            remove: function() { return true; }, has: function() { return false; }
        };

        var utils = {
            randomUserAgent: function() { return '$USER_AGENT'; },
            appUserAgent: function() { return 'SpotiFLAC-Mobile/$HOST_VERSION'; },
            appVersion: function() { return '$HOST_VERSION'; },
            md5: function(s) { return __sflx.digest('md5', String(s)); },
            sha1: function(s) { return __sflx.digest('sha1', String(s)); },
            sha256: function(s) { return __sflx.digest('sha256', String(s)); },
            // SpotiFLAC's signatures: hmacSHA256(message, key) as hex, and
            // hmacSHA1(key, message) over UTF-8 text or byte arrays, returning bytes.
            hmacSHA256: function(message, key) { return __sflx.hmac('sha256', String(key), String(message)); },
            hmacSHA1: function(key, message) {
                function hex(v) {
                    var b = typeof v === 'string' ? Array.from(unescape(encodeURIComponent(v)), function(c) { return c.charCodeAt(0); })
                        : Array.isArray(v) ? v : [];
                    return b.map(function(x) { return ((x & 255) + 256).toString(16).slice(1); }).join('');
                }
                var out = __sflx.hmacHex('sha1', hex(key), hex(message));
                var bytes = [];
                for (var i = 0; i < out.length; i += 2) bytes.push(parseInt(out.substr(i, 2), 16));
                return bytes;
            },
            base64Decode: function(s) { return __sflx.base64Decode(String(s)); },
            base64Encode: function(s) { return __sflx.base64Encode(String(s)); },
            sleep: function(ms) { __sflx.sleep(ms || 0); },
            isDownloadCancelled: function() { return false; },
            getResolutionRemainingMs: function() { return 30000; }
        };
        var gobackend = {
            getLocalTime: function() {
                var d = new Date();
                return { hour: d.getHours(), minute: d.getMinutes(), weekday: d.getDay(),
                         timezone: __sflx.timezone(), offsetSeconds: -d.getTimezoneOffset() * 60,
                         offsetMinutes: d.getTimezoneOffset() };
            },
            checkISRCExists: function() { return false; },
            getAudioQuality: function() { return null; },
            getLyricsLRC: function() { return ''; }
        };

        // Downloads are captured, not performed: the URL is the stream.
        var file = {
            download: function(url, path, options) {
                __sflx_capture = { url: String(url), headers: (options && options.headers) || {}, usable: true };
                return { success: true, path: path };
            },
            downloadSegments: function() {
                __sflx_capture = { usable: false };
                return { success: false, error: 'Segmented streams are not supported' };
            },
            transformPatternedBlocks: function() {
                if (__sflx_capture) __sflx_capture.usable = false;
                return { success: false, error: 'Encrypted streams are not supported' };
            },
            exists: function() { return false; },
            delete: function() { return true; }
        };
        var ffmpeg = {
            convert: function() {
                if (__sflx_capture) __sflx_capture.usable = false;
                return { success: false, error: 'Conversion is not supported' };
            }
        };
        ffmpeg.mov = ffmpeg.convert;

        // Timers run on the next microtask; nothing here needs a real delay.
        var __sflx_timer = 0;
        var __sflx_cancelled = {};
        var setTimeout = function(fn, ms) {
            var id = ++__sflx_timer;
            Promise.resolve().then(function() {
                if (!__sflx_cancelled[id] && typeof fn === 'function') fn();
                delete __sflx_cancelled[id];
            });
            return id;
        };
        var clearTimeout = function(id) { __sflx_cancelled[id] = true; };
        if (typeof AbortController === 'undefined') {
            var AbortController = function() { this.signal = { aborted: false }; };
            AbortController.prototype.abort = function() { this.signal.aborted = true; };
        }

        function __sflx_artist(t) {
            var a = t.artists !== undefined ? t.artists : t.artist;
            if (Array.isArray(a)) return a.map(function(x) { return x && x.name ? x.name : String(x); }).join(', ');
            return a ? String(a) : '';
        }
        function __sflx_cover(t) {
            if (t.cover_url) return t.cover_url;
            if (typeof t.images === 'string') return t.images;
            if (Array.isArray(t.images) && t.images.length) return t.images[0].url || t.images[0];
            return t.thumbnail || null;
        }
        function __sflx_quality(tier) {
            var options = (__sflx_manifest.qualityOptions || []).map(function(q) { return q.id; });
            if (!options.length) return tier === 'LOW' ? 'LOW' : 'HIGH';
            if (tier === 'LOW') {
                for (var i = 0; i < options.length; i++) {
                    var kbps = Number((String(options[i]).match(/(\d{2,4})/) || [])[1] || 0);
                    if (kbps && kbps <= 160) return options[i];
                }
                return options[options.length - 1];
            }
            return options[0];
        }
        function __sflx_init() {
            var defaults = {};
            (__sflx_manifest.settings || []).forEach(function(s) {
                if (s && s.key && s.default !== undefined) defaults[s.key] = s.default;
            });
            if (typeof __sflx_ext.initialize === 'function') {
                try { __sflx_ext.initialize(defaults); } catch (e) { log.warn('initialize failed: ' + e); }
            }
        }

        function __sflx_adapter() {
            var ext = __sflx_ext;
            __sflx_init();
            return {
                // Any function the extension registered, for catalogue use:
                // feed, search, album, playlist and artist pages.
                call: function(name, argsJson) {
                    if (typeof ext[name] !== 'function') return { missing: true };
                    var value = ext[name].apply(ext, JSON.parse(argsJson || '[]'));
                    return Promise.resolve(value).then(function(v) { return { value: v === undefined ? null : v }; });
                },
                searchTracks: function(query, limit) {
                    limit = limit || 20;
                    var list = null;
                    if (typeof ext.searchTracks === 'function') list = ext.searchTracks(query, limit);
                    if ((!list || !list.length) && typeof ext.customSearch === 'function') {
                        list = ext.customSearch(query, { filter: 'tracks', limit: limit });
                    }
                    if (list && !Array.isArray(list)) list = list.tracks || list.items || list.results || [];
                    var tracks = [];
                    (list || []).forEach(function(t) {
                        if (!t || !t.id) return;
                        if (t.item_type && t.item_type !== 'track') return;
                        __sflx_tracks[String(t.id)] = t;
                        tracks.push({
                            id: String(t.id),
                            title: t.name || t.title || '',
                            artist: __sflx_artist(t),
                            album: t.album_name || t.album || '',
                            albumCover: __sflx_cover(t),
                            duration: Math.round(Number(t.duration_ms || 0) / 1000),
                            trackNumber: Number(t.track_number || 0),
                            audioQuality: t.audio_quality || ''
                        });
                    });
                    return { tracks: tracks.slice(0, limit), total: tracks.length };
                },
                getTrackStreamUrl: function(id, tier) {
                    var quality = __sflx_quality(tier);
                    var track = __sflx_tracks[String(id)] || null;
                    var options = { preparedContext: { track: track } };
                    if (typeof ext.getDownloadUrl === 'function') {
                        var direct = null;
                        try { direct = ext.getDownloadUrl(id, quality, options); } catch (e) { log.warn('getDownloadUrl: ' + e); }
                        if (direct && direct.success !== false && direct.url) {
                            return {
                                streamUrl: direct.url,
                                headers: direct.headers || null,
                                track: { id: String(id), audioQuality: String(direct.quality || ''),
                                         mimeType: direct.audio_codec ? 'audio/' + direct.audio_codec : null }
                            };
                        }
                    }
                    if (typeof ext.download !== 'function') return { streamUrl: null };
                    __sflx_capture = null;
                    var result = null;
                    try {
                        result = ext.download(id, quality, '/stream/' + String(id).replace(/[^A-Za-z0-9_-]/g, '_') + '.flac',
                            function() {}, options);
                    } catch (e) { log.warn('download: ' + e); }
                    var capture = __sflx_capture;
                    if (!capture || !capture.usable || !capture.url || (result && result.success === false)) {
                        return { streamUrl: null, error: result && (result.error_message || result.error) || 'no stream' };
                    }
                    var plainHeaders = {};
                    Object.keys(capture.headers || {}).forEach(function(k) { plainHeaders[k] = String(capture.headers[k]); });
                    return {
                        streamUrl: capture.url,
                        headers: plainHeaders,
                        track: { id: String(id), audioQuality: String(result && (result.quality || result.format) || '') }
                    };
                }
            };
        }
    """.trimIndent()
}
