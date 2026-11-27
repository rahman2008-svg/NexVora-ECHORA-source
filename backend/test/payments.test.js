'use strict';
// Requires `npm install` (better-sqlite3 etc.). Run: npm test
const test = require('node:test'), assert = require('node:assert/strict');
const http = require('node:http'), crypto = require('node:crypto');
const { openDb } = require('../src/db'), { createApp } = require('../src/app');

const cfg = { jwtSecret: 'j'.repeat(32), adminToken: 'a'.repeat(32), pricePlus: 99, pricePro: 199, quotaFree: 1, quotaPlus: 2, quotaPro: 3, transcribeModel: 'm', trustProxy: false };
let clock = 1_700_000_000_000;
async function boot() {
  const app = createApp({ db: openDb(':memory:'), config: cfg, now: () => clock });
  const srv = http.createServer(app); await new Promise(r => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const call = async (m, p, { body, h = {} } = {}) => { const r = await fetch(base + p, { method: m, headers: { 'content-type': 'application/json', ...h }, body: body && JSON.stringify(body) }); return { s: r.status, j: await r.json().catch(() => ({})) }; };
  return { srv, call };
}
async function device(call) { const id = crypto.randomUUID(); const e = await call('GET', '/v1/entitlement', { h: { 'x-device-id': id } }); return { id, h: { 'x-device-id': id, authorization: `Bearer ${e.j.token}` }, e }; }
const admin = { authorization: 'Bearer ' + cfg.adminToken };

test('new device is FREE; plan is never client-controlled', async () => {
  const { srv, call } = await boot(); const d = await device(call);
  assert.equal(d.e.j.plan, 'FREE');
  assert.equal((await call('GET', '/v1/entitlement', { h: { 'x-device-id': d.id } })).s, 401);  // can't re-mint a token by id
  srv.close();
});
test('pending -> rejected leaves FREE; pending -> verified grants PLUS 30 days', async () => {
  const { srv, call } = await boot(); const d = await device(call);
  const p = await call('POST', '/v1/payments', { h: { ...d.h, 'idempotency-key': 'k1' }, body: { plan: 'PLUS', method: 'manual', reference: 'abc12345' } });
  assert.equal(p.s, 201); assert.equal(p.j.status, 'PENDING');
  assert.equal((await call('GET', '/v1/entitlement', { h: d.h })).j.plan, 'FREE');             // pending grants nothing
  const rej = await call('POST', `/admin/payments/${p.j.id}/reject`, { h: admin }); assert.equal(rej.j.status, 'REJECTED');
  assert.equal((await call('GET', '/v1/entitlement', { h: d.h })).j.plan, 'FREE');
  const p2 = await call('POST', '/v1/payments', { h: { ...d.h, 'idempotency-key': 'k2' }, body: { plan: 'PLUS', method: 'manual', reference: 'xyz98765' } });
  await call('POST', `/admin/payments/${p2.j.id}/verify`, { h: admin });
  await call('POST', `/admin/payments/${p2.j.id}/verify`, { h: admin });                         // idempotent
  const e = await call('GET', '/v1/entitlement', { h: d.h }); assert.equal(e.j.plan, 'PLUS'); assert.equal(e.j.expiresAtMs, clock + 30 * 86400000);
  clock += 31 * 86400000;
  assert.equal((await call('GET', '/v1/entitlement', { h: d.h })).j.plan, 'FREE');               // expiry
  srv.close();
});
test('duplicate reference and idempotent replay', async () => {
  const { srv, call } = await boot(); const a = await device(call), b = await device(call);
  const body = { plan: 'PRO', method: 'manual', reference: 'ref000111' };
  const first = await call('POST', '/v1/payments', { h: { ...a.h, 'idempotency-key': 'z' }, body });
  const replay = await call('POST', '/v1/payments', { h: { ...a.h, 'idempotency-key': 'z' }, body });
  assert.equal(replay.s, 200); assert.equal(replay.j.id, first.j.id);
  assert.equal((await call('POST', '/v1/payments', { h: { ...b.h, 'idempotency-key': 'q' }, body })).s, 409);
  srv.close();
});
test('admin endpoints require the admin token; bad input rejected', async () => {
  const { srv, call } = await boot(); const d = await device(call);
  assert.equal((await call('GET', '/admin/payments', { h: d.h })).s, 401);
  assert.equal((await call('POST', '/v1/payments', { h: { ...d.h, 'idempotency-key': 'k' }, body: { plan: 'FREE', method: 'manual', reference: 'abc12345' } })).s, 400);
  assert.equal((await call('POST', '/v1/payments', { h: { ...d.h, 'idempotency-key': 'k' }, body: { plan: 'PLUS', method: 'manual', reference: "x'; DROP" } })).s, 400);
  srv.close();
});
