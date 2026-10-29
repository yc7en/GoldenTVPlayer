package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Consumer;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 给字幕 cue 打时间偏移。
 *
 * 官方 media3 的 Player 没有字幕偏移 API（fork 的
 * {@code getTextOffsetMs} 属于私有扩展），因此在解析层平移 cue 时间：
 * 用户在设置里调整偏移后重新加载当前媒体即可生效。
 */
final class SubtitleOffsetParserFactory implements SubtitleParser.Factory {

    interface OffsetSource {
        /** 当前主字幕偏移，单位毫秒，正数表示字幕延后出现。 */
        long getSubtitleOffsetMs();
    }

    private final DefaultSubtitleParserFactory delegate = new DefaultSubtitleParserFactory();
    private final OffsetSource source;

    SubtitleOffsetParserFactory(OffsetSource source) {
        this.source = source;
    }

    @Override
    public boolean supportsFormat(Format format) {
        return delegate.supportsFormat(format);
    }

    @Override
    public int getCueReplacementBehavior(Format format) {
        return delegate.getCueReplacementBehavior(format);
    }

    @Override
    public SubtitleParser create(Format format) {
        SubtitleParser parser = delegate.create(format);
        return new SubtitleParser() {

            @Override
            public void parse(byte[] data, int offset, int length, OutputOptions outputOptions, Consumer<CuesWithTiming> output) {
                long shiftUs = source.getSubtitleOffsetMs() * 1000L;
                parser.parse(data, offset, length, outputOptions, cues -> output.accept(shift(cues, shiftUs)));
            }

            @Override
            public int getCueReplacementBehavior() {
                return parser.getCueReplacementBehavior();
            }

            @Override
            public void reset() {
                parser.reset();
            }
        };
    }

    private static CuesWithTiming shift(CuesWithTiming cues, long shiftUs) {
        if (shiftUs == 0 || cues.cues.isEmpty()) return cues;
        long startUs = Math.max(0, cues.startTimeUs + shiftUs);
        List<Cue> shifted = new ArrayList<>(cues.cues);
        return new CuesWithTiming(shifted, startUs, cues.durationUs);
    }
}
