# Camp Board

Camp Board is a Fabric mod for Minecraft Java 26.1.2 that adds placeable project boards for small servers. Each board can track builds, suggestions, tasks, leaders, locations, and shared material requests from an in-world UI.

## Requirements

- Minecraft Java 26.1.2
- Fabric Loader 0.18.4 or newer
- Fabric API 0.150.0+26.1.2 or newer
- Java 25

Optional client integrations:

- Just Enough Items
- Mod Menu

Camp Board is intended to be installed on both the server and connecting clients.

## Features

- Placeable spruce Camp Board block
- Freestanding and wall-mounted placement
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
```

`projects.json` is used by the command/global board data. Placed boards use files in the `boards` folder unless `allBoardsGlobal` is enabled.

## Commands

All `/campboard` commands require server operator permission. There are no client-only commands.

The in-world UI uses the board that was clicked. Project and material maintenance commands currently operate on the command/global board data.

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
```

Examples:

```mcfunction
/campboard save
/campboard reload
/campboard backup
/campboard restore projects-2026-06-02T23-55-00Z.json
/campboard export
```

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
/campboard config set backupRetention 10
```

After changing `craftingEnabled`, reload recipes or restart the server.

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
```

Examples:

```mcfunction
/campboard materials adjust nether_hub minecraft:stone 64
/campboard materials adjust nether_hub minecraft:spruce_planks 128
/campboard materials adjust nether_hub minecraft:glass 0
```

## Building

Build the mod with:

```text
gradle build
```

The normal mod jar is written to:

```text
build/libs/camp-board-<version>.jar
```

Do not upload or install the `-sources.jar` file as the playable mod.
