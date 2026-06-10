# Skills System

Skills are power-ups. When an AI struggles through something hard, it writes a skill so every other AI gets it easy.

## When To Create A Skill

- A task took >15 minutes of research, internet searching, or trial-and-error
- You had to read documentation, Stack Overflow, or source code to figure something out
- You fixed a tricky bug that required deep knowledge of a library or API
- You set up something complex (Firebase, WebRTC, CI/CD, etc.)

## When To Update A Skill

- You find a better way to do what the skill describes
- You discover an edge case the skill doesn't cover
- The skill's steps no longer work (library updates, etc.)
- You want to add more examples or clarify instructions

## Skill Structure

Each skill lives in its own folder under `.agents/skills/`:

```
.agents/skills/<skill-name>/
├── manifest.json    ← keywords, description, difficulty
└── skill.md         ← the actual guide
```

### manifest.json

```json
{
  "name": "Short skill name",
  "description": "1-2 sentence summary",
  "keywords": ["keyword1", "keyword2", "keyword3"],
  "difficulty": "easy|medium|hard",
  "created_by": "claude|gemini|opencode|...",
  "created_date": "2026-06-10",
  "updated_date": "2026-06-10"
}
```

### skill.md

Write in markdown. Include:

```markdown
# [Skill Name]

## Problem
What does this skill solve? When should an AI use it?

## Steps
1. Step-by-step instructions
2. ...

## Code Examples
```kotlin
// paste working code
```

## Common Pitfalls
Things that can go wrong and how to avoid them.

## Related Files
Which files in the project are relevant?

## Related Skills
Links to other skills that might help.
```

## Index Update

After creating or updating a skill, update `.agents/skills/INDEX.json`:
- Add new skills to the `"skills"` array
- Update `"last_updated"`
