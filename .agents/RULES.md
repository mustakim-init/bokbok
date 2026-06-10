# Rules — Hard And Fast

These rules never change. Every AI must follow them.

## 1. Search Rules

- NEVER search inside these directories: `.git`, `build`, `.gradle`, `.kotlin`, `.idea`, `cache`, `local.properties`
- Use targeted searches — don't glob broad patterns that match thousands of files
- Prefer finding files by name or searching content with keywords over listing directories

## 2. Task Start Rules

- Always read AGENTS.md first
- Always check `.agents/handoff/` for unfinished work
- Always check `.agents/mistakes/` before starting
- Always check `.agents/design/` before creating UI
- Always search `.agents/knowledge/` for relevant context
- Always search `.agents/skills/INDEX.json` for relevant skills

## 3. Writing Rules

- **Mistakes** → write to `.agents/mistakes/` whenever you break something or discover a gotcha
- **Handoffs** → write to `.agents/handoff/` when you stop mid-task, read from it when you start
- **Design** → write to `.agents/design/` whenever you create new UI or change existing UI
- **Knowledge** → write to `.agents/knowledge/` when you learn something non-obvious
- **Skills** → write to `.agents/skills/` when a task takes >15min of research or internet searching

## 4. Consistency Rules

- All UI must match `.agents/design/`. If you break the pattern, fix it.
- Skills are living documents. Any AI can update any skill at any time.
- If you see outdated info anywhere in `.agents/`, update it immediately.
- Do not create new top-level folders or files outside `.agents/` for agent context.

## 5. Code Rules

- Do not commit changes unless the user explicitly asks you to
- Do not add explanatory comments to code unless the user asks you to
- Follow the existing code style in the file you're editing
- Test your changes if possible — check the project README or search for test commands
