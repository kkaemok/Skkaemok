# Skkaemok

[![GitHub Downloads](https://img.shields.io/github/downloads/kkaemok/skkaemok/total?style=for-the-badge&label=GitHub%20Downloads&logo=github&color=181717)](https://github.com/kkaemok/skkaemok/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/skkaemok?style=for-the-badge&label=Modrinth)](https://modrinth.com/plugin/skkaemok)
[![SpigotMC](https://img.shields.io/spiget/downloads/134313?style=for-the-badge&label=SpigotMC&color=ed8106)](https://www.spigotmc.org/resources/skkaemok.134313/)
[![bStats](https://img.shields.io/bstats/players/30391?style=for-the-badge&label=bStats)](https://bstats.org/plugin/bukkit/Skkaemok/30391)

Skkaemok is a Java addon for Skript on Paper. It controls player nametags,
tablist names, chat names, and skins, including values that differ for each
viewer.

## Requirements

- Java 21 or newer (use the Java version required by your server)
- Paper 1.21.5 or later
- Skript 2.17.1 or a compatible later release
- PacketEvents 2.14.0 or a compatible later release
- LuckPerms and TAB are optional

Install the dependencies, put the Skkaemok JAR in `plugins/`, and restart the
server.

If PacketEvents is missing, Skkaemok downloads the latest stable official Spigot
JAR automatically. Restart the server once after the download finishes. Existing
PacketEvents installations are never replaced. Set
`dependencies.packetevents.auto-download: false` to install it manually.

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

Native Brigadier player, entity, game-profile, and score-holder arguments also
accept nametag and chat-name aliases, including viewer-specific names. Real account
names, UUIDs, selectors, and fake score holders keep their native behavior. Command
completion displays usable nicknames; quote names containing spaces.
This command integration needs source-aware Brigadier parsing (verified on Paper
1.21.8 and 26.2, and Leaf 26.3). Older servers retain native command handling.

## Nicknames and appearances

`nick` changes the nametag, chat name, and tablist name together. It accepts
online and offline players, including viewer-specific overrides.

```skript
set nick of player to "Knight"
set nick of player for {_viewer} to "Stranger"
set nick of player to "Disguised" for 30 seconds
reset nick of player
reset nick of player for {_viewer}
```

Appearance presets capture the raw overrides for all three name channels and
any stored skin texture. Applying a preset validates the complete change before
writing values, then refreshes the player profile once. A missing value restores
inheritance; it does not copy the source player's real account name.

```skript
save appearance of player as preset "knight"
save appearance of player for {_viewer} as preset "stranger"
apply appearance preset "knight" to player
apply appearance preset "stranger" to player for viewer {_viewer} for 30 seconds
reset appearance of player
reset appearance of player for {_viewer}
delete appearance preset "knight"
set {_presets::*} to all appearance presets
```

For a direct batch, supply the names together. The optional skin source must be
an online player with an available skin texture. Without it, the stored skin is
kept. URL and username skins can be resolved first and saved in a preset.

```skript
apply appearance to player with nametag "Knight", chat "Sir Knight", tablist "Knight"
apply appearance to player with nametag "Knight", chat "Sir Knight", tablist "Knight" and skin of {_source}
apply appearance to player for viewer {_viewer} with nametag "Unknown", chat "Stranger", tablist "Unknown" for 20 seconds
```

Temporary changes stack separately for each channel. Expiry is checked once per
second and restores the next
unexpired layer or the previous raw value. A later permanent change takes
ownership of its channel and cannot be undone by an older timer. Expiry also
works while players are offline. Temporary changes end on plugin disable or
server restart; recovery data restores their base values after an interrupted
shutdown.

Presets are stored in `appearance-presets.json`. Existing name and skin files
keep their formats. Offline edits use player UUIDs and take effect on join.

## Change results and events

```skript
set {_status} to last skkaemok change result of player
set {_reason} to last skkaemok change reason of player
set {_status} to last skkaemok change result of player for {_viewer}

on appearance changing:
    if appearance event new nametag is "Blocked":
        cancel event

on nametag changed:
    set {_target} to appearance event target
    set {_viewer} to appearance event viewer
    set {_old} to appearance event old nametag
    set {_new} to appearance event new nametag
    set {_cause} to appearance event cause

on skin applied:
    send "Skin applied." to console

on skin request failed:
    send appearance event reason to console
```

Before-change events (`appearance changing`, `nick changing`, `nametag changing`,
`chat name changing`, `tablist name changing`, and `skin changing`) can cancel the
complete batch. Their corresponding `changed` events run after a successful
change. Expiry and startup recovery restore values without a cancellable event;
they still publish changed events. A change to the same target/viewer scope from
inside its change event is rejected as `busy` to prevent recursive updates.

Event expressions expose `target`, optional `viewer`, `old nametag`, `new nametag`,
`old chat name`, `new chat name`, `old tablist name`, `new tablist name`, and `cause`.
Skin changes also expose old/new `skin source`, `skin texture`, and `skin signature`.
Skin failures additionally expose `reason` and `skin source`. Events run on the
server thread. `skin applied` reports a stored/server-profile change, rather than
an acknowledgment from every viewer's client.

Results include `success`, `unchanged`, `pending`, `invalid_name`, `too_long`,
`reserved_alias`, `ambiguous_alias`, `invalid_skin`, `invalid_duration`,
`unknown_preset`, `cancelled`, `busy`, `closed`, `storage_error`, and `apply_failed`.
Remote skin requests return `pending`; use their events for completion.

```skript
request skin of {_offlinePlayer} from username "Notch"
request skin of player for {_viewer} from url "https://example.com/skin.png"
```

Names reject control/invisible format characters and empty visible text.
Nametags retain the 16-character profile limit. `names.reject-reserved-aliases`
rejects native selector/score-holder aliases by default.
`names.reject-ambiguous-aliases` optionally rejects collisions with another
visible player's account name or alias. Duplicate display names remain allowed
by default, while ambiguous command aliases never resolve.

`/skkaemok inspect <account name or UUID> [viewer account name or UUID]` shows the
effective names, override sources, skin request status, usable aliases, temporary
fields, and last result. It requires `skkaemok.inspect` or `skkaemok.op`. Unknown
offline players can be inspected by UUID without a remote username lookup.

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
sender or decorated display component. Skkaemok leaves signed and unsigned player message
bodies unchanged. Server or chat plugins that bake a name into a message body
may therefore retain their own rendering. The legacy
`output-name-rewrite.rewrite-chat-message-content` option no longer rewrites bodies.

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

[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skkaemok)


