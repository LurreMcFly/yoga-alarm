package com.lurremcfly.yogaalarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.model.AlarmSound

class AlarmAudio(
    private val context: Context,
    private val sound: AlarmSound,
    private val onError: (String) -> Unit = { Log.e("AlarmAudio", it) },
) : AutoCloseable {
    private var player: MediaPlayer? = null
    private var level = 1f
    private var prepared = false

    fun start() {
        if (player != null) return
        val resource = when (sound) {
            AlarmSound.SUNBIRD_MORNING_CALL -> R.raw.sunbird_morning_call
            AlarmSound.MORNING_TEMPLE_CALL -> R.raw.morning_temple_call
            AlarmSound.LOTUS_SUNRISE_LOOP -> R.raw.lotus_sunrise_loop
            AlarmSound.DIZI_DAWN_ALARM -> R.raw.dizi_dawn_alarm
            AlarmSound.SILK_ROAD_SUNRISE -> R.raw.silk_road_sunrise
            AlarmSound.SUNRISE_CIRCLE -> R.raw.sunrise_circle
            AlarmSound.BAMBOO_DAWN_BELL -> R.raw.bamboo_dawn_bell
            AlarmSound.MORNING_BELL_RUN -> R.raw.morning_bell_run
            AlarmSound.SUNRISE_FLOW_LOOP -> R.raw.sunrise_flow_loop
            AlarmSound.RICE_PAPER_DAWN -> R.raw.rice_paper_dawn
        }
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        try {
            nextPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                context.resources.openRawResourceFd(resource).use { descriptor ->
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                isLooping = true
                setOnPreparedListener { ready ->
                    if (player === ready) {
                        prepared = true
                        ready.setVolume(level, level)
                        ready.start()
                    }
                }
                setOnErrorListener { failed, _, _ ->
                    if (player === failed) {
                        close()
                        onError("Unable to play this alarm sound")
                    }
                    true
                }
                prepareAsync()
            }
        } catch (error: Exception) {
            close()
            onError(error.message ?: "Unable to prepare this alarm sound")
        }
    }

    fun setLevel(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        if (prepared) player?.setVolume(this.level, this.level)
    }

    override fun close() {
        val previous = player
        player = null
        prepared = false
        previous?.setOnPreparedListener(null)
        previous?.setOnErrorListener(null)
        previous?.release()
    }
}
