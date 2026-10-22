package dev.grokhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Foreground /health probe. Last sync stays on the activity feeds, not on this poll. */
final class ConnectionMonitor {
    /** Recheck while the chip says Connected, the activity is resumed, and ambient is off. */
    private static final long CONNECTED_PROBE_MS = 60_000L;

    private final ConnectionState state;
    private final Supplier<String> server;
    private final Supplier<TabletNetwork.Snapshot> network;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!running) return;
            state.pulse(SystemClock.elapsedRealtime());
            main.postDelayed(this, 1000);
        }
    };
    private final Runnable retry = () -> probe();
    private final Runnable connectedProbe = () -> probe();
    private Call call;
    private int generation;
    private int failures;
    private boolean running;
    private boolean ambient;
    /** elapsedRealtime when the next Connected probe should run. Zero if none is owed. */
    private long connectedDueElapsed;

    ConnectionMonitor(Context context, ConnectionState state, Supplier<String> server) {
        this(state, server, () -> TabletNetwork.read(context),
                new OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build());
    }

    ConnectionMonitor(ConnectionState state, Supplier<String> server, Supplier<TabletNetwork.Snapshot> network, OkHttpClient http) {
        this.state = state;
        this.server = server;
        this.network = network;
        this.http = http;
    }

    void start() {
        if (running) return;
        running = true;
        failures = 0;
        main.removeCallbacks(tick);
        main.post(tick);
        probe();
    }

    void stop() {
        running = false;
        generation++;
        connectedDueElapsed = 0;
        main.removeCallbacks(tick);
        main.removeCallbacks(retry);
        main.removeCallbacks(connectedProbe);
        if (call != null) call.cancel();
    }

    /** Ambient keeps today's offline backoff. It does not start another Connected probe. */
    void setAmbient(boolean value) {
        if (ambient == value) return;
        ambient = value;
        if (ambient) main.removeCallbacks(connectedProbe);
        else if (running && state.mode() == ConnectionRules.Mode.CONNECTED) scheduleConnected();
    }

    /** When the next Connected /health is due, or 0. Interaction must not move a future due time. */
    long connectedProbeDueElapsed() { return connectedDueElapsed; }

    void close() {
        stop();
        http.dispatcher().cancelAll();
        http.dispatcher().executorService().execute(() -> http.connectionPool().evictAll());
    }

    void tryNow() {
        if (!running) return;
        failures = 0;
        main.removeCallbacks(retry);
        probe();
    }

    /** A bot request failed. Flip the chip now, then let the probe confirm. */
    void noteUnreachable() {
        if (!running) return;
        failures++;
        long now = SystemClock.elapsedRealtime();
        long delay = ConnectionRules.retryDelayMs(failures);
        TabletNetwork.Snapshot snapshot = network.get();
        publish(ConnectionRules.Mode.OFFLINE, snapshot, false, voiceDetail(false), now + delay, now);
        schedule(delay);
    }

    void noteSynced(long nowElapsed) { state.noteSynced(nowElapsed); }

    private void probe() {
        if (!running) return;
        final int ticket = ++generation;
        if (call != null) call.cancel();
        connectedDueElapsed = 0;
        main.removeCallbacks(retry);
        main.removeCallbacks(connectedProbe);
        TabletNetwork.Snapshot network = this.network.get();
        long now = SystemClock.elapsedRealtime();
        if (!network.validated) {
            failures++;
            long delay = ConnectionRules.retryDelayMs(failures);
            publish(ConnectionRules.Mode.OFFLINE, network, false, "No connection on the tablet", now + delay, now);
            schedule(delay);
            return;
        }
        HttpUrl url = healthUrl(server.get());
        if (url == null) {
            failures++;
            long delay = ConnectionRules.retryDelayMs(failures);
            publish(ConnectionRules.Mode.OFFLINE, network, false, voiceDetail(false), now + delay, now);
            schedule(delay);
            return;
        }
        call = http.newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            public void onFailure(Call call, IOException error) { main.post(() -> finish(ticket, network, ConnectionRules.Health.parse(0, ""))); }
            public void onResponse(Call call, Response response) {
                ConnectionRules.Health health;
                try (Response body = response) {
                    String text = body.body() == null ? "" : body.body().string();
                    health = ConnectionRules.Health.parse(body.code(), text);
                } catch (Exception error) {
                    health = ConnectionRules.Health.parse(0, "");
                }
                ConnectionRules.Health result = health;
                main.post(() -> finish(ticket, network, result));
            }
        });
    }

    private void finish(int ticket, TabletNetwork.Snapshot network, ConnectionRules.Health health) {
        if (!running || ticket != generation) return;
        long now = SystemClock.elapsedRealtime();
        ConnectionRules.HostKind kind = ConnectionRules.hostKind(server.get());
        ConnectionRules.Mode mode = ConnectionRules.map(network.validated, health.answering, kind);
        if (mode == ConnectionRules.Mode.OFFLINE) {
            failures++;
            long delay = ConnectionRules.retryDelayMs(failures);
            publish(mode, network, false, voiceDetail(false), now + delay, now);
            schedule(delay);
            return;
        }
        failures = 0;
        publish(mode, network, true, "Answering", 0, now);
        if (mode == ConnectionRules.Mode.CONNECTED) scheduleConnected();
    }

    private void publish(ConnectionRules.Mode mode, TabletNetwork.Snapshot network, boolean voice, String detail, long retryAt, long now) {
        state.publish(mode, network.validated, network.transportLabel, network.tailscaleOn, voice, detail, retryAt, now);
    }

    private void schedule(long delay) {
        connectedDueElapsed = 0;
        main.removeCallbacks(retry);
        main.removeCallbacks(connectedProbe);
        if (running) main.postDelayed(retry, delay);
    }

    /**
     * Arms the next Connected probe. A due time already in the future is kept, so a tap,
     * a redraw, or leaving ambient early cannot push it back by another 60 seconds.
     */
    private void scheduleConnected() {
        if (!running || ambient) return;
        long now = SystemClock.elapsedRealtime();
        long delay;
        if (connectedDueElapsed > now) delay = connectedDueElapsed - now;
        else {
            delay = CONNECTED_PROBE_MS;
            connectedDueElapsed = now + delay;
        }
        main.removeCallbacks(connectedProbe);
        main.postDelayed(connectedProbe, delay);
    }

    private String voiceDetail(boolean answering) {
        if (answering) return "Answering";
        int port = ConnectionRules.port(server.get());
        if (port <= 0) return "No answer. Is the computer asleep?";
        return "No answer on port " + port + ". Is the computer asleep?";
    }

    private static HttpUrl healthUrl(String base) {
        if (base == null || base.trim().isEmpty()) return null;
        String trimmed = base.trim();
        if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return HttpUrl.parse(trimmed + "/health");
    }
}
