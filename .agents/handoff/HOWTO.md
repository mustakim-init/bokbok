# Handoff System

When an AI stops mid-task, it writes a handoff file so another AI can pick up without asking the user to re-explain everything.

## When To Write

- You're stopping before the task is fully done
- You're stuck and need another AI to try
- The task is too big for one session

## File Naming

One file per task. Name it something clear:

```
fix-chat-lag.md
add-login-screen.md
refactor-player-module.md
```

## Template

```markdown
# Task: [short name]

**Started by:** [AI name]
**Date:** [date]

## Goal
What was the user asking for?

## What's Done
- Bullet list of completed work

## What's Left
- Bullet list of remaining work

## Stuck On
What blocked you? Any errors, guesses, or half-baked attempts?

## Files Touched
- path/to/file.kt
- path/to/another/file.kt

## Context
Any relevant info the next AI needs to know.
```

## How To Pick Up

1. Read the handoff file
2. Read the files touched
3. Continue from "What's Left"
4. Update the file as you go
5. When done, move it to "completed" or delete it
