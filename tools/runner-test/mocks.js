const common = `
<style>body{font-family:sans-serif;width:400px} .menu{display:none} .menu.open{display:block}</style>
<script>
function uploadViaChooser(){ const i=document.createElement('input'); i.type='file'; i.accept='image/*';
  i.onchange=()=>{ const f=i.files[0]; if(!f) return; const im=document.createElement('img'); im.className='file-item-thumb'; im.src=URL.createObjectURL(f); document.querySelector('.files').appendChild(im); window.__uploaded=f.size; }; i.click(); }
function respond(kind){ const ta=document.querySelector('textarea'); if(ta){ const ub=document.createElement('div'); ub.className='user-bubble'; ub.textContent=ta.value; document.querySelector('.chat').appendChild(ub); ta.value=''; } const stop=document.createElement('button'); stop.className=window.__stopCls; stop.textContent='stop'; document.body.appendChild(stop);
  setTimeout(()=>{ stop.remove(); const m=document.createElement('div'); m.className=window.__respCls;
    if(kind==='text'){ m.textContent='Извините, я не могу редактировать изображения.'; } else { const im=document.createElement('img'); im.src=window.__outUrl; m.appendChild(im); }
    document.querySelector('.chat').appendChild(m); }, 2500); }
</script>`;
module.exports = {
qwen: (opts) => {
  const L = opts.lang === 'en' ? { up: 'Upload', ask: 'Ask Qwen', img: 'Create Image', vid: 'Create Video', web: 'Web Search', model: 'Model 3.0', auto: 'Auto', tools: 'Tools' } : { up: 'Загрузить', ask: 'Спросить Qwen', img: 'Создать изображение', vid: 'Создать видео', web: 'Поиск в сети', model: 'Модель 3.0', auto: 'Авто', tools: 'Инструменты' };
  return `<html><head><meta name="viewport" content="width=device-width"></head><body>${common}
<style>.sheet{display:none;position:fixed;bottom:0;left:0;right:0;background:#fff;padding:10px} .sheet.open{display:block} .grid{display:flex;gap:8px} .grid div{width:60px;height:60px;background:#eee} li{padding:8px;list-style:none}</style>
<script>window.__stopCls='stop-button'; window.__respCls='response-message-content'; window.__outUrl='/qwen-out.png?x-oss-process=image/resize,w_400';
window.__mode = ${opts.preset ? "'t2i'" : 'null'};
function render(){ const on = window.__mode==='t2i';
  document.querySelector('.feat').innerHTML = on ? '<span class="tag-chip"><svg class="icon-image"></svg><i class="x" onclick="window.__mode=null;render()">×</i></span><span class="dd">${L.model}</span><span class="dd">${L.auto}</span>' : '';
  const li=document.getElementById('li-img'); if(li){ li.querySelector('.mark').innerHTML = on ? '<svg class="icon-check"></svg>' : ''; } }
function toggleImg(){ window.__mode = window.__mode==='t2i' ? null : 't2i'; window.__toggles=(window.__toggles||0)+1; render(); }
</script>
<div class="chat"><div class="response-message-content">Чем я могу помочь?</div></div>
<div class="files"></div>
<div class="bar"><textarea class="message-input-textarea" placeholder="${L.ask}"></textarea>
 <div class="row"><button class="ic" onclick="document.querySelector('.sheet').classList.add('open')"><svg></svg></button><span class="feat"></span><button class="mic"><svg></svg></button>
 <button class="send-button" onclick="window.__sent=document.querySelector('textarea').value; window.__sentMode=window.__mode; window.__sentFile=window.__uploaded; respond(('${opts.reply||'img'}'==='textOnce') ? (window.__n=(window.__n||0)+1, window.__n===1?'text':'img') : '${opts.reply||'img'}')"><svg></svg></button></div></div>
<div class="sheet"><div class="hdr"><div>${L.up}</div><button aria-label="close" onclick="this.closest('.sheet').classList.remove('open')">×</button></div>
 <div class="grid"><div class="g" onclick="window.__cam=1"><svg></svg></div><div class="g" onclick="this.closest('.sheet').classList.remove('open');uploadViaChooser()"><svg></svg></div><div class="g"><svg></svg></div><div class="g" onclick="this.closest('.sheet').classList.remove('open');uploadViaChooser()"><svg></svg></div></div>
 <ul><li><span>${L.tools}</span><input type="checkbox"></li>
 ${opts.noImg ? '' : `<li id="li-img" onclick="toggleImg()"><svg></svg><span>${L.img}</span><span class="mark"></span></li>`}
 <li><span>${L.vid}</span></li><li><span>${L.web}</span></li></ul></div>
<script>render()</script>
</body></html>`; },
alice: (opts) => `<html><body>${common}${opts.login ? '<button class="hdr-login">Войти</button>' : ''}
<script>window.__stopCls='Typing'; window.__respCls='MessageBubble'; window.__outUrl='/get-shedevrum/1/abc/400x400';</script>
<div class="chat"><div class="MessageBubble">Привет!</div></div><div class="files"></div>
<textarea data-testid="inputbase-textarea" class="AliceInput-Textarea"></textarea>
<button data-testid="InputControls-More-Button" onclick="document.querySelector('.menu').classList.toggle('open')">Меню</button>
<ul class="menu"><li><button data-testid="button-capture-button">Камера</button><button data-testid="button-gallery-button" onclick="uploadViaChooser()">Фото</button><button data-testid="button-file-button">Файлы</button></li></ul>
<button data-testid="button-send-button" onclick="if(${opts.ignore ? 1 : 0} && !window.__ign){window.__ign=1;return;} window.__sent=document.querySelector('textarea').value; window.__sentFile=window.__uploaded; respond('img')">↑</button>
</body></html>`,
};
