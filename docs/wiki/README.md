# Accident-Prone Wiki

Detailed reference docs that don't need to live in `CLAUDE.md`. Open the relevant page when you're working on that subsystem.

## Subsystems

- [Nanny NPC system](nanny.md) — full per-phase component reference (Phases 1–6, all shipped). Includes Citizens2 wiring, care behaviors, navigation, chat engine, discipline tiers, membership integration, and the OAuth/encryption stack.
- [Hypnosis subsystem](hypnosis.md) — Hypnosis Clock crafting, programming, application, triggering, persistence, and config. Includes the related "Cursed" items.
- [Toilet & Warning system](toilet-warnings.md) — intent-driven toilet relief eligibility (`canRelieveOnToilet`), probability-scaled warnings, session-only Plugin maps, `/pee`/`/poop` context branching, config keys, and the `relieveOnToilet` vs `handleAccident` distinction.
- [Rash system](rash.md) — RP accumulation tiers (mess/wetness/underwear), threshold-driven health effects (none/poison/damage/health_reduction), slowness, persistence, and config keys.
- [Design Registry](design-registry.md) — adding new visual design variants (e.g. Goodnite Pull-Up Stars). Auto-detect script + `/add-design` skill — drop PNGs in a folder, run two commands, done.
- [Resource pack — 1.21.4 format](resource-pack-1-21-4.md) — technical reference for the post-migration pack layout: `range_dispatch` items, equipment definitions, and the nested-`select`-on-trim leather_leggings shape.
- [Plugin dependencies](dependencies.md) — Maven dependencies, `plugin.yml` softdepends, and runtime detection logic (VentureChat / Citizens / PlaceholderAPI).
- [Integrations](integrations.md) — Jobs Reborn / AdvancedJobs / BeautyQuests bridge: `AccidentProneActionEvent`, the 14-action catalog, and how to wire jobs/quests on top.

## Other docs in this repo

- `docs/superpowers/specs/` — design specs (read before planning a phase).
- `docs/security/hardening.md` — admin hardening guide for at-rest encryption.
- `docs/security/oauth-redirect.html` — reference static helper page for OAuth code-paste flow.
- `docs/membership-setup.md` — admin setup guide for Patreon / Subscribestar / Permission paths.
