# Meta-Skill: How The .agents/ System Works

This skill explains the entire shared context system. Read this if you're confused about how AIs work together in this project.

## The Problem This Solves

Multiple AIs (Claude, Gemini, OpenCode, etc.) work on the same codebase but don't share memory. One AI fixes a bug — another AI doesn't know and repeats the same work. One AI starts a task — another can't continue it.

This system fixes that.

## The Directory Structure

```
.agents/
├── RULES.md           ← Hard rules every AI follows
├── handoff/           ← Task handoff (one file per unfinished task)
├── mistakes/          ← Mistake log (one file per screw-up)
├── design/            ← UI design conventions
├── knowledge/         ← Non-obvious learnings and patterns
└── skills/            ← Power-ups for hard problems
    ├── INDEX.json     ← Searchable skill registry
    ├── HOWTO.md       ← How to create/update skills
    └── meta/          ← This skill
```

## The 7-Step Workflow

### Before ANY task:

1. **Read AGENTS.md** — the master rulebook
2. **Check .agents/handoff/** — is someone mid-task? If yes, pick up where they left off
3. **Check .agents/mistakes/** — what went wrong before? Avoid known pitfalls
4. **Check .agents/design/** — how should UI look? Match existing patterns
5. **Search .agents/knowledge/** — has this problem been solved before?
6. **Search .agents/skills/INDEX.json** — is there a skill for this?

### During the task:

- Follow `.agents/RULES.md` strictly
- Follow `.agents/design/` for all UI work
- Don't search `.git`, `build`, `.gradle`, `.kotlin`, `.idea`, `cache`

### After the task:

| What happened | What to write |
|---|---|
| Solved something hard (>15min research) | Write to `.agents/skills/` |
| Broke something or hit a gotcha | Write to `.agents/mistakes/` |
| Learned something non-obvious | Write to `.agents/knowledge/` |
| Stopped mid-task | Write to `.agents/handoff/` |
| Created/changed UI | Write to `.agents/design/` |
| Found outdated info | Update it immediately |

## How Skills Grow

1. First time someone fixes a complex issue → creates a skill
2. Next time, any AI finds the skill in INDEX.json → solves it in minutes
3. Any AI can improve the skill → add steps, edge cases, better examples
4. Skills multiply → the project accumulates expertise over time

## How Handoffs Work

- AI starts task → creates `.agents/handoff/task-name.md` with what's done and what's left
- AI stops → leaves the file for next AI
- Next AI starts → reads the handoff → continues work → updates file
- Task done → remove or archive the handoff file

## How Mistakes Work

- AI screws up → writes what happened, why, and the fix to `.agents/mistakes/`
- All future AIs check before starting → avoid the same mistake
- System gets more robust over time

## How Design Works

- AI creates UI → writes the choices to `.agents/design/`
- AI changes UI → updates `.agents/design/`
- User wants new style → tell one AI, it updates `.agents/design/`, ALL AIs follow

## Key Principle

**Everything is editable by anyone.** If you see outdated info, update it. If a skill is incomplete, improve it. If design is missing something, add it. The system only works if AIs maintain it as they go.
