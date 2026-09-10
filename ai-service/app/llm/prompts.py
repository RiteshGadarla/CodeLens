import json

from app.rag.retriever import Hit

ASK_TEMPLATE = """You are CodeLens, a code intelligence assistant for a Java repository.
Answer the question using ONLY the graph facts and source excerpts below.
- Graph facts come from deterministic static analysis; treat them as ground truth.
- Cite excerpts as [n]. Never cite anything that is not listed.
- If the context is not enough, say what is missing instead of guessing.
- Be concise: short paragraphs or bullet lists.

## Question
{question}

## Graph facts (JSON)
{facts}

## Source excerpts
{sources}

## Answer
"""

REPORT_TEMPLATE = """You are CodeLens. Write a change-impact report for a developer about to modify `{target}`.
Use ONLY the deterministic impact analysis and source excerpts below. Do not invent components.

Use this structure in markdown:
1. **Summary** - what the component does and the overall risk ({level}, score {score}/100).
2. **What breaks first** - direct dependents and why they depend on it.
3. **Blast radius** - affected APIs, services and modules.
4. **Tests to run** - from the listed tests; call out gaps if there are none.
5. **Safe change plan** - concrete, ordered steps.

Cite excerpts as [n].

## Impact analysis (JSON)
{facts}

## Source excerpts
{sources}

## Report
"""

FACTS_SHARE = 0.35
OVERHEAD = 1500


def render_facts(facts: dict, budget: int) -> str:
    text = json.dumps(facts, separators=(",", ":"), ensure_ascii=False, default=str)
    return text if len(text) <= budget else text[:budget] + "...(truncated)"


def render_sources(hits: list[Hit], budget: int) -> tuple[str, list[Hit]]:
    blocks: list[str] = []
    used: list[Hit] = []
    total = 0
    for h in hits:
        c = h.chunk
        block = f"[{len(used) + 1}] {c.label} ({c.kind}) - {c.path}:{c.start_line}-{c.end_line}\n```java\n{c.text}\n```"
        if total + len(block) > budget:
            if used:
                break
            block = block[:budget]
        blocks.append(block)
        used.append(h)
        total += len(block)
    return "\n\n".join(blocks) or "(no source excerpts indexed)", used


def _budgets(max_chars: int) -> tuple[int, int]:
    usable = max(max_chars - OVERHEAD, 1000)
    facts = int(usable * FACTS_SHARE)
    return facts, usable - facts


def ask_prompt(question: str, facts: dict, hits: list[Hit], max_chars: int) -> tuple[str, list[Hit]]:
    facts_budget, sources_budget = _budgets(max_chars)
    sources, used = render_sources(hits, sources_budget)
    prompt = ASK_TEMPLATE.format(
        question=question.strip(),
        facts=render_facts(facts, facts_budget) if facts else "{}",
        sources=sources,
    )
    return prompt, used


def report_prompt(facts: dict, hits: list[Hit], max_chars: int) -> tuple[str, list[Hit]]:
    facts_budget, sources_budget = _budgets(max_chars)
    sources, used = render_sources(hits, sources_budget)
    target = facts.get("target") or {}
    risk = facts.get("risk") or {}
    prompt = REPORT_TEMPLATE.format(
        target=target.get("label") or target.get("qualifiedName") or "the selected component",
        level=risk.get("level", "UNKNOWN"),
        score=risk.get("score", "?"),
        facts=render_facts(facts, facts_budget),
        sources=sources,
    )
    return prompt, used
