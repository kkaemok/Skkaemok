# skkaemok

[![Modrinth](https://img.shields.io/modrinth/dt/skkaemok?style=for-the-badge&label=Modrinth)](https://modrinth.com/plugin/skkaemok)
[![bStats](https://img.shields.io/bstats/players/30391?style=for-the-badge&label=bStats)](https://bstats.org/plugin/bukkit/Skkaemok/30391)

A Skript addon that lets you change player **nametags**, **tablist names**, and **chat names** with simple a few simple syntaxes.

## Features
- Change player nametags
- Change tablist and chat display names
- Supports non‑English languages
- Simple, lightweight syntax

## Requirements
- Minecraft: 1.21+
- Skript: 2.10–2.12
- ProtocolLib (required)

## Installation
1. Install Skript and ProtocolLib on your server.
2. Drop the `skkaemok` JAR into your `plugins/` folder.
3. Restart the server.

## Usage

### Basic Syntax
```skript
set nametag of %player% to %string%
set nametag of %string% to %string%
reset nametag of %player%
reset nametag of %string%
```
### Example: /nick Command
```skript
command /nick <text>:
    trigger:
        if arg-1 is not set:
            send "Usage: /nick <nickname>" to player
            stop
        if arg-1 is "reset":
            reset nametag of player
            stop
        set nametag of player to arg-1
```


[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skkaemok)
