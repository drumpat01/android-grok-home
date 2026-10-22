package dev.grokhome;

import java.util.List;

/**
 * Read-only needs_you for the ambient wall.
 * {@link BridgeNeedsYouAdapter} reads the bridge snapshots already on main.
 * Repoint that adapter when the needs_you model class lands.
 */
interface NeedsYouSource {
    /** Unresolved rows. Empty when no hand is up. */
    List<NeedsYouNotice> items();
}
