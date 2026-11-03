# Camp Board

Camp Board is a Fabric mod for Minecraft Java 26.2 that adds placeable project boards for small servers. Each board can track builds, suggestions, tasks, leaders, locations, and shared material requests from an in-world UI.

This branch contains `1.0.1+26.2`. It has automated regression coverage and clean build verification; live Minecraft rendering and dedicated-server multiplayer have not been tested for this build.

## Requirements

- Minecraft Java 26.2
- Fabric Loader 0.19.4 or newer
- Fabric API 0.158.0+26.2 or newer
- Java 25

Optional client integrations:

- Just Enough Items 30.31.0.206 for 26.2 (build-verified version)
- Mod Menu 20.0.0 (build-verified version)

Install the same Camp Board candidate on both the server and connecting clients. Remove older Camp Board jars before updating. The candidate uses a new packet protocol and does not exchange board actions with older builds.

## Features

- Placeable spruce Camp Board block
- Freestanding and wall-mounted placement
- Standard or large board placement
- Optional command-only board removal
- Separate data for each placed board by default
- Optional global-board mode
- Projects, suggestions, and archive views
- Project statuses: Planned, Active, Paused, Back Burner, Completed, Archived
- Up to three project leaders
- Task helper tracking
- Virtual material storage with contribution and withdrawal support
- Overworld and Nether coordinate pairing
- JSON data stored in the world save
- Automatic backups
- Admin maintenance commands
- JEI item information
- Mod Menu configuration screen

## Crafting

The default recipe uses a 3x3 crafting grid:

```text
Spruce Planks | Spruce Planks | Spruce Planks
Spruce Planks | Book          | Spruce Planks
Chest         | Birch Sign    | Chest
```

The recipe can be disabled with the `craftingEnabled` config option.

## Data Storage

Camp Board stores data in the world save under:

```text
serverconfig/campboard/
```

Important paths:

```text
serverconfig/campboard/config.json
serverconfig/campboard/projects.json
serverconfig/campboard/boards/
serverconfig/campboard/backups/
serverconfig/campboard/imports/
```

`projects.json` is used by the command/global board data. Placed boards use files in the `boards` folder unless `allBoardsGlobal` is enabled.

Global physical boards use `boards/global.json`; `projects.json` is retained for legacy maintenance commands. Switching global mode does not merge or erase existing board files.

Automatic backups capture the previous contents of the board being changed. `/campboard backup` creates a full snapshot of every saved board and the configuration. Retention is applied separately to each board's automatic backups and to full snapshots.

Restoring a full snapshot restores the boards and configuration in that snapshot. Boards created after the snapshot are retained. Restoring a per-board automatic backup changes only that board. Old `projects-*.json` backups remain supported for legacy data. Restore also makes raw recovery copies under `backups/recovery-*/` before replacing files; these recovery directories are retained for manual recovery.

Saved board JSON and block entity `board_id` values retain their existing format. Damaged files cause a visible load error instead of being replaced with an empty board. The on-disk activity log is retained; clients receive the latest 50 entries per project.

## Commands

All `/campboard` commands require server operator permission. There are no client-only commands.

The in-world UI uses the board that was clicked. Project create/list/archive/delete commands operate on legacy `projects.json`. Material commands target the board being looked at, accept an explicit board ID, or use a unique matching project when no board is selected. Duplicate project IDs across boards are rejected when no explicit target is available.

### Give

```mcfunction
/campboard give
/campboard give <player>
/campboard give <player> <amount>
```

Examples:

```mcfunction
/campboard give
/campboard give MadReaper16
/campboard give MadReaper16 5
```

### Save, Reload, Backup

```mcfunction
/campboard save
/campboard reload
/campboard backup
/campboard restore <backupName>
/campboard export
/campboard remove
/campboard boards
```

Examples:

```mcfunction
/campboard save
/campboard reload
/campboard backup
/campboard restore projects-2026-06-02T23-55-00Z.json
/campboard export
/campboard remove
```

`/campboard remove` removes the Camp Board the command runner is looking at. It is mainly intended for servers that disable normal Camp Board breaking.

`/campboard boards` lists saved/loaded board IDs for console targeting. `legacy` selects `projects.json`.

### Config

```mcfunction
/campboard config get <key>
/campboard config set <key> <value>
```

Available keys:

```text
craftingEnabled
allBoardsGlobal
suggestionsEnabled
materialsEnabled
anyoneCanWithdrawMaterials
taskHelpingEnabled
largeBoard
breakable
projectCreation
maxProjectLeaders
maxDescriptionLength
backupRetention
```

Accepted values:

```text
craftingEnabled: true or false
allBoardsGlobal: true or false
suggestionsEnabled: true or false
materialsEnabled: true or false
anyoneCanWithdrawMaterials: true or false
taskHelpingEnabled: true or false
largeBoard: true or false
breakable: true or false
projectCreation: ADMIN_ONLY or ANYONE
maxProjectLeaders: 1 to 3
maxDescriptionLength: 50 to 250
backupRetention: 1 or higher
```

Examples:

```mcfunction
/campboard config get projectCreation
/campboard config set projectCreation ADMIN_ONLY
/campboard config set projectCreation ANYONE
/campboard config set craftingEnabled false
/campboard config set allBoardsGlobal true
/campboard config set largeBoard true
/campboard config set breakable false
/campboard config set backupRetention 10
```

After changing `craftingEnabled`, run `/reload` to refresh recipes.

Mod Menu displays settings received from the current server or singleplayer world. Operators can submit changes; other players see read-only settings. Settings are unavailable outside a world/connection. Changing global mode invalidates open board sessions; reopen the board afterward.

Leader display names may be arbitrary nicknames. The optional player identity field accepts a Minecraft account name or UUID for edit permission, including offline players. A blank identity auto-links an online player with a matching account name; otherwise the entry is display-only. Offline name lookups run away from the server thread and can fail if the account cannot be resolved; an explicit UUID avoids that lookup. Existing legacy leader entries matching a player's actual account name remain recognized.

### Projects

```mcfunction
/campboard project create <projectId> <title>
/campboard project list
/campboard project archive <projectId>
/campboard project delete <projectId>
```

Examples:

```mcfunction
/campboard project create nether_hub "Nether Hub"
/campboard project create casino "Oliver Casino"
/campboard project list
/campboard project archive nether_hub
/campboard project delete nether_hub
```

Projects with stored materials cannot be deleted until those materials are withdrawn or cleared.

### Materials

```mcfunction
/campboard materials adjust <projectId> <itemId> <amount>
/campboard materials import <projectId> <fileName>
/campboard materials adjust <projectId> <itemId> <amount> <boardId>
/campboard materials import <projectId> <fileName> <boardId>
```

Examples:

```mcfunction
/campboard materials adjust nether_hub minecraft:stone 64
/campboard materials adjust nether_hub minecraft:spruce_planks 128
/campboard materials adjust nether_hub minecraft:glass 0
/campboard materials import nether_hub nether_hub_materials.txt
/campboard materials import nether_hub nether_hub_materials.txt board_0123456789abcdef
/campboard materials adjust nether_hub minecraft:stone 64 legacy
```

Material import files are read from:

```text
serverconfig/campboard/imports/
```

Look at the relevant board when running a material command. From the console, use `/campboard boards` to find its ID and supply the final board-ID argument. Without a selected board or explicit ID, the project must be unique across all boards.

The importer accepts common text formats with one material per line. Each line must include an amount and a registry item id.

Supported examples:

```text
minecraft:stone 128
128 minecraft:stone
minecraft:spruce_planks, 64
64 - minecraft:glass
```

Lines starting with `#` or text after `#` are ignored. Duplicate material lines are summed. Unknown items, invalid lines, invalid counts, and arithmetic overflow reject the import before changes are saved. Files must be inside the imports directory and no larger than 4 MiB. File extensions are unrestricted, but structured JSON, native Litematica files, and CSV headers are not parsed. Convert Litematica exports to the supported amount-plus-registry-ID format first.

Reducing a request never discards deposited items. Materials exceeding the new request remain available for withdrawal. The virtual store accepts ordinary item stacks; stacks with custom components such as names, enchantments, or container contents are not consumed, because the store tracks item IDs rather than those components. Withdrawals are limited to available inventory capacity.

## Building

Build the mod with:

```text
.\gradlew.bat clean build
```

The normal mod jar is written to:

```text
build/libs/camp-board-<version>.jar
```

Do not upload or install the `-sources.jar` file as the playable mod.

On macOS/Linux, use `sh ./gradlew clean build`. The wrapper pins Gradle 9.5.0. Build verification includes JUnit tests for storage, backup/restore, material conservation, imports, project lookup, identity permissions, configuration copies, and chunked board transfers. No server is started by the tests.

Board actions require an open session within eight blocks of the same board in the same dimension. Updates are sent to every valid viewer. Snapshots are transferred in chunks, with a limit of 8,388,608 characters; exceeding the limit reports an error rather than attempting an oversized packet.

## Credits

Thanks to [pkozuch25](https://github.com/pkozuch25) for initiating the [Minecraft 26.2 port in pull request #1](https://github.com/madreaper16/mc-camp-board/pull/1).
