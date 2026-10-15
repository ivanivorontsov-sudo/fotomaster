const http = require('http'); const fs = require('fs'); const path = require('path');
const puppeteer = require('/workspace/qwencheck/node_modules/puppeteer-core');
const mocks = require('./mocks');
const ROOT = '/workspace/fotomaster/app/src/main/assets/ai/';
const cfgs = JSON.parse(fs.readFileSync(ROOT + 'providers.json', 'utf8')).providers;
const runner = fs.readFileSync(ROOT + 'runner.js', 'utf8');
const png = fs.readFileSync(process.argv[2] || '/workspace/fotomaster/tools/runner-test/out.png');
const photo = fs.readFileSync('/workspace/fotomaster/tools/runner-test/photo.jpg');
let page$ = {};
const srv = http.createServer((req, res) => {
  const u = req.url;
  if (u.startsWith('/qwen-out.png')) { const full = !u.includes('x-oss'); res.writeHead(200, { 'content-type': 'image/png', 'x-full': full }); res.end(png); return; }
  if (u.startsWith('/get-shedevrum')) { res.writeHead(200, { 'content-type': 'image/png' }); res.end(png); return; }
  const name = u.slice(1).split('?')[0]; const q = new URLSearchParams(u.split('?')[1] || '');
  const opts = Object.fromEntries([...q.entries()].map(([k, v]) => [k, v === '1' ? true : v]));
  if (!mocks[name]) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); res.end(mocks[name](opts));
}).listen(8799);

async function scenario(b, name, providerId, url, rewritePatch) {
  const cfg = JSON.parse(JSON.stringify(cfgs.find(c => c.id === providerId)));
  const res = cfg.steps.find(s => s.do === 'result'); res.srcPattern = res.srcPattern + '|127\\.0\\.0\\.1'; res.timeout = 30000;
  if (providerId === 'alice') res.rewrite = [["(/get-shedevrum/[^/]+/[^/?]+)/[^/?]+", "$1/orig"]];
  cfg.steps.filter(s => s.do === 'wait').forEach(s => s.timeout = 10000);
  const p = await b.newPage(); const logs = [];
  p.on('dialog', d => { logs.push('ALERT ' + d.message()); d.dismiss(); });
  p.on('console', m => { if (m.text().startsWith('FMLOG')) logs.push(m.text().slice(6)); });
  await p.evaluateOnNewDocument((cfgJson, b64) => {
    window.__st = { armed: false, used: false, chunks: '', result: null, fail: null, warn: [], url: null };
    window.FMBridge = { config: () => cfgJson, prompt: () => 'улучши фото', mode: () => 'run', imageBase64: () => b64,
      armChooser: () => { __st.armed = true; __st.used = false; }, chooserUsed: () => __st.used,
      log: s => console.log('FMLOG ' + s), warn: s => { __st.warn.push(s); console.log('FMLOG WARN ' + s); },
      fail: (c, m) => { __st.fail = c + ': ' + m; }, resultUrl: u => { __st.url = u; },
      resultBegin: (u) => { __st.chunks = ''; __st.srcUrl = u; }, resultChunk: s => { __st.chunks += s; }, resultEnd: () => { __st.result = __st.chunks.length; },
      checkDone: () => {} };
  }, JSON.stringify(cfg), photo.toString('base64'));
  p.on('filechooser', async () => {});
  await p.goto('http://127.0.0.1:8799/' + url);
  const chooserTask = (async () => { try { const fc = await p.waitForFileChooser({ timeout: 20000 }); const armed = await p.evaluate(() => __st.armed);
      if (armed) { await fc.accept(['/workspace/fotomaster/tools/runner-test/photo.jpg']); await p.evaluate(() => { __st.used = true; __st.armed = false; }); logs.push('[test] chooser intercepted'); } else await fc.cancel(); } catch (e) {} })();
  await p.evaluate(runner);
  const t0 = Date.now(); let st;
  while (Date.now() - t0 < 90000) { st = await p.evaluate(() => ({ ...window.__st, chunks: undefined, sent: window.__sent, sentMode: window.__sentMode, sentFile: window.__sentFile })); if (st.result || st.fail || st.url) break; await new Promise(r => setTimeout(r, 500)); }
  await p.screenshot({ path: 'shot-' + name + '.png' });
  await chooserTask; await p.close();
  return { name, st, logs };
}

(async () => {
  const b = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', headless: 'new', args: ['--no-sandbox'] });
  const out = [];
  out.push(await scenario(b, 'qwen-image-edit', 'qwen', 'qwen'));
  out.push(await scenario(b, 'qwen-fallback-create-image', 'qwen', 'qwen?noEdit=1&allowNoMode=1'));
  out.push(await scenario(b, 'qwen-no-mode-button', 'qwen', 'qwen?noEdit=1&noGen=1&allowNoMode=1'));
  out.push(await scenario(b, 'qwen-text-only-answer', 'qwen', 'qwen?reply=text'));
  out.push(await scenario(b, 'alice', 'alice', 'alice'));
  await b.close(); srv.close();
  let ok = true;
  const expect = (cond, msg) => { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) ok = false; };
  const [q, qf, qn, qt, al] = out;
  expect(q.st.sentMode === 'image_edit' && q.st.sentFile === photo.length && q.st.sent === 'улучши фото' && q.st.result > 0 && q.st.srcUrl && !q.st.srcUrl.includes('x-oss'), 'Qwen: opens Image Edit, uploads photo via chooser, sends prompt, downloads full-size (x-oss-process stripped) url=' + q.st.srcUrl);
  expect(qf.st.sentMode === 't2i' && qf.st.warn.length === 1 && qf.st.result > 0, 'Qwen: falls back to Create Image with a visible warning');
  expect(qn.st.fail && qn.st.fail.startsWith('mode'), 'Qwen: no mode button -> clear failure: ' + qn.st.fail);
  expect(qt.st.fail && qt.st.fail.startsWith('text'), 'Qwen: text-only answer -> clear failure: ' + (qt.st.fail || '').slice(0, 80));
  expect(al.st.sentFile === photo.length && al.st.result > 0 && al.st.srcUrl.endsWith('/orig'), 'Alice: menu→Фото chooser upload, send, downloads /orig url=' + al.st.srcUrl);
  fs.writeFileSync('results.json', JSON.stringify(out, null, 1));
  out.forEach(o => { console.log('--- ' + o.name); o.logs.forEach(l => console.log('   ' + l)); });
  process.exit(ok ? 0 : 1);
})();
