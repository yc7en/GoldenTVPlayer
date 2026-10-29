package com.fongmi.android.tv.player.hls;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HLS 清单广告块过滤器（纯逻辑，无 Android 依赖，可独立验证）。
 *
 * <p>针对「片源把广告切片混进 m3u8 清单的 {@code #EXT-X-DISCONTINUITY} 短块」这一类插播广告：
 * 正片块时长稳定，广告块明显偏短。判据保持保守，宁漏勿误删：
 *
 * <ul>
 *   <li>内容块数 &lt; {@link #MIN_BLOCKS} 不启用（普通无广告清单通常 0~2 个 DISCONTINUITY）；
 *   <li>块时长中位数 M，块时长 &lt; {@link #AD_RATIO} * M 判为广告块；
 *   <li>剔除总量 &gt; {@link #MAX_DROP_RATIO} * 总时长则放弃过滤（判定不可信）。
 * </ul>
 *
 * <p>剔除后保留块间 DISCONTINUITY 边界与 {@code #EXT-X-ENDLIST} 等尾部标签；
 * 分片 URI 一律改写为绝对地址（分片不经过中继，播放器直连 CDN）；
 * 主清单的子清单 URI 交给 {@link Rewriter} 改写为继续走中继的本地地址。
 */
public final class HlsAdFilter {

    /** 广告块时长上限（相对中位数）。保守取 0.65，宁漏勿删正片。 */
    public static final double AD_RATIO = 0.65;
    /** 剔除总量超过总时长该比例则放弃过滤。 */
    public static final double MAX_DROP_RATIO = 0.25;
    /** 内容块数低于该值不启用过滤。 */
    public static final int MIN_BLOCKS = 5;

    private static final String DISCONTINUITY = "#EXT-X-DISCONTINUITY";
    private static final String ENDLIST = "#EXT-X-ENDLIST";
    private static final String IFramesOnly = "#EXT-X-I-FRAMES-ONLY";
    private static final String EXTINF = "#EXTINF:";
    private static final String STREAM_INF = "#EXT-X-STREAM-INF";
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    private HlsAdFilter() {
    }

    /** 子清单地址改写器：入参为绝对地址，返回改写后的地址（非 m3u8 原样返回）。 */
    public interface Rewriter {
        String rewrite(String absoluteUrl);
    }

    public static final class Result {

        private final String playlist;
        private final int blocks;
        private final long durationMs;
        private final boolean changed;

        Result(String playlist, int blocks, long durationMs, boolean changed) {
            this.playlist = playlist;
            this.blocks = blocks;
            this.durationMs = durationMs;
            this.changed = changed;
        }

        /** 处理后的清单；未做任何改动时为原始文本。 */
        public String getPlaylist() {
            return playlist;
        }

        /** 剔除的广告块数。 */
        public int getBlocks() {
            return blocks;
        }

        /** 剔除的广告总时长（毫秒）。 */
        public long getDurationMs() {
            return durationMs;
        }

        public long getDurationSec() {
            return Math.round(durationMs / 1000.0);
        }

        /** 是否有广告块被剔除。 */
        public boolean isChanged() {
            return changed;
        }
    }

    /** 主清单（含 STREAM-INF）：改写子清单地址后原样返回结构。 */
    public static boolean isMaster(String playlist) {
        return playlist != null && playlist.contains(STREAM_INF);
    }

    /** 媒体清单（含 EXTINF 且不是主清单）。 */
    public static boolean isMedia(String playlist) {
        return playlist != null && playlist.contains(EXTINF) && !playlist.contains(STREAM_INF);
    }

    /**
     * 统一入口：主清单走子清单改写，媒体清单走广告块过滤。
     *
     * @param playlist 清单原文
     * @param base     清单自身的绝对地址（用于解析相对 URI，可为 null）
     * @param rewriter 子清单改写器（主清单用，可为 null）
     */
    public static Result filter(String playlist, URI base, Rewriter rewriter) {
        if (playlist == null || playlist.isEmpty()) return new Result(playlist, 0, 0, false);
        if (isMaster(playlist)) {
            if (rewriter == null) return new Result(playlist, 0, 0, false);
            return new Result(rewriteMaster(playlist, base, rewriter), 0, 0, false);
        }
        if (!isMedia(playlist)) return new Result(playlist, 0, 0, false);
        return filterMedia(playlist, base);
    }

    // region 媒体清单：按 DISCONTINUITY 分块 + 短块剔除

    private static Result filterMedia(String playlist, URI base) {
        List<String> header = new ArrayList<>();
        List<String> tail = new ArrayList<>();
        List<List<String>> chunks = new ArrayList<>();
        List<Long> durations = new ArrayList<>();

        List<String> current = new ArrayList<>();
        long currentMs = 0L;
        boolean started = false;
        boolean inTail = false;

        for (String line : split(playlist)) {
            if (line.isEmpty()) continue;
            if (line.startsWith(DISCONTINUITY)) {
                if (!current.isEmpty()) {
                    chunks.add(current);
                    durations.add(currentMs);
                    current = new ArrayList<>();
                    currentMs = 0L;
                }
                continue;
            }
            if (line.startsWith(ENDLIST) || line.startsWith(IFramesOnly)) {
                inTail = true;
            }
            if (inTail) {
                tail.add(line);
                continue;
            }
            if (line.startsWith(EXTINF)) {
                started = true;
                currentMs += parseExtinf(line);
                current.add(line);
                continue;
            }
            if (!started) {
                header.add(line);
                continue;
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            chunks.add(current);
            durations.add(currentMs);
        }

        // 分片 URI 必须绝对化：交给播放器后 base 变成中继地址，相对路径会 404
        StringBuilder output = new StringBuilder();
        appendLines(output, header, base);

        if (chunks.size() < MIN_BLOCKS) {
            appendChunks(output, chunks, null, base);
            appendLines(output, tail, base);
            return new Result(output.toString(), 0, 0, false);
        }

        long totalMs = 0L;
        for (long d : durations) totalMs += d;
        double median = median(durations);
        if (median <= 0 || totalMs <= 0) {
            appendChunks(output, chunks, null, base);
            appendLines(output, tail, base);
            return new Result(output.toString(), 0, 0, false);
        }

        long threshold = (long) (AD_RATIO * median);
        boolean[] drop = new boolean[chunks.size()];
        long dropMs = 0L;
        int dropCount = 0;
        for (int i = 0; i < durations.size(); i++) {
            if (durations.get(i) < threshold) {
                drop[i] = true;
                dropMs += durations.get(i);
                dropCount++;
            }
        }
        // 判据不可信（删得太多）时放弃过滤，但仍返回绝对化后的清单
        if (dropCount == 0 || dropMs > MAX_DROP_RATIO * totalMs) {
            appendChunks(output, chunks, null, base);
            appendLines(output, tail, base);
            return new Result(output.toString(), 0, 0, false);
        }

        appendChunks(output, chunks, drop, base);
        appendLines(output, tail, base);
        return new Result(output.toString(), dropCount, dropMs, true);
    }

    private static void appendChunks(StringBuilder output, List<List<String>> chunks, boolean[] drop, URI base) {
        boolean first = true;
        for (int i = 0; i < chunks.size(); i++) {
            if (drop != null && i < drop.length && drop[i]) continue;
            if (!first) output.append(DISCONTINUITY).append('\n');
            first = false;
            for (String line : chunks.get(i)) {
                output.append(absoluteLine(line, base)).append('\n');
            }
        }
    }

    private static void appendLines(StringBuilder output, List<String> lines, URI base) {
        for (String line : lines) {
            output.append(absoluteLine(line, base)).append('\n');
        }
    }

    private static long parseExtinf(String line) {
        int start = EXTINF.length();
        int comma = line.indexOf(',', start);
        String value = comma > start ? line.substring(start, comma) : line.substring(start);
        try {
            return Math.round(Double.parseDouble(value.trim()) * 1000);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double median(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int size = sorted.size();
        if (size % 2 == 1) return sorted.get(size / 2);
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    // endregion

    // region 主清单：子清单 URI 改写

    private static String rewriteMaster(String playlist, URI base, Rewriter rewriter) {
        StringBuilder output = new StringBuilder();
        for (String line : split(playlist)) {
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                output.append(rewriteUriAttributes(line, base, rewriter)).append('\n');
                continue;
            }
            output.append(rewrite(absolute(line, base), rewriter)).append('\n');
        }
        return output.toString();
    }

    private static String rewriteUriAttributes(String line, URI base, Rewriter rewriter) {
        Matcher matcher = URI_ATTR.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String uri = matcher.group(1);
            String replaced = rewrite(absolute(uri, base), rewriter);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + replaced + "\""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String rewrite(String url, Rewriter rewriter) {
        if (rewriter == null) return url;
        try {
            return rewriter.rewrite(url);
        } catch (Throwable e) {
            return url;
        }
    }

    // endregion

    /** 单行绝对化：URI 行按 base 解析；标签内的 URI="..." 属性同步绝对化。 */
    private static String absoluteLine(String line, URI base) {
        if (line.startsWith("#")) {
            if (!line.contains("URI=\"")) return line;
            Matcher matcher = URI_ATTR.matcher(line);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String replaced = absolute(matcher.group(1), base);
                matcher.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + replaced + "\""));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
        return absolute(line, base);
    }

    private static String absolute(String uri, URI base) {
        if (base == null) return uri;
        try {
            URI value = URI.create(uri.trim());
            if (value.isAbsolute()) return value.toString();
            return base.resolve(value).toString();
        } catch (Throwable e) {
            return uri;
        }
    }

    private static List<String> split(String playlist) {
        List<String> lines = new ArrayList<>();
        for (String line : playlist.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        return lines;
    }
}
