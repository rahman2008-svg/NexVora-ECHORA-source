'use strict';
const crypto = require('node:crypto');
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const jwt = require('jsonwebtoken');
const multer = require('multer');

const DAY = 86400000, TERM = 30 * DAY, PENDING_TTL = 72 * 3600000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const REF = /^[A-Za-z0-9]{6,32}$/;
const PLANS = ['PLUS', 'PRO'];

function safeEq(a, b) {
  const x = crypto.createHash('sha256').update(String(a)).digest(), y = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(x, y);
}

function createApp({ db, config, now = () => Date.now(), fetchImpl = globalThis.fetch }) {
  if (!config.jwtSecret || config.jwtSecret.length < 32) throw new Error('JWT_SECRET must be at least 32 characters');
  if (!config.adminToken || config.adminToken.length < 32) throw new Error('ADMIN_TOKEN must be at least 32 characters');
  const price = { PLUS: config.pricePlus, PRO: config.pricePro };
  const quota = { FREE: config.quotaFree, PLUS: config.quotaPlus, PRO: config.quotaPro };

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxy ?? 1);
  app.use(helmet());
  app.use(express.json({ limit: '10kb' }));
  const limiter = rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: true, legacyHeaders: false });
  const strict = rateLimit({ windowMs: 60_000, limit: 10, standardHeaders: true, legacyHeaders: false });
  app.use(limiter);

  const q = {
    device: db.prepare('SELECT * FROM devices WHERE device_id=?'),
    addDevice: db.prepare('INSERT OR IGNORE INTO devices(device_id,created_at) VALUES(?,?)'),
    sub: db.prepare('SELECT * FROM subscriptions WHERE device_id=?'),
    putSub: db.prepare('INSERT INTO subscriptions(device_id,plan,expires_at,updated_at) VALUES(?,?,?,?) ON CONFLICT(device_id) DO UPDATE SET plan=excluded.plan,expires_at=excluded.expires_at,updated_at=excluded.updated_at'),
    payByIdem: db.prepare('SELECT * FROM payments WHERE device_id=? AND idempotency_key=?'),
    payByRef: db.prepare('SELECT * FROM payments WHERE reference=?'),
    payById: db.prepare('SELECT * FROM payments WHERE id=?'),
    payList: db.prepare('SELECT * FROM payments WHERE device_id=? ORDER BY created_at DESC LIMIT 100'),
    payAdmin: db.prepare('SELECT * FROM payments WHERE (? IS NULL OR status=?) ORDER BY created_at DESC LIMIT 200'),
    addPay: db.prepare('INSERT INTO payments(id,device_id,reference,idempotency_key,plan,amount_bdt,method,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)'),
    setPay: db.prepare('UPDATE payments SET status=?,updated_at=? WHERE id=?'),
    hist: db.prepare('INSERT INTO payment_history(payment_id,from_status,to_status,actor,at) VALUES(?,?,?,?,?)'),
    expire: db.prepare("SELECT id FROM payments WHERE status='PENDING' AND created_at<?"),
    usage: db.prepare('SELECT count FROM usage WHERE device_id=? AND month=?'),
    bump: db.prepare('INSERT INTO usage(device_id,month,count) VALUES(?,?,1) ON CONFLICT(device_id,month) DO UPDATE SET count=count+1'),
  };

  const effective = (deviceId) => {
    const s = q.sub.get(deviceId);
    if (!s || s.expires_at <= now()) return { plan: 'FREE', expiresAtMs: 0, status: s ? 'expired' : 'none' };
    return { plan: s.plan, expiresAtMs: s.expires_at, status: 'active' };
  };
  const view = (p) => ({ id: p.id, reference: p.reference, plan: p.plan, amountBdt: p.amount_bdt, method: p.method, status: p.status, createdAtMs: p.created_at });
  const transition = db.transaction((p, to, actor) => {
    q.setPay.run(to, now(), p.id); q.hist.run(p.id, p.status, to, actor, now());
  });
  const sweepExpired = () => { for (const r of q.expire.all(now() - PENDING_TTL)) { const p = q.payById.get(r.id); transition(p, 'EXPIRED', 'system'); } };

  const sign = (deviceId) => jwt.sign({ sub: deviceId, scope: 'device' }, config.jwtSecret, { expiresIn: '90d', algorithm: 'HS256' });

  // Device auth: Bearer JWT whose sub must equal X-Device-Id. (First contact is handled by /v1/entitlement.)
  const auth = (req, res, next) => {
    const id = req.get('X-Device-Id');
    const h = req.get('Authorization') || '';
    if (!id || !UUID.test(id) || !h.startsWith('Bearer ')) return res.status(401).json({ error: 'unauthorized' });
    try {
      const c = jwt.verify(h.slice(7), config.jwtSecret, { algorithms: ['HS256'] });
      if (c.scope !== 'device' || c.sub !== id) throw new Error('mismatch');
    } catch { return res.status(401).json({ error: 'unauthorized' }); }
    req.deviceId = id; next();
  };

  app.get('/healthz', (_q, r) => r.json({ ok: true }));

  app.get('/v1/plans', (_q, r) => r.json({ plans: [{ plan: 'FREE', priceBdt: 0 }, { plan: 'PLUS', priceBdt: price.PLUS }, { plan: 'PRO', priceBdt: price.PRO }], instructions: config.paymentInstructions || null }));

  app.get('/v1/entitlement', strict, (req, res) => {
    const id = req.get('X-Device-Id');
    if (!id || !UUID.test(id)) return res.status(400).json({ error: 'invalid device id' });
    const h = req.get('Authorization') || '';
    if (h.startsWith('Bearer ')) {
      try { const c = jwt.verify(h.slice(7), config.jwtSecret, { algorithms: ['HS256'] }); if (c.sub !== id) throw 0; }
      catch { return res.status(401).json({ error: 'unauthorized' }); }
    } else if (q.device.get(id)) {
      return res.status(401).json({ error: 'device already registered; token required' });   // prevents token theft by id guessing
    } else q.addDevice.run(id, now());
    sweepExpired();
    res.json({ ...effective(id), token: sign(id) });
  });

  app.post('/v1/payments', strict, auth, (req, res) => {
    const { plan, method, reference } = req.body || {};
    const idem = req.get('Idempotency-Key');
    if (!PLANS.includes(plan)) return res.status(400).json({ error: 'invalid plan' });
    if (typeof method !== 'string' || !/^[a-z0-9_-]{1,20}$/.test(method)) return res.status(400).json({ error: 'invalid method' });
    if (typeof reference !== 'string' || !REF.test(reference)) return res.status(400).json({ error: 'invalid transaction reference' });
    if (!idem || idem.length > 80) return res.status(400).json({ error: 'Idempotency-Key required' });
    const ref = reference.toUpperCase();
    const again = q.payByIdem.get(req.deviceId, idem);
    if (again) return res.status(200).json(view(again));                      // idempotent replay
    if (q.payByRef.get(ref)) return res.status(409).json({ error: 'transaction reference already used' });   // duplicate payment protection
    q.addDevice.run(req.deviceId, now());
    const p = { id: crypto.randomUUID(), device: req.deviceId, ref, plan, amount: price[plan] };
    try {
      db.transaction(() => {
        q.addPay.run(p.id, p.device, p.ref, idem, p.plan, p.amount, method, 'PENDING', now(), now());
        q.hist.run(p.id, null, 'PENDING', 'device', now());
      })();
    } catch (e) { if (String(e.code).startsWith('SQLITE_CONSTRAINT')) return res.status(409).json({ error: 'duplicate' }); throw e; }
    res.status(201).json(view(q.payById.get(p.id)));
  });

  app.get('/v1/payments', auth, (req, res) => { sweepExpired(); res.json({ payments: q.payList.all(req.deviceId).map(view) }); });

  // ---- online transcription proxy (server holds the provider key) ----
  const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 25 * 1024 * 1024, files: 1 } });
  app.post('/v1/transcribe', strict, auth, upload.single('file'), async (req, res) => {
    if (!config.openaiKey) return res.status(503).json({ error: 'online transcription not configured' });
    if (!req.file) return res.status(400).json({ error: 'file required' });
    const month = new Date(now()).toISOString().slice(0, 7);
    const used = q.usage.get(req.deviceId, month)?.count ?? 0;
    const limit = quota[effective(req.deviceId).plan];
    if (used >= limit) return res.status(402).json({ error: 'monthly online transcription allowance used' });
    const form = new FormData();
    form.append('file', new Blob([req.file.buffer]), req.file.originalname || 'audio.m4a');
    form.append('model', config.transcribeModel);
    form.append('response_format', 'json');
    if (['bn', 'en'].includes(req.body?.language)) form.append('language', req.body.language);
    try {
      const r = await fetchImpl('https://api.openai.com/v1/audio/transcriptions', { method: 'POST', headers: { Authorization: `Bearer ${config.openaiKey}` }, body: form });
      const text = await r.text();
      if (!r.ok) return res.status(r.status === 429 ? 429 : 502).json({ error: 'provider error' });
      q.bump.run(req.deviceId, month);
      res.type('json').send(text);
    } catch { res.status(502).json({ error: 'provider unreachable' }); }
  });

  // ---- admin (separate secret; use over HTTPS only, ideally behind VPN/IP allow-list) ----
  const admin = (req, res, next) => {
    const h = req.get('Authorization') || '';
    if (!h.startsWith('Bearer ') || !safeEq(h.slice(7), config.adminToken)) return res.status(401).json({ error: 'unauthorized' });
    next();
  };
  const adminLimiter = rateLimit({ windowMs: 60_000, limit: 30 });
  app.get('/admin/payments', adminLimiter, admin, (req, res) => { sweepExpired(); const s = req.query.status ? String(req.query.status).toUpperCase() : null; res.json({ payments: q.payAdmin.all(s, s).map(p => ({ ...view(p), deviceId: p.device_id })) }); });

  app.post('/admin/payments/:id/:action', adminLimiter, admin, (req, res) => {
    const p = q.payById.get(req.params.id);
    if (!p) return res.status(404).json({ error: 'not found' });
    const act = req.params.action;
    const target = { verify: 'VERIFIED', reject: 'REJECTED', refund: 'REFUNDED' }[act];
    if (!target) return res.status(400).json({ error: 'unknown action' });
    if (p.status === target) return res.json(view(p));                          // idempotent
    const allowed = { verify: ['PENDING'], reject: ['PENDING'], refund: ['VERIFIED'] }[act];
    if (!allowed.includes(p.status)) return res.status(409).json({ error: `cannot ${act} a ${p.status} payment` });
    db.transaction(() => {
      transition(p, target, 'admin');
      if (target === 'VERIFIED') {
        const cur = q.sub.get(p.device_id);
        const base = cur && cur.expires_at > now() && cur.plan === p.plan ? cur.expires_at : now();
        q.putSub.run(p.device_id, p.plan, base + TERM, now());                 // plan change starts a fresh term
      } else if (target === 'REFUNDED') q.putSub.run(p.device_id, 'FREE', 0, now());
    })();
    res.json(view(q.payById.get(p.id)));
  });

  app.use((err, _req, res, _next) => { console.error(err.message); res.status(err.status || 500).json({ error: 'server error' }); });
  return app;
}
module.exports = { createApp };
