---
title: Install & first run
description: What Accident-Prone is, how to install it, and what to do the first time you log in.
---

## What you're installing

Accident-Prone is a Spigot/Bukkit plugin that adds a bladder/bowel/hydration roleplay loop to Minecraft. Players accumulate bladder and bowel fill over time and have to manage it — by using a toilet, by wearing absorbent gear, or by letting nature take its course. Optional layered systems on top of that: caregivers, hypnosis, cursed binding diapers, integrations with Jobs Reborn / BeautyQuests. **All of the advanced systems are admin-gated** and ship off by default.

The plugin's default mode is a **bathroom simulator** — bladder, bowels, hydration, and toilets — with everything else disabled.

## Server requirements

| Required | Optional softdep |
|---|---|
| **Paper or Spigot 1.21.4** (the only supported server version — pre-1.21.4 is explicitly dropped) | VentureChat (for chat hypnosis triggers) |
| **Java 21** | Citizens (for the Nanny NPC subsystem) |
| | PlaceholderAPI (for `%accidentprone_*%` placeholders) |
| | Jobs Reborn / AdvancedJobs (for Jobs payouts on accident-prone actions) |
| | BeautyQuests (for quest-stage triggers) |

The plugin works without any of the softdeps — they enable additional features when present.

## Install

1. Download `Accident-Prone-<version>.jar` from the latest [GitHub release](https://github.com/StoryNook/Accident-Prone-1.21.4/releases).
2. Drop it into your server's `plugins/` folder.
3. Start the server. The plugin will create its config files in `plugins/Accident-Prone/`.
4. On first connect, accept the bundled resource pack when prompted. The HUD and custom items are unusable without it.

## First run

When you first log into a server with Accident-Prone installed, you'll be given a **Welcome Book**. Read it — it explains the mechanics relevant to the features the admin has turned on.

The two most useful commands to know:

- `/settings` — opens an in-inventory GUI for your personal preferences (HUD style, sound effects, caregivers, incontinence locks, etc.).
- `/stats` — shows your current bladder/bowel/hydration values.

If a feature looks like it should work but the toggle in `/settings` does nothing, it's because the admin has the corresponding global flag off. Server admins should read the [Subsystems overview](/Accident-Prone-1.21.4/wiki/) and check `config.yml` to enable the systems they want.

## Where to go next

- Server admins: [Subsystems overview](/Accident-Prone-1.21.4/wiki/) for what each system does and how it's configured.
- Caregiver / nanny setup: [Nanny NPC system](/Accident-Prone-1.21.4/wiki/nanny/) and [Membership setup](/Accident-Prone-1.21.4/membership-setup/).
- Hardening for a production server: [Security hardening](/Accident-Prone-1.21.4/security/hardening/).
