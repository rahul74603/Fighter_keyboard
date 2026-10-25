# Fighter Study Keyboard ⌨️📚

Ek Android keyboard jo **padhai (study)** ke liye bana hai:

- **Unlimited notes** — jitne chahe notes add karo (koi limit nahi). `+ Add note` dabao, apna text paste/type karo, save.
- **Auto play** — `Play` dabao aur note ke **shabd ek-ek karke** khud type hote rahenge — WhatsApp message, YouTube comment, notes app… jahan bhi cursor ho.
- **Speed control** — keyboard ke upar slider se reading speed set karo: dheere (1500 ms/word) se tez (200 ms/word) tak. Practice karte-karte slider aage badhao, reading speed badhegi.
- **Loop** — ek hi note baar-baar chalane ke liye.
- **Voice mode (optional)** — phone ki text-to-speech se note bologe bhi, aur saath-saath type bhi hota rahega (Android 8+).
- **Suggestion strip** — aapke hi notes ke words se suggestions.
- **Letter by letter** — chaho to word ke bajaye akshar-akshar typing effect (app settings me toggle).
- **Paste button** — naya note kholne pe field **blank** aata hai; clipboard se paste karna ho to `Paste` dabao (auto-fill nahi hota).
- **Import / Export** — notes ka backup clipboard se.

Sab kuch **offline, phone me hi** save hota hai (koi login/server nahi).

---

## APK kaise banaye / install kare

### Option 1 — seedha APK download (sabse aasan)
**Releases** page kholo: <https://github.com/rahul74603/Fighter_keyboard/releases/latest> →
`fighter-study-keyboard-v1.3.0.apk` download karo → phone me install karo
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

## Notes / limitations

- Notes phone ki local storage me hain — app uninstall karne se delete ho jayenge. Pehle **Export** se backup le lena.
- Voice mode device ke TTS engine pe depend karta hai; na ho to typing mode chalta hai.
- Typing target app ke hisaab se dikhega: kuch apps (bank apps etc.) keyboard se typing block karte hain — unme play nahi hoga.
- Android 5.0 (API 21) se upar sab versions.

## License
MIT
