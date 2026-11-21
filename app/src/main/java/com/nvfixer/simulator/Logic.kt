package com.nvfixer.simulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Tone { BAD, OK, WARN }
enum class LogKind { N, I, S, W, E }

data class LogLine(val text: String, val kind: LogKind)
data class Profile(val name: String, val chip: String, val prefix: String)
data class ChipOption(val key: String, val label: String)

val PROFILES = listOf(
    Profile("Generic MTK phone", "MTK", "35"),
    Profile("Generic Snapdragon phone", "QCOM", "86"),
    Profile("Generic Unisoc phone", "SPD", "35"),
    Profile("Pixel-ish (Qualcomm)", "QCOM", "35"),
    Profile("Redmi-ish (MediaTek)", "MTK", "86"),
    Profile("Tecno-ish (MediaTek)", "MTK", "35"),
)

val CHIPS = listOf(
    ChipOption("MTK", "MediaTek (MTK Engineering / META)"),
    ChipOption("QCOM", "Qualcomm Snapdragon (Diag Port 901D)"),
    ChipOption("SPD", "Unisoc / Spreadtrum (AT Interface)"),
)

/** Luhn check for a 15 digit string (same rule as the HTML version). */
fun luhnOk(s: String): Boolean {
    if (s.length != 15 || !s.all { it.isDigit() }) return false
    var sum = 0
    for (i in 0 until 15) {
        var d = s[i] - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return sum % 10 == 0
}

/** Builds a Luhn-valid 15 digit number starting with [prefix]. */
fun makeImei(prefix: String): String {
    val rnd = Random.nextLong(0L, 1_000_000_000_000L).toString().padStart(12, '0')
    val b = (prefix + rnd).take(14)
    var sum = 0
    for (i in 0 until 14) {
        var d = b[i] - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return b + ((10 - sum % 10) % 10)
}

/** Live validation hint under each IMEI field. Returns null when empty. */
fun imeiHint(v: String): Pair<String, LogKind>? {
    val s = v.trim()
    if (s.isEmpty()) return null
    if (!s.all { it.isDigit() }) return "✗ digits only" to LogKind.E
    if (s.length < 15) return "${s.length}/15 digits" to LogKind.W
    return if (luhnOk(s)) "✓ Luhn checksum valid · TAC ${s.take(8)}" to LogKind.S
    else "✗ Luhn checksum invalid" to LogKind.E
}

object Sound {
    fun beep(freq: Int, ms: Int) {
        Thread {
            try {
                val sr = 22050
                val n = sr * ms / 1000
                val buf = ShortArray(n) { i ->
                    (sin(2.0 * PI * i * freq / sr) * Short.MAX_VALUE * 0.15).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, n)
                track.play()
                Thread.sleep(ms + 60L)
                track.release()
            } catch (e: Exception) {
            }
        }.start()
    }
}
