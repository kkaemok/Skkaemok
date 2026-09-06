# Skkaemok

Skkaemok is a Java addon for Skript on Paper. It controls player nametags,
tablist names, chat names, and skins, including values that differ for each
viewer.

## Requirements

- Java 21
- Paper 1.21 or later
- Skript 2.9.1 or a compatible later release
- ProtocolLib 5.4.0 or a compatible later release
- LuckPerms and TAB are optional

Install the dependencies, put the Skkaemok JAR in `plugins/`, and restart the
server.

## Name resolution

Nametag, tablist-name, and chat-name getters use the same resolution order:

```text
viewer-specific override
-> global custom value
-> real Minecraft account name
```

The conditions below inspect stored values instead. For example, if Steve has
the global nametag `깨목` and no Alex-specific value, `nametag of Steve for Alex`
returns `깨목`, while `Steve has a custom nametag for Alex` is false.

All player lookup expressions search online players only. If more than one
online player resolves to the requested visible name, the expression returns no
player instead of choosing one arbitrarily. Matching is exact and case-sensitive.

## Changeable name expressions

These expressions return the effective value and accept `set`, `reset`, and
`delete` changers.

```skript
# Nametag
nametag of %player%
nametag of %player% for %player%
nametag of %player% for all players

set nametag of %player% to %string%
set nametag of %player% for %player% to %string%
set nametag of %player% for all players to %string%
reset nametag of %player%
reset nametag of %player% for %player%
reset nametag of %player% for all players

# Tablist name
tablist name of %player%
tablist name of %player% for %player%
tablist name of %player% for all players

set tablist name of %player% to %string%
set tablist name of %player% for %player% to %string%
set tablist name of %player% for all players to %string%
reset tablist name of %player%
reset tablist name of %player% for %player%
reset tablist name of %player% for all players

# Chat name
chat name of %player%
chat name of %player% for %player%
chat name of %player% for all players

set chat name of %player% to %string%
set chat name of %player% for %player% to %string%
set chat name of %player% for all players to %string%
reset chat name of %player%
reset chat name of %player% for %player%
reset chat name of %player% for all players
```

`for all players` is an alias for the global value. The older effect forms with
a `%string%` target remain available for compatibility:

```skript
set nametag of %string% to %string%
reset nametag of %string%
set tablist name of %string% to %string%
reset tablist name of %string%
set chat name of %string% to %string%
reset chat name of %string%
```

## Conditions

Global conditions check only the global custom value. Per-viewer conditions
check only the explicit override for that target/viewer pair.

```skript
%player% has [a] custom nametag
%player% does(n't| not) have [a] custom nametag
%player% has [a] custom nametag for %player%
%player% does(n't| not) have [a] custom nametag for %player%

%player% has [a] custom tablist name
%player% does(n't| not) have [a] custom tablist name
%player% has [a] custom tablist name for %player%
%player% does(n't| not) have [a] custom tablist name for %player%

%player% has [a] custom chat name
%player% does(n't| not) have [a] custom chat name
%player% has [a] custom chat name for %player%
%player% does(n't| not) have [a] custom chat name for %player%

%player% has [a] custom skin
%player% does(n't| not) have [a] custom skin
%player% has [a] custom skin for %player%
%player% does(n't| not) have [a] custom skin for %player%
```

## Lookup and account-name expressions

```skript
player with nametag %string%
player with nametag %string% for %player%
player with tablist name %string%
player with tablist name %string% for %player%
player with chat name %string%
player with chat name %string% for %player%

real name of %player%
original name of %player%
```

The real/original expressions always return `Player#getName()`, independent of
all Skkaemok overrides.

## Skins

```skript
set skin of %player% to %player%
set skin of %player% to %string%
set skin of %player% to url %string%
reset skin of %player%

set skin of %player% for %player% to %player%
set skin of %player% for %player% to %string%
set skin of %player% for %player% to url %string%
reset skin of %player% for %player%
```

The first player is the target and the player after `for` is the viewer.
Username sources use Mojang profile data. URL sources use MineSkin according to
`config.yml`. The legacy `%string%` target forms remain supported globally:

```skript
set skin of %string% to %player%
set skin of %string% to %string%
set skin of %string% to url %string%
reset skin of %string%
```

## Per-viewer packet behavior

Skkaemok resolves the target independently for every online viewer and rebuilds
that viewer's PLAYER_INFO/profile entry. The profile name and texture property
can therefore differ by viewer. The tablist display component is also written
per viewer. Global values continue to use TAB's custom-name API when TAB
integration is enabled; viewer-specific tablist values use PLAYER_INFO because
TAB exposes a global value for this purpose. TAB configurations that continually
replace PLAYER_INFO or scoreboard-team packets can still override Skkaemok's
result; the configured delayed reapply handles normal late updates without a
continuous packet fight.

Per-viewer chat names are applied to outgoing protocol fields that expose the
sender or decorated display component. Skkaemok leaves the signed player message
body unchanged by default, preserving modern signed-chat semantics. Server or
chat plugins that bake a name into an opaque/signed body may therefore retain
their own rendering. The experimental
`output-name-rewrite.rewrite-chat-message-content` option can rewrite exposed
message components when a server explicitly accepts that tradeoff.

## Vanilla scoreboard teams

The real player's main-scoreboard membership remains authoritative and is never
modified. If the server team is `red` with entry `Steve`, it remains exactly
that way after Steve is shown as `깨목`.

Clients identify scoreboard entries by the rewritten profile name. Skkaemok
therefore sends each viewer a packet-only `sm...` team containing that viewer's
effective profile-name entry. It does not register the synthetic team with
Bukkit. Each packet mirrors the real team found with
`mainScoreboard.getEntryTeam(player.getName())` and carries:

- display name, prefix, and suffix
- all 16 vanilla named team colors (or RESET when no source color exists)
- friendly-fire and friendly-invisibility flags
- exact `NAME_TAG_VISIBILITY` status
- exact `COLLISION_RULE` status

Paper's `DEATH_MESSAGE_VISIBILITY` value is captured in the mirrored snapshot
and participates in change detection. It has no field in the clientbound
scoreboard-team packet; the real server team remains authoritative for deciding
which recipients receive death messages.

The named team color is sent in the packet's color field. It colors the central
profile-name section independently from any rich Adventure formatting in the
prefix or suffix.

The default nametag composition is configurable:

```yaml
output:
  nametag: "%team_prefix%%prefix%%nickname%%suffix%%team_suffix%"
```

`%team_prefix%` and `%team_suffix%` are vanilla components, `%prefix%` and
`%suffix%` are LuckPerms metadata, `%nickname%` is the profile-name slot, and
`%original%` is the account name. Existing templates remain valid. A template
without `%nickname%` falls back to the safe default.

For `output.tablist`, the legacy `%prefix%` and `%suffix%` tokens continue to
mean the vanilla team decorations. `%team_prefix%` and `%team_suffix%` are their
explicit aliases, while `%lp_prefix%` and `%lp_suffix%` make LuckPerms metadata
available to custom tablist templates. Whenever the rewritten profile name
differs from the independently resolved tablist name, Skkaemok sends an explicit
colored `UPDATE_DISPLAY_NAME` component so the tablist never falls back to the
nametag/profile name.

Outgoing non-Skkaemok SCOREBOARD_TEAM packets trigger one debounced main-thread
comparison. This catches vanilla `/team` changes and Bukkit/Paper scoreboard API
changes as their packets are sent. A configurable active-player-only snapshot
comparison (20 ticks by default) catches updates hidden by another scoreboard
plugin. It performs no disk I/O and sends no display packets when the team state
has not changed. Join, leave, team movement, option changes, color changes, and
prefix/suffix changes all use the same snapshot path.

## Persistence and compatibility

Existing `nicknames.json`, `tablist-names.json`, `chat-names.json`, and
`skins.json` files remain the global stores and require no migration. New
viewer-specific values are stored by UUID in `viewer-names.json` and
`viewer-skins.json`. Files are loaded into concurrent in-memory indexes; packet
and expression lookups never read from disk.

LuckPerms and TAB remain soft dependencies. LuckPerms prefix/suffix changes
refresh active displays. Skins, Unicode profile names, tablist values, chat-name
rewriting, joins/quits, deaths, advancements, persistence, and the older Skript
effect forms continue through the same service layer.

## Example

```skript
command /nick <text>:
    trigger:
        if arg-1 is not set:
            send "Usage: /nick <name|reset>" to player
            stop
        if arg-1 is "reset":
            reset nametag of player
            stop
        set nametag of player to arg-1
```

[SkriptHub documentation](http://skripthub.net/docs/?addon=skkaemok)
