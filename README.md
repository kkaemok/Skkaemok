# skkaemok

[![Modrinth](https://img.shields.io/modrinth/dt/skkaemok?style=for-the-badge&label=Modrinth)](https://modrinth.com/plugin/skkaemok)
[![bStats](https://img.shields.io/bstats/players/30391?style=for-the-badge&label=bStats)](https://bstats.org/plugin/bukkit/Skkaemok/30391)

A Skript addon that lets you change player **nametags**, **tablist names**, and **chat names** with a few simple syntaxes.


## Features
- Change player nametags
- Change tablist and chat display names
- Supports non‑English languages
- Simple, lightweight syntax

## Requirements
- Minecraft: 1.20+, 1.21+, 26.1+
- Skript: 2.10+
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
set skin of %player% to %player% 
set skin of %string% to %player% 
set skin of %player% to %string% 
set skin of %string% to %string% 
set skin of %player% to url %string% 
set skin of %string% to url %string% 
reset skin of %player%  
reset skin of %string% 
```



### Example: /nick Command
```skript
command /nick <text> <text>:
    trigger:
        if arg-1 is "reset":
            reset nametag of player
            stop

        if arg-1 is "set":
            if arg-2 is not set:
                send "Usage: /nick set <nick>" to player
                stop
            set nametag of player to arg-2
            stop

        send "Usage: /nick <set|reset> <nick>" to player
```
### Example: /skin Command
```skript
command /skin <text> [<text>]:
    trigger:
        if arg-1 is "reset":
            reset skin of player
            stop

        if arg-1 is "set":
            if arg-2 is not set:
                send "Usage: /skin set <url|name>" to player
                stop

            if arg-2 starts with "http":
                set skin of player to url arg-2
                stop

            set skin of player to arg-2
            stop

        send "Usage: /skin <set|reset> <url|name>" to player
```
        

[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skkaemok)
