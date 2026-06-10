# Design Conventions

All UI must look like it was made by one person. This folder is where we keep the rules so every AI produces consistent UI.

## When To Update

- You create a new UI component
- You change an existing UI pattern
- The user asks for a style change
- You see something in the codebase that isn't documented here

## File Naming

One file per design concern. Start with these:

```
colors.md          — color palette
typography.md      — fonts, sizes, weights
spacing.md         — padding, margins, gaps
components.md      — buttons, cards, dialogs, etc.
navigation.md      — screen navigation patterns
animation.md       — transitions, animations
```

## Template For Each File

```markdown
# [Topic]

## Rule
What's the standard?

## Example
Code snippet showing the correct usage.

## Exceptions
When it's okay to break the rule.
```

## How To Stay In Sync

- Always check this folder before writing UI code
- If the existing code doesn't match `.agents/design/`, either update the code OR update the design file — don't leave mismatches
- User says "change the style"? Update here. Now ALL AIs follow the new style.
