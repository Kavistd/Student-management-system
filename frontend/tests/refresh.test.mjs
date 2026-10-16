import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import Module, { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import axios from 'axios';
const require = createRequire(import.meta.url);

function loadApi() {
  const filename = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src/lib/api.ts');
  const loaded = new Module(filename);
  loaded.filename = filename;
  loaded.paths = require.resolve.paths('axios');
  loaded._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText, filename);
  return loaded.exports.default;
}
function response(config, status, data = {}) {
  return {config, status, data, headers: {}, statusText: String(status)};
}
function fail(config, status, code) {
  throw new axios.AxiosError('Test HTTP error', undefined, config, null, response(config, status, {code}));
}
function browser(t) {
  const events = [];
  const previousDocument = globalThis.document, previousWindow = globalThis.window;
  globalThis.document = { cookie: 'XSRF-TOKEN=test-csrf' };
  globalThis.window = { location: { href: 'http://localhost:3000/' }, dispatchEvent: event => events.push(event.type) };
  t.after(() => {
    if (previousDocument === undefined) delete globalThis.document; else globalThis.document = previousDocument;
    if (previousWindow === undefined) delete globalThis.window; else globalThis.window = previousWindow;
  });
  return events;
}

test('explicit refresh race recovers after another tab installs cookies', async t => {
  const events = browser(t), api = loadApi();
  let protectedCalls = 0, refreshCalls = 0;
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/refresh') { refreshCalls++; return fail(config, 401, 'REFRESH_RACE'); }
    if (++protectedCalls < 3) return fail(config, 401, 'UNAUTHENTICATED');
    return response(config, 200, {ok:true});
  };
  assert.equal((await api.get('/api/auth/me')).status, 200);
  assert.equal(refreshCalls, 1);
  assert.equal(protectedCalls, 3);
  assert.deepEqual(events, []);
});

test('unresolved race is bounded and ends the session only after three protected retries', async t => {
  const events = browser(t), api = loadApi();
  let protectedCalls = 0, refreshCalls = 0;
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/refresh') { refreshCalls++; return fail(config, 401, 'REFRESH_RACE'); }
    protectedCalls++; return fail(config, 401, 'UNAUTHENTICATED');
  };
  await assert.rejects(api.get('/api/auth/me'));
  assert.equal(refreshCalls, 1);
  assert.equal(protectedCalls, 4);
  assert.deepEqual(events, ['studentms:session-expired']);
});

test('terminal refresh failure keeps the existing single fallback retry', async t => {
  const events = browser(t), api = loadApi();
  let protectedCalls = 0, refreshCalls = 0;
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/refresh') { refreshCalls++; return fail(config, 401, 'SESSION_EXPIRED'); }
    protectedCalls++; return fail(config, 401, 'UNAUTHENTICATED');
  };
  await assert.rejects(api.get('/api/auth/me'));
  assert.equal(refreshCalls, 1);
  assert.equal(protectedCalls, 2);
  assert.equal(events.length, 1);
});

test('concurrent protected requests share one refresh attempt within a tab', async t => {
  browser(t); const api = loadApi();
  let refreshCalls = 0, refreshed = false;
  let release;
  const gate = new Promise(resolve => {release = resolve;});
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/refresh') {
      refreshCalls++; await gate; refreshed = true; return response(config, 200);
    }
    if (!refreshed) return fail(config, 401, 'UNAUTHENTICATED');
    return response(config, 200);
  };
  const left = api.get('/api/auth/me'), right = api.get('/api/courses');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(refreshCalls, 1);
  release();
  await Promise.all([left, right]);
  assert.equal(refreshCalls, 1);
});

test('CSRF recovery still retries once with a fresh readable CSRF cookie', async t => {
  browser(t); const api = loadApi();
  let posts = 0, csrf = 0;
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/csrf') {
      csrf++; globalThis.document.cookie = 'XSRF-TOKEN=new-csrf'; return response(config, 204);
    }
    posts++;
    if (posts === 1) return fail(config, 403, 'CSRF_TOKEN_INVALID');
    assert.equal(config.headers.get('X-XSRF-TOKEN'), 'new-csrf');
    return response(config, 200);
  };
  await api.post('/api/courses', {courseName:'Test'});
  assert.equal(posts, 2);
  assert.equal(csrf, 1);
});

test('non-authentication errors during race recovery do not end the session', async t => {
  const events = browser(t), api = loadApi(); let calls = 0;
  api.defaults.adapter = async config => {
    if (config.url === '/api/auth/refresh') return fail(config, 401, 'REFRESH_RACE');
    return fail(config, ++calls === 1 ? 401 : 500, 'ERROR');
  };
  await assert.rejects(api.get('/api/auth/me'));
  assert.equal(calls, 2);
  assert.deepEqual(events, []);
});
