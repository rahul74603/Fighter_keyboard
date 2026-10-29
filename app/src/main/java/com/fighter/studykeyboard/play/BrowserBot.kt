package com.fighter.studykeyboard.play

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.fighter.studykeyboard.data.PlayerPrefs
import com.fighter.studykeyboard.util.DebugLog
import org.json.JSONObject

/**
 * Local bot that runs *inside our own browser* (WebView).
 *
 * The user logs in (Facebook / YouTube / any site) in the in-app browser, taps the
 * comment / message box and (optionally) taps the Send button once so we learn the
 * exact CSS selectors. After that the bot fills the box with the next note and
 * presses Send - driven from Kotlin, so it keeps working while the app is in the
 * background or the screen is locked (foreground service + wake lock).
 *
 * Only DOM operations on the page the user opened are performed - no server, no
 * credentials leave the phone, nothing is uploaded.
 */
object BrowserBot {

    private const val FILL_TO_SEND_MS = 450L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var webView: WebView? = null

    @Volatile
    var running: Boolean = false

    var inputSelector: String? = null
        private set
    var sendSelector: String? = null
        private set

    fun attach(view: WebView?, context: Context) {
        webView = view
        val prefs = PlayerPrefs(context)
        inputSelector = prefs.botInputSelector
        sendSelector = prefs.botSendSelector
        DebugLog.log("Bot", "attached webview=${view != null} input=$inputSelector send=$sendSelector")
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    fun hasPage(): Boolean = webView != null

    fun saveSelectors(context: Context, input: String?, send: String?) {
        val prefs = PlayerPrefs(context)
        if (input != null) {
            inputSelector = input
            prefs.botInputSelector = input
        }
        if (send != null) {
            sendSelector = send
            prefs.botSendSelector = send
        }
        DebugLog.log("Bot", "selectors saved input=$inputSelector send=$sendSelector")
    }

    /** Runs fill + click for one message. Callback: (success, message). */
    fun runOnce(text: String, callback: (Boolean, String) -> Unit) {
        fill(text) { filled, message ->
            if (!filled) {
                callback(false, message)
                return@fill
            }
            handler.postDelayed({
                click { clicked, clickMessage ->
                    callback(clicked, clickMessage)
                }
            }, FILL_TO_SEND_MS)
        }
    }

    fun fill(text: String, callback: (Boolean, String) -> Unit) {
        val view = webView ?: run {
            callback(false, "no page")
            return
        }
        val js = """
            (function(){
              try {
                var inputSel = ${JSONObject.quote(inputSelector ?: "")};
                var text = ${JSONObject.quote(text)};
                function visible(el){
                  if(!el) return false;
                  var r = el.getBoundingClientRect();
                  var s = getComputedStyle(el);
                  return r.width > 2 && r.height > 2 && s.visibility !== 'hidden' && s.display !== 'none';
                }
                function findInput(){
                  if (inputSel) { try { var e = document.querySelector(inputSel); if (e) return e; } catch(e){} }
                  var list = [].slice.call(document.querySelectorAll('textarea,[contenteditable="true"],[role="textbox"]'));
                  for (var i = 0; i < list.length; i++) {
                    if (visible(list[i]) && !list[i].disabled) return list[i];
                  }
                  return null;
                }
                function setValue(el, val){
                  el.focus();
                  if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    setter.call(el, val);
                  } else {
                    el.textContent = val;
                  }
                  try {
                    el.dispatchEvent(new InputEvent('input', {bubbles: true, data: val, inputType: 'insertText'}));
                  } catch (e) {
                    el.dispatchEvent(new Event('input', {bubbles: true}));
                  }
                  el.dispatchEvent(new Event('change', {bubbles: true}));
                }
                var inp = findInput();
                if (!inp) return JSON.stringify({ok:false, step:'input'});
                try { inp.scrollIntoView({block:'center'}); } catch (e) {}
                setValue(inp, text);
                return JSON.stringify({ok:true, step:'filled'});
              } catch (err) {
                return JSON.stringify({ok:false, step:'error', msg: '' + err});
              }
            })()
        """.trimIndent()
        evaluate(js) { ok, message ->
            DebugLog.log("Bot", "fill ok=$ok $message")
            callback(ok, message)
        }
    }

    fun click(callback: (Boolean, String) -> Unit) {
        val view = webView ?: run {
            callback(false, "no page")
            return
        }
        val js = """
            (function(){
              try {
                var sendSel = ${JSONObject.quote(sendSelector ?: "")};
                var textRe = /^(send|post|comment|reply|publish|submit|share|भेजें|पोस्ट|कमेंट|जवाब|टिप्पणी)/i;
                function visible(el){
                  if (!el) return false;
                  var r = el.getBoundingClientRect();
                  var s = getComputedStyle(el);
                  return r.width > 2 && r.height > 2 && s.visibility !== 'hidden' && s.display !== 'none';
                }
                function findSend(){
                  if (sendSel) { try { var e = document.querySelector(sendSel); if (e) return e; } catch(e){} }
                  var all = [].slice.call(document.querySelectorAll('button,[role="button"],input[type="submit"],div[aria-label]'));
                  for (var i = 0; i < all.length; i++) {
                    var el = all[i];
                    if (!visible(el) || el.disabled || el.getAttribute('aria-disabled') === 'true') continue;
                    var label = (el.getAttribute('aria-label') || el.innerText || el.value || '').trim();
                    if (textRe.test(label)) return el;
                  }
                  return null;
                }
                var btn = findSend();
                if (!btn) return JSON.stringify({ok:false, step:'send'});
                btn.click();
                return JSON.stringify({ok:true, step:'clicked', label:(btn.getAttribute('aria-label')||btn.innerText||'').trim()});
              } catch (err) {
                return JSON.stringify({ok:false, step:'error', msg: '' + err});
              }
            })()
        """.trimIndent()
        evaluate(js) { ok, message ->
            DebugLog.log("Bot", "click ok=$ok $message")
            callback(ok, message)
        }
    }

    /** Quick check that JS runs in the page at all. */
    fun ping(callback: (Boolean) -> Unit) {
        val view = webView ?: run {
            callback(false)
            return
        }
        view.evaluateJavascript("(function(){return document.title ? 'ok' : 'ok';})()") { result ->
            callback(result?.contains("ok") == true)
        }
    }

    /** Next tap on the page is captured and saved as the input or send selector. */
    fun learn(kind: String) {
        val view = webView ?: return
        val js = """
            (function(){
              if (window.__fighterLearn) { document.removeEventListener('click', window.__fighterLearn, true); }
              window.__fighterLearn = function(ev){
                try {
                  var el = ev.target;
                  var parts = [];
                  while (el && el.nodeType === 1 && parts.length < 8) {
                    var sel = el.tagName.toLowerCase();
                    if (el.id) { sel += '#' + el.id; parts.unshift(sel); break; }
                    var cls = (typeof el.className === 'string') ? el.className.trim() : '';
                    if (cls) sel += '.' + cls.split(/\s+/).slice(0,2).join('.');
                    var parent = el.parentElement;
                    if (parent) sel += ':nth-child(' + ([].indexOf.call(parent.children, el) + 1) + ')';
                    parts.unshift(sel);
                    el = parent;
                  }
                  var path = parts.join(' > ');
                  if (window.FighterBot && window.FighterBot.pick) window.FighterBot.pick('${kind}', path);
                  document.removeEventListener('click', window.__fighterLearn, true);
                  window.__fighterLearn = null;
                } catch (e) {}
              };
              document.addEventListener('click', window.__fighterLearn, true);
              return 'learning';
            })()
        """.trimIndent()
        view.evaluateJavascript(js, null)
        DebugLog.log("Bot", "learn mode: $kind")
    }

    private fun evaluate(js: String, callback: (Boolean, String) -> Unit) {
        val view = webView ?: run {
            callback(false, "no page")
            return
        }
        handler.post {
            try {
                view.evaluateJavascript(js) { result ->
                    val clean = result?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                    val ok = try {
                        JSONObject(clean).optBoolean("ok", false)
                    } catch (error: Exception) {
                        clean.contains("\"ok\":true")
                    }
                    callback(ok, clean)
                }
            } catch (error: Exception) {
                DebugLog.log("Bot", error)
                callback(false, "eval failed")
            }
        }
    }
}
