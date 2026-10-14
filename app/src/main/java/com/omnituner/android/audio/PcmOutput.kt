package com.omnituner.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

object PcmOutput {

    const val SAMPLE_RATE = 48000

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun playOnce(samples: FloatArray, length: Int, sampleRate: Int, contentType: Int) {
        if (length <= 0) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes(contentType))
            .setAudioFormat(format(sampleRate))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(length * Float.SIZE_BYTES)
            .build()
        if (track.write(samples, 0, length, AudioTrack.WRITE_BLOCKING) < 0) {
            track.release()
            return
        }
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(finished: AudioTrack) {
                    finished.release()
                }

                override fun onPeriodicNotification(finished: AudioTrack) = Unit
            },
            mainHandler,
        )
        track.setNotificationMarkerPosition(length)
        track.play()
    }

    fun stream(sampleRate: Int, bufferBytes: Int, contentType: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(attributes(contentType))
        .setAudioFormat(format(sampleRate))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes)
        .build()

    private fun attributes(contentType: Int): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(contentType)
        .build()

    private fun format(sampleRate: Int): AudioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()
}
