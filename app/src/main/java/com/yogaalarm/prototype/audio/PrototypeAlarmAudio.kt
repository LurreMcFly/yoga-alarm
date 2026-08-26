package com.yogaalarm.prototype.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.yogaalarm.prototype.R
import com.yogaalarm.prototype.model.AlarmSound

class PrototypeAlarmAudio(
    private val context: Context,
    private val sound: AlarmSound,
) : AutoCloseable {
    private var player: MediaPlayer? = null

    fun start() {
        if (player != null) return
        val resource = when (sound) {
            AlarmSound.MORNING_BELLS -> R.raw.morning_bells
            AlarmSound.NATURE_BIRDS -> R.raw.nature_birds
        }
        val descriptor = context.resources.openRawResourceFd(resource)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            isLooping = true
            prepare()
            setVolume(1f, 1f)
            start()
        }
        descriptor.close()
    }

    fun setLevel(level: Float) {
        val volume = level.coerceIn(0f, 1f)
        player?.setVolume(volume, volume)
    }

    override fun close() {
        player?.release()
        player = null
    }
}
