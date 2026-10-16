# Security

This is an experimental personal-device launcher and private gateway. Keep the BDK listener on loopback and use a trusted LAN or access-controlled private Tailscale network. Network location is part of the gateway's trust boundary; LAN clients do not have individual account authentication.

Do not expose either service to the public internet. Keep credentials, bot rosters, transcript exports, device identifiers and signing keys outside version control. Review staged changes for private information before pushing. Never include tokens or private transcripts in issue reports.

For a suspected vulnerability, use GitHub's private vulnerability reporting feature when available. Otherwise open an issue requesting a private contact method without publishing exploit details or secrets. Do not test against accounts or services you do not own.

## Dependency advisories

At publication on 2026-10-16, `npm audit` reports 15 affected dependency entries (13 moderate and 2 high) in the pinned Cursor BDK dependency tree. The high entries include the MCP SDK and its BDK parent. `npm audit fix` does not resolve these advisories with the current dependency constraints. These are dependency audit results, not 15 independently confirmed exploitable paths in this project. Keep the BDK listener on loopback; review the current audit before deployment and retest internal BDK imports when upgrading. This repository does not claim to be free of dependency vulnerabilities.
