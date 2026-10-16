import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, cp, symlink, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { once } from 'node:events';

// This exercises discovery and validation only. No live bot send/check/read is called.
test('real BDK loads the bridge tools with patched dependencies', { timeout: 30000 }, async () => {
  const source = dirname(fileURLToPath(import.meta.url));
  const dir = await mkdtemp(join(tmpdir(), 'grok-bdk-smoke-'));
  let child;
  let output = '';
  try {
    const project = join(dir, 'project');
    await cp(join(source, 'bot'), join(project, 'bot'), { recursive: true });
    for (const file of ['config.mjs', 'native-activity.mjs']) await cp(join(source, file), join(project, file));
    await writeFile(join(project, 'package.json'), '{"type":"module"}');
    await writeFile(join(project, 'bots.local.json'), '[{"name":"Synthetic Bot","color":"#FFFFFF"}]');
    await symlink(join(source, 'node_modules'), join(project, 'node_modules'), 'dir');
    // Deny upstream fetches even if a future BDK version starts doing them at boot.
    const guard = join(dir, 'offline.mjs');
    await writeFile(guard, `const original = globalThis.fetch; globalThis.fetch = (input, ...args) => { const u = new URL(typeof input === 'string' || input instanceof URL ? input : input.url); if (u.hostname !== '127.0.0.1' && u.hostname !== 'localhost') throw new Error('Upstream network disabled in BDK smoke test'); return original(input, ...args); };`);
    const socket = createServer();
    socket.listen(0, '127.0.0.1'); await once(socket, 'listening');
    const port = socket.address().port;
    await new Promise(resolve => socket.close(resolve));
    const pkg = JSON.parse(await (await import('node:fs/promises')).readFile(join(source, 'node_modules/@cursor/bdk/package.json'), 'utf8'));
    const bin = typeof pkg.bin === 'string' ? pkg.bin : pkg.bin.bdk;
    const cli = join(source, 'node_modules/@cursor/bdk', bin);
    child = spawn(process.execPath, ['--import', guard, cli, 'serve', '--dir', project, '--mode', 'single', '--host', '127.0.0.1', '--port', String(port), '--no-schedules', '--no-playground', '--no-docs'], {
      cwd: project,
      env: { PATH: process.env.PATH, HOME: dir, XDG_CONFIG_HOME: join(dir, 'config'), CURSOR_API_KEY: 'synthetic-offline-key', NODE_ENV: 'test' },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.on('data', data => { output += data; });
    child.stderr.on('data', data => { output += data; });
    let tools;
    for (let attempt = 0; attempt < 100; attempt++) {
      if (child.exitCode !== null) throw new Error('BDK exited before discovery: ' + output);
      try {
        const response = await fetch(`http://127.0.0.1:${port}/v1/tools`, { signal: AbortSignal.timeout(500) });
        if (response.ok) { tools = await response.json(); break; }
      } catch {}
      await new Promise(resolve => setTimeout(resolve, 150));
    }
    assert(tools, 'BDK did not become ready: ' + output);
    const names = tools.tools.map(tool => tool.name);
    for (const name of ['grokbot__ask', 'grokbot__check', 'grokbot__interrupt', 'native_activity']) assert(names.includes(name), 'Missing tool ' + name);
    const response = await fetch(`http://127.0.0.1:${port}/v1/tools/native_activity`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ input: { agent: 'Unknown Bot' } }),
    });
    const result = await response.json();
    assert.match(JSON.stringify(result), /choose_configured_bot/);
  } finally {
    if (child && child.exitCode === null) {
      const exited = once(child, 'exit'); child.kill('SIGTERM');
      const timer = setTimeout(() => child.kill('SIGKILL'), 3000);
      await exited; clearTimeout(timer);
    }
    await rm(dir, { recursive: true, force: true });
  }
});
