# skkaemok

A Skript addon that lets you change player **nametags**, **tablist names**, **chat names**, and **skins** with simple a few simple syntaxes.

## Features
- Change player nametags
- Change tablist and chat display names
- Change player skins
- Supports non‑English languages
- Simple, lightweight syntax

## Requirements
- Minecraft: 1.21+
- Skript: 2.10–2.12 (required)
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

```skript
set skin of %player% to %string%
set skin of %string% to %string%
set skin of %player% to url 
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
