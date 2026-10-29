package com.fongmi.android.tv.player.hls;

import androidx.annotation.Nullable;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Auth;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.ResponseBody;

/**
 * HLS 清单本地中继：只回 m3u8 文本，不代理分片（分片由播放器直连 CDN）。
 *
 * <p>上游地址登记为随机 token（{@code /t/<token>}），中继按 token 保存 UA / Referer 等请求头，
 * 拉取清单后交 {@link HlsAdFilter} 处理再返回；点播清单（含 ENDLIST）结果缓存，seek 不重复拉；
 * 拉取失败则原样透传并标记，下一次 {@link #wrap} 对该地址退回直连，避免反复走死路。
 *
 * <p>只监听 127.0.0.1 随机端口，外部进程无法枚举访问。
 */
public final class HlsRelay extends NanoHTTPD {

    private static final String HOST = "127.0.0.1";
    private static final String PATH = "/t/";
    /** 中继地址保留 .m3u8 后缀：media3 依赖后缀判定内容类型。 */
    private static final String SUFFIX = ".m3u8";
    private static final String MIME_HLS = "application/vnd.apple.mpegurl";
    private static final int MAX_ENTRIES = 256;

    private static volatile HlsRelay instance;

    private final Map<String, String> urlTokens = Collections.synchronizedMap(new LinkedHashMap<>());

    private final java.util.Set<String> failedUrls = Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private final Map<String, RelayEntry> entries = Collections.synchronizedMap(new LinkedHashMap<String, RelayEntry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RelayEntry> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            urlTokens.values().remove(eldest.getKey());
            return true;
        }
    });

    private HlsRelay() {
        super(HOST, 0);
    }

    /** 把远程清单地址包装为中继地址。非 http(s)、非 m3u8、或中继启动失败时原样返回。 */
    public static String wrap(@Nullable String url, @Nullable Map<String, String> headers, @Nullable Auth.Context auth) {
        if (!isM3u8(url)) return url;
        HlsRelay relay = obtain();
        if (relay == null) return url;
        // 该地址此前拉取失败过：退回直连，避免反复走死路
        if (relay.failedUrls.contains(url)) return url;
        return relay.register(url, headers, auth);
    }

    private static boolean isM3u8(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (!lower.contains(".m3u8")) return false;
        int query = lower.indexOf('?');
        String path = query < 0 ? lower : lower.substring(0, query);
        return path.endsWith(".m3u8") || path.contains(".m3u8/");
    }

    private static synchronized HlsRelay obtain() {
        if (instance != null) return instance;
        HlsRelay relay = new HlsRelay();
        try {
            relay.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            instance = relay;
            return relay;
        } catch (Throwable e) {
            try {
                relay.stop();
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private String register(String url, Map<String, String> headers, Auth.Context auth) {
        String exists = urlTokens.get(url);
        if (exists != null && entries.containsKey(exists)) {
            return "http://" + HOST + ":" + getListeningPort() + PATH + exists + SUFFIX;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        entries.put(token, new RelayEntry(url, headers, auth));
        urlTokens.put(url, token);
        return "http://" + HOST + ":" + getListeningPort() + PATH + token + SUFFIX;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null || !uri.startsWith(PATH)) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "not found");
        }
        String token = uri.substring(PATH.length());
        if (token.endsWith(SUFFIX)) token = token.substring(0, token.length() - SUFFIX.length());
        RelayEntry entry = entries.get(token);
        if (entry == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "unknown token");
        }
        String playlist = load(entry);
        if (playlist == null) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "upstream failed");
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HLS, playlist);
    }

    @Nullable
    private String load(RelayEntry entry) {
        synchronized (entry) {
            if (entry.cached != null && entry.finalCache) return entry.cached;
            String text = fetch(entry);
            if (text == null) {
                if (failedUrls.size() > MAX_ENTRIES) failedUrls.clear();
                failedUrls.add(entry.url);
                return null;
            }
            if (!text.startsWith("#EXTM3U")) {
                entry.cached = text;
                entry.finalCache = true;
                return text;
            }
            URI base = parse(entry.url);
            HlsAdFilter.Result result;
            if (HlsAdFilter.isMaster(text)) {
                result = HlsAdFilter.filter(text, base, url -> wrap(url, entry.headers, entry.auth));
                entry.finalCache = true;
            } else {
                result = HlsAdFilter.filter(text, base, null);
                // 直播（无 ENDLIST）清单需持续刷新，不缓存
                entry.finalCache = text.contains("#EXT-X-ENDLIST");
            }
            entry.cached = result.getPlaylist();
            return entry.cached;
        }
    }

    @Nullable
    private String fetch(RelayEntry entry) {
        okhttp3.Response response = null;
        try {
            okhttp3.Call call = OkHttp.newCall(entry.url, entry.headers == null ? Map.of() : entry.headers, entry.auth);
            response = call.execute();
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            return body == null ? null : body.string();
        } catch (Throwable e) {
            return null;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static URI parse(String url) {
        try {
            return URI.create(url);
        } catch (Throwable e) {
            return null;
        }
    }

    private static final class RelayEntry {

        private final String url;
        private final Map<String, String> headers;
        private final Auth.Context auth;

        private String cached;
        private boolean finalCache;

        private RelayEntry(String url, Map<String, String> headers, Auth.Context auth) {
            this.url = url;
            this.headers = headers;
            this.auth = auth;
        }
    }
}
