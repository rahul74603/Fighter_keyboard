# Fighter Study Keyboard ⌨️📚

Ek Android keyboard jo **padhai (study)** ke liye bana hai:

- **Unlimited notes** — jitne chahe notes add karo (koi limit nahi). `+ Add note` dabao, apna text paste/type karo, save.
- **Auto play** — `Play` dabao aur note ke **shabd ek-ek karke** khud type hote rahenge — WhatsApp message, YouTube comment, notes app… jahan bhi cursor ho.
- **Speed control** — keyboard ke upar slider se reading speed set karo: dheere (1500 ms/word) se tez (200 ms/word) tak. Practice karte-karte slider aage badhao, reading speed badhegi.
- **Loop** — ek hi note baar-baar chalane ke liye.
- **Voice mode (optional)** — phone ki text-to-speech se note bologe bhi, aur saath-saath type bhi hota rahega (Android 8+).
- **Suggestion strip** — aapke hi notes ke words se suggestions.
- **T-mode (CAPS + digit map)** — keyboard toolbar ka `T` button: ON hone par sab CAPITAL + aapka digit map: **A=4, B=8, T=7, O=0, E=3, I=1, S=5, Z=2** (jaise `THANKS` → `7H4NK5`, `BEST` → `8E57`). Typing aur Play dono par lagta hai. SHIFT dabao to us waqt normal.
- **Auto-send** — har note type/speak hone ke baad khud send hota hai: pehle app ka asli **Send button** (optional accessibility helper), warna IME ka **Send action**, warna Enter, isliye har note alag message ban kar chala jata hai — manually send nahi karna padta. Ek Play = ek note. Long-press Play / `Play all` = saare notes **ek-ek karke, alag-alag** send.
- **Shuffle** — Play dabao to **random note** chunta hai (bilkul wahi note baar-baar nahi jata); Play all me notes ka order bhi shuffle hota hai; Loop me har round naya order.
- **Background play** — voice mode screen off / phone lock / app minimise hone par bhi **bolta rehta hai** (notification me Stop button). Typing mode me words tab type hote hain jab text field saamne ho (Android keyboard ka rule — doosre app me jab wo front me na ho, type nahi kar sakta).
- **Letter by letter** — chaho to word ke bajaye akshar-akshar typing effect (app settings me toggle).
- **Paste button** — naya note kholne pe field **blank** aata hai; clipboard se paste karna ho to `Paste` dabao (auto-fill nahi hota).
- **Import / Export** — notes ka backup clipboard se.

Sab kuch **offline, phone me hi** save hota hai (koi login/server nahi).

---

## APK kaise banaye / install kare

### Option 1 — seedha APK download (sabse aasan)
**Releases** page kholo: <https://github.com/rahul74603/Fighter_keyboard/releases/latest> →
`fighter-study-keyboard-v2.3.0.apk` download karo → phone me install karo
(Unknown sources / "Install unknown apps" allow karna padega).

Ya phir **Actions** tab → latest `Build APK` run → **Artifacts** → `fighter-study-keyboard-apk` (zip).
3. App kholo → **1. Enable keyboard** → list me *Fighter Study Keyboard* ON karo → **2. Choose keyboard** se select karo.

### Option 2 — local build
```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Requirements: JDK 17 + Android SDK (compileSdk 34).

---

## Kaise use kare (padhai workflow)

1. Keyboard kholo, upar **Notes** dabao → **+ Add note** → apna chapter/definition/question paste karo → **Save**.
2. Kisi bhi app me text box pe tap karo (message/comment/notes), Fighter keyboard aayega.
3. **Play** dabao — text ek-ek word karke type hoga. `Pause` / `Stop` se control, `Loop` se repeat.
4. **Speed** slider se speed badhao-ghatao. Speed zyada = words tezi se aayenge (reading speed practice).
5. Long-press **Play** = saare notes ek ke baad ek play.

Notes panel: har note ke saath **Play / Edit / Del** buttons. `Play all` = sabhi notes.

---

## Project structure

```
app/src/main/java/com/fighter/studykeyboard/
├── MainActivity.kt              # setup + note manager + speed/loop/voice settings
├── NoteEditActivity.kt          # note add/edit screen (dialog style)
├── FighterKeyboardService.kt    # InputMethodService = asli keyboard + player controls
├── data/
│   ├── Note.kt                  # ek note ka model (JSON)
│   ├── NoteStore.kt             # unlimited notes, SharedPreferences me save
│   └── PlayerPrefs.kt           # speed / loop / voice settings
├── play/
│   ├── ReadingPlayer.kt         # word-by-word auto typing engine
│   ├── SpeechSpeaker.kt         # optional text-to-speech mode
│   └── TextTools.kt             # words + suggestions
└── ui/NotesAdapter.kt           # notes list adapter (dark + light layout)
```

Res: `layout/keyboard_view.xml` (keyboard + notes panel), `layout/activity_main.xml`, strings **English + हिंदी**.

---

## Update / reinstall note

- APK ek **fixed signing key** se sign hota hai (`keystore/fighter-study.p12`) taaki naya version purane ke **upar seedha install** ho jaye aur notes bane rahein.
- Sirf **v1.1.0 / v1.2.0** se aate waqt ek baar purana version uninstall karna padega (un builds ka key alag tha). Pehle **Export** dabao → JSON clipboard me aa jayega (WhatsApp/Keep me paste kar lo) → reinstall → **Import**.
- Ye key demo/personal use ke liye hai. Play Store pe publish karna ho to apni private key (Play App Signing) use karo.

## v2.3 — Loop fix + auto-pilot

- **Loop fix**: pehle queue ke notes hi repeat hote the (isliye ek hi note baar-baar). Ab har loop round **saare notes se dobara banate hain**, shuffle ke saath — loop = “all notes loop”.
- **Auto-pilot (optional, accessibility)**: keyboard band hone par bhi queue chalti hai — accessibility se app ke field me text set + Send tap. Shart: wahi app screen par ho aur Auto-send helper ON ho.
- Screen off / phone lock me **koi bhi keyboard** type nahi kar sakta (Android ka hard rule) — us case me queue pause hoti hai, notification me “Typing paused” + app wapas aane par wahin se continue.

## v2.2 — Play = saare notes, ek-ek karke apne aap

- **Play** ab poora queue chalata hai: **saare notes shuffle hokar, ek-ek karke type + auto-send**, phir agla — bina koi button dabaye.
- Per-note **Play** (notes list me) = sirf wahi note. **Long-press Play** = sirf last note.
- **Long-press Shuffle** = Loop ON/OFF (Loop ON to saare notes khatam hone par naya shuffled round).
- Send ke baad app apna text field dobara banata hai — keyboard uska **intezaar** karta hai (8 retry) taaki agla note miss na ho.
- Status: `Note 3/11` + notification me progress; end par “Saare 11 notes bhej diye ✓”.

## v2.1 — Auto-send **accessibility ke bina** (Restricted setting fix)

Android 13+ (vivo/oppo/samsung) sideloaded APK ko accessibility allow nahi karta: **“Restricted setting”**. Isliye send ab accessibility par depend nahi karta. Har note ke baad keyboard ye order me try karta hai (aur verify karta hai ki message chala gaya):

1. `performEditorAction(IME_ACTION_SEND)` — app ka apna send action
2. `performEditorAction(IME_ACTION_DONE)`
3. **Hardware Enter key event** (`InputConnection.sendKeyEvent`) — WhatsApp/comment box ise “send” maante hain, soft newline ko nahi
4. Soft newline (last resort)
5. (Optional) Accessibility se Send button click — agar allow kiya ho

App me **“Restricted setting kaise hataye?”** button: Settings → Apps → Fighter Study Keyboard → ⋮ → Allow restricted settings (direct App-info shortcut bhi hai).

## v2.0 — Internet fix + log

- **Browser fix**: manifest me `INTERNET` + `ACCESS_NETWORK_STATE` permission missing tha — isliye browser me “internet nahi chal raha” tha. Ab add hai, saath me network check + error code.
- **Background confirm**: app ke “Background play” button se start karne par pehle “Study mode shuru.” bolta hai — awaaz aayi matlab chalu. Log me har step likha jata hai.
- **Auto-send helper dialog**: notes hone par app ek baar batata hai ki Send button ke liye accessibility helper ON karna hai (WhatsApp me Enter nayi line daalta hai, send nahi karta).
- **Log screen** (`Log` button app me): TTS engine state, background service start/stop, har note, send ka kaunsa tarika chala, browser load/error — sab timestamp ke saath. **Copy** karke bhej sakte ho.

## Test & Fix (app me) — v1.9

App khologe to neeche chhota status milega: **Notification / Accessibility / Battery free**.
- **“Battery se chhoot do”** — Vivo/Oppo/Mi phones background service kill karte hain; ye button battery optimization se exempt karta hai (background play ke liye zaroori).
- **“Voice test”** — turant awaaz test (TTS engine + volume check).
- Queue me **send retry + verify + watchdog**: send button na mile to 2 retry, phir apna typed text clear karke agla note — queue kabhi rukti nahi.

## Full background playback (v1.8)

Poora playback engine ab **service ke andar** hai (`StudyPlaybackService`), screen ke andar nahi:

- App minimise / back / screen off / phone lock — **chalta rehta hai**.
- Notification me **⏮ Prev · ⏯ Play-Pause · ⏭ Next · ✕ Stop** — phone lock screen se bhi control.
- **Wake lock** (2 ghante tak renew hota hai) taaki Doze mode me playback na ruke.
- App me **“Background play (screen off)”** button — ek tap, phir phone pocket me.
- Study mode screen sirf controller hai: band kar do, playback chalta rahega; wapas kholne par wahi note + progress.

## Study / Test mode

App me bada button **“Study / Test mode (voice)”** (keyboard ke Notes panel me bhi `Study`):

- Notes **bol kar sunaye jate hain** — screen on/off dono me chalta hai (voice foreground service).
- Note ka text **screen par bada likha** aata hai, aur jo word bola ja raha hai wo **highlight** hota hai — aankh band karke suno, kholo to wahi text saamne.
- **Hide text** ON karo → text chhup jata hai (“🙈 aankh band karke bolo”), tap karke answer check — self test ke liye.
- **Auto next** → ek note khatam, 1.5s baad agla (shuffle ke saath random order).
- **Screen on** → test karte waqt screen band na ho.
- Speed slider voice ki speed badalta hai; Hindi (Devanagari) notes ke liye Hindi voice, English ke liye English voice.
- Notification me **Stop**; home/back karne par bhi voice chalti rehti hai.

## Send kaise chalta hai (jo Enter se send nahi hota)

1. App settings me **“3. Auto-send ON karo (Send button)”** dabao → Accessibility me *Fighter Study Keyboard* ON karo.
2. Ab Play karte waqt keyboard app ka asli **Send/Post button** dhoondh kar dabata hai (WhatsApp chat, YouTube comment, FB comment etc.) — Enter ki zarurat hi nahi, isliye nayi line wala problem khatam.
3. Helper OFF ho to keyboard pehle IME ka **Send action** bhejta hai; agar app wo nahi deta to Enter.

Note: helper sirf aapke Play dabane par ek tap karta hai; screen content read/store/upload nahi hota.

## Browser

App me chhota in-app browser hai (`Browser` button) — wahan ek baar login karke comment/msg box pe tap karo aur keyboard ka Play use karo.

## Background play — sach kya hai

- **Voice mode = 100% background**: foreground service + notification chalta rehta hai, screen off/lock ke baad bhi notes bologe.
- **Typing mode = foreground only**: Android kisi bhi keyboard ko permission nahi deta ki wo kisi app me (jo screen pe nahi hai) text type kare. Isliye Play karte waqt target app khula ho, keyboard dikh raha ho. Screen off karte hi typing rukti hai, wapas aane par queue wahin se chalu hoti hai.
- **In-app browser / FB auto-comment**: is app me browser daal kar FB login + auto-comment karana intentionally nahi kiya — (a) FB/Instagram ke rules ke against hai (automation se account ban ho sakta hai), (b) background WebView me page JS/click bharosemand nahi hota. Background chahiye to **voice mode** use karo, ya typing ke liye app saamne rakho — queue + auto-send usme kaam kar deta hai.

## Notes / limitations

- Notes phone ki local storage me hain — app uninstall karne se delete ho jayenge. Pehle **Export** se backup le lena.
- Voice mode device ke TTS engine pe depend karta hai; na ho to typing mode chalta hai.
- Typing target app ke hisaab se dikhega: kuch apps (bank apps etc.) keyboard se typing block karte hain — unme play nahi hoga.
- Android 5.0 (API 21) se upar sab versions.

## License
MIT
