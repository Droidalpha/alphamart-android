// Copies www-src to www, filling in the server address. Run before `cap sync`: APP_URL=https://shop.example.com node prepare.js
const fs = require('fs');
const url = (process.env.APP_URL || '').replace(/\/$/, '');
if (!/^https?:\/\/[^\s]+$/.test(url)) { console.error('Set APP_URL to your server address, e.g. https://shop.yourdomain.com'); process.exit(1); }
const path = require('./capacitor.config.ts.path.json').path;
fs.rmSync('www', { recursive: true, force: true }); fs.mkdirSync('www');
for (const f of fs.readdirSync('www-src')) fs.writeFileSync(`www/${f}`, fs.readFileSync(`www-src/${f}`, 'utf8').replace(/__APP_URL__/g, url).replace(/__APP_PATH__/g, path));
console.log('www ready for', url);
