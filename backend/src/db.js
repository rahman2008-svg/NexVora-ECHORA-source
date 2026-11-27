'use strict';
const Database = require('better-sqlite3');

function openDb(path) {
  const db = new Database(path);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.exec(`
    CREATE TABLE IF NOT EXISTS devices (device_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS subscriptions (
      device_id TEXT PRIMARY KEY REFERENCES devices(device_id),
      plan TEXT NOT NULL, expires_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS payments (
      id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(device_id),
      reference TEXT NOT NULL UNIQUE,            -- a transaction ID can be used once, globally
      idempotency_key TEXT NOT NULL, plan TEXT NOT NULL, amount_bdt INTEGER NOT NULL, method TEXT NOT NULL,
      status TEXT NOT NULL CHECK (status IN ('PENDING','VERIFIED','REJECTED','EXPIRED','REFUNDED')),
      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
      UNIQUE (device_id, idempotency_key));
    CREATE TABLE IF NOT EXISTS payment_history (
      id INTEGER PRIMARY KEY AUTOINCREMENT, payment_id TEXT NOT NULL, from_status TEXT, to_status TEXT NOT NULL,
      actor TEXT NOT NULL, at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS usage (device_id TEXT NOT NULL, month TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (device_id, month));
  `);
  return db;
}
module.exports = { openDb };
