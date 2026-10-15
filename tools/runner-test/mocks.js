const common = `
<style>body{font-family:sans-serif;width:400px} .menu{display:none} .menu.open{display:block}</style>
<script>
function uploadViaChooser(){ const i=document.createElement('input'); i.type='file'; i.accept='image/*';
  i.onchange=()=>{ const f=i.files[0]; if(!f) return; const im=document.createElement('img'); im.className='file-item-thumb'; im.src=URL.createObjectURL(f); document.querySelector('.files').appendChild(im); window.__uploaded=f.size; }; i.click(); }
function respond(kind){ const stop=document.createElement('button'); stop.className=window.__stopCls; stop.textContent='stop'; document.body.appendChild(stop);
  setTimeout(()=>{ stop.remove(); const m=document.createElement('div'); m.className=window.__respCls;
    if(kind==='text'){ m.textContent='Извините, я не могу редактировать изображения.'; } else { const im=document.createElement('img'); im.src=window.__outUrl; m.appendChild(im); }
    document.querySelector('.chat').appendChild(m); }, 2500); }
</script>`;
module.exports = {
qwen: (opts) => `<html><body>${common}
<script>window.__stopCls='stop-button'; window.__respCls='response-message-content'; window.__outUrl='/qwen-out.png?x-oss-process=image/resize,w_400';</script>
<div class="chat"><div class="response-message-content">Hello, how can I help?</div></div>
<div class="files"></div><div class="tags"></div>
<textarea class="message-input-textarea"></textarea>
<button class="chat-input-feature-btn" onclick="document.querySelector('.menu').classList.toggle('open')">+</button>
<div class="menu" role="menu">
 ${opts.noEdit ? '' : '<div role="menuitem" onclick="this.parentNode.classList.remove(\'open\');document.querySelector(\'.tags\').innerHTML=\'<span class=chat-input-feature-tag image_edit>Image Edit</span>\';window.__mode=\'image_edit\'">Image Edit</div>'}
 ${opts.noGen ? '' : '<div role="menuitem" onclick="this.parentNode.classList.remove(\'open\');window.__mode=\'t2i\'">Create Image</div>'}
 <div role="menuitem">Deep Research</div>
</div>
<button class="chat-input-upload" onclick="if(!window.__mode&&!${!!opts.allowNoMode}){alert('no mode')}; uploadViaChooser()">📎</button>
<button class="send-button" onclick="window.__sent=document.querySelector('textarea').value; window.__sentMode=window.__mode; window.__sentFile=window.__uploaded; respond('${opts.reply||'img'}')">send</button>
</body></html>`,
alice: () => `<html><body>${common}
<script>window.__stopCls='Typing'; window.__respCls='MessageBubble'; window.__outUrl='/get-shedevrum/1/abc/400x400';</script>
<div class="chat"><div class="MessageBubble">Привет!</div></div><div class="files"></div>
<textarea data-testid="inputbase-textarea" class="AliceInput-Textarea"></textarea>
<button data-testid="InputControls-More-Button" onclick="document.querySelector('.menu').classList.toggle('open')">Меню</button>
<ul class="menu"><li><button data-testid="button-capture-button">Камера</button><button data-testid="button-gallery-button" onclick="uploadViaChooser()">Фото</button><button data-testid="button-file-button">Файлы</button></li></ul>
<button data-testid="button-send-button" onclick="window.__sent=document.querySelector('textarea').value; window.__sentFile=window.__uploaded; respond('img')">↑</button>
</body></html>`,
};
