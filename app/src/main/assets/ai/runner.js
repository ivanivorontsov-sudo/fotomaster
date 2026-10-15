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
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
      el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true }));
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
  async function stepMode(s) {
    var direct = q(s.item, s.itemText);
    if (q(s.active) || (s.activeText && q(s.active.concat(['[class*="feature"]', '[class*="tag"]', '[class*="chip"]']), s.activeText) && !direct)) { log('режим «' + s.name + '» уже включён'); return; }
    if (direct && !/^(LI|SPAN)$/.test(direct.tagName)) { log('нажимаю «' + txt(direct) + '»'); click(direct); await sleep(1000); return; }
    var it = await openMenuItem(s.open || [], s.item || [], s.itemText);
    if (it) { log('включаю режим: ' + txt(it)); click(it); await sleep(1200); return; }
    if (s.fallbackText) {
      it = q(s.item, s.fallbackText) || await openMenuItem(s.open || [], s.item || [], s.fallbackText);
      if (it) { log('ВНИМАНИЕ: «' + s.name + '» не найден, включаю «' + txt(it) + '» (картинка может перерисоваться сильнее)'); B.warn('Режим «' + s.name + '» не найден, использован «' + txt(it) + '»'); click(it); await sleep(1200); return; }
    }
    if (!s.optional) fail('mode', 'Не нашёл кнопку режима «' + s.name + '». Сайт мог измениться: обновите селекторы в providers.json');
  }
  async function stepUpload(s) {
    var input = q(s.input);
    if (input) {
      log('загружаю фото через поле файла');
      try { var dt = new DataTransfer(); dt.items.add(b64ToFile(B.imageBase64(), 'photo.jpg', 'image/jpeg')); input.files = dt.files; input.dispatchEvent(new Event('change', { bubbles: true })); input.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) { log('DataTransfer: ' + e.message); }
    } else {
      B.armChooser();
      var opened = false;
      var o = q(s.open);
      if (o) { log('открываю загрузку: ' + (txt(o) || o.className)); click(o); await sleep(900);
        if (!B.chooserUsed()) { var it = q(s.item, s.itemText); if (it) { log('пункт меню: ' + txt(it)); click(it); } }
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
  async function stepType(s) { var e = q(s.selectors); if (!e) fail('input', 'Нет поля ввода'); setText(e, prompt); await sleep(500); log('запрос введён'); }
  async function stepSend(s) {
    before = {}; allImgSrc().forEach(function (x) { before[x] = 1; });
    var e = await waitFor(function () { var b = q(s.selectors); return b && !b.disabled && !/disabled/.test(b.className) ? b : null; }, 20000);
    if (e) { click(e); log('отправлено'); }
    else if (s.enterFallback) { var ta = q(['textarea', '[contenteditable=true]']); ta && ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true })); log('отправлено через Enter'); }
    else fail('send', 'Кнопка отправки не найдена');
    FM.sendSel = s.selectors; await sleep(1500);
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
        for (var k = 0; k < urls.length; k++) { if (await deliver(urls[k])) { FM.running = false; return; } }
        B.resultUrl(urls[urls.length - 1]); FM.running = false; return;
      }
      var lastMsg = null; for (var c = 0; c < s.containers.length && !lastMsg; c++) { try { var all = document.querySelectorAll(s.containers[c]); if (all.length) lastMsg = all[all.length - 1]; } catch (e) {} }
      var t = lastMsg ? txt(lastMsg) : '';
      if (t && t === lastText && !busy) stable++; else stable = 0; lastText = t;
      if (stable >= 10 && t.length > 0) fail('text', 'Сервис ответил текстом без картинки: ' + t.slice(0, 300));
      if ((Date.now() - t0) % 15000 < 1600) log(busy ? 'генерация…' : 'жду ответ…');
    }
    fail('timeout', 'Не дождался картинки за ' + Math.round((s.timeout || 300000) / 1000) + ' с');
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
  (async function () {
    try {
      if (B.mode() === 'check') { await sleep(4000); B.checkDone(FM.loggedIn(), document.title + ' | ' + location.href); FM.running = false; return; }
      log('старт ' + cfg.id + ' ' + location.href);
      if (!FM.loggedIn()) B.warn('Похоже, вход в аккаунт не выполнен. Лимиты могут быть меньше или потребуется войти.');
      for (var i = 0; i < cfg.steps.length; i++) {
        var s = cfg.steps[i]; log('шаг ' + (i + 1) + ': ' + s.do);
        if (s.do === 'wait') await stepWait(s); else if (s.do === 'dismiss') await stepDismiss(s); else if (s.do === 'mode') await stepMode(s);
        else if (s.do === 'upload') await stepUpload(s); else if (s.do === 'type') await stepType(s); else if (s.do === 'send') await stepSend(s); else if (s.do === 'result') await stepResult(s);
      }
    } catch (e) { if (e.message !== '__fm_stop') { FM.running = false; B.fail('js', e.message); } }
  })();
})();
