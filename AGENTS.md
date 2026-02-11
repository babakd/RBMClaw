# Agent Startup Instructions

1. Read `/Users/babakd/RBMClaw/CODEX.md` first for current project context and status.
2. Do not expose or commit secrets from `/Users/babakd/RBMClaw/.env`.
3. Use repo-root Gradle commands (project is flattened; no nested Android folder).
4. Validate changes with:
   - `./gradlew :app:compileDebugKotlin`
   - `./gradlew :app:testDebugUnitTest`
5. For device debugging, use `adb` and capture relevant `OpenClaw/` logs.

