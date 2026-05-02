# Hardening Guide — Accident-Prone Encryption at Rest

## What's encrypted

The plugin encrypts the following sensitive values with AES-256-GCM:

- `Nanny.Membership.Patreon.Client_Secret` (in `config.yml`)
- `Nanny.Membership.Subscribestar.Client_Secret` (in `config.yml`)
- `Nanny.Chat.AI.API_Key` (in `config.yml`)
- `nanny_membership_email` (per-player YAML)
- `nanny_membership_refresh_token` (per-player YAML)

Encrypted values are prefixed with `enc:` followed by base64. Plaintext values placed
in config.yml are encrypted in-place on the next save.

## Threat model

**Defended:** accidental leaks — backups that exclude hidden dotfiles, configs pasted in
support channels, log files, casual snooping on shared MC hosts.

**Not defended:** an attacker with full filesystem read access; a malicious server admin;
JVM memory dumps; an attacker who explicitly grabs `.crypto.key` alongside the data files.

## Key file

By default the key lives at `<plugin-data-folder>/.crypto.key` (32 random bytes).
Override via `Crypto.Key_Path` in `config.yml`. Supports `${ENV_VAR}` expansion.

```yaml
Crypto:
  Key_Path: "/var/secrets/accidentprone.key"
  # or
  Key_Path: "${ACCIDENTPRONE_KEY_PATH}"
```

Recommended for hardening:

1. Place the key on a different filesystem from the plugin data folder.
2. Restrict permissions: `chmod 600` on POSIX; the plugin attempts this best-effort.
3. Exclude the key file from regular backups; back it up separately to a different location.

## Server migration

When moving the plugin data folder to a new host, copy `.crypto.key` along with everything
else. **Some backup tools skip dotfiles** — verify your tool includes them, or copy the key
explicitly. Without the key, all encrypted data is permanently undecryptable.

## Recovery if the key is lost or compromised

1. Delete `.crypto.key`.
2. Restart the server. The plugin generates a new key.
3. All previously encrypted values are now invalid. Players who linked Patreon/SS must
   re-run `/nanny link`. Admins must re-paste OAuth client secrets and the AI API key
   in `config.yml`.

## Recovery if `.crypto.key` is replaced (existing encrypted data)

The plugin will throw `Decryption failed (key file may have been replaced)` on the first
attempt to read an existing encrypted value. This is intentional — silent re-encryption
under the wrong key would lose data. Fix: restore the original key, or wipe the encrypted
fields (set them to empty strings in YAML) and re-link.
