# Security

This is an experimental personal-device launcher and private gateway. Keep the BDK listener on loopback and use a trusted LAN or access-controlled private Tailscale network. Network location is part of the gateway's trust boundary; LAN clients do not have individual account authentication.

Do not expose either service to the public internet. Keep credentials, bot rosters, transcript exports, device identifiers and signing keys outside version control. Review staged changes for private information before pushing. Never include tokens or private transcripts in issue reports.

For a suspected vulnerability, use GitHub's private vulnerability reporting feature when available. Otherwise open an issue requesting a private contact method without publishing exploit details or secrets. Do not test against accounts or services you do not own.

## Dependency advisories

As of 2026-10-16, the bridge uses Cursor BDK 0.2.26 with BDK-scoped npm overrides for MCP SDK 1.32.1 and OpenTelemetry Core 2.12.0. These replace vulnerable transitive versions while preserving the BDK's existing exporter family. A clean `npm ci` followed by `npm audit --audit-level=low` reports zero vulnerabilities. This is an audit snapshot, not a guarantee against future advisories.

The overrides address [MCP OAuth issuer confusion](https://github.com/modelcontextprotocol/typescript-sdk/security/advisories/GHSA-6qxp-vccf-f47h) and [unbounded OpenTelemetry baggage extraction](https://github.com/open-telemetry/opentelemetry-js/security/advisories/GHSA-8988-4f7v-96qf). Keep these overrides until an upstream BDK release includes compatible fixes; do not remove them merely because BDK has a newer version.

The bot bridge does not configure external OAuth MCP connections. If you independently add such connections, credentials saved before MCP SDK 1.31.0 may lack an `issuer` binding. Clear those old MCP OAuth credentials and sign in again only to trusted servers, or explicitly bind them to the verified issuer before reuse. Never share or commit the OAuth store. The BDK store preserves new issuer metadata, as covered by an isolated synthetic regression test. This upgrade does not inspect or migrate your existing credentials. Cursor bot sign-in is separate from these external MCP OAuth credentials.

`npm test` exercises wrong-issuer rejection, same-issuer token requests, bounded string and multi-line baggage headers through BDK/exporter dependency resolution, issuer persistence, and real BDK tool loading. All credentials and bot names in those checks are synthetic; no live bot message is sent. The regular bridge tests cover send/check behavior with mock responses. Device voice and authenticated live replies require a separate user-run check.
