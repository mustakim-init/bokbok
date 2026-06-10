# Mistake Logging

When you break something, hit a weird bug, or discover a "don't do that" — write it here so no AI ever repeats it.

## When To Write

- You caused a build error that took time to fix
- You discovered a known pitfall in a library or API
- You found a gotcha in the codebase (e.g., "this function crashes if called twice")
- You wasted time on something that could have been avoided

## File Naming

One file per mistake. Name it after what went wrong:

```
compose-recomposition-loop.md
firebase-auth-null-pointer.md
webrtc-crash-on-disconnect.md
```

## Template

```markdown
# [What went wrong]

**Date:** 2026-06-10
**AI:** [your name]
**Related files:** path/to/file.kt

## What Happened
Short description of the mistake or bug.

## Root Cause
Why it happened. Be specific.

## The Fix
What you did to fix it. Include code if relevant.

## How To Avoid
What to check or do differently next time.

## Tags
`compose`, `firebase`, `webrtc`, `build` — whatever helps search
```
