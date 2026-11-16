package com.example.backtest

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import java.security.MessageDigest
import android.os.Build
import android.os.Environment
import kotlin.math.abs

/**
 * Compact, little-endian columnar binary candle cache (.btbin) built alongside cached CSV files
 * using O(1) JVM heap. Enables:
 * 1) Sub-second array loading across all parallel Python worker processes.
 * 2) O(1) random-access byte seeking for millisecond trade replay windows even on 4M+ bar CSVs.
 *
 * Layout (all little-endian):
 *   Header (32 bytes):
 *     [0..3]   magic "BTBN"
 *     [4..7]   version (int32 = 1)
 *     [8..15]  candleCount (int64 = N)
 *     [16..23] sourceSizeBytes (int64)
 *     [24..31] sourceMtimeMs (int64)
 *   opens[N]       : N * float64
 *   highs[N]       : N * float64
 *   lows[N]        : N * float64
 *   closes[N]      : N * float64
 *   volumes[N]     : N * float64
 *   timeOffsets[N+1]: (N + 1) * uint32 (UTF-8 byte offsets into timeBuf)
 *   timeBuf        : timeOffsets[N] bytes (UTF-8 timestamps)
 */
object BinaryCandleCache {

    private const val MAGIC = 0x4E425442 // "BTBN" little-endian
    private const val VERSION = 1
    private const val HEADER_BYTES = 32L

    private data class Header(
        val candleCount: Int,
        val sourceSizeBytes: Long,
        val sourceMtimeMs: Long
    )

    private data class ColumnMap(
        val delimiter: Char,
        val openIdx: Int,
        val highIdx: Int,
        val lowIdx: Int,
        val closeIdx: Int,
        val volumeIdx: Int,
        val hasDateAndTime: Boolean,
        val dateIdx: Int,
        val timeIdx: Int,
        val tsIdx: Int
    )

    fun cacheDirectory(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tradepulse/cache")

    fun hasCacheStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun cacheFileFor(csvFile: File): File {
        val identity = runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(csvFile.canonicalPath.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }
        }.getOrElse { csvFile.name.hashCode().toUInt().toString(16) }
        return File(cacheDirectory(), "${csvFile.name}-${identity}.btbin")
    }

    fun isCacheValid(csvFile: File): Boolean {
        val binFile = cacheFileFor(csvFile)
        if (!csvFile.exists() || !binFile.exists() || binFile.length() < HEADER_BYTES) return false
        val header = readHeader(binFile) ?: return false
        return header.sourceSizeBytes == csvFile.length() &&
            abs(header.sourceMtimeMs - csvFile.lastModified()) <= 2000L
    }

    fun readCandleCount(csvFile: File): Int? {
        if (!isCacheValid(csvFile)) return null
        return readHeader(cacheFileFor(csvFile))?.candleCount
    }

    fun readTimestampAt(csvFile: File, index: Int): String? {
        val binFile = cacheFileFor(csvFile)
        if (!isCacheValid(csvFile)) return null
        val header = readHeader(binFile) ?: return null
        val n = header.candleCount
        if (n <= 0 || index !in 0 until n) return null
        val nLong = n.toLong()
        val offsetsBase = HEADER_BYTES + 40L * nLong
        val timeBufBase = offsetsBase + 4L * (nLong + 1L)
        return runCatching {
            RandomAccessFile(binFile, "r").use { raf ->
                val pair = ByteArray(8)
                raf.seek(offsetsBase + index * 4L)
                raf.readFully(pair)
                val buf = ByteBuffer.wrap(pair).order(ByteOrder.LITTLE_ENDIAN)
                val tStart = buf.int.toLong() and 0xFFFFFFFFL
                val tEnd = buf.int.toLong() and 0xFFFFFFFFL
                val len = (tEnd - tStart).toInt().coerceIn(0, 128)
                val bytes = ByteArray(len)
                if (len > 0) {
                    raf.seek(timeBufBase + tStart)
                    raf.readFully(bytes)
                }
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /**
     * Ensures an up-to-date `.btbin` file exists for [csvFile] using fixed small I/O buffers.
     * Safe for concurrent callers via per-path synchronization.
     */
    @Synchronized
    fun ensureCache(csvFile: File, onProgress: (Int) -> Unit = {}): File? {
        if (!hasCacheStorageAccess()) return null
        if (!csvFile.exists() || !csvFile.isFile || csvFile.length() == 0L) return null
        val binFile = cacheFileFor(csvFile)
        binFile.parentFile?.mkdirs()
        if (binFile.parentFile?.isDirectory != true) return null
        if (isCacheValid(csvFile)) return binFile

        val workDir = File(binFile.parentFile, ".btbin_tmp_${System.nanoTime()}")
        workDir.mkdirs()
        val opensTmp = File(workDir, "o.bin")
        val highsTmp = File(workDir, "h.bin")
        val lowsTmp = File(workDir, "l.bin")
        val closesTmp = File(workDir, "c.bin")
        val volumesTmp = File(workDir, "v.bin")
        val offsetsTmp = File(workDir, "off.bin")
        val timesTmp = File(workDir, "t.bin")
        val targetTmp = File(binFile.parentFile, "${binFile.name}.tmp")

        try {
            val (pushback, charset) = openUtfAwareReader(csvFile)
            var count = 0
            var timeByteCursor = 0L

            BufferedReader(InputStreamReader(pushback, charset), 65_536).use { reader ->
                var headerLine: String? = null
                var isFirst = true
                while (true) {
                    val raw = reader.readLine() ?: break
                    val cleaned = if (isFirst) {
                        isFirst = false
                        raw.removePrefix("\uFEFF")
                    } else {
                        raw
                    }
                    if (cleaned.isNotBlank()) {
                        headerLine = cleaned.trim()
                        break
                    }
                }
                val header = headerLine ?: return null
                val cols = parseHeader(header) ?: return null

                BufferedOutputStream(FileOutputStream(opensTmp), 32_768).use { oOut ->
                    BufferedOutputStream(FileOutputStream(highsTmp), 32_768).use { hOut ->
                        BufferedOutputStream(FileOutputStream(lowsTmp), 32_768).use { lOut ->
                            BufferedOutputStream(FileOutputStream(closesTmp), 32_768).use { cOut ->
                                BufferedOutputStream(FileOutputStream(volumesTmp), 32_768).use { vOut ->
                                    BufferedOutputStream(FileOutputStream(offsetsTmp), 32_768).use { offOut ->
                                        BufferedOutputStream(FileOutputStream(timesTmp), 32_768).use { tOut ->
                                            val dblBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                                            val intBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

                                            fun writeDouble(out: BufferedOutputStream, value: Double) {
                                                dblBuf.clear()
                                                dblBuf.putDouble(value)
                                                out.write(dblBuf.array(), 0, 8)
                                            }

                                            fun writeUint32(out: BufferedOutputStream, value: Long) {
                                                intBuf.clear()
                                                intBuf.putInt(value.toInt())
                                                out.write(intBuf.array(), 0, 4)
                                            }

                                            // Initial offset 0
                                            writeUint32(offOut, 0L)

                                            while (true) {
                                                val rawLine = reader.readLine() ?: break
                                                val line = rawLine.trim()
                                                if (line.isEmpty()) continue
                                                val fields = splitCsvLine(line, cols.delimiter)
                                                val needed = maxOf(
                                                    cols.openIdx, cols.highIdx, cols.lowIdx,
                                                    cols.closeIdx, cols.volumeIdx, cols.tsIdx,
                                                    cols.dateIdx, cols.timeIdx
                                                )
                                                if (fields.size <= needed) continue

                                                val o = fields[cols.openIdx].trim().toDoubleOrNull() ?: continue
                                                val h = fields[cols.highIdx].trim().toDoubleOrNull() ?: continue
                                                val l = fields[cols.lowIdx].trim().toDoubleOrNull() ?: continue
                                                val c = fields[cols.closeIdx].trim().toDoubleOrNull() ?: continue
                                                val v = if (cols.volumeIdx >= 0) {
                                                    fields[cols.volumeIdx].trim().toDoubleOrNull() ?: 0.0
                                                } else {
                                                    0.0
                                                }

                                                val ts = if (cols.hasDateAndTime) {
                                                    "${fields[cols.dateIdx].trim()} ${fields[cols.timeIdx].trim()}"
                                                } else {
                                                    fields[cols.tsIdx].trim()
                                                }
                                                val tsBytes = ts.toByteArray(Charsets.UTF_8)

                                                // Ensure total timeBuf fits uint32 offsets (<4GB)
                                                if (timeByteCursor + tsBytes.size > 0xFFFFFFFFL) {
                                                    return null
                                                }

                                                writeDouble(oOut, o)
                                                writeDouble(hOut, h)
                                                writeDouble(lOut, l)
                                                writeDouble(cOut, c)
                                                writeDouble(vOut, v)
                                                tOut.write(tsBytes)
                                                timeByteCursor += tsBytes.size
                                                writeUint32(offOut, timeByteCursor)
                                                count++

                                                if ((count and 65_535) == 0) {
                                                    onProgress(count)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (count == 0) return null

            // Stitch columnar blocks cleanly into final binary file
            BufferedOutputStream(FileOutputStream(targetTmp), 65_536).use { finalOut ->
                val headerBuf = ByteBuffer.allocate(HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN)
                headerBuf.putInt(MAGIC)
                headerBuf.putInt(VERSION)
                headerBuf.putLong(count.toLong())
                headerBuf.putLong(csvFile.length())
                headerBuf.putLong(csvFile.lastModified())
                finalOut.write(headerBuf.array(), 0, HEADER_BYTES.toInt())

                val copyBuf = ByteArray(65_536)
                for (part in listOf(opensTmp, highsTmp, lowsTmp, closesTmp, volumesTmp, offsetsTmp, timesTmp)) {
                    BufferedInputStream(FileInputStream(part), 65_536).use { inp ->
                        while (true) {
                            val n = inp.read(copyBuf)
                            if (n <= 0) break
                            finalOut.write(copyBuf, 0, n)
                        }
                    }
                }
            }

            if (binFile.exists()) binFile.delete()
            if (!targetTmp.renameTo(binFile)) {
                targetTmp.copyTo(binFile, overwrite = true)
                targetTmp.delete()
            }
            onProgress(count)
            return binFile
        } catch (_: Exception) {
            targetTmp.delete()
            return null
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Reads a contiguous or middle-compressed candle window in O(1) disk seeks
     * from a validated `.btbin` file.
     */
    fun readTradeWindow(
        csvFile: File,
        trade: Trade,
        paddingBefore: Int = 20,
        paddingAfter: Int = 15,
        maxWindowCandles: Int = 220
    ): TradeReplayWindow? {
        if (trade.openIndex < 0 || trade.closeIndex < trade.openIndex) return null
        val binFile = cacheFileFor(csvFile)
        if (!isCacheValid(csvFile)) return null
        val header = readHeader(binFile) ?: return null
        val n = header.candleCount
        if (n <= 0 || trade.openIndex >= n) return null

        val clampedClose = trade.closeIndex.coerceAtMost(n - 1)
        val startIdx = (trade.openIndex - paddingBefore).coerceAtLeast(0)
        val endIdx = (clampedClose + paddingAfter).coerceAtMost(n - 1)
        val rawSpan = (endIdx - startIdx + 1).coerceAtLeast(1)

        val indices: List<Int>
        val truncatedMiddle: Boolean
        if (rawSpan <= maxWindowCandles) {
            indices = (startIdx..endIdx).toList()
            truncatedMiddle = false
        } else {
            val headEnd = (trade.openIndex + 40).coerceAtMost(endIdx)
            val tailStart = (clampedClose - 60).coerceAtLeast(headEnd + 1)
            val middleBudget = (maxWindowCandles - (headEnd - startIdx + 1) - (endIdx - tailStart + 1)).coerceAtLeast(20)
            val middleSpan = (tailStart - headEnd - 1).coerceAtLeast(1)
            val middleStep = ((middleSpan + middleBudget - 1) / middleBudget).coerceAtLeast(1)
            val picked = ArrayList<Int>(maxWindowCandles + 16)
            for (idx in startIdx..endIdx) {
                val keep = when {
                    idx == trade.openIndex || idx == clampedClose -> true
                    idx <= headEnd -> true
                    idx >= tailStart -> true
                    else -> ((idx - headEnd) % middleStep) == 0
                }
                if (keep) picked.add(idx)
            }
            indices = picked
            truncatedMiddle = true
        }

        if (indices.isEmpty()) return TradeReplayWindow(emptyList(), -1, -1)

        val nLong = n.toLong()
        val opensBase = HEADER_BYTES
        val highsBase = opensBase + 8L * nLong
        val lowsBase = highsBase + 8L * nLong
        val closesBase = lowsBase + 8L * nLong
        val offsetsBase = closesBase + 16L * nLong // skip closes & volumes
        val timeBufBase = offsetsBase + 4L * (nLong + 1L)

        val candles = ArrayList<ReplayCandle>(indices.size)
        RandomAccessFile(binFile, "r").use { raf ->
            val firstI = indices.first()
            val lastI = indices.last()
            val spanCount = lastI - firstI + 1

            if (spanCount <= 2048) {
                val opens = readDoubleSlice(raf, opensBase + firstI * 8L, spanCount)
                val highs = readDoubleSlice(raf, highsBase + firstI * 8L, spanCount)
                val lows = readDoubleSlice(raf, lowsBase + firstI * 8L, spanCount)
                val closes = readDoubleSlice(raf, closesBase + firstI * 8L, spanCount)
                val offBytes = ByteArray((spanCount + 1) * 4)
                raf.seek(offsetsBase + firstI * 4L)
                raf.readFully(offBytes)
                val offBuf = ByteBuffer.wrap(offBytes).order(ByteOrder.LITTLE_ENDIAN)
                val relOffsets = LongArray(spanCount + 1) { offBuf.int.toLong() and 0xFFFFFFFFL }

                val firstTimeByte = relOffsets[0]
                val lastTimeByte = relOffsets[spanCount]
                val totalTimeSpan = (lastTimeByte - firstTimeByte).toInt().coerceAtLeast(0)
                val timeSpanBytes = ByteArray(totalTimeSpan)
                if (totalTimeSpan > 0) {
                    raf.seek(timeBufBase + firstTimeByte)
                    raf.readFully(timeSpanBytes)
                }

                for (idx in indices) {
                    val rel = idx - firstI
                    val tStart = (relOffsets[rel] - firstTimeByte).toInt().coerceIn(0, timeSpanBytes.size)
                    val tEnd = (relOffsets[rel + 1] - firstTimeByte).toInt().coerceIn(tStart, timeSpanBytes.size)
                    val timeStr = String(timeSpanBytes, tStart, tEnd - tStart, Charsets.UTF_8)
                    candles.add(
                        ReplayCandle(
                            index = idx,
                            time = timeStr,
                            open = opens[rel],
                            high = highs[rel],
                            low = lows[rel],
                            close = closes[rel]
                        )
                    )
                }
            } else {
                val dblBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                val offPairBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

                fun readDoubleAt(base: Long, idx: Int): Double {
                    raf.seek(base + idx * 8L)
                    raf.readFully(dblBuf.array())
                    dblBuf.clear()
                    return dblBuf.double
                }

                for (idx in indices) {
                    val o = readDoubleAt(opensBase, idx)
                    val h = readDoubleAt(highsBase, idx)
                    val l = readDoubleAt(lowsBase, idx)
                    val c = readDoubleAt(closesBase, idx)
                    raf.seek(offsetsBase + idx * 4L)
                    raf.readFully(offPairBuf.array())
                    offPairBuf.clear()
                    val tStart = offPairBuf.int.toLong() and 0xFFFFFFFFL
                    val tEnd = offPairBuf.int.toLong() and 0xFFFFFFFFL
                    val len = (tEnd - tStart).toInt().coerceIn(0, 128)
                    val tBytes = ByteArray(len)
                    if (len > 0) {
                        raf.seek(timeBufBase + tStart)
                        raf.readFully(tBytes)
                    }
                    candles.add(
                        ReplayCandle(
                            index = idx,
                            time = String(tBytes, Charsets.UTF_8),
                            open = o,
                            high = h,
                            low = l,
                            close = c
                        )
                    )
                }
            }
        }

        val entryPos = candles.indexOfFirst { it.index == trade.openIndex }.let { if (it >= 0) it else 0 }
        val exitPos = candles.indexOfFirst { it.index == clampedClose }.let { if (it >= 0) it else candles.lastIndex }
        return TradeReplayWindow(
            candles = candles,
            entryCandlePos = entryPos,
            exitCandlePos = exitPos,
            truncatedMiddle = truncatedMiddle
        )
    }

    private fun readDoubleSlice(raf: RandomAccessFile, byteOffset: Long, count: Int): DoubleArray {
        val raw = ByteArray(count * 8)
        raf.seek(byteOffset)
        raf.readFully(raw)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return DoubleArray(count) { buf.double }
    }

    private fun readHeader(binFile: File): Header? {
        return try {
            RandomAccessFile(binFile, "r").use { raf ->
                if (raf.length() < HEADER_BYTES) return null
                val raw = ByteArray(HEADER_BYTES.toInt())
                raf.readFully(raw)
                val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buf.int
                val version = buf.int
                val count = buf.long
                val srcSize = buf.long
                val srcMtime = buf.long
                if (magic != MAGIC || version != VERSION || count < 0L || count > Int.MAX_VALUE) {
                    null
                } else {
                    Header(count.toInt(), srcSize, srcMtime)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openUtfAwareReader(file: File): Pair<PushbackInputStream, Charset> {
        val pb = PushbackInputStream(BufferedInputStream(FileInputStream(file), 65_536), 4)
        val bom = ByteArray(4)
        var read = 0
        while (read < 4) {
            val count = pb.read(bom, read, 4 - read)
            if (count <= 0) break
            read += count
        }
        return when {
            read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
                if (read > 2) pb.unread(bom, 2, read - 2)
                pb to Charsets.UTF_16LE
            }
            read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
                if (read > 2) pb.unread(bom, 2, read - 2)
                pb to Charsets.UTF_16BE
            }
            read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> {
                if (read > 3) pb.unread(bom, 3, read - 3)
                pb to Charsets.UTF_8
            }
            else -> {
                if (read > 0) pb.unread(bom, 0, read)
                pb to Charsets.UTF_8
            }
        }
    }

    private fun parseHeader(firstLine: String): ColumnMap? {
        val delim = when {
            firstLine.contains('\t') -> '\t'
            firstLine.contains(';') -> ';'
            else -> ','
        }
        val cols = splitCsvLine(firstLine, delim).map {
            it.trim().trim('<', '>', '"').lowercase(Locale.US)
        }
        val idxMap = HashMap<String, Int>(cols.size)
        cols.forEachIndexed { index, name ->
            if (name.isNotEmpty() && !idxMap.containsKey(name)) {
                idxMap[name] = index
            }
        }
        val openIdx = idxMap["open"] ?: return null
        val highIdx = idxMap["high"] ?: return null
        val lowIdx = idxMap["low"] ?: return null
        val closeIdx = idxMap["close"] ?: return null
        val volumeIdx = idxMap["tickvol"] ?: idxMap["volume"] ?: -1
        val hasDateTime = idxMap.containsKey("date") && idxMap.containsKey("time")
        val dateIdx = idxMap["date"] ?: -1
        val timeIdx = idxMap["time"] ?: -1
        val tsIdx = idxMap["datetime"] ?: idxMap["timestamp"] ?: idxMap["date"] ?: idxMap["time"] ?: return null
        return ColumnMap(delim, openIdx, highIdx, lowIdx, closeIdx, volumeIdx, hasDateTime, dateIdx, timeIdx, tsIdx)
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        if ('"' !in line) return line.split(delimiter)
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
