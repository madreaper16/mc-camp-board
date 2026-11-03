# Changelog

## 1.0.1+26.3

- Port Camp Board and the 26.2 reliability improvements to Minecraft Java 26.3.
- Update Fabric Loader to 0.20.1, Fabric API to 0.165.2+26.3, Loom to 1.17.21, and the Gradle wrapper to 9.6.0.
- Update the build-verified optional integrations to JEI 31.14.0.72 and Mod Menu 21.0.0.
- Remove the obsolete block codec override required by older Minecraft versions.
- Replace the removed GLFW Tab-key constant with Minecraft's SDL-compatible input constant.
- Release text-field focus before rebuilding or closing the board screen to keep SDL input state synchronized.
- Retain the saved board JSON format, board IDs, material handling, backups, imports, and multiplayer fixes from the 26.2 build.

Java 25 remains required. Install the 26.3 build on both clients and server. JEI's own MezzConfig dependency is required when JEI is installed.

The clean build and all 20 automated regression tests pass. Live Minecraft rendering, SDL text entry, multiplayer, optional integrations at runtime, and a real-world upgrade have not been tested.

## 1.0.1+26.2

Release build based on the Minecraft 26.2 port in pull request #1.

Thanks to [pkozuch25](https://github.com/pkozuch25) for initiating the [Minecraft 26.2 port](https://github.com/madreaper16/mc-camp-board/pull/1).

### Data and materials

- Back up physical board files before changes and include all boards/configuration in manual snapshots and exports.
- Restore full or individual-board snapshots while retaining unrelated/newer boards and making raw recovery copies first.
- Preserve stored items when a material request is reduced, including requests reduced to zero.
- Reject invalid saved data without substituting an empty board.
- Roll back board/inventory changes when an action cannot be saved; report save failures.
- Avoid consuming stacks with custom components that cannot be represented by the virtual store.
- Limit withdrawals to available inventory capacity.
- Validate complete material imports before applying changes, including amounts and unknown items.
- Add optional board-ID arguments to material commands and `/campboard boards` for console targeting.
- Reject ambiguous project IDs instead of changing the first matching board.

### Multiplayer and configuration

- Validate actions against the player's open nearby board session and dimension.
- Refresh all valid viewers of an updated board.
- Split board snapshots into bounded chunks; send recent activity while preserving the full saved log.
- Keep ordinary refresh packets from reopening closed screens.
- Resolve real player identities for leader permissions while retaining arbitrary display nicknames.
- Retain support for legacy leader entries matching actual account names.
- Synchronize server configuration to clients and accept Mod Menu changes only from server operators.
- Prevent stale config submissions from overwriting another admin's changes.
- Reset world/session state when a server stops.
- Use explicit desired task/helper states to avoid toggling back when clients have stale snapshots.

### Interface and build

- Add scrolling to board lists, forms, details, and Mod Menu settings.
- Keep navigation controls outside the scrolling area; preserve form drafts during board refreshes.
- Wrap descriptions, fit labels, and allow longer registry IDs in material fields.
- Restrict large-board removal to matching board IDs and parts; validate placement height/world borders.
- Pin Fabric Loader minimum to 0.19.4 and add a Gradle 9.5.0 wrapper.
- Add automated regression tests and update the 26.2 documentation.

### Compatibility

Install this version on both client and server. Its packet protocol differs from older releases. Saved board JSON and block entity IDs keep their existing format.

No dedicated server or graphical Minecraft session has been run for this build. Multiplayer interaction, rendering, optional integrations at runtime, and a real-world upgrade remain unverified.
