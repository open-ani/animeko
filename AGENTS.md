# Repository Guidelines

This is the repository for the app. For the server, you can navigate to ../ani-api-server

Read docs/contributing for project guidelines. Before modifying a subsystem, check docs/contributing/code/ for its documentation (e.g. the media framework docs cover terminology, class-level code maps, and the playback flow) and read the relevant docs first.

Additional requirements:

- You should add imports, instead of using fully qualified names in code.
- For Android Instrumented tests, you can just use `@Test`, no need to write `@RunWith` to the class.
- 注释和文档应直接描述当前设计、职责、行为与约束，不要用“不再…”“改为…”“新设计…”等措辞叙述开发过程，也不要记录未上线方案、被纠正的错误假设或对话历史。只有在解释兼容性或迁移逻辑确有必要时，才说明已发布版本的历史行为。

## UI Verification

- **Prefer reusable interactive screenshot tests** over driving a real window: use `runAniComposeUiTest` (`utils/ui-testing`) with synthetic input (`performClick`, `performTextInput`, `sendKeyEvent`) and `onNodeWithTag(...).assertScreenshot(...)`. They run without OS input — no focus stealing, no real mouse — and stay in the repo as regression tests. When you verify a UI change manually, consider leaving such a test behind.
- Reserve the skills below for what headless tests cannot cover: JCEF, VLC/mpv playback, native libraries, packaging, window chrome, emulator behavior.

## Agent Skills

- For interactive Android UI verification (start emulator, install the app, then tap/swipe/type and verify via screenshots, UI-hierarchy dumps, logcat, and Figma design comparison), use the repo-local skill at `.agents/skills/android-ui-verify/SKILL.md`. Its toolbox script is `.agents/skills/android-ui-verify/scripts/droid.sh` (run `droid.sh help`). `.claude/skills/android-ui-verify` is a symlink to it for Claude Code auto-discovery.
- For desktop/PC executable validation (Compose Desktop, JCEF, VLC/native libraries, packaging, macOS window screenshots), use the repo-local skill at `.agents/skills/desktop-ui-verify/SKILL.md`.

## Generating Client

If you change server API, you can then use `./gradlew generateOpenApiForAnimeko` to automatically re-generate the client. Don't manually write http client.
