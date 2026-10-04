# Chrono SMP - Technical Documentation

## Overview
Chrono SMP is a server-side Minecraft time quota system with Fabric mod and Paper plugin builds. Players receive
limited playtime that burns while they're online, with various ways to extend their quota.

## Documentation Maintenance
After implementing code changes, update this document to reflect applicable behavior, architecture, configuration, and
testing changes. Keep it aligned with the implementation rather than leaving changed behavior undocumented.

## Architecture

### Technology Stack
- **Minecraft**: 1.21.11
- **Fabric Loader**: 0.18.2
- **Fabric API**: 0.139.4+1.21.11
- **Paper API**: 1.21.11 (Paper module)
- **Java**: 21 toolchain
- **Fabric Loom**: 1.14-SNAPSHOT
- **Fabric Language Kotlin**: 1.13.9+kotlin.2.3.10
- **Kotlin**: 2.3.10
- **Gradle**: 9.2.1
- **Mappings**: Mojang Official Mappings

### Build System Notes
- Uses Gradle 9.2.1 (required for Loom 1.14+ and MC 1.21.11)
- Mojang mappings instead of Yarn (required for MC 1.21.11 compatibility)
- Kotlin plugin with kotlinx.serialization for JSON persistence
- Shared quota logic is in `core/`; platform entry points and adapters are in `fabric/` and `paper/`
- The Paper build compiles against Paper API and optionally integrates with PlaceholderAPI when it is installed
- Fabric bundles Fabric Permissions API 0.6.1 for named permission checks; permission-manager mods such as LuckPerms remain optional
- The Paper build compiles against Paper API and optionally integrates with PlaceholderAPI when it is installed
- All class references use Mojang mapping names (e.g., `ServerPlayer` not `ServerPlayerEntity`)

## Project Structure

```
core/src/main/kotlin/com/chronosmp/   # Shared business logic, persistence, commands, and platform interfaces
fabric/src/main/kotlin/com/chronosmp/ # Fabric entry point and Fabric platform adapter
fabric/src/main/java/com/chronosmp/   # Fabric mixin for advancement completion
paper/src/main/kotlin/com/chronosmp/  # Paper plugin entry point, PlaceholderAPI expansion, Paper adapter
paper/src/main/resources/plugin.yml   # Paper plugin metadata and /chrono declaration
```


## Architectural Principles

### Separation of Concerns
The project follows a clean architecture with distinct layers:

1. **Platform Layer** (`fabric/`, `paper/`) - Thin adapters translate platform events and APIs to shared operations
2. **Business Logic Layer** (`core/`) - Centralizes quota management rules and platform-neutral command handling
3. **Data Layer** (`data/PlayerTimeData`) - Pure data model with minimal logic
4. **Configuration Layer** (`config/`) - Externalized configuration management

**Key Design Decision**: Platform entry points are thin. They translate Fabric or Bukkit/Paper events into calls to
the shared `ChronoSMP` facade, while core handlers and `PlayerDataManager` own quota rules and persistence.

## Core Components

### 0. ModConfig (Configuration Management)
**File**: `core/src/main/kotlin/com/chronosmp/config/config/ModConfig.kt`

```kotlin
@Serializable
data class ModConfig(
    val initialQuotaSeconds: Long = 8L * 60 * 60,
    val periodicAllotmentSeconds: Long = 2L * 60 * 60,
    val pvpTransferSeconds: Long = 1L * 60 * 60,
    val allotmentPeriodLength: Long = 24L * 60 * 60,
    val advancementTaskSeconds: Long = 15L * 60,
    val advancementGoalSeconds: Long = 30L * 60,
    val advancementChallengeSeconds: Long = 60L * 60,
    val pvpTransferMultiplier: Double = 2.0
)
```

**Parameters**:
- `STARTING_TIME` - New-player quota in seconds (default: 28,800 = 8 hours)
- `RECEIVED_TIME` - Periodic allotment in seconds (default: 7,200 = 2 hours)
- `TIME_RECEIVE_DURATION` - Time between allotments in seconds (default: 86,400 = 1 day)
- `KILL_TRANSFER_AMOUNT` - Base PvP transfer in seconds (default: 3,600 = 1 hour)
- `RUSHHOUR_KILL_MULTIPLIER` - Rush hour PvP transfer multiplier (default: 2.0)
- `RUSHHOUR_GLOWING` - Apply the glowing effect to online players during rush hour (default: `true`)
- `ADVANCEMENT_TASK` - Task advancement reward in seconds (default: 900 = 15 minutes)
- `ADVANCEMENT_GOAL` - Goal advancement reward in seconds (default: 1,800 = 30 minutes)
- `ADVANCEMENT_CHALLENGE` - Challenge advancement reward in seconds (default: 3,600 = 1 hour)

**ModConfigManager**:
- **Location**: Fabric uses `config/chrono-smp/config.yml`; Paper uses `config.yml` in the plugin data folder
- **Auto-creation**: Creates file with defaults if missing
- **Error handling**: Falls back to defaults on invalid YAML
- **Legacy migration**: Imports an existing `config.json` into `config.yml` if YAML is absent; leaves the JSON file intact
- **Format**: YAML block mapping with uppercase keys

### 1. PlayerTimeData (Data Model)
**File**: `src/main/kotlin/com/chronosmp/data/PlayerTimeData.kt`

```kotlin
@Serializable
data class PlayerTimeData(
    @Contextual val uuid: UUID,
    var remainingTimeSeconds: Long,
    @Contextual var lastWeeklyAllotment: Instant,
    var username: String = "",
    val pendingNotifications: MutableList<String> = mutableListOf()
)
```

**Fields**:
- `uuid` — Player unique identifier
- `remainingTimeSeconds` — Current quota in seconds
- `lastWeeklyAllotment` — Timestamp of last allotment (used to calculate eligibility)
- `username` — Minecraft username (updated on join; enables offline player resolution by name)
- `pendingNotifications` — Queue of chat messages from offline operations (e.g., transfers, admin commands while player away); persisted in JSON so messages survive server restarts

**Key Methods**:
- `createNew(uuid, initialQuotaSeconds)` - Initialize new player with configurable quota
- `isEligibleForAllotment(periodLength)` - Check if allotment period has elapsed
- `grantAllotment(allotmentSeconds)` - Add quota and update timestamp
- `addTime(seconds)` - Add quota without updating `lastWeeklyAllotment` (used for advancement rewards)
- `decrementQuota(seconds)` - Reduce quota, return false if depleted
- `transferQuotaTo(other, amount)` - Transfer quota between players
- `formatRemainingTime()` - Format as "HH:MM:SS"
- `hasAwardedAdvancement(id)` - Check if advancement already granted time this session
- `markAdvancementAwarded(id)` - Record advancement as awarded this session
- `clearAwardedAdvancements()` - Clear session set on disconnect (memory cleanup)

**Backward Compatibility**: New fields `username` and `pendingNotifications` have default values. Existing JSON files without these fields load without error (defaults are applied).

### 2. PlayerDataManager (Persistence & Business Logic)
**File**: `src/main/kotlin/com/chronosmp/data/PlayerDataManager.kt`

**Storage**:
- **Location**: `config/chrono-smp/player-data.json`
- **Format**: JSON with UUID keys
- **Caching**: ConcurrentHashMap for thread safety
- **Config**: Holds reference to `ModConfig` for business logic

**Custom Serializers**:
- `UUIDSerializer` - Converts UUID to/from string
- `InstantSerializer` - Converts Instant to/from epoch seconds

**Data Methods**:
- `load()` - Load from disk on server start
- `save()` - Write to disk (auto-save + manual triggers)
- `getOrCreate(uuid)` - Get existing or create new player data (uses config for initial quota)
- `get(uuid)` - Get player data if exists
- `exists(uuid)` - Check if player has data
- `getByUsername(name)` - Find player data by username (case-insensitive); returns null if not found
- `resolvePlayer(name, server)` - Resolve player by name (online or offline)
  - Checks online players first, then falls back to stored username
  - Returns `Pair<ServerPlayer?, PlayerTimeData?>` (both null if not found, only one may be non-null for offline players)

**Business Logic Methods**:
- `checkAndGrantAllotment(uuid)` - Check eligibility and grant allotment if eligible
  - Returns `AllotmentResult.Granted` with new total, or
  - Returns `AllotmentResult.NotEligible` with current total
- `transferQuotaOnPvPKill(victimUuid, killerUuid)` - Handle PvP quota transfer
  - Returns `PvPTransferResult.Success` with transfer details
  - Returns `PvPTransferResult.NoTimeAvailable` if victim has no quota
  - Returns `PvPTransferResult.NoData` if player data missing
- `grantAdvancementTime(uuid, seconds)` - Grant quota for completing an advancement
  - Returns `AdvancementGrantResult.Granted` with formatted duration
  - Returns `AdvancementGrantResult.NoData` if player data missing

**Result Types**:
```kotlin
sealed class AllotmentResult {
    data class Granted(val newTotal: String) : AllotmentResult()
    data class NotEligible(val currentTotal: String) : AllotmentResult()
}

sealed class PvPTransferResult {
    data class Success(
        val transferred: Long,
        val victimRemaining: String,
        val killerRemaining: String
    ) : PvPTransferResult()
    object NoTimeAvailable : PvPTransferResult()
    object NoData : PvPTransferResult()
}

sealed class AdvancementGrantResult {
    data class Granted(val amountFormatted: String) : AdvancementGrantResult()
    object NoData : AdvancementGrantResult()
}
```

**Design Note**: By centralizing business logic here, event handlers remain thin and config-unaware. All quota
calculation rules are encapsulated in one place.

### 3. QuotaTracker (Time Burning)
**File**: `src/main/kotlin/com/chronosmp/systems/QuotaTracker.kt`

**Mechanism**:
- Registers `ServerTickEvents.END_SERVER_TICK`
- Runs every 20 ticks (1 second)
- Decrements quota for all online players
- Disconnects players who run out of quota while online
- Quota burning runs only while the persisted `QuotaSystemState` is started; `/chrono stop` pauses it.

**QuotaSystemState**:
- **File**: `core/src/main/kotlin/com/chronosmp/systems/QuotaSystemState.kt`
- Persists `started` or `stopped` in `quota-system.state` beside `player-data.json`; a missing state file means the SMP has never started.
- `/chrono stop` pauses quota mutations but preserves the state and player data; `/chrono reset` erases both the player-data contents and lifecycle state.

**Kick Message**: "Your time quota has been depleted! Come back next week for more time."

### Paper Plugin
**Files**: `paper/src/main/kotlin/com/chronosmp/PaperChronoPlugin.kt`, `paper/src/main/resources/plugin.yml`

- Plugin metadata declares `ChronoSMP`, API version `1.21`, the `/chrono` command, Bukkit permission nodes with public/operator defaults, and `chrono.admin` children (including reload)
- On enable, initializes shared logic with Bukkit's plugin data folder, loads player data, registers events and the `/chrono` executor/tab completer, and schedules the shared tick handler every server tick
- On disable, saves player data
- `AsyncPlayerPreLoginEvent` calls the shared quota join check only while started. Before start or while stopped, players may join regardless of stored quota.
- `PlayerJoinEvent` and `PlayerQuitEvent` delegate join/disconnect behavior to the shared handlers
- `PlayerDeathEvent` delegates player kills to the shared PvP transfer handler when Bukkit provides a killer
- `PlayerAdvancementDoneEvent` maps TASK, GOAL, and CHALLENGE frames to shared advancement rewards; other frames are ignored
- `/chrono help`, `/chrono reload`, and the lifecycle commands are available through the shared command handler to players and console; lifecycle commands require their admin permissions.
- Paper players are checked through Bukkit named permissions; the shared command handler checks each action node and the `chrono.admin` umbrella
- Console may run every command except `transfer`; bare `/chrono balance` prints the console-specific message, while `/chrono balance <player>` prints the target balance
- Invalid subcommands or malformed arguments display the permission-filtered help output; `plugin.yml` uses `/chrono help` as its fallback usage to avoid listing hidden admin commands
- When PlaceholderAPI is installed, registers the `chrono` expansion. `%chrono_balance%` returns remaining seconds, `%chrono_balance_formatted%` returns `HH:MM:SS`, unknown parameters return null, and a player without stored data returns `00:00:00`
- PlaceholderAPI is optional; without it, the plugin logs a warning and continues without placeholders

Paper stores `config.yml` and `player-data.json` in the plugin data folder supplied to the shared initializer (normally `plugins/ChronoSMP/`).

### 4. RushHourManager (Rush Hour State Management)
**File**: `src/main/kotlin/com/chronosmp/systems/RushHourManager.kt`

**Purpose**: Manages rush hour lifecycle and state without enforcing game mechanics

**State Machine**:
```kotlin
sealed class RushHourState {
    object Inactive : RushHourState()
    data class Active(val endTimeEpochMs: Long) : RushHourState()
}
```

**Thread Safety**: Uses `AtomicReference<RushHourState>` for concurrent access

**Key Methods**:
- `start(durationSeconds: Long)` - Start rush hour, returns end time
- `end()` - Immediately end rush hour
- `isActive()` - Check if rush hour active and not expired (auto-expires if needed)
- `getSafeRemainingSeconds()` - Get remaining seconds with auto-expiration
- `getState()` - Get current state without auto-expiring

**Design Note**: Pure state management - no game logic dependencies. Allows other systems to query and react to rush hour state independently.

### 5. BossbarTracker (Countdown Display)
**File**: `src/main/kotlin/com/chronosmp/systems/BossbarTracker.kt`

**Purpose**: Display rush hour countdown to players with persistent, smooth visual updates via action bar messages

**Mechanism**:
- Registers `ServerTickEvents.END_SERVER_TICK`
- Updates every 20 ticks (1 second) for smooth, real-time countdown
- Applies the glowing effect to all online players every 10 seconds during rush hour when `RUSHHOUR_GLOWING` is enabled
- Broadcasts remaining time to all online players simultaneously
- Shows "▶ Rush Hour: M:SS remaining [Progress Bar]" format with Unicode visual indicators
- Progress bar uses block characters: "▓" (filled) → "░" (empty) as time drains
- Color logic: Yellow (§6) normally → Red (§c) when <5 seconds remain for urgency

**Data Structures**:
- `bossbarUuid` - Identifier for tracking active rush hour countdown (UUID)
- `playerUuidsWithBossbar` - ConcurrentHashMap of UUID → Boolean tracking which players see the countdown
- Thread-safe: Uses ConcurrentHashMap for multi-threaded tick events

**Key Methods**:
- `createBossbar(durationSeconds, server)` - Initialize countdown display, start tick updates
- `update(server)` - Called every 20 ticks; queries RushHourManager state and broadcasts updated message
- `showToPlayer(uuid, server)` - Add player to active countdown display (called on player join)
- `removeFromPlayer(uuid, server)` - Remove player from countdown display (called on player disconnect)
- `removeBossbarForAll(server)` - Clear countdown for all players when rush hour ends

**Behavior**:
- **Inactive**: No tracking, no messages sent
- **Active**: Action bar message broadcasts every 1 second
  - Format: "▶ Rush Hour: 1:23 remaining ▓▓▓▓▓▓▒░░░░"
  - Progress bar width: ~30 characters max, scaled to remaining time
  - Smooth visual drain: Each second removes one block character
- **Late Joins**: Players joining during active rush hour immediately added to display via `PlayerJoinHandler.onJoin()`
- **Color Warning**: Last 5 seconds turn message red (§c) to alert players
- **Auto-cleanup**: Clears display from all players when rush hour ends via `ChronoCommand.executeRushHourEnd()`

**Design Notes**:
- Uses `sendSystemMessage(component, true)` where second parameter targets action bar
- Queries `RushHourManager.getSafeRemainingSeconds()` each tick to auto-expire stale rush hours
- Requires global `ChronoSMP.currentServer` reference to access ServerPlayer entity for broadcasting
- Stateless display logic - recomputes progress bar every tick based on current time
- Thread-safe UUID tracking allows concurrent player join/disconnect during updates

### 6. PlayerJoinHandler (Lifecycle Events)
**File**: `src/main/kotlin/com/chronosmp/events/PlayerJoinHandler.kt`

**Dependencies**: `PlayerDataManager`, `BossbarTracker`, `Logger`, `ChronoSMP` (for global server ref)

**Join Logic**:
1. Check if new player → Grant initial quota (via `dataManager.getOrCreate()`)
2. Existing player → Check allotment eligibility (via `dataManager.checkAndGrantAllotment()`)
3. Pattern match on `AllotmentResult` to send appropriate message
4. Save data immediately
5. Update stored username (via `playerData.username = player.name.string`) — handles name changes
6. Deliver any queued `pendingNotifications` — send each to player as chat message, then clear and save
7. Show countdown display if rush hour active (via `bossbarTracker.showToPlayer(uuid, server)`)
   - Late-joining players immediately added to active countdown display
   - Updates with rest of player population at 1-second intervals

**Disconnect Logic**:
- Remove player from countdown display (via `bossbarTracker.removeFromPlayer(uuid, server)`)
- Clear session advancement set via `playerData.clearAwardedAdvancements()`
- Save player data on disconnect

**Server Reference Pattern**:
- Event handlers extract server via `ChronoSMP.currentServer` (global singleton set on SERVER_STARTED)
- Avoids Java reflection on private `ServerPlayer.server` field
- Null-checked before use (defensive pattern)

**Design Note**: Handler is a thin adapter - it translates Minecraft events to business operations. Coordinates with `BossbarTracker` to ensure joining players see active rush hour and are removed on disconnect. Username updates enable offline player resolution.

### 7. PvPTransferHandler (Combat Transfers)
**File**: `src/main/kotlin/com/chronosmp/events/PvPTransferHandler.kt`

**Dependencies**: `PlayerDataManager`, `RushHourManager`, `ModConfig`, `Logger`

**Event**: `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`

**Transfer Logic**:
1. Verify both entities are ServerPlayer instances
2. Calculate multiplier: 2x from config if rush hour active, else 1.0x
3. Delegate to `dataManager.transferQuotaOnPvPKill(victimUuid, killerUuid, multiplier)`
4. Pattern match on `PvPTransferResult`:
   - `Success` → Notify both players with multiplier indicator, save data
   - `NoTimeAvailable` → Log debug message
   - `NoData` → Log warning
5. All quota calculation done in PlayerDataManager

**Rush Hour Integration**:
- Chat messages include "§b(Rush Hour 2x!)" indicator if active
- Multiplier applied in `PlayerDataManager.transferQuotaOnPvPKill()`
- Logging includes multiplier factor for admin visibility

**Design Note**: Handler queries rush hour state to determine multiplier, then passes to business logic.

### 8. AdvancementHandler (Advancement Rewards)
**File**: `src/main/kotlin/com/chronosmp/events/AdvancementHandler.kt`

**Dependencies**: `PlayerDataManager`, `Logger` (no config dependency)

**Called from**: `ChronoSMP.onAdvancementCompleted()` (bridge from `PlayerAdvancementsMixin`)

**Reward Logic**:
1. Skip advancements without display info (recipe unlocks, silent grants)
2. Skip root advancements (IDs containing `/root`)
3. Check session deduplication via `playerData.hasAwardedAdvancement(id)`
4. Map `AdvancementType` → config seconds:
   - `TASK` → `config.advancementTaskSeconds` (default: 15 min)
   - `GOAL` → `config.advancementGoalSeconds` (default: 30 min)
   - `CHALLENGE` → `config.advancementChallengeSeconds` (default: 1 hour)
5. Delegate to `dataManager.grantAdvancementTime(uuid, seconds)`
6. On `Granted` → mark advancement awarded, send chat message, save
7. On `NoData` → log warning

### 7. PlayerAdvancementsMixin (Mixin)
**File**: `src/main/java/com/chronosmp/mixin/PlayerAdvancementsMixin.java`
**Config**: `src/main/resources/chrono-smp.mixins.json`

**Target**: `net.minecraft.server.PlayerAdvancements`

**Injection**: `@Inject(method = "award", at = @At("RETURN"))`

**Logic**:
- `award()` is called per criterion; only fires when `cir.returnValue == true` (criterion newly granted)
- Checks `getOrStartProgress(advancement).isDone()` — advancement fully completed
- Skips advancements without display info (`advancement.value().display().isEmpty`)
- Calls `ChronoSMP.INSTANCE.onAdvancementCompleted(player, advancement)`

**Design Note**: Written in Java (not Kotlin) to avoid annotation processing edge cases with Mixin.

### 9. ChronoCommand (Player Commands)
### 9. ChronoCommand (Shared Commands)
**File**: `core/src/main/kotlin/com/chronosmp/commands/commands/ChronoCommand.kt`

**Dependencies**: `PlayerDataManager`, `RushHourManager`, `BossbarTracker`, `ModConfig`, `QuotaSystemState`, `Logger`

**Tab Completion**: Built-in suggestion provider lists both online and offline players (by stored username)

**Commands**:
- `/chrono start` - OP/admin-only; persistently starts quota gameplay and initializes any players already online
- `/chrono stop` - OP/admin-only; pauses depletion, rewards, and quota transfers while preserving player data
- `/chrono reset` - OP/admin-only; stops quota gameplay and wipes player data from `player-data.json`
- `/chrono balance` - Show own remaining quota
- `/chrono balance <player>` - Show another player's remaining quota (supports offline by name, tab-complete)
- `/chrono list [all|online]` - List players' quotas alphabetically; defaults to `all`. Offline players display by stored username (or a UUID prefix); 0-quota entries are red (§c)
- `/chrono transfer <player> <amount>` - Transfer quota to another player (no self-transfer; requires sufficient quota). Bare numbers mean minutes; suffix `s`, `m`, `h`, or `d` selects seconds, minutes, hours, or days (for example, `1h` or `1s`).
  - **Offline Support**: Works with offline players by name; queues notification in `pendingNotifications` for next login
  - **Revive Detection**: If target had 0 quota (was revived), sender sees revive confirmation message and target receives revive notification on next login
  - **Notification Format**: "§aWhile you were offline, §e{sender}§a transferred §e{X min}§a to you!"
- `/chrono help` - Show short descriptions only for commands the sender currently has permission to use. Public users see balance, list, transfer, and help; permitted admin actions appear individually based on their effective permissions.

**Admin Commands** (operator-default permissions):
  - Requires `chrono.use.add`; defaults to operators
  - Requires `chrono.use.remove`; defaults to operators
  - Requires `chrono.use.set`; defaults to operators
  - Requires `chrono.use.rushhour.start`; defaults to operators
  - Requires `chrono.use.reload`; defaults to operators
- `/chrono add <player> <amount>` - Grant time to an online or stored offline player; bare numbers mean minutes, with `s`, `m`, `h`, and `d` suffixes available
  - Example: `/chrono add Steve 60` grants 60 minutes to Steve
  - Notifies online targets immediately; queues a notification for offline targets
  - Shows before/after quota totals
  - Offline grants can revive a depleted player
  - Operator verification required

- `/chrono remove <player> <amount>` - Remove time from an online or stored offline player; bare numbers mean minutes, with `s`, `m`, `h`, and `d` suffixes available
  - Example: `/chrono remove Steve 30` removes 30 minutes from Steve
  - Validates player has sufficient quota before removal
  - Shows before/after quota totals
  - Notifies online targets immediately; queues a notification for offline targets
  - Operator verification required

- `/chrono set <player> <amount>` - Set an online or stored offline player's quota; accepts zero or greater, with bare numbers in minutes and `s`, `m`, `h`, or `d` suffixes
  - Notifies online targets immediately; queues a notification for offline targets
  - Operator verification required

- `/chrono rushhour start <minutes>` - Start a rush hour event
  - Broadcasts fullscreen title and subtitle to all players
  - Activates countdown display: "▶ Rush Hour: M:SS remaining [Progress Bar]" via action bar (1-second updates)
  - Parameters: duration in minutes (minimum 1)
  - Players' quota doesn't burn during this time
  - PvP transfers are multiplied by `config.pvpTransferMultiplier` (default: 2.0)
  - All online players see persistent countdown with smooth visual progress drain
  - Late-joining players added to active countdown display automatically
  - Replaces any existing rush hour
  - **Permission Check**: Returns an error if the sender is not an operator

- `/chrono rushhour end` - Immediately end the current rush hour
  - Broadcasts end message to all players
  - Clears countdown display for all players
  - Quota burning resumes for all players
  - **Permission Check**: Returns an error if the sender is not an operator
  - Requires `chrono.use.rushhour.end`; defaults to operators
- `/chrono reload` - Reload `config.yml` without restarting the server/plugin/mod and apply the new settings to live quota, PvP, advancement, command, and glowing behavior. Invalid YAML follows the config manager's existing fallback-to-defaults behavior.

Before the first `/chrono start`, `/chrono list`, `/chrono balance`, and `/chrono transfer` report `The SMP didn't start yet.` While stopped, balance/list remain available but all quota changes (including join allotments, advancement rewards, PvP, transfers, admin add/remove/set, and playtime depletion) are paused. The lifecycle state persists across restarts.

**Permission defaults**: `chrono.use.balance`, `chrono.use.list`, `chrono.use.transfer`, and `chrono.use.help` default to everyone. Lifecycle and other admin action nodes default to operators. `chrono.admin` and the `chrono.admins` alias default to operators and grant only admin action nodes; they do not grant public nodes. Fabric uses Fabric Permissions API fallback defaults when no compatible permission manager is installed, so operator senders receive admin help entries and regular senders receive only public entries.

**Platform behavior**: Paper and Fabric expose the full command set and accept console senders for balance-by-name, list, admin quota actions, and rush-hour actions. Console cannot transfer quota; bare console balance prints `The console doesn't have a balance, add a players username to check their time`. Tab completion includes online and stored offline player names where applicable.

**Console constraints**: Paper and Fabric allow console execution for help, reload, balance-by-name, list, admin quota actions, and rush-hour actions. Console cannot transfer quota; bare balance prints `The console doesn't have a balance, add a players username to check their time`. Paper tab completion offers online and stored offline names for player arguments, `all|online` for list scope, and `start|end` for rush hour.

### 10. ChronoSMP (Main Entry)
**File**: `src/main/kotlin/com/chronosmp/ChronoSMP.kt`

**Global State**:
- `var currentServer: MinecraftServer? = null` - Singleton reference to active server
  - Set on `SERVER_STARTED` event
  - Cleared on `SERVER_STOPPING` event
  - Accessed by event handlers that need to broadcast action bar messages without storing server reference
  - Enables `PlayerJoinHandler`, `PlayerJoinHandler`, and `BossbarTracker` to access ServerPlayer entities

**Initialization Order**:
1. Load configuration from `config/chrono-smp/config.yml`
2. Create PlayerDataManager with config reference
3. Initialize RushHourManager and BossbarTracker
4. Initialize all system components with dependencies
5. Register server lifecycle events
6. Register all component events
7. Setup auto-save (every 5 minutes)

**Lifecycle Hooks**:
- `SERVER_STARTED` → Set `currentServer`, load data
- `SERVER_STOPPING` → Save data, clear `currentServer`
- `END_SERVER_TICK` → Auto-save timer, RushHourManager ticker, BossbarTracker ticker

**Mixin Bridge**: `onAdvancementCompleted(player, advancement)` — called by `PlayerAdvancementsMixin`, delegates to `AdvancementHandler`

**Design Notes**: 
- Config is loaded first and passed to all components that need it
- RushHourManager and BossbarTracker are initialized early and passed to dependent systems
- Global server reference allows event handlers to broadcast action bar messages without parameter threading
- Thread safety: All system references initialized before thread-sensitive events fire

## Data Persistence

### Save Triggers
1. **Auto-save**: Every 5 minutes (6000 ticks)
2. **Player disconnect**: Immediate save
3. **Server shutdown**: Immediate save
4. **Quota changes**: After join bonuses, PvP transfers

### JSON Format Example
```json
{
  "550e8400-e29b-41d4-a716-446655440000": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "remainingTimeSeconds": 25200,
    "lastWeeklyAllotment": 1710374400,
    "username": "Steve",
    "pendingNotifications": [
      "§aWhile you were offline, §eAlice§a transferred §e1h§a to you!"
    ]
  }
}
```

## Game Mechanics

### Time Burn Rate
- **Rate**: 1 second of quota per 1 second of playtime
- **Applies to**: Online players only
- **Tick frequency**: Every 20 game ticks (1 real-world second)
- **Rush Hour Override**: Quota does NOT burn during active rush hour

### Periodic Allotment
- **Amount**: Configurable via `RECEIVED_TIME` (default: 2 hours)
- **Frequency**: Configurable via `TIME_RECEIVE_DURATION` (default: 1 day)
- **Trigger**: Player login
- **Condition**: `Instant.now() - lastWeeklyAllotment >= allotmentPeriodLength`

### PvP Transfers
- **Trigger**: Player kills another player
- **Base Amount**: Configurable via `pvpTransferSeconds` (default: 1 hour)
- **Direction**: Victim → Killer
- **Minimum**: Transfers available quota (can be less if victim has insufficient time)
- **Rush Hour Multiplier**: During rush hour, transfer amount is multiplied by `pvpTransferMultiplier` (default: 2.0x)
  - Example: Base 1 hour × 2.0 multiplier = 2 hours transferred during rush hour

### Rush Hour
- **Trigger**: Admin command `/chrono rushhour start <minutes>`
- **Duration**: Configurable per activation (minimum 1 minute)
- **Effects**: 
  - Players' quota doesn't burn (frozen in time)
  - PvP transfers are multiplied by `config.pvpTransferMultiplier`
  - All online players see countdown via action bar
  - New players joining see rush hour notification
- **Replacement**: Starting new rush hour ends any existing one
- **State**: Session-scoped (not persisted across restarts)

### Quota Depletion
- **While online**: Player is disconnected when their quota reaches 0
- **On Paper login**: Players with 0 quota are denied before joining unless a periodic allotment is due
- **Message**: "Your time quota has been depleted! Come back later."
- **Prevention**: Players without quota cannot rejoin until they become eligible for an allotment or receive more time

## API Usage

### Fabric Events Used
- `ServerLifecycleEvents.SERVER_STARTED`
- `ServerLifecycleEvents.SERVER_STOPPING`
- `ServerTickEvents.END_SERVER_TICK`
- `ServerPlayConnectionEvents.JOIN`
- `ServerPlayConnectionEvents.DISCONNECT`
- `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`
- Advancement events via Mixin (`PlayerAdvancements.award`)

### Paper Events Used
- `AsyncPlayerPreLoginEvent` - Denies login for existing players with depleted quota when no allotment is due
- `PlayerJoinEvent` - Runs shared join logic, including initial quota or a due periodic allotment
- `PlayerQuitEvent` - Runs shared disconnect handling and persistence
- `PlayerDeathEvent` - Delegates player kills to the shared PvP transfer handler when a killer is available
- `PlayerAdvancementDoneEvent` - Delegates TASK, GOAL, and CHALLENGE advancement rewards

### Minecraft APIs Used
- `ServerPlayer` - Player entity operations
- `Component` - Text/chat messages (Mojang mappings)
- `PlayerAdvancements` / `AdvancementHolder` / `AdvancementType` - Advancement system

## Mojang Mappings Migration

### Key Class Name Changes (from Yarn)
| Yarn Name | Mojang Name |
|-----------|-------------|
| `ServerPlayerEntity` | `ServerPlayer` |
| `Text` | `Component` |
| `PlayerManager` | `PlayerList` |
| `ServerWorld` | `ServerLevel` |

### Method Changes
- `player.sendMessage(text, actionBar)` → `player.sendSystemMessage(component)`
- `player.networkHandler` → `player.connection`
- `server.playerManager` → `server.playerList`
- `playerList.playerList` → `playerList.players`

## Building

### Commands
```bash
# Build mod
./gradlew build

# Build Paper plugin
./gradlew :paper:build

# Clean build
./gradlew clean build

# Run development server
./gradlew runServer
```

### Output
- **Fabric JAR**: `fabric/build/libs/chrono-smp-<version>.jar`
- **Paper JAR**: `paper/build/libs/chronosmp-paper-<version>.jar`
- The Paper artifact is built with `shadowJar`; Paper API and PlaceholderAPI are compile-only dependencies, and PlaceholderAPI remains optional at runtime

## Testing Checklist

- [ ] New player joins → Receives initial quota (default: 8 hours)
- [ ] Player plays for 1 hour → Quota decreases by 1 hour
- [ ] Player rejoins after allotment period → Receives periodic allotment
- [ ] Player A kills Player B → Configured amount transferred
- [ ] Player uses `/chrono transfer` → Quota transferred voluntarily
- [ ] `/chrono balance` → Shows own remaining time
- [ ] `/chrono balance <player>` → Shows target player's remaining time
- [ ] `/chrono list` → Lists all players sorted alphabetically
- [ ] Player completes task advancement → +15 min granted, chat message shown
- [ ] Player completes goal advancement → +30 min granted, chat message shown
- [ ] Player completes challenge advancement → +1 hour granted, chat message shown
- [ ] Player completes advancement with multiple criteria → time granted only once
- [ ] Player completes root advancement (e.g. story/root) → no time granted
- [ ] Player unlocks recipe → no time granted, no message
- [ ] Player quota reaches 0 while online → Disconnected from server
- [ ] Paper player with 0 quota and no allotment due attempts to join → Login denied before joining
- [ ] Paper player with 0 quota and an allotment due attempts to join → Login allowed and allotment granted
- [ ] Paper plugin starts with PlaceholderAPI absent → Plugin loads and logs that placeholders are unavailable
- [ ] Paper with PlaceholderAPI installed → `%chrono_balance%` returns seconds and `%chrono_balance_formatted%` returns `HH:MM:SS`
- [ ] Paper `/chrono` command from console → Rejected with player-only message
- [ ] Paper and Fabric console can run balance-by-name, list, admin quota actions, and rush-hour actions; transfer is rejected
- [ ] Paper and Fabric public permission nodes (`balance`, `list`, `transfer`, `help`) default to everyone; admin nodes including `reload` and `chrono.admin` default to operators
- [ ] `chrono.admin` grants admin action nodes only; public nodes remain independently permission-checked and individual grants and denials are honored
- [ ] `/chrono help` shows only commands allowed by the sender's effective permissions; regular players see balance, list, transfer, help
- [ ] `/chrono reload` is operator-only and rereads config.yml, applying updated allotment, PvP multiplier, and glowing settings without restart
- [ ] Before `/chrono start`, all quota interactions are paused and player balance/list/transfer show the pre-start error
- [ ] `/chrono start`, `/chrono stop`, and `/chrono reset` are OP/`chrono.admin`/`chrono.admins` only
- [ ] `/chrono stop` pauses all quota changes without deleting player records; `/chrono start` resumes them
- [ ] `/chrono reset` stops the system and empties player-data.json; started state survives server restart
- [ ] Invalid root command or malformed arguments (for example `/chrono money`) show permission-filtered help
- [ ] Fabric command tree includes start, stop, reset, balance, list, transfer, add, remove, set, rushhour start/end, reload, and help
- [ ] Paper player death with a player killer → Shared PvP transfer behavior runs
- [ ] Paper advancement completion → TASK, GOAL, and CHALLENGE rewards are dispatched; other frames are ignored
- [ ] Server restart → Data persists
- [ ] Multiple players online → All quotas burn correctly
- [ ] Config file created on first run with defaults (including advancement & rush hour fields)
- [ ] Config changes applied after server restart
- [ ] **Offline Players: `/chrono balance <offline-player>`** → Shows stored quota for offline player by name
- [ ] **Offline Players: `/chrono list`** → Offline players show by stored username (not UUID); 0-quota entries in red
- [ ] **Offline Players: `/chrono transfer <offline-player> 10`** → Transfers 10 min; target receives notification on login
- [ ] **Offline Players: `/chrono transfer <depleted-offline-player> 10`** → Revive case: sender sees "revived" message; target receives revive notification on login
- [ ] **Admin Add: `/chrono add <online-player> 60` (OP)** → Grants time; shows before/after totals
- [ ] **Admin Remove: `/chrono remove <online-player> 30` (OP)** → Removes time; checks sufficient quota exists
- [ ] **Offline Players: Tab-complete** → Shows both online and offline player names
- [ ] **Offline Players: Case-insensitive** → `/chrono transfer STEVE 5` resolves against stored username "Steve"
- [ ] **Offline Players: Server restart** → Pending notifications survive restarts (persisted in JSON)
- [ ] **Offline Players: Notification delivery** → Offline messages shown at top of join sequence, before allotment/welcome
- [ ] **Rush Hour: Admin runs `/chrono rushhour start 5`** → Fullscreen title "Rush Hour!" shown to all players
- [ ] **Rush Hour: Countdown visible** → Action bar shows "▶ Rush Hour: 4:XX remaining ▓▓▓▓▓▒░░░░" format
- [ ] **Rush Hour: Updates every 1 second** → Countdown updates smoothly without 10-second gaps
- [ ] **Rush Hour: Progress bar drains** → Unicode block characters visually decrease as time passes (▓→░)
- [ ] **Rush Hour: Color urgency** → Progress bar yellow (§6) normally, turns red (§c) in final 5 seconds
- [ ] **Rush Hour: Quota doesn't burn** → Player plays 2 minutes, quota unchanged
- [ ] **Rush Hour: PvP transfers multiplied** → Transfer is 2x config value (default 2h instead of 1h)
- [ ] **Rush Hour: Late join shows countdown** → Player joining during rush hour sees countdown immediately
- [ ] **Rush Hour: Disconnect clears display** → Player doesn't see countdown after disconnecting
- [ ] **Rush Hour: `/chrono rushhour end`** → Happy hour ends, action bar clears, quota burn resumes
- [ ] **Rush Hour: New start replaces** → Start arbitrary rush hour, then start new one → new duration replaces old
- [ ] **Rush Hour: Config multiplier** → Change `pvpTransferMultiplier: 3.0`, restart, verify 3x transfers during rush hour
- [ ] **Backward Compatibility** → Existing JSON without `username` or `pendingNotifications` fields loads without error

## Configuration

The mod supports YAML-based configuration. Fabric stores it at `config/chrono-smp/config.yml`; Paper stores it as
`config.yml` in the plugin data folder.

### Configuration File
```yaml
STARTING_TIME: 28800
RECEIVED_TIME: 7200
TIME_RECEIVE_DURATION: 86400
KILL_TRANSFER_AMOUNT: 3600
RUSHHOUR_KILL_MULTIPLIER: 2.0
RUSHHOUR_GLOWING: true
ADVANCEMENT_TASK: 900
ADVANCEMENT_GOAL: 1800
ADVANCEMENT_CHALLENGE: 3600
```

### Configuration Options
- **STARTING_TIME**: New-player quota in seconds (default: 28,800 = 8 hours)
- **RECEIVED_TIME**: Periodic allotment in seconds (default: 7,200 = 2 hours)
- **TIME_RECEIVE_DURATION**: Seconds between allotments (default: 86,400 = 1 day)
- **KILL_TRANSFER_AMOUNT**: PvP transfer quota in seconds (default: 3,600 = 1 hour)
- **RUSHHOUR_KILL_MULTIPLIER**: Rush hour PvP multiplier (default: 2.0)
- **RUSHHOUR_GLOWING**: Apply the glowing effect to online players during rush hour (default: `true`)
- **ADVANCEMENT_TASK**: Task advancement reward in seconds (default: 900 = 15 minutes)
- **ADVANCEMENT_GOAL**: Goal advancement reward in seconds (default: 1,800 = 30 minutes)
- **ADVANCEMENT_CHALLENGE**: Challenge advancement reward in seconds (default: 3,600 = 1 hour)

### Behavior
- **Auto-creation**: File created with defaults if missing on first run
- **Error handling**: Falls back to defaults if YAML is invalid
- **Legacy migration**: If `config.yml` is absent and `config.json` exists, imports its values and creates `config.yml`; the old file is not deleted
- **Hot-reload**: Available through operator-only `/chrono reload`; changed config values apply to active runtime components without restarting
- **Logging**: Loaded values are logged on startup

### Customization Example
For a competitive server with daily allotments:
```yaml
STARTING_TIME: 14400
RECEIVED_TIME: 7200
TIME_RECEIVE_DURATION: 86400
KILL_TRANSFER_AMOUNT: 1800
RUSHHOUR_KILL_MULTIPLIER: 2.0
RUSHHOUR_GLOWING: false
ADVANCEMENT_TASK: 300
- [ ] **Rush Hour: Glowing toggle** → `RUSHHOUR_GLOWING: false` disables glow effects; `true` enables them during rush hour
ADVANCEMENT_GOAL: 600
ADVANCEMENT_CHALLENGE: 1800
```
This gives new players 4 hours, daily allotments of 2 hours, 30-minute PvP transfers, and 5/10/30-minute advancement rewards.

## Known Limitations

1. **Time precision**: 1-second granularity (may lose <1 sec on crashes)
2. **No grace period**: Instant kick when quota depleted
3. **Single quota pool**: No separate "bonus time" tracking
4. **No hot-reload**: Config changes require server restart
5. **Advancement deduplication is session-scoped**: Restarting the server resets the session set (safe — MC won't re-fire already-completed advancements)

## Future Enhancements

- [ ] Grace period before kick
- [ ] Playtime leaderboard
- [ ] Time purchase system (with in-game currency)
- [ ] AFK detection (pause quota burn)
- [ ] Config hot-reload support

## Troubleshooting

### Build Issues
- **Issue**: "Unsupported unpick version"
  - **Solution**: Use Gradle 9.2.1+ and Loom 1.14+

- **Issue**: "Unresolved reference" for Minecraft classes
  - **Solution**: Check Mojang mapping names, not Yarn names

### Runtime Issues
- **Issue**: Data not persisting
  - **Check**: `config/chrono-smp/` directory permissions
  - **Check**: Server logs for save/load errors

- **Issue**: Advancement rewards not triggering
  - **Check**: `chrono-smp.mixins.json` is present in the JAR
  - **Check**: `fabric.mod.json` contains `"mixins": ["chrono-smp.mixins.json"]`
  - **Check**: Server logs for mixin application errors on startup

## License
MIT License - See LICENSE file for details
