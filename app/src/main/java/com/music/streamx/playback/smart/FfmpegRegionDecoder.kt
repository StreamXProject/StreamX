package com.music.streamx.playback.smart

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import java.nio.ByteBuffer

/**
 * [AudioDecoder]'s decode loop on StreamX's bundled FFmpeg instead of
 * [MediaCodec], for formats the platform cannot be trusted with.
 *
 * Written for ALAC: Apple Music's lossless stream plays through this same
 * decoder, but handed to the phone's own ALAC component (`c2.qti.alac.sw` on the
 * Xiaomi it was measured on) it fails on the first buffer, so analysis of those
 * tracks never got any audio. Same contract as the MediaCodec loop: 16-bit PCM
 * to [onBuffer], and the output rate paired with the region's real start.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object FfmpegRegionDecoder {

    fun handles(mime: String): Boolean = mime == "audio/alac" && FfmpegLibrary.supportsFormat(mime)

    fun decode(
        extractor: MediaExtractor,
        format: MediaFormat,
        endUs: Long,
        abort: () -> Boolean,
        onBuffer: (ByteBuffer, MediaCodec.BufferInfo, Int) -> Unit,
    ): Pair<Double, Double>? {
        val csd = format.getByteBuffer("csd-0")?.let { buffer -> ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) } }
        val media3Format = Format.Builder()
            .setSampleMimeType(format.getString(MediaFormat.KEY_MIME))
            .setChannelCount(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            .setSampleRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            .setInitializationData(listOfNotNull(csd))
            .build()
        val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1 shl 16
        val decoder = FfmpegAudioDecoder(media3Format, 8, 8, maxInput, false)
        try {
            val info = MediaCodec.BufferInfo()
            var actualStartSeconds = -1.0
            var inputDone = false
            while (true) {
                if (abort()) return null
                var progressed = false
                if (!inputDone) {
                    decoder.dequeueInputBuffer()?.let { input ->
                        val size = extractor.sampleSize.toInt()
                        val timeUs = extractor.sampleTime
                        if (size < 0 || timeUs > endUs) {
                            input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            input.ensureSpaceForWrite(size)
                            val data = input.data!!
                            val read = extractor.readSampleData(data, data.position())
                            data.position(data.position() + read)
                            input.timeUs = timeUs
                            extractor.advance()
                        }
                        input.flip()
                        decoder.queueInputBuffer(input)
                        progressed = true
                    }
                }
                val output = decoder.dequeueOutputBuffer()
                if (output != null) {
                    progressed = true
                    if (output.isEndOfStream) {
                        output.release()
                        break
                    }
                    val data = output.data
                    if (data != null && data.remaining() > 0) {
                        if (actualStartSeconds < 0) actualStartSeconds = output.timeUs / 1_000_000.0
                        info.set(data.position(), data.remaining(), output.timeUs, 0)
                        onBuffer(data, info, decoder.channelCount)
                    }
                    output.release()
                    if (output.timeUs > endUs) break
                }
                if (!progressed) Thread.sleep(1)
            }
            if (actualStartSeconds < 0 || decoder.sampleRate <= 0) return null
            return decoder.sampleRate.toDouble() to actualStartSeconds
        } finally {
            decoder.release()
        }
    }
}
