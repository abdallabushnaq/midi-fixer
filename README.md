# midi-fixer

[![Build Native Images](https://github.com/abdallabushnaq/midi-fixer/actions/workflows/native-build.yml/badge.svg)](https://github.com/abdallabushnaq/midi-fixer/actions/workflows/native-build.yml)
[![GitHub Release](https://img.shields.io/github/v/release/abdallabushnaq/midi-fixer?label=latest%20release)](https://github.com/abdallabushnaq/midi-fixer/releases/latest)

CLI tool to detect and fix overlapping notes in MIDI files.

A note "overlaps itself" when its duration extends past the start of the same
pitch's next occurrence on the same channel. This tool shortens the first
note so its NOTE_OFF fires exactly one tick before the next NOTE_ON.

---

## The Problem — Visualised

MIDI notes on the **same pitch and channel** must not overlap.
Many DAWs and synths behave unpredictably when a second NOTE_ON arrives before
the previous NOTE_OFF fires: the voice may cut out, retrigger, or become stuck.

### Before — overlapping notes ❌

```
Pitch 60  ┤                                                           │
          │  [═══════════ NOTE A ════════════════╪══overlaps══]       │
          │                                      │                    │
          │                                   [══╪═══ NOTE B ══════]  │
          │                                      │                    │
          ├──────────────────────────────────────┼────────────────────┤ ticks →
          0       480      960     1440     1920  ↑  2400     2880
                                             NOTE_B starts here,
                                             but NOTE_A hasn't ended yet
```

### After — fixed ✅

```
Pitch 60  ┤                                                           │
          │  [═══════════ NOTE A ════════════]·  │                    │
          │                                   ↑  │                    │
          │                            NOTE_OFF  │                    │
          │                           moved to   [══ NOTE B ══════]   │
          │                         T_B − 1      │                    │
          ├─────────────────────────────────────────────────────────── ticks →
          0       480      960     1440     1920     2400     2880
```

### Rule applied

```
D_fixed  =  max(1,  T_next  −  T_current  −  1)
                    └──────────────────────┘
                    gap between the two NOTE_ONs
```

> Only notes of the **same pitch on the same channel** are compared.
> Notes on different pitches or channels can freely overlap in time — that is normal polyphony.

---

## Download pre-built binaries

No JVM needed — grab the native executable for your platform from the
[**latest GitHub Release**](https://github.com/abdallabushnaq/midi-fixer/releases/latest):

| Platform | File to download         |
|----------|--------------------------|
| Linux    | `midi-fixer-linux`       |
| macOS    | `midi-fixer-macos`       |
| Windows  | `midi-fixer-windows.exe` |

> **Linux / macOS:** mark the file executable after downloading:
> ```bash
> chmod +x midi-fixer-linux   # or midi-fixer-macos
> ```

Releases are created automatically when a `v*` tag is pushed to `main`.

---

## Requirements

| Build target  | Requirement                                                                        |
|---------------|------------------------------------------------------------------------------------|
| Fat JAR (JVM) | JDK 25+                                                                            |
| Native binary | [Oracle GraalVM for JDK 25](https://www.graalvm.org/downloads/) set as `JAVA_HOME` |

---

## Building

### Fat JAR (runs on any JDK 25+)

```bash
mvn package
java -jar target/midi-fixer-1.0-SNAPSHOT-fat.jar --help
```

### Native executable (no JVM needed at runtime)

Requires GraalVM JDK 25 as `JAVA_HOME`:

```bash
mvn -Pnative package
# produces: target/midi-fixer.exe  (Windows)
#           target/midi-fixer      (Linux / macOS)
```

---

## Usage

```
Usage: midi-fixer [-hnvV] [-o=<output-file>] <input-file>

  <input-file>          MIDI file to process.
  -o, --output=<file>   Output file. If omitted, the input file is overwritten.
  -n, --dry-run         Report fixes without writing any output.
  -v, --verbose         Print details of every fix applied.
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

### Examples

```bash
# Fix overlaps and write to a new file
midi-fixer input.mid -o output.mid

# Fix overlaps in-place (atomic overwrite — safe)
midi-fixer input.mid

# Check what would be fixed, without writing
midi-fixer input.mid --dry-run

# Verbose: show every individual fix
midi-fixer input.mid -o output.mid --verbose
```

---

## Generating GraalVM native-image config (advanced)

If the native build fails due to missing reflection config (most likely from
`javax.sound.midi`), run the tracing agent against a real MIDI file:

```bash
# 1. Build the fat JAR first
mvn package

# 2. Run with the tracing agent (replace input.mid with a real file)
mvn -Pnative-agent -Dexec.args="input.mid --dry-run" exec:exec

# 3. Review the generated files under src/main/resources/META-INF/native-image/
# 4. Commit them, then rebuild the native image
mvn -Pnative package
```

---

## Running tests

```bash
mvn test
```

