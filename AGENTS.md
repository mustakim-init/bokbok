# AGENTS — Master Rules for All AI Agents

You are an AI agent working on **BokBok v2**, an Android voice chat app with music integration.

## Your Workflow (Follow This Every Time)

```
BEFORE starting any task:
  1. Read this file (AGENTS.md)
 2. Check .agents/handoff/ — is someone mid-task?
 3. Check .agents/mistakes/ — what broke before?
 4. Check .agents/design/ — how should UI look?
 5. Search .agents/knowledge/ — has this been solved?
 6. Search .agents/skills/INDEX.json — is there a skill for this?

DURING the task:
  - Follow the rules in .agents/RULES.md
  - Follow the design in .agents/design/

AFTER finishing (or stopping):
  - If you solved something hard → write to .agents/skills/
  - If you broke something or hit a gotcha → write to .agents/mistakes/
  - If you learned something non-obvious → write to .agents/knowledge/
  - If you stopped mid-task → write to .agents/handoff/
  - If you changed how UI looks → update .agents/design/
  - If you see outdated info ANYWHERE → update it
```

## Core Principles

1. **Search smart.** Skip `.git`, `build`, `.gradle`, `.kotlin`, `.idea`, `cache`, `local.properties`. Don't waste time in irrelevant directories.

2. **Never repeat mistakes.** Every mistake gets logged in `.agents/mistakes/`. Check it before you start. Add to it when you slip up.

3. **UI is consistent.** All UI must match `.agents/design/`. If you create something new, add your choices there. If you change something, update it so all AIs stay in sync.

4. **Pass the baton.** If you can't finish a task, write to `.agents/handoff/` so another AI can pick up exactly where you left off. No need for the user to re-explain.

5. **Skills are power-ups.** If a task takes >15 minutes of research, internet searching, or trial-and-error → write a skill. Next AI gets it in 2 minutes. Skills are living docs — any AI can update them.

6. **Share what you learn.** Non-obvious patterns, architecture decisions, and clever fixes belong in `.agents/knowledge/`.

7. **Everything is editable.** If you see something wrong or outdated in ANY file in `.agents/`, fix it. The system grows with use.

## Project Quick Reference

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Architecture:** Multi-module, MVVM, Hilt DI
- **Key modules:** `:app`, `:innertube` (YouTube Music), `:kizzy` (Discord RPC), `:shazamkit`, feature modules
- **How to run:** Open root project in Android Studio, sync Gradle, run `:app`
