# Membership Setup

The Nanny AI chat tier can be unlocked via three independent paths. Enable one or more.

**`Nanny.Membership.enabled` is the master switch.** When it is `false`, no unlock
path is registered at all — not even the permission node — and the AI tier stays
locked for everyone regardless of the per-path `enabled` flags. Set it to `true`
first, then turn on whichever path(s) you want below. When it is `false`, the
per-path settings are inert, so a permission node only "makes sense" / takes
effect once the master switch is on.

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
2. Set the redirect URI to your OAuth helper page — by default this is
   `https://accident-prone.io/oauth-redirect`, or you can self-host the
   bundled helper (see "Self-hosting the OAuth helper page" below).
   Patreon supports multiple redirect URIs on one OAuth app; if you also use
   the same Client_ID/Secret for a website login flow (e.g. NextAuth's
   `/api/auth/callback/patreon`), add the plugin's redirect URI alongside it.
3. Copy `Client ID` and `Client Secret` into `config.yml`:

```yaml
Settings_Menu:
  Membership: true
Nanny:
  Membership:
    enabled: true
    Allow_Linking: true
    Redirect_URI: "https://your-host.example/oauth/accident-prone"
    Patreon:
      enabled: true
      Client_ID: "your-patreon-client-id"
      Client_Secret: "your-patreon-client-secret"
      Tier_Required: ["Diamond Tier", "Platinum Tier"]
      Campaign_ID: "your-campaign-id"
```

4. Players run `/nanny link patreon`, click the chat link, sign in, copy the
   `/nanny link patreon <code>|<state>` command from the redirect page, and
   paste it into chat.
5. The Client_Secret is encrypted in-place on the next save. After first save,
   the file shows `Client_Secret: "enc:..."` — do NOT edit that value by hand.

### Finding your Campaign_ID

This is the numeric id Patreon uses for *your* campaign. The plugin uses it to
ignore patronage of unrelated creators (otherwise any active patron of any
creator could unlock AI on your server).

The easiest way: get an access token for your own creator account
(authenticate yourself through `/nanny link patreon` on a test server, or use
Patreon's "Creator's Access Token" shown on your app's detail page), then:

```bash
curl -H "Authorization: Bearer <your-creator-access-token>" \
     "https://www.patreon.com/api/oauth2/v2/campaigns"
```

The response's `data[0].id` is your Campaign_ID. Paste it into config.

If you leave Campaign_ID blank, the plugin falls back to legacy behavior: any
active Patreon membership counts. Fine for self-hosted single-creator setups,
**not safe for public release** — anyone who pays any creator on Patreon would
unlock AI on your server.

### Tier names

`Tier_Required` matches against the **tier title** Patreon returns
(case-sensitive). Find your tier titles on your creator dashboard under
"Tiers." Empty list = any tier unlocks (still requires active patronage and a
matching campaign).

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
