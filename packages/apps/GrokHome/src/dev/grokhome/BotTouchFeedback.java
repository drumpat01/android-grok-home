package dev.grokhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** A short warm tap on the same volume as bot speech. Playback never takes audio focus. */
final class BotTouchFeedback {
    private final AudioTrack sound;
    private final Vibrator vibrator;

    BotTouchFeedback(Context context) {
        sound = tone();
        Vibrator motor = context.getSystemService(Vibrator.class);
        vibrator = motor != null && motor.hasVibrator() ? motor : null;
    }

    void play() {
        if (sound != null) {
            try {
                int state = sound.getPlayState();
                if (state == AudioTrack.PLAYSTATE_PLAYING || state == AudioTrack.PLAYSTATE_PAUSED) sound.stop();
                sound.setPlaybackHeadPosition(0);
                sound.play();
            } catch (IllegalStateException ignored) {
                // A failed restart leaves the next tap able to try again.
            }
        }
        if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(12, 40));
    }

    void release() {
        if (sound != null) sound.release();
    }

    private static AudioTrack tone() {
        int rate = 48000;
        int count = (int) (rate * 0.16);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) rate;
            // One soft G with its octave. A slow fade, no high metallic partials.
            double fade = Math.sin(Math.min(1, t / 0.014) * Math.PI / 2) * Math.exp(-t * 11);
            double note = Math.sin(2 * Math.PI * 392 * t) * 0.62 + Math.sin(2 * Math.PI * 784 * t) * 0.16;
            double sample = note * fade * 0.42;
            pcm[i] = (short) Math.max(-32767, Math.min(32767, Math.round(sample * 32767)));
        }
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        AudioFormat format = new AudioFormat.Builder()
            .setSampleRate(rate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build();
        AudioTrack track = null;
        try {
            track = new AudioTrack(attributes, format, pcm.length * 2, AudioTrack.MODE_STATIC, 0);
            // Static tracks stay uninitialized until the whole buffer is written.
            if (track.getState() == AudioTrack.STATE_UNINITIALIZED || track.write(pcm, 0, pcm.length) != pcm.length || track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                return null;
            }
            track.setVolume(0.62f);
            return track;
        } catch (IllegalArgumentException | IllegalStateException failed) {
            if (track != null) track.release();
            return null;
        }
    }
}
