package dev.grokhome;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observable connection snapshot. Raise Your Hand's offline banner subscribes here.
 * Callers stay on the main thread.
 */
public final class ConnectionState {
    public interface Observer {
        void onConnectionChanged(ConnectionState state);
    }

    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private ConnectionRules.Mode mode = ConnectionRules.Mode.OFFLINE;
    private boolean known;
    private long lastSyncElapsed;
    private long retryAtElapsed;
    private boolean tabletOnline;
    private String transportLabel = "";
    private boolean tailscaleOn;
    private boolean voiceAnswering;
    private String voiceDetail = "";
    private int retrySeconds = -1;
    private String syncPhrase = "";

    public ConnectionRules.Mode mode() { return mode; }
    /** False until the first probe. The chip stays hidden so launch does not flash Offline. */
    public boolean known() { return known; }
    /** elapsedRealtime of the last non-stale activity sync, or 0. */
    public long lastSyncElapsed() { return lastSyncElapsed; }
    public long retryAtElapsed() { return retryAtElapsed; }
    public boolean tabletOnline() { return tabletOnline; }
    public String transportLabel() { return transportLabel; }
    public boolean tailscaleOn() { return tailscaleOn; }
    public boolean voiceAnswering() { return voiceAnswering; }
    public String voiceDetail() { return voiceDetail; }

    public long syncAgeMs(long nowElapsed) {
        if (lastSyncElapsed <= 0 || nowElapsed < lastSyncElapsed) return -1;
        return nowElapsed - lastSyncElapsed;
    }

    public long retryInMs(long nowElapsed) {
        if (mode != ConnectionRules.Mode.OFFLINE || retryAtElapsed <= 0) return 0;
        return Math.max(0, retryAtElapsed - nowElapsed);
    }

    public void observe(Observer observer) {
        observers.add(observer);
        observer.onConnectionChanged(this);
    }

    public void noteSynced(long nowElapsed) {
        if (nowElapsed <= 0 || nowElapsed == lastSyncElapsed) return;
        lastSyncElapsed = nowElapsed;
        retrySeconds = seconds(retryInMs(nowElapsed));
        syncPhrase = phrase(nowElapsed);
        notifyObservers();
    }

    void publish(ConnectionRules.Mode next, boolean online, String transport, boolean tailscale, boolean voice, String detail, long retryAt, long nowElapsed) {
        known = true;
        mode = next;
        tabletOnline = online;
        transportLabel = transport == null ? "" : transport;
        tailscaleOn = tailscale;
        voiceAnswering = voice;
        voiceDetail = detail == null ? "" : detail;
        retryAtElapsed = retryAt;
        retrySeconds = seconds(retryInMs(nowElapsed));
        syncPhrase = phrase(nowElapsed);
        notifyObservers();
    }

    /** Refreshes the retry sentence and the sync wording without resetting the other fields. */
    void pulse(long nowElapsed) {
        int seconds = seconds(retryInMs(nowElapsed));
        String phrase = phrase(nowElapsed);
        if (seconds == retrySeconds && phrase.equals(syncPhrase)) return;
        retrySeconds = seconds;
        syncPhrase = phrase;
        notifyObservers();
    }

    private String phrase(long nowElapsed) {
        long age = syncAgeMs(nowElapsed);
        return ConnectionRules.syncCompact(age) + "\n" + ConnectionRules.syncSentence(age);
    }

    private void notifyObservers() {
        for (Observer observer : observers) observer.onConnectionChanged(this);
    }

    private static int seconds(long remainingMs) {
        return (int) Math.max(0, (remainingMs + 999) / 1000);
    }
}
