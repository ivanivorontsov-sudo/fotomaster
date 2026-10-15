(function () {
  if (window.__FM && window.__FM.running) return;
  var B = window.FMBridge;
  var FM = window.__FM = { running: true };
  var cfg = JSON.parse(B.config());
  var prompt = B.prompt();
  var t0 = Date.now();
  function log(m) { try { B.log(((Date.now() - t0) / 1000).toFixed(1) + 's ' + m); } catch (e) {} }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function vis(e) { if (!e) return false; var r = e.getBoundingClientRect(); var s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; }
  function txt(e) { return ((e.innerText || e.textContent || e.getAttribute('aria-label') || e.getAttribute('title') || '') + '').trim(); }
  function q(sels, re) {
    var rx = re ? new RegExp(re, 'i') : null;
    for (var i = 0; i < (sels || []).length; i++) {
      var list; try { list = document.querySelectorAll(sels[i]); } catch (e) { continue; }
      for (var j = 0; j < list.length; j++) { var e = list[j]; if (!vis(e) && !(e.tagName === 'INPUT' && e.type === 'file')) continue; if (rx && !rx.test(txt(e))) continue; return e; }
    }
    return null;
  }
  async function waitFor(fn, timeout, every) { var end = Date.now() + timeout; while (Date.now() < end) { var v = fn(); if (v) return v; await sleep(every || 400); } return null; }
  function click(e) {
    var r = e.getBoundingClientRect(); var o = { bubbles: true, cancelable: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
    try { e.dispatchEvent(new PointerEvent('pointerdown', o)); e.dispatchEvent(new MouseEvent('mousedown', o)); e.dispatchEvent(new PointerEvent('pointerup', o)); e.dispatchEvent(new MouseEvent('mouseup', o)); } catch (x) {}
    e.click();
  }
  function fail(code, msg) { FM.running = false; log('ОШИБКА ' + code + ': ' + msg); B.fail(code, msg); throw new Error('__fm_stop'); }
  function setText(el, value) {
    el.focus();
    if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
      var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      try { el.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: value })); } catch (x) {}
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
      try { el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value })); } catch (x) { el.dispatchEvent(new Event('input', { bubbles: true })); } el.dispatchEvent(new Event('change', { bubbles: true }));
    } else {
      document.execCommand('selectAll', false, null); document.execCommand('insertText', false, value);
      if (txt(el) !== value) { el.textContent = value; el.dispatchEvent(new InputEvent('input', { bubbles: true, data: value })); }
    }
  }
  function b64ToFile(b64, name, mime) { var bin = atob(b64); var u = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return new File([u], name, { type: mime }); }
  function allImgSrc() { return Array.prototype.map.call(document.querySelectorAll('img'), function (i) { return i.currentSrc || i.src; }); }
  var before = {};

  async function stepWait(s) { var e = await waitFor(function () { return q(s.selectors); }, s.timeout || 60000); if (!e) fail('page', 'Страница не загрузилась или изменилась вёрстка (нет поля ввода)'); log('поле ввода найдено'); }
  async function stepDismiss(s) { var e = q(s.selectors, s.text); if (e) { log('закрываю окно: ' + txt(e)); click(e); await sleep(800); if (s.resend) { FM.needResend = true; } } }
  async function openMenuItem(open, item, re) {
    for (var i = 0; i < open.length; i++) {
      var list; try { list = document.querySelectorAll(open[i]); } catch (e) { continue; }
      for (var j = 0; j < list.length; j++) {
        var o = list[j]; if (!vis(o)) continue;
        click(o); await sleep(900);
        var it = q(item, re);
        if (it) return it;
        try { document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); } catch (e) {}
        await sleep(300);
      }
    }
    return null;
  }
  function textOf(e) { return [txt(e), e.getAttribute('aria-label') || '', e.getAttribute('title') || '', e.getAttribute('data-tooltip') || ''].join(' ').replace(/\s+/g, ' ').trim(); }
  function findByText(sels, re) {
    var rx = new RegExp(re, 'i'); var best = null;
    for (var i = 0; i < sels.length; i++) {
      var list; try { list = document.querySelectorAll(sels[i]); } catch (e) { continue; }
      for (var j = 0; j < list.length; j++) { var e = list[j]; if (!vis(e)) continue; var t = textOf(e); if (t.length > 60 || !rx.test(t)) continue;
        if (!best || t.length < textOf(best).length) best = e; }
      if (best) return best;
    }
    return null;
  }
  function modeActive(s) {
    if (q(s.active)) return true;
    if (!s.activeText) return false;
    var rx = new RegExp(s.activeText, 'i');
    var chips = document.querySelectorAll('[class*="tag"],[class*="chip"],[class*="selected"],[class*="active"],[aria-pressed="true"],[aria-selected="true"],[aria-checked="true"]');
    for (var i = 0; i < chips.length; i++) { if (vis(chips[i]) && rx.test(textOf(chips[i])) && textOf(chips[i]).length < 60) return true; }
    var ta = q(['textarea', '[contenteditable=true]']); if (ta && rx.test(ta.getAttribute('placeholder') || '')) return true;
    var bar = inputBar(); for (var k = 0; k < 2 && bar && bar.parentElement; k++) bar = bar.parentElement;
    if (bar) { var els = bar.querySelectorAll('button,span,div'); for (var j = 0; j < els.length; j++) { var t = textOf(els[j]); if (t && t.length < 30 && vis(els[j]) && rx.test(t)) return true; } }
    return false;
  }
  function inputBar() { var ta = q(['textarea', '[contenteditable=true]']); var box = ta; for (var k = 0; k < 4 && box && box.parentElement; k++) { box = box.parentElement; if (box.querySelectorAll('button,[role=button]').length >= 2) break; } return box; }
  function openers(s) {
    var out = []; var seen = new Set();
    function add(e) { if (e && vis(e) && !seen.has(e)) { seen.add(e); out.push(e); } }
    (s.open || []).forEach(function (sel) { try { document.querySelectorAll(sel).forEach(add); } catch (e) {} });
    var box = inputBar();
    if (box) { var fb = Array.prototype.filter.call(box.querySelectorAll('button,[role=button]'), vis); if (fb.length) add(fb[0]); }
    for (var k2 = 0; k2 < 2 && box && box.parentElement; k2++) box = box.parentElement;
    if (box) box.querySelectorAll('button,[role=button]').forEach(function (b) { var t = textOf(b); if (/^\+$|^$|more|tool|plus|feature|mode|add|ещё|инструмент|функц/i.test(t + ' ' + (b.className || ''))) add(b); });
    return out.filter(function (e) { return !/send|stop|voice|mic|отправ/i.test(textOf(e) + ' ' + (e.className || '')); }).slice(0, 12);
  }
  function isChecked(e) {
    for (var n = e, k = 0; n && k < 4; n = n.parentElement, k++) {
      if (['aria-checked', 'aria-selected', 'aria-pressed', 'data-checked', 'data-selected'].some(function (a) { return n.getAttribute && n.getAttribute(a) === 'true'; })) return true;
      if (/(^|[\s_-])(active|checked|selected|is-active|is-checked)([\s_-]|$)/i.test(String(n.className || ''))) return true;
      if (n.querySelector && n.querySelector('[class*="check" i]:not([class*="checkbox"]),[data-icon*="check"],[aria-label*="check" i]')) return true;
      if (/[✓✔]/.test(n.textContent || '') && (n.textContent || '').length < 60) return true;
      if (k >= 1 && n.querySelectorAll && n.querySelectorAll('li,[role=menuitem],[role=option]').length > 1) break;
    }
    return false;
  }
  async function closeSheet() {
    var c = q(['[class*="sheet"] [aria-label*="close" i]', '[class*="drawer"] [aria-label*="close" i]', '[aria-label*="Закрыть"]', '[aria-label*="close" i]', '.ant-drawer-close', '[class*="close"]']);
    if (c) click(c); else { try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); } catch (e) {} }
    await sleep(500);
  }
  async function pickItem(s, it) {
    if (isChecked(it)) { log('«' + textOf(it) + '» уже отмечен, не переключаю'); if (s.closeAfter) await closeSheet(); return; }
    log('включаю режим: ' + textOf(it)); click(it); await sleep(1100);
    if (s.closeAfter && findByText(s.item, s.itemText)) await closeSheet();
  }
  async function stepMode(s) {
    if (s.urlActivates && location.href.indexOf(s.urlActivates) >= 0) await sleep(1500);
    if (modeActive(s)) { log('режим «' + s.name + '» уже включён'); FM.modeOk = true; return; }
    var direct = findByText(s.item, s.itemText);
    if (direct) { await pickItem(s, direct); FM.modeOk = true; return; }
    var ops = openers(s); log('ищу режим в меню: кнопок-кандидатов ' + ops.length);
    for (var i = 0; i < ops.length; i++) {
      var o = ops[i]; if (!document.contains(o) || !vis(o)) continue;
      click(o); await sleep(1000);
      var it = findByText(s.item, s.itemText);
      if (it) { log('меню: ' + (textOf(o) || String(o.className) || '?').slice(0, 30)); await pickItem(s, it); FM.modeOk = true; return; }
      try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); document.body.click(); } catch (e) {}
      await sleep(400);
    }
    B.saveDump(dumpPage('mode-not-found'));
    if (s.optional) { FM.modeOk = false; FM.modeStep = s; log('режим «' + s.name + '» не найден, пробую обычный чат: фото + запрос'); B.warn('Кнопка «' + s.name + '» не найдена — отправляю фото с запросом в обычный чат'); return; }
    fail('mode', 'Не нашёл кнопку режима «' + s.name + '»');
  }
  function dumpPage(reason) {
    var items = [];
    document.querySelectorAll('button,[role=button],[role=menuitem],[role=option],[role=tab],a,input,textarea,[contenteditable=true],[aria-haspopup],li').forEach(function (e) {
      if (items.length > 400) return;
      items.push({ tag: e.tagName, vis: vis(e), text: txt(e).slice(0, 80), aria: e.getAttribute('aria-label') || '', title: e.getAttribute('title') || '', cls: String(e.className || '').slice(0, 120), id: e.id || '', type: e.getAttribute('type') || '', placeholder: e.getAttribute('placeholder') || '', testid: e.getAttribute('data-testid') || '' });
    });
    return JSON.stringify({ reason: reason, url: location.href, title: document.title, time: new Date().toISOString(), ua: navigator.userAgent, items: items }, null, 1);
  }
  FM.dumpPage = dumpPage;
  async function stepUpload(s) {
    var input = q(s.input);
    if (input) {
      log('загружаю фото через поле файла');
      try { var dt = new DataTransfer(); dt.items.add(b64ToFile(B.imageBase64(), 'photo.jpg', 'image/jpeg')); input.files = dt.files; input.dispatchEvent(new Event('change', { bubbles: true })); input.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) { log('DataTransfer: ' + e.message); }
    } else {
      B.armChooser();
      var opened = false;
      var o = q(s.open) || openers(s)[0];
      if (o) { log('открываю загрузку: ' + (textOf(o) || String(o.className).slice(0, 30))); click(o); await sleep(1000);
        if (!B.chooserUsed()) {
          var g = gridItems(s); var idx = s.gridIndex || [];
          for (var gi = 0; gi < idx.length && !B.chooserUsed(); gi++) { if (g[idx[gi]]) { log('иконка загрузки №' + (idx[gi] + 1)); click(g[idx[gi]]); await waitFor(function () { return B.chooserUsed(); }, 2500); } }
          if (!B.chooserUsed()) { var it = q(s.item, s.itemText); if (it) { log('пункт меню: ' + textOf(it)); click(it); } }
        }
        opened = true; }
      await waitFor(function () { return B.chooserUsed() || q(s.input); }, 4000);
      if (!B.chooserUsed()) {
        input = q(s.input);
        if (input) { var dt2 = new DataTransfer(); dt2.items.add(b64ToFile(B.imageBase64(), 'photo.jpg', 'image/jpeg')); input.files = dt2.files; input.dispatchEvent(new Event('change', { bubbles: true })); }
        else {
          log('кнопки загрузки нет, пробую вставку из буфера');
          var ta = q(['textarea', '[contenteditable=true]']);
          if (ta) { var dt3 = new DataTransfer(); dt3.items.add(b64ToFile(B.imageBase64(), 'photo.jpg', 'image/jpeg'));
            ta.focus(); ta.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt3, bubbles: true, cancelable: true }));
            ta.dispatchEvent(new DragEvent('drop', { dataTransfer: dt3, bubbles: true, cancelable: true })); }
          else if (!opened) fail('upload', 'Не нашёл, куда загрузить фото');
        }
      } else log('фото передано через окно выбора файла');
    }
    var ok = await waitFor(function () { return q(s.doneWhen); }, 15000, 400);
    log(ok ? 'фото прикреплено' : 'не вижу превью вложения, продолжаю');
    await sleep(1500);
  }
  function gridItems(s) {
    if (!s.gridAfterText) return [];
    var rx = new RegExp(s.gridAfterText, 'i'); var all = document.querySelectorAll('div,span,p,h3,h4,label');
    for (var i = 0; i < all.length; i++) {
      var e = all[i]; if (!vis(e) || e.children.length > 0 || !rx.test(txt(e))) continue;
      for (var n = e; n && n !== document.body; n = n.parentElement) {
        var sib = n.nextElementSibling; while (sib && !vis(sib)) sib = sib.nextElementSibling;
        if (sib) { var items = Array.prototype.filter.call(sib.querySelectorAll('button,[role=button],div[class*="item"],div[class*="upload"],label'), vis).filter(function (x) { return !x.querySelector('button,[role=button]'); });
          if (items.length < 2) items = Array.prototype.filter.call(sib.children, vis);
          if (items.length >= 2) return items; }
      }
    }
    return [];
  }
  async function stepType(s) { var e = q(s.selectors); if (!e) fail('input', 'Нет поля ввода'); setText(e, prompt); await sleep(500); log('запрос введён'); }
  function composerText() { var ta = q(['textarea', '[contenteditable=true]']); return ta ? (ta.value !== undefined && ta.tagName !== 'DIV' ? ta.value : txt(ta)) : ''; }
  function promptBubbles() {
    var key = (prompt || '').trim().slice(0, 40); if (!key) return 0; var n = 0;
    var ta = q(['textarea', '[contenteditable=true]']);
    document.querySelectorAll('div,p,span').forEach(function (e) { if (e.children.length === 0 && vis(e) && (!ta || !ta.contains(e)) && txt(e).indexOf(key) >= 0) n++; });
    return n;
  }
  function sendButton(s) {
    var b = q(s.selectors); if (b && !b.disabled) return b;
    if (s.composer) { var bar = inputBar(); if (bar) { var bs = Array.prototype.filter.call(bar.querySelectorAll('button,[role=button]'), function (x) { return vis(x) && !x.disabled; }); if (bs.length) return bs[bs.length - 1]; } }
    return null;
  }
  function pressEnter() { var ta = q(['textarea', '[contenteditable=true]']); if (!ta) return; ta.focus(); var o = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true };
    ['keydown', 'keypress', 'keyup'].forEach(function (t) { ta.dispatchEvent(new KeyboardEvent(t, o)); }); }
  async function sentOk(b0) { return await waitFor(function () { return composerText().trim() === '' || promptBubbles() > b0 ? true : null; }, 6000); }
  async function stepSend(s) {
    before = {}; allImgSrc().forEach(function (x) { before[x] = 1; });
    FM.sendSel = s.selectors;
    if (composerText().trim() === '') { var ti = cfg.steps.find(function (x) { return x.do === 'type'; }); if (ti) await stepType(ti); }
    var b0 = promptBubbles();
    for (var attempt = 0; attempt < 2; attempt++) {
      var e = await waitFor(function () { var b = sendButton(s); return b && !/disabled/.test(String(b.className)) ? b : null; }, attempt ? 5000 : 20000);
      if (e) { click(e); log('нажата отправка' + (attempt ? ' (повтор)' : '')); }
      if (await sentOk(b0)) { log('отправлено'); await sleep(1000); return; }
      if (s.enterFallback) { pressEnter(); log('пробую Enter'); if (await sentOk(b0)) { log('отправлено через Enter'); await sleep(1000); return; } }
    }
    log('запрос остался в поле ввода — не отправлено');
    B.saveDump(dumpPage('send-failed'));
    B.needUser('Сайт не принял отправку. Нажмите кнопку отправки в окне сайта, затем «Продолжить».');
    var end = Date.now() + 300000;
    while (Date.now() < end) { await sleep(700); if (B.userDone() || (await sentOk(b0))) break; }
    B.userHandled();
    if (composerText().trim() !== '' && promptBubbles() <= b0) fail('send', 'Запрос не отправлен: сайт не принял нажатие кнопки отправки');
    log('отправлено (вручную)');
  }
  function largest(img) {
    var best = img.currentSrc || img.src;
    var ss = img.getAttribute('srcset'); if (ss) { var mw = 0; ss.split(',').forEach(function (p) { var a = p.trim().split(/\s+/); var w = parseInt(a[1]) || 0; if (w > mw) { mw = w; best = a[0]; } }); }
    ['data-original', 'data-src', 'data-full', 'data-origin'].forEach(function (k) { if (img.getAttribute(k)) best = img.getAttribute(k); });
    var a = img.closest('a[href]'); if (a && /\.(png|jpe?g|webp)(\?|$)|download/i.test(a.href)) best = a.href;
    return new URL(best, location.href).href;
  }
  async function stepResult(s) {
    if (FM.needResend && FM.sendSel) { var b = q(FM.sendSel); if (b) { click(b); log('повторная отправка после окна'); } }
    var rx = new RegExp(s.srcPattern, 'i');
    var end = Date.now() + (s.timeout || 300000); var lastText = ''; var stable = 0;
    while (Date.now() < end) {
      await sleep(1500);
      var busy = !!q(s.busy);
      var cands = [];
      for (var i = 0; i < s.containers.length; i++) { var cs; try { cs = document.querySelectorAll(s.containers[i]); } catch (e) { continue; } if (cs.length) { var last = cs[cs.length - 1];
        Array.prototype.forEach.call(last.querySelectorAll('img'), function (im) { var src = im.currentSrc || im.src; if (!before[src] && rx.test(src) && im.naturalWidth >= 256 && im.complete) cands.push(im); }); if (cands.length) break; } }
      if (!cands.length) Array.prototype.forEach.call(document.querySelectorAll('img'), function (im) { var src = im.currentSrc || im.src; if (!before[src] && rx.test(src) && im.naturalWidth >= 384 && im.complete) cands.push(im); });
      if (cands.length && !busy) {
        var im = cands[cands.length - 1]; var url = largest(im);
        var urls = [url]; (s.rewrite || []).forEach(function (rw) { var u2 = url.replace(new RegExp(rw[0]), rw[1]); if (u2 !== url) urls.unshift(u2); });
        log('картинка найдена ' + im.naturalWidth + 'x' + im.naturalHeight);
        for (var k = 0; k < urls.length; k++) { if (await deliver(urls[k])) { FM.running = false; return 'ok'; } }
        B.resultUrl(urls[urls.length - 1]); FM.running = false; return 'ok';
      }
      var lastMsg = null; for (var c = 0; c < s.containers.length && !lastMsg; c++) { try { var all = document.querySelectorAll(s.containers[c]); if (all.length) lastMsg = all[all.length - 1]; } catch (e) {} }
      var t = lastMsg ? txt(lastMsg) : '';
      if (t && t === lastText && !busy) stable++; else stable = 0; lastText = t;
      if (stable >= 10 && t.length > 0) { FM.textAnswer = t; return 'text'; }
      if ((Date.now() - t0) % 15000 < 1600) log(busy ? 'генерация…' : 'жду ответ…');
    }
    return 'timeout';
  }
  async function deliver(url) {
    try {
      var r = await fetch(url, { credentials: 'include' }); if (!r.ok) { log('скачивание ' + r.status); return false; }
      var blob = await r.blob(); if (!/^image\//.test(blob.type) && blob.size < 10000) return false;
      var b64 = await new Promise(function (res, rej) { var fr = new FileReader(); fr.onload = function () { res(String(fr.result).split(',')[1]); }; fr.onerror = rej; fr.readAsDataURL(blob); });
      var CH = 512 * 1024; B.resultBegin(url, b64.length);
      for (var i = 0; i < b64.length; i += CH) B.resultChunk(b64.substring(i, i + CH));
      B.resultEnd(); log('картинка скачана, ' + Math.round(blob.size / 1024) + ' КБ'); return true;
    } catch (e) { log('fetch не удался (' + e.message + '), скачаю через приложение'); return false; }
  }
  FM.loggedIn = function () { var l = cfg.loggedIn || {}; return !q(l.absent || []) && !(l.absentText && q(['button', 'a'], l.absentText)); };
  async function runSteps(from) {
    for (var i = from; i < cfg.steps.length; i++) {
      var s = cfg.steps[i]; log('шаг ' + (i + 1) + ': ' + s.do);
      if (s.do === 'wait') await stepWait(s); else if (s.do === 'dismiss') await stepDismiss(s); else if (s.do === 'mode') await stepMode(s);
      else if (s.do === 'upload') await stepUpload(s); else if (s.do === 'type') await stepType(s); else if (s.do === 'send') await stepSend(s);
      else if (s.do === 'result') return await stepResult(s);
    }
    return 'ok';
  }
  async function askUser(reason) {
    B.saveDump(dumpPage(reason));
    var ms = FM.modeStep;
    B.needUser('Нажмите в окне сайта кнопку режима «' + (ms ? ms.name : 'Image Edit') + '» (обычно под «+» у поля ввода), затем «Продолжить».');
    var end = Date.now() + 300000;
    while (Date.now() < end) { await sleep(700); if (B.userDone()) break; if (ms && modeActive(ms)) { log('режим включён пользователем'); await sleep(800); break; } }
    B.userHandled();
    if (Date.now() >= end) fail('user', 'Не дождался выбора режима');
  }
  (async function () {
    try {
      if (B.mode() === 'check') { await sleep(4000); B.checkDone(FM.loggedIn(), document.title + ' | ' + location.href); FM.running = false; return; }
      if (B.mode() === 'dump') { await sleep(3000); B.saveDump(dumpPage('manual')); FM.running = false; return; }
      log('старт ' + cfg.id + ' ' + location.href);
      await sleep(1500);
      if (!FM.loggedIn()) { if (cfg.requireLogin) fail('login', 'Вход в аккаунт не выполнен: на странице кнопка «Войти». Нажмите «Войти» в приложении.'); else B.warn('Вход не выполнен — продолжаю без входа (лимиты могут быть меньше).'); }
      var res = await runSteps(0);
      if (res !== 'ok' && FM.modeOk === false && FM.modeStep && FM.modeStep.askUserOnFail) {
        log('обычный чат не вернул картинку (' + res + '), прошу выбрать режим вручную');
        await askUser('no-image-' + res);
        var up = cfg.steps.findIndex(function (x) { return x.do === 'upload'; });
        res = await runSteps(up);
      }
      if (res === 'text') { B.saveDump(dumpPage('text-answer')); fail('text', 'Сервис ответил текстом без картинки: ' + (FM.textAnswer || '').slice(0, 300)); }
      if (res === 'timeout') { B.saveDump(dumpPage('timeout')); fail('timeout', 'Не дождался картинки'); }
    } catch (e) { if (e.message !== '__fm_stop') { FM.running = false; try { B.saveDump(dumpPage('js-error: ' + e.message)); } catch (x) {} B.fail('js', e.message); } }
  })();
})();
