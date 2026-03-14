# Best Trainer Auth (Cobblemon Edition)

A Fabric server-side profile login system designed for Cobblemon servers and shared-computer environments.

This mod allows multiple persistent **profiles** to exist independently of the Minecraft account used to connect to the server.

Originally developed for **Best Disability Support** to allow participants using shared computers to maintain their own characters without needing their own dedicated Minecraft accounts.

https://bestdisabilitysupport.com.au

---

# Key Concept

Most Minecraft servers identify players by **UUID**, which ties a player to a specific Minecraft account.

Best Trainer Auth instead treats the Minecraft account as **transport only**.

A player logs in using a **profile key + password**, which loads that profile's saved state.

Each profile has its own:

- Inventory
- Location
- Cobblemon party
- Cobblemon PC storage
- Vanilla player data

This allows persistent characters even when players use different computers or Minecraft accounts.

---

# Features

- Multiple persistent profiles per server
- Profile authentication using passwords
- Automatic swapping of player save data
- Compatible with Cobblemon
- Works behind Velocity proxy setups
- Designed for shared computer environments
- Snapshot-based player data system
- Automatic autosaves
- Rotating per-profile backups
- Backup retention limit
- Supports both `/trainer` and `/profile` commands

---

# Commands

## Player Commands

/profile login `<key>` `<password>`  
/profile logout  
/profile whoami  

## Admin Commands

/profile create `<key>` `<password>`  
/profile delete `<key>`  
/profile setpassword `<key>` `<password>`  
/profile enable `<key>`  
/profile disable `<key>`  
/profile list  

## Legacy Compatibility

The mod also supports legacy `/trainer ...` commands for backward compatibility.

---

# How It Works

Instead of changing player UUIDs, this mod swaps the underlying player data files before login.

When a profile logs in:

1. The profile key is authenticated
2. The player reconnects
3. The server loads the profile's saved snapshot
4. Minecraft loads the player normally using that snapshot

When a profile logs out:

1. Current player data is saved as a profile snapshot
2. The player reconnects
3. They may log in as a different profile

This ensures data isolation between profiles.

---

# Autosave and Backups

Best Trainer Auth automatically protects profile progress.

It supports:

- Save on logout
- Save on disconnect
- Save on server stop
- Timed autosaves
- Rotating backups per profile

By default:

- autosave runs every **5 minutes**
- the last **10 backups per profile** are kept

These settings can be changed in:

`config/best-trainer-auth/config.json`

---

# Intended Use Cases

- Shared computer environments
- Disability support centres
- Schools and training labs
- LAN events
- Cobblemon servers
- Roleplay servers with multiple characters

---

# Requirements

- Minecraft **1.21.1**
- Fabric Loader
- Fabric API
- Cobblemon

---

# Compatibility

Developed and tested with:

- Fabric
- Cobblemon
- Velocity proxy environments

Other modpacks may work but have not been extensively tested.

---

# Building

Run:

```
./gradlew build
```

The compiled mod will appear in:

```
build/libs/
```

---

# Configuration

Example config:

```json
{
  "configVersion": 2,
  "adminBypassPermissionLevel": 4,
  "lockMovement": true,
  "lockInteractions": true,
  "lockBlockBreaking": true,
  "lockCombat": true,
  "autosaveIntervalTicks": 6000,
  "maxBackupsPerProfile": 10
}
```

---

# Known Limitations

- Profile switching currently requires a reconnect
- Designed primarily for Cobblemon environments
- Other mods may need dedicated support handlers in future versions

---

# Credits

Developed by **Best Disability Support**

https://bestdisabilitysupport.com.au

This mod was created to support inclusive gaming environments and shared computer access for participants.

---

# Contributing

Pull requests and improvements are welcome.

If you build improvements or extensions, please ensure attribution to **Best Disability Support** remains intact as required by the license.

---

# License

Licensed under the Apache License 2.0 with an additional attribution requirement.

Attribution to **Best Disability Support (https://bestdisabilitysupport.com.au)** must be preserved in derivative works.

See the LICENSE file for full details.
