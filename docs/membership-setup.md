---
title: Membership setup
description: Admin guide for Patreon, Subscribestar, and Permission membership paths.
---

# Membership Setup

The Nanny AI chat tier can be unlocked via three independent paths. Enable one or more.

## Path 1 — Permission node (recommended for LuckPerms + DiscordSRV servers)

```yaml
Settings_Menu:
  Membership: true
Nanny:
  Membership:
    enabled: true
    Allow_Linking: false
    Permission:
      enabled: true
      node: "accidentprone.nanny.ai_unlocked"
    Patreon:
      enabled: false
    Subscribestar:
      enabled: false
```

Grant the perm node via your permission plugin. With DiscordSRV's role-sync the perm
flips automatically when a Discord role syncs from Patreon.

## Path 2 — Direct Patreon OAuth

1. Register a Patreon developer app at https://www.patreon.com/portal/registration/register-clients
2. Set the redirect URI to `https://accident-prone.io/oauth-redirect` (or your
   self-hosted helper page — see below).
3. Copy `Client ID` and `Client Secret` into `config.yml`:

```yaml
Settings_Menu:
  Membership: true
Nanny:
  Membership:
    enabled: true
    Allow_Linking: true
    Patreon:
      enabled: true
      Client_ID: "your-patreon-client-id"
      Client_Secret: "your-patreon-client-secret"
      Tier_Required: []
      Campaign_ID: "your-campaign-id"
```

4. Players run `/nanny link patreon`, click the chat link, sign in, copy the code from
   the redirect page, and run `/nanny link patreon <code>` in chat.
5. The Client_Secret is encrypted in-place on the next save. After first save,
   the file shows `Client_Secret: "enc:..."` — do NOT edit that value by hand.

## Path 3 — Direct Subscribestar OAuth

Same pattern as Patreon. Register a client at https://www.subscribestar.com/, configure:

```yaml
Nanny:
  Membership:
    Subscribestar:
      enabled: true
      Client_ID: "your-ss-client-id"
      Client_Secret: "your-ss-client-secret"
      Tier_Required: []
```

If Subscribestar issues only short-lived tokens for your client, players may see a
"link expired" message on a future login asking them to re-link. This is normal.

## Self-hosting the OAuth helper page

The default `Redirect_URI` points to a static helper page hosted by the plugin author.
If you'd rather host your own (for branding or compliance), copy
`docs/security/oauth-redirect.html` to any static host (GitHub Pages / Cloudflare Pages
/ S3) and point `Nanny.Membership.Redirect_URI` at it. **Update the redirect URI in
your Patreon/Subscribestar OAuth client registration to match** — providers pin
redirect URIs.

## Combining paths

Setting multiple `enabled: true` entries means **any** path that returns "unlocked"
unlocks the AI tier (OR semantics). All-disabled is the default; AI tier stays locked.
