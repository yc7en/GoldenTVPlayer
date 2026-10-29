package com.fongmi.android.tv.player.exo;

import androidx.annotation.NonNull;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.hls.HlsRelay;
import com.fongmi.android.tv.player.media.ConcatenatingUrl;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Auth;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 媒体源工厂。只使用官方 media3 制品提供的类型（ADR-9）。
 *
 * 与 fork 版本的差异：没有 ISO/BD-J 分轨、没有 MP4 clear-key 注入、
 * 没有 ISO 渐进式缓存包装；分段 URL 用官方的
 * {@link ConcatenatingMediaSource} 表达。
 */
public final class ExoMediaSourceFactory implements MediaSource.Factory {

    private static final int CACHE_SPACE_PERCENT = 80;

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory delegate;

    private ExoMediaSourceFactory(DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
        this.delegate = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);
    }

    public static MediaSource.Factory create() {
        return new ExoMediaSourceFactory(createDataSourceFactory(null, null), createDefaultExtractorsFactory());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers, Auth.Context auth) {
        HttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player(auth));
        factory.setDefaultRequestProperties(headers == null ? Map.of() : headers);
        return new DefaultDataSource.Factory(App.get(), factory);
    }

    public static DataSource.Factory createDataSourceFactory(Map<String, String> headers) {
        return createDataSourceFactory(headers, null);
    }

    public static DataSource.Factory createDataSourceFactory(Map<String, String> headers, Auth.Context auth) {
        Cache sharedCache = getCache();
        return new CacheDataSource.Factory()
                .setCache(sharedCache)
                .setUpstreamDataSourceFactory(createUpstreamDataSourceFactory(headers, auth))
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        return cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(getMaxCacheSize(dir)), getDatabaseProvider());
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static long getMaxCacheSize(File dir) {
        long availableBytes = Math.max(0, Path.available(dir));
        long maxCacheBytes = PreloadSetting.getSizeBytes();
        if (availableBytes >= maxCacheBytes * 100 / CACHE_SPACE_PERCENT) return maxCacheBytes;
        long usedBytes = Path.size(dir);
        long storageBudget = (usedBytes + availableBytes) * CACHE_SPACE_PERCENT / 100;
        return Math.min(maxCacheBytes, storageBudget);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider);
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return delegate.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        // 分段表达式必须用原始地址解析，否则中继包装会破坏解析
        List<ConcatenatingUrl.Segment> segments = mediaItem.localConfiguration == null ? List.of()
                : ConcatenatingUrl.parse(mediaItem.localConfiguration.uri.toString());
        MediaItem item = wrapHls(mediaItem);
        MediaSource.Factory factory = createFor(item);
        if (segments.isEmpty()) return factory.createMediaSource(item);
        MediaSource[] sources = new MediaSource[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            sources[i] = factory.createMediaSource(wrapHls(item.buildUpon().setUri(UriUtil.uri(segments.get(i).url())).build()));
        }
        return new ConcatenatingMediaSource(false, sources);
    }

    /**
     * m3u8 地址包装为本地中继地址（剔除插播广告块）。非 m3u8、开关关闭或中继不可用时原样返回。
     */
    private static MediaItem wrapHls(MediaItem mediaItem) {
        if (!Setting.isHlsAdFilter() || mediaItem.localConfiguration == null) return mediaItem;
        String url = mediaItem.localConfiguration.uri.toString();
        String wrapped = HlsRelay.wrap(url, ExoUtil.extractHeaders(mediaItem), ExoUtil.extractAuth(mediaItem));
        if (wrapped == null || wrapped.equals(url)) return mediaItem;
        return mediaItem.buildUpon().setUri(Uri.parse(wrapped)).setMimeType(MimeTypes.APPLICATION_M3U8).build();
    }

    private MediaSource.Factory createFor(MediaItem mediaItem) {
        DataSource.Factory dataSourceFactory = createDataSourceFactory(ExoUtil.extractHeaders(mediaItem), ExoUtil.extractAuth(mediaItem));
        return new DefaultMediaSourceFactory(dataSourceFactory, createDefaultExtractorsFactory())
                .setSubtitleParserFactory(new SubtitleOffsetParserFactory(SubtitleSetting::getOffsetMs));
    }

    static DefaultExtractorsFactory createDefaultExtractorsFactory() {
        return new DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
    }
}
