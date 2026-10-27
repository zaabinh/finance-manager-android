# Finance Manager Android — Claude Code guidance

This repository contains the Finance Manager Android application. Keep changes scoped to the user's request; inspect the existing project before changing implementation.

## Design/Figma work
- For tasks involving Figma canvas, FigWright, Figma screens/components/variables or design system, load and follow `.claude/skills/figwright-design/SKILL.md`.
- Do not inspect the whole Figma document unless explicitly required. Start with the selected frame/node and reuse existing components and variables.
- Figma work must not silently change Android source code. Android implementation must not silently change the Figma canvas.
- Never delete or recreate an entire Figma screen without explicit approval.
- Design preferences: professional minimalist, accessible, green accent, English UI labels, Vietnamese explanations when conversing in Vietnamese.
- Keep completed work summaries concise and state any unverified changes.

## Coding
- Preserve existing Java/Kotlin, XML, Gradle, navigation, architecture and naming conventions as found in the repository.
- Keep edits targeted, do not claim tests or builds passed unless actually run.
- Do not add API keys, local router credentials, or private machine settings to Git.
- Use the established feature -> develop PR workflow when applicable.

## Efficiency
- Read only files/nodes necessary for the task; use deterministic commands before allocating a model agent.
- Delegate independent bounded exploration or summarization to inexpensive subagents when available. Do not delegate trivial operations with more overhead than benefit.
- Avoid unnecessary repeated reads, screenshots, long tool output and commentary.
