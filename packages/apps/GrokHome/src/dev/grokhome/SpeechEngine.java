package dev.grokhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * App-owned, offline speech input. It replaces Android's SpeechRecognizer, which on this tablet
 * resolves to a text-to-speech stub (GoogleTTSRecognitionService) and never becomes ready.
 *
 * One worker thread owns the microphone. WAKE listens for "Hey Bots" with a tiny grammar, plays a
 * chime, and reports the wake. COMMAND transcribes one free-form utterance. Callbacks come on the
 * worker thread; the caller posts to the UI thread.
 */
final class SpeechEngine {
    static final String TAG = "GrokHomeSpeech";
    static final int RATE = 16000;
    /**
     * One small model for wake and commands. The 0.22-lgraph model was measured on the SM-T733 at
     * about 12x slower than real time, which dropped audio and timed out commands.
     */
    private static final String WAKE_MODEL = "vosk-model-small-en-us-0.15";
    private static final String COMMAND_MODEL = WAKE_MODEL;
    /** Backstop only: a wake phrase never takes this long. The real guard is the [unk] taint below. */
    private static final long WAKE_MAX_SPEECH_MS = 3500;
    private static final String WAKE_GRAMMAR = "[\"hey bots\",\"okay bots\",\"[unk]\"]";
    /** Give up if nothing is said after the chime. */
    private static final long COMMAND_START_MS = 8000;
    /** Hard cap on one spoken command. */
    private static final long COMMAND_MAX_MS = 30000;

    interface Callback {
        /** Microphone is open and the wake grammar is running. */
        void armed();
        /** Wake heard and chime finished. The engine has stopped; start a command session. */
        void wake();
        /** Microphone is open for a command. */
        void listening();
        void partial(String text);
        /** Final command text. The engine has stopped. */
        void result(String text);
        /** No command was spoken. The engine has stopped. */
        void silence();
        /** Unrecoverable problem. The engine has stopped. */
        void failed(String title, String detail);
    }

    enum Mode { WAKE, COMMAND }

    private static final java.util.Map<String, Model> MODELS = new java.util.HashMap<>();
    private static final java.util.Map<String, String> MODEL_ERRORS = new java.util.HashMap<>();
    private static final Object MODEL_LOCK = new Object();

    private final Context context;
    private Thread worker;
    private volatile boolean cancelled;

    SpeechEngine(Context context) { this.context = context.getApplicationContext(); }

    /** True when `spoken` is a wake phrase. Recognised text is already lower case. */
    static boolean isWake(String spoken) {
        if (spoken == null) return false;
        String text = spoken.trim().toLowerCase(java.util.Locale.US);
        return text.equals("hey bots") || text.equals("okay bots");
    }

    synchronized void start(Mode mode, Callback callback) {
        stop();
        cancelled = false;
        final Thread[] self = new Thread[1];
        self[0] = new Thread(() -> run(mode, callback, self[0]), "speech-" + mode);
        worker = self[0];
        worker.start();
    }

    /** Stops capture and waits briefly so the next session can claim the microphone. */
    synchronized void stop() {
        cancelled = true;
        Thread t = worker;
        worker = null;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
            try { t.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private boolean live(Thread self) { return !cancelled && worker == self && !self.isInterrupted(); }

    private void run(Mode mode, Callback callback, Thread self) {
        Recognizer recognizer = null;
        AudioRecord record = null;
        try {
            Model model = model(context, mode == Mode.WAKE ? WAKE_MODEL : COMMAND_MODEL);
            if (!live(self)) return;
            record = openMic();
            if (record == null) { callback.failed("Microphone unavailable", "Allow microphone access and close apps that use it"); return; }
            recognizer = mode == Mode.WAKE ? new Recognizer(model, RATE, WAKE_GRAMMAR) : new Recognizer(model, RATE);
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) { callback.failed("Microphone busy", "Another app is using the microphone"); return; }
            Log.i(TAG, mode + " mic open");
            if (mode == Mode.WAKE) callback.armed(); else callback.listening();
            // A reader thread drains the microphone so a slow decode never overruns the AudioRecord buffer.
            final java.util.concurrent.LinkedBlockingQueue<short[]> pending = new java.util.concurrent.LinkedBlockingQueue<>();
            final AudioRecord source = record;
            final boolean[] readFailed = {false};
            Thread reader = new Thread(() -> {
                short[] chunk = new short[RATE / 10];
                while (live(self) && !Thread.currentThread().isInterrupted()) {
                    int got = source.read(chunk, 0, chunk.length);
                    if (got < 0) { readFailed[0] = true; return; }
                    if (got > 0) pending.offer(java.util.Arrays.copyOf(chunk, got));
                }
            }, "speech-reader");
            reader.start();
            long started = System.currentTimeMillis();
            boolean heardSpeech = false;
            String lastPartial = "";
            long speechStart = 0;
            boolean tainted = false;
            long lastChange = 0;
            long levelLog = started, peak = 0;
            try {
            while (live(self)) {
                if (readFailed[0]) { callback.failed("Microphone error", "Tap microphone to retry"); return; }
                short[] buffer = pending.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (buffer == null) continue;
                int n = buffer.length;
                for (int i = 0; i < n; i++) peak = Math.max(peak, Math.abs(buffer[i]));
                long t0 = System.nanoTime();
                boolean final_ = recognizer.acceptWaveForm(buffer, n);
                long ms = (System.nanoTime() - t0) / 1000000;
                if (ms > 150 + n * 1000L / RATE) Log.w(TAG, mode + " slow decode " + ms + " ms for " + (n * 1000 / RATE) + " ms of audio, backlog " + pending.size());
                String text = field(final_ ? recognizer.getResult() : recognizer.getPartialResult(), final_ ? "text" : "partial");
                long now = System.currentTimeMillis();
                if (now - levelLog > 10000) { Log.i(TAG, mode + " mic peak=" + peak); levelLog = now; peak = 0; }
                if (mode == Mode.WAKE) {
                    boolean voiced = !text.isEmpty();
                    if (voiced && !text.equals(lastPartial)) {
                        // A pause of over a second between changes means a new utterance: forget the old one.
                        if (now - lastChange > 1200) { speechStart = 0; tainted = false; }
                        lastChange = now; lastPartial = text; Log.i(TAG, "wake partial: " + text);
                    }
                    if (voiced && speechStart == 0) speechStart = now;
                    long spoke = speechStart == 0 ? 0 : now - speechStart;
                    // A sentence squeezed into a wake phrase passes through unknown-word tokens first.
                    if (text.contains("[unk]")) tainted = true;
                    boolean wake = isWake(text);
                    if (wake && (tainted || spoke > WAKE_MAX_SPEECH_MS)) {
                        Log.i(TAG, "ignored non-wake utterance: " + text);
                        recognizer.reset(); speechStart = 0; tainted = false; continue;
                    }
                    if (final_ || !voiced) { speechStart = 0; tainted = false; }
                    if (isWake(text)) {
                        Log.i(TAG, "wake heard: " + text);
                        record.stop(); record.release(); record = null;
                        if (!live(self)) return;
                        Chime.play(context);
                        if (live(self)) callback.wake();
                        return;
                    }
                    if (final_ && !text.isEmpty() && !text.equals("[unk]")) Log.i(TAG, "ignored: " + text);
                    // Vosk only rolls its window on silence; bound a very long unbroken noise.
                    if (now - started > 60000) { recognizer.reset(); started = now; }
                } else {
                    if (!text.isEmpty() && !text.equals("[unk]")) {
                        heardSpeech = true;
                        if (!final_ && !text.equals(lastPartial)) { lastPartial = text; Log.i(TAG, "partial: " + text); callback.partial(text); }
                    }
                    if (final_ && !text.isEmpty() && !text.equals("[unk]")) {
                        Log.i(TAG, "command: " + text);
                        if (live(self)) callback.result(text);
                        return;
                    }
                    if (!heardSpeech && now - started > COMMAND_START_MS) { if (live(self)) callback.silence(); return; }
                    if (now - started > COMMAND_MAX_MS) {
                        String tail = field(recognizer.getFinalResult(), "text");
                        if (live(self)) { if (tail.isEmpty()) callback.silence(); else callback.result(tail); }
                        return;
                    }
                }
            }
            } finally { cancelledReader(reader, self); }
        } catch (InterruptedException stopped) {
            // stop() interrupts the worker; not a failure.
        } catch (Throwable error) {
            Log.e(TAG, "engine failed", error);
            if (live(self)) callback.failed("Speech unavailable", String.valueOf(error.getMessage()));
        } finally {
            if (record != null) { try { record.stop(); } catch (RuntimeException ignored) {} record.release(); }
            if (recognizer != null) recognizer.close();
        }
    }

    private void cancelledReader(Thread reader, Thread self) {
        reader.interrupt();
        try { reader.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String field(String json, String key) {
        try { return new JSONObject(json).optString(key, "").trim(); } catch (JSONException e) { return ""; }
    }

    @SuppressWarnings("MissingPermission")
    private static AudioRecord openMic() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return null;
        int size = Math.max(min, RATE * 2);
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) { record.release(); return null; }
        return record;
    }

    /** Starts loading the speech model in the background so the first wake is instant. */
    static void preload(Context context) {
        final Context app = context.getApplicationContext();
        Thread t = new Thread(() -> { try { model(app, WAKE_MODEL); } catch (Throwable e) { Log.e(TAG, "preload failed", e); } }, "speech-preload");
        t.setDaemon(true);
        t.start();
    }

    static Model model(Context context, String name) throws IOException {
        synchronized (MODEL_LOCK) {
            Model cached = MODELS.get(name);
            if (cached != null) return cached;
            if (MODEL_ERRORS.containsKey(name)) throw new IOException(MODEL_ERRORS.get(name));
            long begun = System.currentTimeMillis();
            try {
                File dir = new File(context.getFilesDir(), "vosk/" + name);
                File done = new File(dir, ".complete-" + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode());
                if (!done.exists()) {
                    deleteTree(dir);
                    unzip(context, name + ".zip", new File(context.getFilesDir(), "vosk"));
                    if (!done.createNewFile()) throw new IOException("Cannot finish speech model install");
                }
                org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS);
                Model model = new Model(dir.getAbsolutePath());
                MODELS.put(name, model);
                Log.i(TAG, name + " ready in " + (System.currentTimeMillis() - begun) + " ms");
                return model;
            } catch (Exception e) {
                String message = "Speech model unavailable: " + e.getMessage();
                MODEL_ERRORS.put(name, message);
                throw new IOException(message, e);
            }
        }
    }

    private static void unzip(Context context, String asset, File into) throws IOException {
        String root = into.getCanonicalPath() + File.separator;
        try (InputStream in = context.getAssets().open(asset); ZipInputStream zip = new ZipInputStream(in)) {
            byte[] chunk = new byte[64 * 1024];
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                File out = new File(into, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new IOException("Bad speech model entry");
                if (entry.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (OutputStream sink = new FileOutputStream(out)) {
                    for (int n; (n = zip.read(chunk)) > 0; ) sink.write(chunk, 0, n);
                }
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    /** Short two-note chime played out loud on the media stream. */
    static final class Chime {
        private Chime() {}
        static byte[] pcm() {
            int rate = 24000;
            double[][] notes = {{880, 0.14}, {1318.5, 0.30}};
            int total = 0;
            for (double[] note : notes) total += (int) (note[1] * rate);
            short[] samples = new short[total];
            int at = 0;
            for (double[] note : notes) {
                int count = (int) (note[1] * rate);
                for (int i = 0; i < count; i++) {
                    double t = (double) i / rate;
                    double fade = Math.min(1, Math.min(i / (rate * 0.01), (count - i) / (rate * 0.12)));
                    double wave = Math.sin(2 * Math.PI * note[0] * t) + 0.3 * Math.sin(4 * Math.PI * note[0] * t);
                    samples[at++] = (short) (wave / 1.3 * fade * 0.8 * Short.MAX_VALUE);
                }
            }
            byte[] bytes = new byte[samples.length * 2];
            for (int i = 0; i < samples.length; i++) { bytes[2 * i] = (byte) samples[i]; bytes[2 * i + 1] = (byte) (samples[i] >> 8); }
            return bytes;
        }

        /** Blocks until the chime has played (about half a second). */
        static void play(Context context) {
            byte[] data = pcm();
            AudioTrack track = null;
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            try {
                // This is a dedicated home tablet: keep the chime and Max's spoken replies audible.
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), floor = Math.round(max * 0.7f);
                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < floor) audio.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0);
                track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(data.length).build();
                track.write(data, 0, data.length);
                track.setVolume(1f);
                track.play();
                long deadline = System.currentTimeMillis() + 1500;
                int frames = data.length / 2;
                while (track.getPlaybackHeadPosition() < frames && System.currentTimeMillis() < deadline) Thread.sleep(15);
                Thread.sleep(80);
                Log.i(TAG, "chime played, media volume " + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                Log.e(TAG, "chime failed", e);
            } finally {
                if (track != null) { try { track.stop(); } catch (RuntimeException ignored) {} track.release(); }
            }
        }
    }
}
