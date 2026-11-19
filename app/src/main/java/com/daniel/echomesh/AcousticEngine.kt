package com.daniel.echomesh

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AcousticEngine(
    private val sampleRate: Int = 44100
) {
    private var listenJob: Job? = null

    fun transmitPulse(freqHz: Double = 1850.0, durationMs: Int = 600) {
        CoroutineScope(Dispatchers.IO).launch {
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmData = ShortArray(totalSamples)
            val fadeSamples = (sampleRate * 0.03).toInt().coerceAtMost(totalSamples / 4)

            for (i in 0 until totalSamples) {
                val angle = 2.0 * PI * freqHz * i / sampleRate
                val envelope = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > totalSamples - fadeSamples -> (totalSamples - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                pcmData[i] = (sin(angle) * Short.MAX_VALUE * 0.7 * envelope).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmData, 0, pcmData.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong() + 120L)
            audioTrack.stop()
            audioTrack.release()
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        targetFreqHz: Double = 1850.0,
        threshold: Double = 25.0,
        onLevelUpdate: (score: Double, detected: Boolean) -> Unit
    ) {
        stopListening()
        val blockSize = 1024
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(blockSize * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        recorder.startRecording()
        val buffer = ShortArray(blockSize)

        listenJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val readCount = recorder.read(buffer, 0, blockSize)
                    if (readCount > 0) {
                        val score = goertzelMagnitude(buffer, readCount, targetFreqHz, sampleRate)
                        val detected = score >= threshold
                        launch(Dispatchers.Main) {
                            onLevelUpdate(score, detected)
                        }
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                recorder.release()
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    private fun goertzelMagnitude(
        samples: ShortArray,
        count: Int,
        targetFreq: Double,
        sr: Int
    ): Double {
        val k = ((0.5 + (count * targetFreq) / sr)).toInt()
        val omega = (2.0 * PI * k) / count
        val coeff = 2.0 * cos(omega)

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / Short.MAX_VALUE
            q0 = coeff * q1 - q2 + normalized
            q2 = q1
            q1 = q0
        }

        val magnitude = kotlin.math.sqrt((q1 * q1) + (q2 * q2) - (q1 * q2 * coeff))
        return magnitude * 100.0
    }
}
