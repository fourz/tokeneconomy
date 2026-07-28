# ROADMAP — deprecated (points to sql-memory)

Project status, scope, and durable facts for TokenEconomy now live in **sql-memory** — the
authoritative store. This file is no longer maintained.

> **Standing principle:** roadmap files outside `metamake/` point to sql-memory rather than
> duplicating it. Do not re-add maintained roadmap content here.

## Where the content went

**Status & scope** — sql-memory entity `TokenEconomy` (Plugin):
```bash
python scripts/sql-memory/recall.py --bank ravenkraftdev --entity TokenEconomy
```
(run from the Ravenkraft-Dev repo root)

**Open work** — GitHub Issues:
```bash
gh issue list --repo fourz/Ravenkaft-Dev --label "board:tokeneconomy" --json number,title
```

## Note on scope
TokenEconomy is deliberately in **maintenance mode** — a simple vote-token-reward economy, not a
full economy plugin. The old roadmap's long tail (taxes, multi-currency, banks, Redis, stock
market, REST/GraphQL, mobile) is **aspirational wishlist, not planned work**. That scope decision
is recorded in the `TokenEconomy` entity (2026-07-27) so it is not mistaken for a backlog.
