import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import Module, { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Compile in memory using the installed compiler; no test dependency or generated files.
const filename = path.resolve(__dirname, '../src/lib/apiError.ts');
const loaded = new Module(filename);
loaded.filename = filename;
loaded.paths = require.resolve.paths('axios');
loaded._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText, filename);
const { toApiError } = loaded.exports;
const error = (status, data) => toApiError({ isAxiosError: true, response: { status, data } });

test('field-error objects are parsed without returning rejected values', () => {
  assert.equal(error(400, { code: 'VALIDATION_ERROR', errors: { password: 'At most 72 UTF-8 bytes' } }).message, 'At most 72 UTF-8 bytes');
});
for (const [status, kind] of [[400,'validation'],[401,'unauthorized'],[403,'forbidden'],[404,'notFound'],[409,'conflict'],[429,'rateLimited'],[500,'server']]) {
  test(`HTTP ${status} retains its classification and safe fallback`, () => {
    for (const data of [null, {}, { message: 'Useful message' }, { errors: { email: 'Invalid email' } }]) {
      const result = error(status, data);
      assert.equal(result.kind, kind);
      assert.equal(result.status, status);
      assert.ok(result.message.length);
      if (status === 500) assert.ok(!result.message.includes('Useful message'));
    }
  });
}
test('duplicate fallback, legacy bodies and generic authentication remain supported', () => {
  assert.equal(error(409, { code: 'DUPLICATE_RECORD' }).message, 'A record with these details already exists.');
  assert.equal(error(404, 'Missing record').message, 'Missing record');
  assert.equal(error(400, { errors: [{ defaultMessage: 'Invalid value' }] }).message, 'Invalid value');
  assert.equal(error(401, { message: 'private detail' }).message, 'Your session has ended. Please sign in again to continue.');
  assert.equal(error(422, {}).kind, 'unknown');
});
