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
  const p = await b.newPage(); await p.setViewport({ width: 412, height: 915, isMobile: true, hasTouch: true, deviceScaleFactor: 2 }); await p.setUserAgent('Mozilla/5.0 (Linux; Android 15; RMX5010) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36'); const logs = [];
  p.on('dialog', d => { logs.push('ALERT ' + d.message()); d.dismiss(); });
  p.on('console', m => { if (m.text().startsWith('FMLOG')) logs.push(m.text().slice(6)); });
  await p.evaluateOnNewDocument((cfgJson, b64) => {
    window.__st = { armed: false, used: false, chunks: '', result: null, fail: null, warn: [], url: null };
    window.FMBridge = { config: () => cfgJson, prompt: () => 'улучши фото', mode: () => 'run', imageBase64: () => b64,
      armChooser: () => { __st.armed = true; __st.used = false; }, chooserUsed: () => __st.used,
      log: s => console.log('FMLOG ' + s), warn: s => { __st.warn.push(s); console.log('FMLOG WARN ' + s); },
      fail: (c, m) => { __st.fail = c + ': ' + m; }, resultUrl: u => { __st.url = u; },
      resultBegin: (u) => { __st.chunks = ''; __st.srcUrl = u; }, resultChunk: s => { __st.chunks += s; }, resultEnd: () => { __st.result = __st.chunks.length; },
      checkDone: () => {}, needUser: m => { __st.needUser = (__st.needUser || 0) + 1; __st.userDone = true; }, userDone: () => !!__st.userDone, userHandled: () => { __st.userDone = false; },
      saveDump: j => { __st.dumps = (__st.dumps || 0) + 1; __st.lastDump = j.length; } };
  }, JSON.stringify(cfg), photo.toString('base64'));
  p.on('filechooser', async () => {});
  await p.goto('http://127.0.0.1:8799/' + url);
  let stopChooser = false;
  const chooserTask = (async () => { while (!stopChooser) { try { const fc = await p.waitForFileChooser({ timeout: 3000 }); const armed = await p.evaluate(() => __st.armed);
      if (armed) { await fc.accept(['/workspace/fotomaster/tools/runner-test/photo.jpg']); await p.evaluate(() => { __st.used = true; __st.armed = false; }); logs.push('[test] chooser intercepted'); } else await fc.cancel(); } catch (e) {} } })();
  await p.evaluate(runner);
  const t0 = Date.now(); let st;
  while (Date.now() - t0 < 150000) { st = await p.evaluate(() => ({ ...window.__st, chunks: undefined, sent: window.__sent, sentMode: window.__sentMode, sentFile: window.__sentFile })); if (st.result || st.fail || st.url) break; await new Promise(r => setTimeout(r, 500)); }
  await p.screenshot({ path: 'shot-' + name + '.png' });
  stopChooser = true; await chooserTask; await p.close();
  return { name, st, logs };
}

(async () => {
  const b = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', headless: 'new', args: ['--no-sandbox'] });
  const out = [];
  out.push(await scenario(b, 'qwen-ru-create-image', 'qwen', 'qwen'));
  out.push(await scenario(b, 'qwen-already-checked', 'qwen', 'qwen?preset=1'));
  out.push(await scenario(b, 'qwen-en-ui', 'qwen', 'qwen?lang=en'));
  out.push(await scenario(b, 'qwen-no-item-text-then-user', 'qwen', 'qwen?noImg=1&reply=textOnce'));
  out.push(await scenario(b, 'qwen-mode-ok-text-only', 'qwen', 'qwen?reply=text'));
  out.push(await scenario(b, 'alice', 'alice', 'alice'));
  out.push(await scenario(b, 'alice-first-click-ignored', 'alice', 'alice?ignore=1'));
  out.push(await scenario(b, 'alice-logged-out', 'alice', 'alice?login=1'));
  await b.close(); srv.close();
  let ok = true;
  const expect = (cond, msg) => { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) ok = false; };
  const [q, qc, qe, qu, qt, al, ai, alo] = out;
  expect(q.st.sentMode === 't2i' && q.st.sentFile === photo.length && q.st.sent === 'улучши фото' && q.st.result > 0 && !q.st.srcUrl.includes('x-oss'), 'Qwen RU: «+» → «Создать изображение», «+» → gallery icon upload, prompt, send, full-size result');
  expect(qc.st.sentMode === 't2i' && qc.st.result > 0, 'Qwen: already checked «Создать изображение» is not toggled off');
  expect(qe.st.sentMode === 't2i' && qe.st.result > 0, 'Qwen EN UI: «Create Image» found');
  expect(qu.st.needUser === 1 && qu.st.result > 0 && !qu.st.fail && qu.st.dumps >= 1, 'Qwen: no item -> non-fatal default chat, text answer -> asks user in site window, then continues and gets image');
  expect(qt.st.fail && qt.st.fail.startsWith('text') && !qt.st.needUser, 'Qwen: mode active but text-only answer -> clear failure');
  expect(al.st.sentFile === photo.length && al.st.result > 0 && al.st.srcUrl.endsWith('/orig'), 'Alice: Меню→Фото upload, send, /orig download');
  expect(ai.st.sent === 'улучши фото' && ai.st.result > 0 && !ai.st.needUser, 'Alice: send ignored first time -> verified by composer, retried, sent');
  expect(!alo.st.fail && alo.st.sent === 'улучши фото' && alo.st.result > 0 && alo.st.warn.length > 0, 'Alice: «Войти» on page -> only a hint, still sends and gets image');
  fs.writeFileSync('results.json', JSON.stringify(out, null, 1));
  out.forEach(o => { console.log('--- ' + o.name); o.logs.forEach(l => console.log('   ' + l)); });
  process.exit(ok ? 0 : 1);
})();
