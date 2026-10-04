# Chrono SMP

A server-side Minecraft time quota system for Fabric and Paper (1.21.11). Players receive limited playtime that burns
while they're online, encouraging strategic play and creating a fair, time-limited gaming experience.

## Current Support

This project currently targets the following platforms:
- Fabric 1.21.11
- Paper 1.21.11

Future support for additional loaders is planned, but it is not the active priority. The current build and release scope is intentionally limited to Fabric and Paper to keep development focused.

## Features

### ⏰ Time Quota System
- **Initial Quota**: After an administrator runs `/chrono start`, new players receive a configurable amount of playtime (default: 8 hours)
- **Time Burn**: Quota decreases in real-time while online (1 second per second)
- **Automatic Kick**: Players are kicked when their quota reaches zero

### 📅 Periodic Allotment
- Once `/chrono start` is active, players receive additional playtime at regular intervals (default: 2 hours every day)
- Granted automatically on login after the configured period has elapsed
- Players are notified on login when their next allotment is due, using a human-readable countdown like "2 days, 4 hours, 30 minutes"
- Prevents stockpiling - only active players benefit

### ⚔️ PvP Quota Transfer
- When a player kills another player, quota is transferred to the killer (default: 1 hour)
- Creates strategic gameplay decisions
- Transfers only available quota (victim can't go negative)

### 🎁 Voluntary Quota Transfer
- Players can voluntarily transfer quota to others using `/chrono transfer`
- Useful for helping friends or coordinating team play
- Prevents self-transfers and negative amounts
- Requires at least 1 minute transfer (no exploits)

### 💾 Persistent Storage
- Player quotas automatically saved every 5 minutes
- Manual save on player disconnect and server shutdown
- Survives server restarts and crashes
- Stored in `config/chrono-smp/player-data.json`

### 🏆 Advancement Rewards
- Completing advancements grants bonus quota time
- Three tiers based on advancement type (configurable):
  - **Task**: +15 minutes (default)
  - **Goal**: +30 minutes (default)
  - **Challenge**: +1 hour (default)
- Root and silent advancements (e.g. recipe unlocks) are excluded
- Each advancement can only grant time once per session

### 🎊 Rush Hour Events
- **Admin-Initiated Time Windows**: Server admins can activate rush hours using commands
- **No Quota Burn**: Players' quota doesn't burn during rush hour
- **Glowing Effect**: Players glow every 10 seconds during rush hour by default; configure with `RUSHHOUR_GLOWING`
- **Enhanced PvP Transfers**: PvP kills transfer multiplied quota (default: 2x, configurable)
- **Live Countdown**: All players see persistent countdown via action bar: `▶ Rush Hour: 4:23 remaining ▓▓▓▓▓▒░░░░`
- **Smooth Updates**: Countdown updates every 1 second with smooth visual progress (not 10-second intervals)
- **Visual Progress Bar**: Unicode block characters (▓ = remaining, ░ = expired) drain visually as time passes
- **Urgency Indicator**: Countdown turns red (§c) with 5 seconds or less remaining to alert players
- **Fullscreen Announcement**: Title + subtitle broadcast to all players when rush hour starts
- **Late Join Support**: Players joining during rush hour automatically added to active countdown display
- **Single Rush Hour**: Starting a new rush hour replaces any existing one

### 🔄 Offline Player Support
- **Persistent Notifications**: Messages queued for offline players are persisted and survive server restarts
- **Username Tracking**: Player usernames are stored and updated on every login, enabling name-based commands
- **Offline Transfers**: Admins and players can transfer quota to offline players by name
- **Offline Messages**: Queued messages are delivered as chat when the player logs back in
- **Revive System**: Depleted players (0 quota) show a special revive message when brought back
- **Offline Admin Tools**: Admins can add/remove time from offline players with automatic notifications
- **List Display**: `/chrono list` displays offline players by stored username instead of UUID; depleted players shown in red

## Installation

### Requirements
- Minecraft Server 1.21.11
- Fabric Loader 0.18.2+ for the Fabric build
- Paper 1.21.11 for the Paper build
- Java 21 or higher

### Fabric Setup
1. Install [Fabric Loader](https://fabricmc.net/use/) on your Minecraft 1.21.11 server
2. Download required dependencies:
   - [Fabric API 0.139.4+1.21.11](https://modrinth.com/mod/fabric-api)
   - [Fabric Language Kotlin 1.13.9+](https://modrinth.com/mod/fabric-language-kotlin)
3. Download the Fabric release artifact from releases
4. Place all JARs in your server's `mods/` folder
5. Start the server

### Paper Setup
1. Install Paper 1.21.11 on your server
2. Download the Paper release artifact from releases
3. Place the plugin JAR in your server's `plugins/` folder
4. Start the server

### Release Scope
Current builds are produced for:
- Fabric mod jar
- Paper plugin jar

Additional loaders may be added later, but they are not part of the active support target right now.

## How It Works

### For Players
1. **SMP Start**: An administrator runs `/chrono start` to enable quota gameplay
2. **First Join**: You receive initial playtime quota (default: 8 hours)
3. **Playing**: Your quota burns at 1:1 ratio with real time
4. **Periodic Bonus**: After the allotment period, log in to receive bonus time (default: +8 hours every 7 days)
5. **PvP**: Defeating another player grants you quota from their pool (default: +1 hour)
6. **Voluntary Transfer**: Share quota with friends using `/chrono transfer <player> <amount>` — works even if they're offline; bare amounts are minutes, and `s`, `m`, `h`, or `d` suffixes select seconds, minutes, hours, or days
7. **Offline Messages**: If you receive transfers or admin adjustments while offline, you'll see messages when you log back in
8. **Revived**: If an admin or another player gives you quota when you had 0 (were depleted), you'll see a special revive message
9. **Quota Depleted**: You'll be kicked and must wait for the next allotment period or ask an admin to revive you

**Note**: Default values shown above are configurable by server admins.

### Commands

**Tab Completion**: All commands support tab-completion, showing both online and offline player names.

**SMP lifecycle (OP / `chrono.admin` or `chrono.admins` only)**:
- `/chrono start` - Start quota management. New and returning players can receive quota and playtime depletion, rewards, and transfers become active. The started state survives server restarts.
- `/chrono stop` - Pause all quota changes without deleting player data. Balance and list remain readable; transfers and quota mutations are paused.
- `/chrono reset` - Stop quota management and erase all player records from `player-data.json`.

- `/chrono balance` - Check your own remaining quota
- `/chrono balance <player>` - Check another player's remaining quota, including offline players by name
- `/chrono list [all|online]` - Show quota balances sorted alphabetically; defaults to all players
  - Offline players shown by stored username instead of UUID
  - Players with 0 quota displayed in red (§c) to show they are depleted
- `/chrono transfer <player> <amount>` - Transfer quota to another player (online or offline); bare numbers mean minutes, with `s`, `m`, `h`, and `d` suffixes available
  - Example: `/chrono transfer Steve 30` transfers 30 minutes to Steve
  - **Offline Transfer**: If Steve is offline, a notification is queued and delivered on their next login
  - **Revive Feature**: If transferring to a player with 0 quota (reviving them), they'll see a special revive message on login
  - Minimum: 1 minute
  - Cannot transfer to yourself
  - Requires sufficient quota in your account

**Admin Commands** (OP-level only):
**Admin Commands** (individual nodes default to operators):
- `/chrono add <player> <amount>` - Grant time to an online or stored offline player; bare numbers mean minutes, with `s`, `m`, `h`, and `d` suffixes available
  - Example: `/chrono add Steve 60` grants 60 minutes to Steve
  - Shows before/after quota totals to the admin
  - Minimum: 1 minute
  - Requires `chrono.use.add` (default: operators)

- `/chrono remove <player> <amount>` - Remove time from an online or stored offline player; bare numbers mean minutes, with `s`, `m`, `h`, and `d` suffixes available
  - Example: `/chrono remove Steve 30` removes 30 minutes from Steve
  - Validates player has sufficient quota before removal
  - Shows before/after quota totals to the admin
  - Minimum: 1 minute
  - Requires `chrono.use.remove` (default: operators)

- `/chrono set <player> <amount>` - Set an online or stored offline player's quota to zero or more, with bare numbers in minutes and `s`, `m`, `h`, or `d` suffixes
  - Requires `chrono.use.set` (default: operators)

- `/chrono rushhour start <minutes>` - Start a rush hour event
  - Example: `/chrono rushhour start 60` starts a 60-minute rush hour
  - Broadcasts fullscreen title announcement to all players
  - Players don't burn quota during this time
  - PvP transfers are multiplied (default: 2x)
  - Minimum: 1 minute
  - Replaces any existing rush hour
  - Requires `chrono.use.rushhour.start` (default: operators)
- `/chrono rushhour end` - Immediately end the current rush hour
  - Announces end to all players
  - Quota burning resumes for all players
  - Requires `chrono.use.rushhour.end` (default: operators)

### Permissions and Console
- `chrono.use.balance`, `chrono.use.list`, and `chrono.use.transfer` default to everyone and can be changed by a permission manager.
- Admin action nodes, including `chrono.use.start`, `chrono.use.stop`, and `chrono.use.reset`, default to operators and can be assigned individually. `chrono.admin` and its alias `chrono.admins` default to operators and grant admin action nodes only; they do not imply public permissions such as balance, list, transfer, or help.
- Paper uses Bukkit permissions. Fabric includes Fabric Permissions API 0.6.1; install a compatible permission manager such as LuckPerms to manage named grants. Without a provider, public commands default to everyone and admin commands to operators.
- The server console can run all commands except `/chrono transfer`. Console `/chrono balance` prints: `The console doesn't have a balance, add a players username to check their time`; use `/chrono balance <player>` to view a balance.
- Until `/chrono start` is run, quota gameplay is inactive: player quota commands and all quota-changing actions return `The SMP didn't start yet.` While stopped, joins, playtime depletion, periodic/advancement rewards, PvP transfers, voluntary transfers, and admin add/remove/set are paused. Balance and list remain available after the SMP has previously been started.

### Example Timeline
```
Day 0:  Admin runs `/chrono start`
Day 0:  Join server → 8 hours quota
Day 0:  Complete "Stone Age" advancement (task) → +15 min → 8h 15m
Day 0:  Complete "Into Fire" advancement (challenge) → +1h → 9h 15m
Day 0:  Admin starts rush hour for 30 min (quota frozen, PvP 2x)
Day 0:  Kill player during rush hour → +2h (multiplied) → 11h 15m remaining
Day 1:  Play 2 hours → 9h 15m remaining
Day 3:  Play 3 hours → 6h 15m remaining
Day 5:  Kill player (normal) → 7h 15m remaining (+1 from PvP)
Day 6:  Transfer 1h to friend → 6h 15m remaining
Day 7:  Login → 14h 15m remaining (+8 weekly allotment, had 6h 15m)
```

## Development

### Requirements
- Java 21 or higher
- Gradle 9.2.1+ (included via wrapper)

### Building
```bash
# Clone the repository
git clone https://github.com/yourusername/chrono-smp.git
cd chrono-smp

# Build the mod
./gradlew build

# Output: build/libs/chrono-smp-0.1.0.jar
```

### Development Server
```bash
./gradlew runServer
```

### Project Structure
```
src/main/kotlin/com/chronosmp/
├── ChronoSMP.kt              # Main entry point
├── commands/                 # Player commands (/chrono)
├── config/                   # Configuration management
├── data/                     # Data models & persistence
├── systems/                  # Core game systems
├── events/                   # Event handlers
src/main/java/com/chronosmp/
└── mixin/                    # Minecraft mixins
```

## Configuration

The mod's time quota parameters can be configured via `config/chrono-smp/config.yml`. The file is automatically created
with default values on first run.

### Configuration File
Location: `config/chrono-smp/config.yml` on Fabric; `config.yml` in the plugin data folder on Paper.

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

### Configuration Parameters
- **STARTING_TIME**: New-player quota in seconds (default: 28,800 = 8 hours)
- **RECEIVED_TIME**: Periodic allotment in seconds (default: 7,200 = 2 hours)
- **TIME_RECEIVE_DURATION**: Time between allotments in seconds (default: 86,400 = 1 day)
- **KILL_TRANSFER_AMOUNT**: PvP quota transfer in seconds (default: 3,600 = 1 hour)
- **RUSHHOUR_KILL_MULTIPLIER**: Rush hour PvP multiplier (default: 2.0)
- **RUSHHOUR_GLOWING**: Apply the glowing effect to online players during rush hour (default: `true`)
- **ADVANCEMENT_TASK**: Task advancement reward in seconds (default: 900 = 15 minutes)
- **ADVANCEMENT_GOAL**: Goal advancement reward in seconds (default: 1,800 = 30 minutes)
- **ADVANCEMENT_CHALLENGE**: Challenge advancement reward in seconds (default: 3,600 = 1 hour)
- **Auto-save Interval**: 5 minutes (hardcoded in ChronoSMP.kt)

### Changing Values
1. Stop your server
2. Edit `config/chrono-smp/config.yml` (Fabric) or the plugin's `config.yml` (Paper)
3. Modify values (all times in seconds, multiplier as decimal)
4. Start your server
5. Changes take effect for new quota grants/transfers and rush hour events

**Note**: Existing player quotas are not retroactively adjusted when config changes.
An existing `config.json` is imported to `config.yml` on first startup after upgrading; the old JSON file is retained.

See [CLAUDE.md](CLAUDE.md) for technical documentation.

## Technical Details

- **Language**: Kotlin 2.3.10
- **Mappings**: Mojang Official Mappings
- **Build System**: Gradle 9.2.1 with Fabric Loom 1.14
- **Data Format**: JSON with kotlinx.serialization
- **Thread Safety**: ConcurrentHashMap for player data

For detailed technical documentation, see [CLAUDE.md](CLAUDE.md).

## Troubleshooting

### Players not receiving weekly allotment
- Check server logs for errors
- Verify `config/chrono-smp/player-data.json` exists and is readable
- Ensure at least 7 days have passed since last allotment

### Data not persisting
- Verify `config/chrono-smp/` directory has write permissions
- Check disk space availability
- Review server logs for JSON serialization errors

## License
MIT License - see [LICENSE](LICENSE) file for details

## Credits
Built with:
- [Fabric](https://fabricmc.net/) - Modding toolchain
- [Kotlin](https://kotlinlang.org/) - Programming language
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON persistence
