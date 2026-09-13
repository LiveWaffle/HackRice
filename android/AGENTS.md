# AGENTS.md

## Project overview
This repository is an Android app built with Kotlin and Gradle. Keep changes aligned with the existing architecture, naming conventions, and dependency patterns used by the project.

## Working conventions
- Prefer existing app modules, packages, and patterns before introducing new abstractions.
- Use Kotlin idiomatic style and Android-safe APIs.
- Keep feature work scoped to the current task and avoid unrelated refactors.
- Preserve backward compatibility unless the task explicitly requires a breaking change.
- Favor small, reviewable edits over broad rewrites.

## Build and verification
- Validate with the smallest relevant Gradle command for the changed behavior.
- Typical checks include:
  - ./gradlew test
  - ./gradlew assembleDebug
  - ./gradlew lint
- Run the narrowest verification command that checks the changed area.

## Code guidance
- Follow package structure under app/src/main/java and keep UI, data, and domain responsibilities separated.
- Prefer repository/service/data-layer patterns already present in the codebase.
- Keep dependencies explicit and avoid unnecessary new libraries.
- Preserve secure handling of API keys and environment-specific values.
- Do not commit generated build artifacts or local-only credentials.

## When modifying Android app files
- Prefer ViewModel, LiveData/Flow, and established Compose or Android UI patterns already used by the project.
- Ensure resource names, manifest entries, and Gradle configuration remain consistent with the existing setup.
- If a change affects network or Supabase integration, verify the corresponding data contract and usage.

## Response expectations
- Explain the root cause before changing behavior.
- Keep implementation focused and remove accidental debug code.
- Summarize the fix and any verification steps used.
