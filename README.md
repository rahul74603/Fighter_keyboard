# Fighter Study Keyboard ⌨️📚

Ek Android keyboard jo **padhai (study)** ke liye bana hai:

- **Unlimited notes** — jitne chahe notes add karo (koi limit nahi). `+ Add note` dabao, apna text paste/type karo, save.
- **Auto play** — `Play` dabao aur note ke **shabd ek-ek karke** khud type hote rahenge — WhatsApp message, YouTube comment, notes app… jahan bhi cursor ho.
- **Speed control** — keyboard ke upar slider se reading speed set karo: dheere (2400 ms/word) se tez (200 ms/word) tak. Practice karte-karte speed badhao, reading speed badhegi.
- **Loop** — ek hi note baar-baar chalane ke liye.
- **Voice mode (optional)** — phone ki text-to-speech se note bologe bhi, aur saath-saath type bhi hota rahega (Android 8+).
- **Suggestion strip** — aapke hi notes ke words se suggestions.
- **Import / Export** — notes ka backup clipboard se.

Sab kuch **offline, phone me hi** save hota hai (koi login/server nahi).

---

## APK kaise banaye / install kare

### Option 1 — GitHub Actions se (recommended, phone pe hi)
1. Repo ke **Actions** tab me `Build APK` workflow kholo (push pe automatic chalta hai).
2. Latest successful run → **Artifacts** → `fighter-study-keyboard-apk` download karo (zip).
3. Zip se `app-debug.apk` nikaalo, phone me install karo (Unknown sources allow karna padega).
4. App kholo → **1. Enable keyboard** → list me *Fighter Study Keyboard* ON karo → **2. Choose keyboard** se select karo.

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

## Notes / limitations

- Notes phone ki local storage me hain — app uninstall karne se delete ho jayenge. Pehle **Export** se backup le lena.
- Voice mode device ke TTS engine pe depend karta hai; na ho to typing mode chalta hai.
- Typing target app ke hisaab se dikhega: kuch apps (bank apps etc.) keyboard se typing block karte hain — unme play nahi hoga.
- Android 5.0 (API 21) se upar sab versions.

## License
MIT
