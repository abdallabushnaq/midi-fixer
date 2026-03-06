# midi-fixer — Copilot Instructions

## Project Overview

CLI tool to detect and fix overlapping notes in MIDI files.  
Written in **Java 25**, built with **Maven**, packaged as a **GraalVM native image**.

---

## Technology Stack

| Layer        | Technology                    | Version |
|--------------|-------------------------------|---------|
| Language     | Java                          | 25      |
| Build        | Maven                         | 3.x     |
| CLI          | picocli                       | 4.7.6   |
| Logging      | SLF4J Simple                  | 2.0.16  |
| Boilerplate  | Lombok                        | 1.18.42 |
| Testing      | JUnit Jupiter                 | 5.11.4  |
| Native image | GraalVM `native-maven-plugin` | 0.10.3  |

---

## Build Commands

```bash
# Compile + run unit tests (JVM)
mvn verify

# Build fat JAR (JVM)
mvn package

# Build native executable (requires GraalVM JDK 25 as JAVA_HOME)
mvn -Pnative package

# Run GraalVM tracing agent to regenerate native-image config
mvn -Pnative-agent -Dexec.args="input.mid" package exec:exec
```

---

## Module Layout

```
src/
  main/java/org/example/   — application source
  main/resources/
    META-INF/native-image/ — GraalVM reflection / resource config (auto-generated)
    simplelogger.properties
  test/java/org/example/   — JUnit 5 tests
  test/resources/references/ — reference MIDI files for golden-file tests
```

---

## CI Pipeline (GitHub Actions)

Workflow file: `.github/workflows/native-build.yml`

| Trigger                    | Jobs run                        |
|----------------------------|---------------------------------|
| Push / PR to `main`        | `build` (Linux, macOS, Windows) |
| Push of a `v*` tag         | `build` + `release`             |
| Manual `workflow_dispatch` | `build` (Linux, macOS, Windows) |

### `build` job (matrix)

1. Check out repo.
2. Install **GraalVM JDK 25** via `graalvm/setup-graalvm@v1`.
3. Cache `~/.m2/repository` keyed on `pom.xml` hash.
4. `mvn verify` — runs unit tests on the JVM.
5. `mvn -Pnative package -DskipTests` — compiles the native image.
6. Uploads the binary as a workflow artifact (`midi-fixer-linux`, `midi-fixer-macos`, `midi-fixer-windows`).

### `release` job

Runs only on `v*` tags, after all three `build` jobs succeed.  
Downloads the three binaries and creates a GitHub Release with auto-generated release notes.

---

## Native Image Profile

The `native` Maven profile (`mvn -Pnative package`) uses `native-maven-plugin` with:

- `--no-fallback` — fail hard if native compilation is impossible.
- `--initialize-at-build-time=org.slf4j` — SLF4J is safe to initialise at build time.
- `-H:+ReportExceptionStackTraces` — better diagnostics.
- GraalVM community metadata repository enabled.

