import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const bdk = createRequire(require.resolve('@cursor/bdk'));
const { ROOT_CONTEXT, propagation } = require('@opentelemetry/api');
const { fetchToken, auth } = await import(pathToFileURL(bdk.resolve('@modelcontextprotocol/sdk/client/auth.js')));
const getter = { get: (carrier, key) => carrier[key], keys: Object.keys };
const parents = [
  '@cursor/bdk',
  '@opentelemetry/exporter-logs-otlp-http',
  '@opentelemetry/exporter-logs-otlp-proto',
  '@opentelemetry/exporter-metrics-otlp-http',
  '@opentelemetry/exporter-metrics-otlp-proto',
  '@opentelemetry/exporter-trace-otlp-http',
  '@opentelemetry/exporter-trace-otlp-proto',
  '@opentelemetry/sdk-logs',
];

for (const parent of parents) {
  test(`baggage extraction is bounded through ${parent}`, () => {
    const fromParent = createRequire(parent === '@cursor/bdk' ? require.resolve(parent) : bdk.resolve(parent));
    const { W3CBaggagePropagator } = fromParent('@opentelemetry/core');
    const propagator = new W3CBaggagePropagator();
    const entries = value => propagation.getBaggage(propagator.extract(ROOT_CONTEXT, { baggage: value }, getter))?.getAllEntries() ?? [];
    // Exercise both string headers and multiple header lines, including encoded values.
    const many = Array.from({ length: 300 }, (_, i) => `key${i}=value`);
    assert.equal(entries(many.join(',')).length, 180);
    assert.equal(entries([many.slice(0, 150).join(','), many.slice(150).join(',')]).length, 180);
    assert.equal(entries('oversized=' + 'x'.repeat(5000)).length, 0);
    const large = entries(Array.from({ length: 100 }, (_, i) => `key${i}=${'x'.repeat(200)}`).join(','));
    assert(large.length > 0 && large.length < 100);
    assert(large.reduce((sum, [key, entry]) => sum + key.length + entry.value.length + 2, 0) <= 8193);
    assert.deepEqual(entries('team=hello%20world,feature=chat'), [['team', { value: 'hello world' }], ['feature', { value: 'chat' }]]);
  });
}

function provider(issuer) {
  return {
    clientMetadata: {},
    clientInformation: () => ({ client_id: 'synthetic-client', client_secret: 'synthetic-secret', issuer }),
    prepareTokenRequest: () => new URLSearchParams({ grant_type: 'client_credentials' }),
  };
}
function options(issuer, onFetch) {
  return {
    metadata: { issuer, token_endpoint: issuer + '/token' },
    fetchFn: async (...args) => {
      onFetch(...args);
      return new Response(JSON.stringify({ access_token: 'synthetic-token', token_type: 'Bearer' }), { headers: { 'content-type': 'application/json' } });
    },
  };
}

test('OAuth never sends bound client credentials to a different issuer', async () => {
  for (const issuer of ['https://untrusted.example', 'https://trusted.example.attacker.invalid']) {
    let requests = 0;
    await assert.rejects(fetchToken(provider('https://trusted.example'), new URL(issuer), options(issuer, () => requests++)), /issuer/i);
    assert.equal(requests, 0);
  }
});

test('OAuth still issues a token request to the bound issuer', async () => {
  let requests = 0;
  const issuer = 'https://trusted.example';
  const tokens = await fetchToken(provider(issuer), new URL(issuer), options(issuer, () => requests++));
  assert.equal(tokens.access_token, 'synthetic-token');
  assert.equal(requests, 1);
});

test('the real BDK entry points and private credential helpers remain importable', async () => {
  for (const specifier of ['@cursor/bdk', '@cursor/bdk/tools', '@cursor/bdk/extensions/cursor-grokbot-agents']) {
    await import(specifier);
  }
  const credentials = await import('./node_modules/@cursor/bdk/dist/internal/cursor/credentials.js');
  const session = await import('./node_modules/@cursor/bdk/dist/internal/cursor/session-token.js');
  assert.equal(typeof credentials.resolveApiKeySync, 'function');
  assert.equal(typeof session.exchangeCursorApiKeyForSession, 'function');
});


test('BDK OAuth storage preserves issuer binding without real credentials', async () => {
  const { mkdtemp, rm } = await import('node:fs/promises');
  const { tmpdir } = await import('node:os');
  const { join } = await import('node:path');
  const { FileMcpOAuthStore } = await import('./node_modules/@cursor/bdk/dist/internal/mcp-oauth.js');
  const dir = await mkdtemp(join(tmpdir(), 'grok-oauth-test-'));
  try {
    const path = join(dir, 'synthetic-auth.json');
    const store = new FileMcpOAuthStore(path);
    const resource = 'https://resource.example/mcp';
    const issuer = 'https://trusted.example';
    await store.saveTokens('test', resource, { access_token: 'synthetic', token_type: 'Bearer', issuer });
    await store.saveClientInformation('test', resource, { client_id: 'synthetic', issuer });
    const loaded = new FileMcpOAuthStore(path);
    assert.equal((await loaded.loadTokens('test', resource)).issuer, issuer);
    assert.equal((await loaded.loadClientInformation('test', resource)).issuer, issuer);
    assert.equal(await loaded.loadTokens('test', 'https://other-resource.example/mcp'), undefined);
  } finally { await rm(dir, { recursive: true, force: true }); }
});


test('OAuth refresh tokens remain bound to their original issuer', async () => {
  for (const issuer of ['https://untrusted.example', 'https://trusted.example']) {
    let requests = 0;
    let redirect;
    let saved;
    const resource = 'https://resource.example/mcp';
    const metadata = { issuer, authorization_endpoint: issuer + '/authorize', token_endpoint: issuer + '/token', response_types_supported: ['code'], code_challenge_methods_supported: ['S256'] };
    const p = {
      redirectUrl: 'http://localhost/callback',
      clientMetadata: { redirect_uris: ['http://localhost/callback'] },
      // The target has its own public client ID, but must not receive another issuer's refresh token.
      clientInformation: () => ({ client_id: 'synthetic-client', issuer }),
      tokens: () => ({ access_token: 'synthetic', refresh_token: 'synthetic-refresh', token_type: 'Bearer', issuer: 'https://trusted.example' }),
      discoveryState: () => ({ authorizationServerUrl: issuer, resourceMetadata: { resource }, authorizationServerMetadata: metadata }),
      saveTokens: value => { saved = value; },
      saveCodeVerifier: () => {},
      redirectToAuthorization: url => { redirect = url; },
    };
    const result = await auth(p, { serverUrl: resource, fetchFn: options(issuer, () => requests++).fetchFn });
    if (issuer === 'https://trusted.example') {
      assert.equal(result, 'AUTHORIZED'); assert.equal(requests, 1); assert.equal(saved.issuer, issuer);
    } else {
      assert.equal(result, 'REDIRECT'); assert.equal(requests, 0); assert.equal(redirect.origin, issuer);
    }
  }
});
