# midi-fixer

CLI tool to detect and fix overlapping notes in MIDI files.

A note "overlaps itself" when its duration extends past the start of the same
pitch's next occurrence on the same channel. This tool shortens the first
note so its NOTE_OFF fires exactly one tick before the next NOTE_ON.

---

## Requirements

| Build target | Requirement |
|---|---|
| Fat JAR (JVM) | JDK 25+ |
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

