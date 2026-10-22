package dev.grokhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.InetAddress;

/** Tablet-side network facts. The Wi-Fi name is not read: this app has no location permission. */
final class TabletNetwork {
    static final class Snapshot {
        final boolean validated;
        final String transportLabel;
        final boolean tailscaleOn;

        Snapshot(boolean validated, String transportLabel, boolean tailscaleOn) {
            this.validated = validated;
            this.transportLabel = transportLabel == null ? "" : transportLabel;
            this.tailscaleOn = tailscaleOn;
        }
    }

    static Snapshot read(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return read(manager);
    }

    static Snapshot read(ConnectivityManager manager) {
        if (manager == null) return new Snapshot(false, "", false);
        boolean validated = false;
        boolean wifi = false;
        boolean cellular = false;
        boolean tailscale = false;
        Network[] networks = manager.getAllNetworks();
        if (networks != null) {
            for (Network network : networks) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps != null) {
                    boolean internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    if (internet && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) validated = true;
                    if (internet && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
                    if (internet && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellular = true;
                }
                LinkProperties links = manager.getLinkProperties(network);
                if (links != null && links.getLinkAddresses() != null) {
                    for (LinkAddress link : links.getLinkAddresses()) {
                        InetAddress address = link.getAddress();
                        if (address != null && ConnectionRules.isTailscaleIpv4(address.getHostAddress())) tailscale = true;
                    }
                }
            }
        }
        String label = wifi ? "Wi-Fi" : cellular ? "mobile data" : "";
        return new Snapshot(validated, label, tailscale);
    }

    private TabletNetwork() {}
}
