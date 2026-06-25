# How To Use .inspirations/

This folder contains cloned open-source Android apps. They exist so AIs can reference real production code instead of guessing.

## The Decision Tree

```
Task starts
  → does .inspirations/INDEX.json have a repo matching your task?
      YES → REFERENCE MODE
          → find the matching repo
          → study its UI AND backend logic closely
          → copy with 1:1 fidelity where applicable
          → do NOT loosely "adapt" — that creates sloppy code
          → adapt dependencies to bokbok's version catalog
          → show result to user
              → user likes it? ✅ done
              → user says "change" or "I don't like this"?
                  → STOP referencing
                  → switch to CREATIVE MODE
      NO → check if new repos were added
          → list directories in .inspirations/
          → read READMEs of new ones to understand them
          → add new entries to INDEX.json for future AIs
          → if match found → REFERENCE MODE
          → if still no match → CREATIVE MODE

CREATIVE MODE:
  → write original code from scratch
  → do not look at .inspirations/ for reference
  → if user asks for changes → iterate on your own ideas
```

## Search Rules

- Check `.agents/inspirations/INDEX.json` first (fast path)
- Search by `feature_keywords` when you need backend logic (music, vpn, chat, etc.)
- Search by `ui_keywords` when you need UI ideas (expressive, minimal, cute, dark, etc.)
- If INDEX.json exists but has no match → scan directories directly
- If INDEX.json doesn't exist yet → scan directories + build it

## Self-Discovery (For New Repos)

When you find a repo not listed in INDEX.json:
1. Read its README thoroughly
2. If README is unclear, browse its source code to understand what it does
3. Categorize its UI style and feature keywords
4. Add a new entry to `INDEX.json` so all future AIs benefit

## Copy Rules

- Copy code 1:1 when it solves the exact problem — don't rewrite working code
- But adapt dependencies to bokbok's own version catalog (`gradle/libs.versions.toml`)
- Don't import the repo's build system or DI framework — use bokbok's
- Look at architecture, state management, UI patterns, edge case handling
- Credit where appropriate
