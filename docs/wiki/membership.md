---
title: Nanny AI Membership
description: How the AI chat tier is unlocked — Permission node, Patreon OAuth, or Subscribestar OAuth. OR-semantics composite.
---

# Nanny AI Membership

The Nanny's AI chat tier is gated behind a `MembershipProvider`. Three providers ship; any combination can be enabled. The composite returns "unlocked" if **any** enabled provider unlocks the player — pure OR semantics.

> 💡 **For step-by-step setup of each provider** (registering OAuth apps, finding tier names, the code-paste link flow) — see [`docs/membership-setup.md`](https://github.com/StoryNook/Accident-Prone-1.21.4/blob/main/docs/membership-setup.md). This page is the architecture + admin reference.

---

## The three providers

| Provider | What it checks | Persistence | Best for |
|---|---|---|---|
| `PermissionMembershipProvider` | `Player.hasPermission(node)` at call time | None (live check) | LuckPerms + DiscordSRV role sync, manual grant, or staff/testing |
| `PatreonMembershipProvider` | Active Patreon membership matching `Campaign_ID` and (optionally) `Tier_Required` | Encrypted refresh token on the player's stats file. Auto-renews on join. | Real patrons of your campaign |
| `SubscribestarMembershipProvider` | Same shape as Patreon | Encrypted refresh token | Subscribestar subscribers |

The class wrapping all three is `CompositeMembershipProvider` — it iterates enabled providers and short-circuits on the first `true`.

## The master switch

```yaml
Nanny:
  Membership:
    enabled: true     # MASTER. When false, ZERO providers register — AI tier locked for everyone.
```

When `enabled: false`, `Plugin.buildMembershipProvider()` returns `AlwaysLockedProvider` and no per-path flags are even read. Flip the master on first, then enable the path(s) you want below.

## Path 1 — Permission node (no OAuth)

The simplest path. Works with LuckPerms, GroupManager, or any permission plugin.

```yaml
Settings_Menu:
  Membership: true            # required only for OAuth paths; harmless if Permission-only
Nanny:
  Membership:
    enabled: true
    Permission:
      enabled: true
      node: "accidentprone.nanny.ai_unlocked"
```

Grant the node in-game:

```
/lp user <player> permission set accidentprone.nanny.ai_unlocked true
```

Revoke at any time — `isUnlocked()` is checked live on each chat call, no token to invalidate.

**With DiscordSRV:** if you sync Discord roles to LuckPerms groups, flipping a Discord role automatically flips the AI unlock. Pair this with a Patreon-linked Discord role and you get effectively the Patreon path without any of the OAuth setup. Many self-hosters do exactly this.

## Path 2 — Patreon OAuth

Players run `/nanny link patreon`, click the chat link, sign in on Patreon, copy a `code|state` string from the redirect helper page, and paste it back into chat with `/nanny link patreon <code>|<state>`. The plugin exchanges the code for a refresh token, stores it encrypted, and from then on auto-renews on every join.

```yaml
Settings_Menu:
  Membership: true            # REQUIRED — gates persistence of the OAuth tokens
Nanny:
  Membership:
    enabled: true
    Allow_Linking: true
    Redirect_URI: "https://accident-prone.io/oauth-redirect"   # or your self-hosted helper
    Patreon:
      enabled: true
      Client_ID: "your-patreon-client-id"
      Client_Secret: "your-patreon-client-secret"     # auto-encrypted to "enc:..." on first save
      Tier_Required: ["Diamond Tier"]                  # empty list = any tier on your campaign
      Campaign_ID: "9315392"                           # YOUR campaign — see below
```

> ⚠️ **`Settings_Menu.Membership: true` is mandatory for the Patreon (or Subscribestar) path.** Without it, the six membership-related `PlayerStats` fields (provider, encrypted email, encrypted refresh token, tier, status, last-check timestamp) are NOT persisted — every server restart, every player rejoin, the link is forgotten and they have to re-paste `code|state`. With it on, the refresh token survives and the plugin auto-renews silently. This is the difference between "indefinite" and "ephemeral" linking.

### Finding your Campaign_ID

Without this, the plugin falls back to legacy behavior: **any active Patreon membership counts** — any patron of *any* creator on Patreon would unlock your AI tier. Fine for a private single-creator server; **not safe for a public release**. Fill this in.

**Step 1** — get your Creator's Access Token from your Patreon developer app:

1. Open <https://www.patreon.com/portal/registration/register-clients>
2. Open your existing OAuth app, or create one if you don't have one yet
3. The detail page has a field labelled **"Creator's Access Token"** — copy that string. (This is *your* OAuth token as the creator; treat it like a password.)

**Step 2** — query the API:

```bash
curl -H "Authorization: Bearer <your-creator-access-token>" \
     "https://www.patreon.com/api/oauth2/v2/campaigns"
```

**Step 3** — read the `data[0].id` field. Example response:

```json
{
  "data": [
    {
      "id": "9315392",
      "type": "campaign",
      "attributes": {}
    }
  ],
  "meta": { "pagination": { "total": 1, "cursors": { "next": null } } }
}
```

The campaign id here is `9315392`. **That number** goes into `Nanny.Membership.Patreon.Campaign_ID` (as a YAML string — keep the quotes).

> 🔒 **Rotate the Creator Access Token after this.** Once you have your Campaign_ID, the token's job is done. Rotate it on the same Patreon portal page if it's been visible in any logs/transcripts. The Campaign_ID itself is not secret.

### Tier names

`Tier_Required` matches against your Patreon tiers' *exact title* (case-sensitive). Find tier titles on your creator dashboard under "Tiers" — copy the title verbatim. Empty list (`Tier_Required: []`) means "any active patron of this campaign unlocks AI."

## Path 3 — Subscribestar OAuth

Same shape as Patreon. Register an OAuth client at <https://www.subscribestar.com/>, configure:

```yaml
Nanny:
  Membership:
    Subscribestar:
      enabled: true
      Client_ID: "your-ss-client-id"
      Client_Secret: "your-ss-client-secret"
      Tier_Required: []
```

If Subscribestar issues only short-lived tokens for your client, players may see a "link expired" message on a future login and have to re-link. This is upstream behavior, not a plugin bug.

## Combining paths

All three `enabled: true` simultaneously is valid and common. Composite returns `true` if any path unlocks the player. Players don't have to choose — they get whichever unlocks them first. Common combinations:

- **Permission + Patreon** — patrons unlock via OAuth, staff unlock via the perm node. Both work.
- **Permission only** — for fully gift-economy servers, or LuckPerms + DiscordSRV chains.
- **Patreon only** — public-facing patron-gated AI.

## The OAuth helper redirect page

`Redirect_URI` points to a static HTML page that catches Patreon/Subscribestar's `?code=&state=` and shows the player a copyable `code|state` string. The plugin ships `docs/security/oauth-redirect.html` — host it on GitHub Pages / Cloudflare Pages / S3 / your existing site, and point the config at it. **You also have to re-pin that URL in the Patreon/Subscribestar developer portal** — providers reject mismatched redirect URIs.

The default `https://accident-prone.io/oauth-redirect` is hosted by the plugin author; self-hosting is recommended for branding and trust.

## Encryption at rest

OAuth client secrets and player refresh tokens are encrypted on disk with AES-256-GCM. The key lives at `<dataFolder>/.crypto.key` (or `Crypto.Key_Path` if overridden). **Lose the key file → all encrypted values become permanently unreadable** — the plugin will refuse to start rather than silently re-encrypt under a new key. See [`docs/security/hardening.md`](https://github.com/StoryNook/Accident-Prone-1.21.4/blob/main/docs/security/hardening.md) for backup advice and threat model.

## Admin commands

- `/nanny link <patreon|subscribestar>` — kick off the OAuth flow for the speaker
- `/nanny link <patreon|subscribestar> <code>|<state>` — paste back from the redirect page
- `/nanny unlink` — drop the stored refresh token + tier info
- `/nanny refresh [player]` — admin-gated; force a re-check against the provider API
- `/diaperreload` — rebuilds the membership provider from current config (no restart needed for config changes)

## Common pitfalls

- **"My linked players forget the link on restart."** You skipped `Settings_Menu.Membership: true`. Flip it on, restart, re-link, you're set.
- **"Any patron unlocks AI on my server."** `Campaign_ID` is blank. Fetch yours per *Finding your Campaign_ID* above.
- **"The OAuth code paste says 'invalid state'."** The state is single-use and 15-min TTL. Run `/nanny link patreon` again to get a fresh state.
- **"AI tier menu item is still locked even with the perm node."** Check `Nanny.Membership.enabled: true` first (the master switch). Then check `Nanny.Membership.Permission.enabled: true`. `Permission.node` must match exactly what you granted in LuckPerms. Run `/lp user <you> info` to inspect.
- **"`/nanny refresh me` returns LAPSED but I'm still a patron."** Patreon may have rate-limited the call or the refresh token may have expired upstream. `/nanny unlink` then re-link.
