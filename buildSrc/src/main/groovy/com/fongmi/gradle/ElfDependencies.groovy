package com.fongmi.gradle

import org.gradle.api.GradleException

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 读取 ELF 的 DT_NEEDED 依赖名列表。
 *
 * 由原生库校验逻辑中抽出为通用工具：Python 运行时打包需要校验 .so 依赖。
 */
class ElfDependencies {

    static Set<String> needed(byte[] bytes, String abi) {
        boolean wide = abi == 'arm64-v8a'
        if (!AbiApkPackaging.ABI_FLAVORS.containsValue(abi) || bytes.length < (wide ? 64 : 52)) throw new GradleException("Invalid ELF for ${abi}")
        def data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (data.getInt(0) != 0x464c457f || data.get(4) != (wide ? 2 : 1) || data.get(5) != 1 || data.getShort(18) != (wide ? 183 : 40)) throw new GradleException("ELF ABI mismatch for ${abi}")
        try {
            def segments = readSegments(data, wide, bytes.length)
            def dynamic = readDynamic(data, wide, segments.dynamic)
            return readDependencies(bytes, segments.loads, dynamic)
        } catch (IndexOutOfBoundsException exception) {
            throw new GradleException("Invalid ELF dynamic data for ${abi}", exception)
        }
    }

    private static Map readSegments(ByteBuffer data, boolean wide, int size) {
        long headers = wide ? data.getLong(32) : Integer.toUnsignedLong(data.getInt(28))
        int stride = Short.toUnsignedInt(data.getShort(wide ? 54 : 42))
        int count = Short.toUnsignedInt(data.getShort(wide ? 56 : 44))
        if (stride < (wide ? 56 : 32) || count == 0) throw new GradleException('Invalid ELF program headers')
        def loads = []
        def dynamic = null
        for (int index = 0; index < count; index++) {
            int position = offset(headers + (long) index * stride, stride, size)
            int type = data.getInt(position)
            long start = wide ? data.getLong(position + 8) : Integer.toUnsignedLong(data.getInt(position + 4))
            long address = wide ? data.getLong(position + 16) : Integer.toUnsignedLong(data.getInt(position + 8))
            long length = wide ? data.getLong(position + 32) : Integer.toUnsignedLong(data.getInt(position + 16))
            if (type == 1) loads.add([start: start, address: address, length: length])
            if (type == 2) dynamic = [start: offset(start, length, size), length: length]
        }
        if (dynamic == null) throw new GradleException('Missing ELF dynamic segment')
        return [loads: loads, dynamic: dynamic]
    }

    private static Map readDynamic(ByteBuffer data, boolean wide, Map dynamic) {
        long strings = -1
        long stringsSize = -1
        def names = []
        int step = wide ? 16 : 8
        for (long position = dynamic.start; position + step <= dynamic.start + dynamic.length; position += step) {
            long tag = wide ? data.getLong((int) position) : data.getInt((int) position)
            long value = wide ? data.getLong((int) position + 8) : Integer.toUnsignedLong(data.getInt((int) position + 4))
            if (tag == 0) break
            if (tag == 1) names.add(value)
            if (tag == 5) strings = value
            if (tag == 10) stringsSize = value
        }
        return [strings: strings, stringsSize: stringsSize, names: names]
    }

    private static Set<String> readDependencies(byte[] bytes, List loads, Map dynamic) {
        long strings = dynamic.strings
        long stringsSize = dynamic.stringsSize
        def segment = loads.find { strings >= it.address && strings - it.address < it.length }
        if (segment == null || stringsSize <= 0) throw new GradleException('Missing ELF string table')
        int start = offset(segment.start + strings - segment.address, stringsSize, bytes.length)
        def result = new LinkedHashSet<String>()
        for (long name : dynamic.names) result.add(readDependency(bytes, start, stringsSize, name))
        return result
    }

    private static String readDependency(byte[] bytes, int start, long stringsSize, long name) {
        if (name < 0 || name >= stringsSize) throw new GradleException('Invalid ELF dependency')
        int end = start + (int) name
        while (end < start + stringsSize && bytes[end] != 0) end++
        if (end == start + stringsSize) throw new GradleException('Unterminated ELF dependency')
        return new String(bytes, start + (int) name, end - start - (int) name, 'UTF-8')
    }

    private static int offset(long start, long length, int size) {
        if (start < 0 || length < 0 || start > size || length > size - start) throw new GradleException('ELF data outside file')
        return (int) start
    }
}
