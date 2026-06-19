# Best Profile Auth (Best Trainer Auth)

A Minecraft server-side mod that allows multiple persistent player profiles per account, designed especially for shared-computer environments.

Originally built for use at Best Disability Support, this mod enables multiple users to safely share the same Minecraft account or device while maintaining completely separate inventories, locations, and (optionally) mod-specific data such as Cobblemon Pokémon.

---

# Features

- Multiple profiles per server
- Password-protected profile login
- Works across different computers and accounts
- Full inventory, position, and data swapping
- Cobblemon support (party + PC storage)
- Automatic autosave on logout and intervals
- Rotating backup system with retention limits
- Config auto-upgrade system
- Profile-based session system
- Dual command support (`/trainer` and `/profile`)
- Existing-server migration support (`/profile claimcurrent`)
- Safe disconnect handling to prevent data loss

---

# Commands

## Player Commands

- `/profile login <key> <password>`  
  Log into a profile

- `/profile logout`  
  Save and exit current profile

- `/profile whoami`  
  Displays current active profile

- `/profile claimcurrent <key> <password>`  
  Imports your current server progress into a new profile (one-time use)

---

## Admin Commands

- `/profile create <key> <password>`
- `/profile delete <key>`
- `/profile setpassword <key> <password>`
- `/profile enable <key>`
- `/profile disable <key>`
- `/profile list`

---

# How It Works

Each profile stores its own snapshot of player data, including:

- Inventory
- Position
- Health and status
- Cobblemon Pokémon (if installed)

When a player logs in:
1. The current live UUID data is saved
2. The selected profile snapshot is loaded
3. The player is reconnected into that profile

When logging out:
1. The profile snapshot is updated
2. The live UUID is cleared
3. The player is disconnected safely

---

# Existing Server Migration

If this mod is installed on an already-established server, players may already have progress tied to their normal Minecraft UUID save before profiles were introduced.

To import that existing progress into a new profile, use:

`/profile claimcurrent <key> <password>`

This will:
- Copy your current UUID-based data into a new profile
- Mark your account as migrated
- Prevent duplicate imports

This is intended as a **one-time operation per Minecraft account UUID**.

---

# Profile Storage Structure

Profiles are stored under:

```
config/best-trainer-auth/trainers/<profile>/
```

Snapshots now use handler-based storage:

```
snapshot/
  vanilla/
    playerdata.dat
    playerdata.dat_old
  cobblemon/
    cobblemonplayerdata.json
    cobblemonplayerdata.json.old
    party/
    pc/
```

Backups are stored in:

```
backups/<timestamp>/
```

---

# Configuration

Example config:

```json
{
  "adminBypassPermissionLevel": 4,
  "lockMovement": true,
  "lockInteractions": true,
  "lockBlockBreaking": true,
  "lockCombat": true,
  "maxBackupsPerProfile": 10,
  "autosaveIntervalSeconds": 300
}
```

The config file automatically upgrades when new versions are installed.

---

# Safety Features

- Prevents login while already in a profile
- Forces reconnect on profile switch
- Locks player actions until logged in
- Automatic backups before overwrite
- Backup rotation system
- Migration protection (prevents duplicate imports)

---

# Important Warning

Once profiles are in use, player progression may exist primarily inside profile snapshots rather than only in the default UUID save files.

If this mod is removed from a server without first migrating profile data back to standard UUID-based player storage, profile progress may become inaccessible.

For this reason, server owners should treat Best Profile Auth as a data-owning system once deployed.

A future utility or migration tool may be provided to export a selected profile back to a single standard UUID save for transition away from the mod.

---

# Compatibility

## Supported
- Fabric servers
- Cobblemon (full support)

## Planned
- Generic mod handler system
- Additional mod integrations
- Vanilla-only full support mode
- Cross-loader compatibility (future)

---

# Project Origin

This mod was created for:

Best Disability Support  
https://bestdisabilitysupport.com.au

It was designed to allow participants using shared computers to maintain persistent characters in Minecraft.

---

# License

This project is open source.

Attribution is required:

**Best Disability Support**  
https://bestdisabilitysupport.com.au

You are free to use, modify, and distribute this software, provided proper credit is given.

---

# Version

v0.3.0

- Introduced handler-based architecture
- Added existing-server migration system
- Added migration markers
- Improved data safety and structure
- Prepared foundation for future mod support expansion
