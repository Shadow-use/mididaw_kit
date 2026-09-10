# MidiDaw Kit — стартовий код для MVP (піано-рол + відтворення)

Це не повний Android-проєкт (без build.gradle/AndroidManifest), а набір
Kotlin-файлів, які потрібно вставити у твій існуючий проєкт (той, що
через А-Код + GitHub CI). Структура пакетів вже готова — просто скопіюй
папку `app/src/main/java/com/example/mididaw/` у своє дерево
`app/src/main/java/...` і за потреби зміни `com.example.mididaw` на
свій реальний пакет (find & replace по всіх файлах).

## Що є в архіві

```
app/src/main/java/com/example/mididaw/
├── model/
│   └── MidiJson.kt        — дата-класи + парсер/серіалізатор твого JSON MIDI формату
├── audio/
│   ├── Synth.kt           — хвилі (sawtooth/triangle/sine/square) + ADSR обвідна
│   ├── AudioRenderer.kt   — рендерить MidiSong у PCM-буфер (offline-мікс усіх треків)
│   └── MidiPlayer.kt      — грає PCM через AudioTrack
└── ui/
    ├── PianoRollView.kt   — Compose Canvas, показує ноти прямокутниками
    └── MainScreen.kt      — демо-екран: завантажує json, Play/Stop, піано-рол

app/src/main/assets/
└── jingle-bells.json      — твій приклад для тесту
```

## Вимоги до build.gradle

Нічого додаткового ставити не треба:
- `org.json.*` — вбудований в Android SDK, окремої залежності не потрібно
- `AudioTrack` — теж вбудований (android.media)
- Потрібен лише Jetpack Compose (Compose UI + Material3 + foundation) —
  якщо в тебе вже Compose-проєкт, усе збереться без змін у Gradle.

## Як підключити MainScreen

У своєму `MainActivity` (або де в тебе `setContent { ... }`):

```kotlin
setContent {
    MaterialTheme {
        MainScreen(assetFileName = "jingle-bells.json")
    }
}
```

## Як це працює

1. `MidiJsonParser.parse()` читає твій JSON (розпізнає рекомендований,
   Gemini і старий delay-формат — усі три з твоєї
   JSON_MIDI_Format_Instruction.md).
2. `AudioRenderer.render()` конвертує ticks у секунди через `bpm` і
   `ticks_per_beat`, генерує хвилю (за замовчуванням — triangle,
   звучить м'якше за чистий синус) з ADSR-обвідною для кожної ноти,
   змішує всі треки в один PCM-буфер.
3. `MidiPlayer.play()` конвертує float → 16-bit PCM і програє через
   `AudioTrack` (MODE_STATIC — простіше й надійніше для коротких
   мелодій, ніж потоковий рендер у реальному часі).
4. `PianoRollView` малює ноти на Canvas — поки що лише перегляд.

## Наступні кроки (коли цей MVP запрацює)

- Редагування нот у `PianoRollView` (drag = зсув, resize країв = зміна `dur`)
- Кнопка "Зберегти" → `MidiJsonParser.toJson(song)` → запис у файл
- Вибір інструменту/waveform по треку (program → інший Waveform або
  інша форма огинаючої)
- Експорт у .mid — можна портувати логіку `midi_converter_v3.py` на
  Kotlin, коли дійде до цього етапу
