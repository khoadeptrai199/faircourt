---
name: spec-lookup
description: Look up what the FairCourt blueprint actually says about a topic (endpoint, table, state, rule, screen, test) before implementing or answering. Quotes section numbers, applies the precedence order, and flags silence or contradictions instead of guessing. Use before any FairCourt implementation decision or when the user asks "what does the spec say about X".
argument-hint: <topic, e.g. "guest cookie", "finish match", "/me response", "seed">
---

# /spec-lookup

Find the blueprint's exact requirements for: **$ARGUMENTS**

Source file: `FairCourt_Project_Blueprint.md` (repository root).

## Steps

1. Search the blueprint for the topic and its synonyms (route path, table name, state name, DTO field, component name). Use Grep with line numbers, then Read the surrounding section.
2. Collect **every** passage that touches the topic — the same thing is often described in Part I, Part II and §0.
3. Apply precedence: **§0 → Part II (§11–17) → Part I (§1–10)**.
4. Compare passages. If they disagree, or a needed detail is missing, mark it.

## Output format

```text
Topic: <topic>

Requirements (quoted or tightly paraphrased, with location):
- §<n> (line <L>): "<text>"
- ...

Effective rule after precedence:
- <one or two lines>

Conflicts:
- <§a says X, §b says Y> — or "none found"

Not specified by the blueprint:
- <detail needed for implementation that the file does not decide> — or "none"

Needs owner decision: yes/no
```

## Rules

- Quote; do not embellish. Never fill a gap with "common practice" and present it as the spec.
- If "Needs owner decision: yes", stop and ask the owner before implementing that part.
- Known open items are listed in `CLAUDE.md` → "Open questions"; mention them when relevant.
