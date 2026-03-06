# Working with Abdalla on Kassandra

## About the Developer

- **30+ years** of Java experience; highly proficient — do not over-explain basic Java or Spring concepts.
- Works on Kassandra **in his free time** as an open-source hobby project. Prefer focused, minimal changes over sweeping
  refactors.
- **AI / LLM tooling is new territory.** When touching the `ai/` package, explain design decisions clearly and flag
  anything non-obvious.

---

## Infrastructure

- you are running in Intellij, use the integrated tools you have to access the codebase. you do nto need to use grep.

## Project Reference

For all build commands, test strategy, module layout, technology versions, and CI pipeline details see
**`.github/copilot-instructions.md`** — do not duplicate that information here.

---

## Coding Standards

### Java

- **Javadoc is required on every `public` method and every `public` field.** Use `/** … */` style. Include `@param`,
  `@return`, and `@throws` tags where applicable.
- Use **Lombok** for boilerplate: `@Getter`, `@Setter`, `@Slf4j`, `@NoArgsConstructor`, `@ToString`,
  `@EqualsAndHashCode`, etc. Do **not** write getters/setters by hand.
- Do **not** add Lombok as a `<dependency>` — it is already declared as an annotation-processor path in `pom.xml`.
- Follow the existing package structure:
    - `dao/` — JPA entities (`*DAO`)
    - `dto/` — API transfer objects (plain class name, no suffix)
    - `repository/` — Spring Data interfaces (`*Repository`)
    - `rest/controller/` — REST controllers (`*Controller`)
    - `service/` — Business logic (`*Service`)
- Every new file must carry the Apache 2.0 licence header matching the style already used in the codebase
  (`Copyright (C) 2025-2026 Abdalla Bushnaq`). The year range must reflect the current year. IntelliJ manages this
  automatically — do not hardcode a stale year.
- All code, comments, Javadoc, log messages, and commit messages must be written in **English**.
- Use `@Slf4j` + `log.debug/info/warn/error` — never `System.out.println`.
- Formatting is enforced by **Spotless / Eclipse formatter**. Run `mvn spotless:apply` before committing. Never submit
  unformatted code.

### Spring

- Inject dependencies with `@Autowired` on fields (consistent with the existing codebase).
- Secure REST endpoints with `@PreAuthorize` expressions; never leave endpoints unauthenticated.
- New REST endpoints belong in `rest/controller/`; their test client stubs go in `rest/api/`.

### Tests

- **Default validation: run `@Tag("UnitTest")` classes only** — fast, no external deps.
  ```
  mvn test -Dtest="ProductApiTest,SprintApiTest,GanttTest,BurndownTest,CalendarTest"
  ```
- Only run the full suite (`mvn test -Dselenium.headless=true`) when the change touches Vaadin views or security.
- New REST API tests extend `AbstractEntityGenerator`, use `@Tag("UnitTest")`, `@WithMockUser`, and in-memory H2.
- Every new test class must truncate state via the `AbstractTestUtil.beforeEach()` mechanism — do not rely on data from
  a previous test.

---

## Collaboration Style

- **Design before coding on large changes.** If a task touches more than one or two classes, introduces a new
  abstraction, or could reasonably produce more than ~50 lines of new/changed code, stop and present a written design
  first — affected classes, proposed API/signatures, and key decisions. Wait for explicit approval before writing any
  code. A few minutes of review prevents hundreds of lines written in the wrong direction.
- **Minimal diffs.** Change only what is needed to fulfil the request; do not reformat unrelated code.
- **No unsolicited refactors.** If you spot something worth improving that is unrelated to the task, mention it as a
  suggestion rather than doing it silently.
- **Ask before adding dependencies.** Adding a new Maven dependency requires explicit approval.
- **Explain AI-related changes.** Anything in the `ai/` package should include a short rationale, since this is a new
  area for the developer.
- When something is ambiguous, make a reasonable choice consistent with the existing code and **note the assumption**
  in a comment or in the response rather than asking a blocking question.
- **One commit per logical change.** Keep commits focused and atomic where possible.
- **Test coverage.** Tests are not required before the feature works and is proven. Once stable, aim for 70–80% coverage
  over time. Do not add tests just to hit a number.
- **TODO comments belong to the developer.** Do not create `// TODO` stubs in generated code. Do not resolve existing
  TODOs unless the current task explicitly closes one — in that case, point it out so the developer can remove it.
- **No partial implementations.** Deliver complete, working code. If a task is too large to complete in one step, say so
  and propose how to split it up — do not silently leave stubs or unfinished methods.





