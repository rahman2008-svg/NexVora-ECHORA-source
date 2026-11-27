'use strict';
const fs = require('node:fs'), path = require('node:path');
const { openDb } = require('./db');
const { createApp } = require('./app');
const num = (v, d) => (v === undefined || v === '' ? d : Number(v));
const dbPath = process.env.DB_PATH || './data/echora.db';
fs.mkdirSync(path.dirname(dbPath), { recursive: true });
const app = createApp({
  db: openDb(dbPath),
  config: {
    jwtSecret: process.env.JWT_SECRET, adminToken: process.env.ADMIN_TOKEN,
    openaiKey: process.env.OPENAI_API_KEY, transcribeModel: process.env.OPENAI_TRANSCRIBE_MODEL || 'gpt-4o-mini-transcribe',
    pricePlus: num(process.env.PRICE_PLUS, 99), pricePro: num(process.env.PRICE_PRO, 199),
    quotaFree: num(process.env.QUOTA_FREE, 5), quotaPlus: num(process.env.QUOTA_PLUS, 100), quotaPro: num(process.env.QUOTA_PRO, 500),
    paymentInstructions: process.env.PAYMENT_INSTRUCTIONS,
  },
});
app.listen(num(process.env.PORT, 8080), () => console.log('ECHORA backend listening'));
