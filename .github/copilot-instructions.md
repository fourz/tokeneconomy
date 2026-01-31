# TokenEconomy Copilot Instructions

**Parent Hub**: See Ravenkraft-Dev CLAUDE.md for complete ecosystem standards.

## Tool Discovery

**Server Management**: `mcp_rvnkdev-minec_*` tools (console, files, state, db)
**Live Testing**: `/rvnktest [health|services|db|plugins|run all]`
**Agents**: Browse `.claude/agents/` for specialized workflows
**Skills**: Browse `.claude/skills/` for domain capabilities
**Rules Import**: Use `@import ../../.claude/rules/<rule>.md` for shared directives

## Archon Integration

**Board**: `d4e5f6a7-8901-bcde-f234-567890123456` (TokenEconomy)
**Workflow**: `find_tasks()` → `manage_task("update", status="doing")` → implement → `status="done"`

## Plugin-Specific Standards

### Economy Design
- Fiat currency for vote reward tokens only (not player exchange)
- Server-provided services, not player-to-player trading
- Integration with BarterShops for optional token payments

### Services (via RVNKCore)
- `ITokenService` for token management
- `IEconomyService` for economy operations
- Optional Vault bridge for external economy plugins

### Message Prefixes
- `&c▶` usage | `&6⚙` progress | `&a✓` success | `&c✖` error | `&e⚠` warning

### Logging
Use `LogManager.getInstance(plugin, "ClassName")` from RVNKCore.

## References

- **Architecture Patterns**: `docs/architecture/shared-patterns.md`
- **Coding Standards**: `docs/standard/coding-standards.md`
