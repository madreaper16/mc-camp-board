package com.clockworktown.campboard.command;

import com.clockworktown.campboard.CampBoardMod;
import com.clockworktown.campboard.block.CampBoardBlock;
import com.clockworktown.campboard.data.MaterialStack;
import com.clockworktown.campboard.data.Project;
import com.clockworktown.campboard.data.ProjectStatus;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CampBoardCommands {
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");

    private CampBoardCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("campboard")
                        .requires(CampBoardCommands::isAdmin)
                        .then(Commands.literal("give")
                                .executes(context -> give(context, context.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> give(context, EntityArgument.getPlayer(context, "player"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> give(context, EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("save")
                                .executes(CampBoardCommands::save))
                        .then(Commands.literal("reload")
                                .executes(CampBoardCommands::reload))
                        .then(Commands.literal("backup")
                                .executes(CampBoardCommands::backup))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("backupName", StringArgumentType.word())
                                        .executes(context -> restore(context, StringArgumentType.getString(context, "backupName")))))
                        .then(Commands.literal("export")
                                .executes(CampBoardCommands::export))
                        .then(Commands.literal("remove")
                                .executes(CampBoardCommands::removeTargetedBoard))
                        .then(Commands.literal("config")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(context -> getConfig(context, StringArgumentType.getString(context, "key")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .executes(context -> setConfig(
                                                                context,
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")))))))
                        .then(Commands.literal("project")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .then(Commands.argument("title", StringArgumentType.string())
                                                        .executes(context -> createProject(
                                                                context,
                                                                StringArgumentType.getString(context, "projectId"),
                                                                StringArgumentType.getString(context, "title"))))))
                                .then(Commands.literal("list")
                                        .executes(CampBoardCommands::listProjects))
                                .then(Commands.literal("archive")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .executes(context -> archiveProject(context, StringArgumentType.getString(context, "projectId")))))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .executes(context -> deleteProject(context, StringArgumentType.getString(context, "projectId"))))))
                        .then(Commands.literal("materials")
                                .then(Commands.literal("adjust")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .then(Commands.argument("itemId", StringArgumentType.string())
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(context -> adjustMaterial(
                                                                        context,
                                                                        StringArgumentType.getString(context, "projectId"),
                                                                        StringArgumentType.getString(context, "itemId"),
                                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                                .then(Commands.literal("import")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .then(Commands.argument("fileName", StringArgumentType.string())
                                                        .executes(context -> importMaterials(
                                                                context,
                                                                StringArgumentType.getString(context, "projectId"),
                                                                StringArgumentType.getString(context, "fileName")))))))));
    }

    private static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int give(CommandContext<CommandSourceStack> context, ServerPlayer target, int amount) {
        ItemStack stack = new ItemStack(CampBoardMod.CAMP_BOARD_ITEM, amount);
        target.getInventory().add(stack);
        context.getSource().sendSuccess(
                () -> Component.literal("Gave " + amount + " Camp Board(s) to " + target.getName().getString() + "."),
                true
        );
        return amount;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        try {
            CampBoardMod.save();
            context.getSource().sendSuccess(() -> Component.literal("Camp Board data saved."), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to save Camp Board data: " + exception.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        try {
            CampBoardMod.reload(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Camp Board data reloaded."), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to reload Camp Board data: " + exception.getMessage()));
            return 0;
        }
    }

    private static int backup(CommandContext<CommandSourceStack> context) {
        try {
            Path backup = CampBoardMod.backup();
            context.getSource().sendSuccess(() -> Component.literal("Camp Board backup created: " + backup.getFileName()), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to create Camp Board backup: " + exception.getMessage()));
            return 0;
        }
    }

    private static int export(CommandContext<CommandSourceStack> context) {
        try {
            Path export = CampBoardMod.export();
            context.getSource().sendSuccess(() -> Component.literal("Camp Board export created: " + export.getFileName()), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to export Camp Board data: " + exception.getMessage()));
            return 0;
        }
    }

    private static int restore(CommandContext<CommandSourceStack> context, String backupName) {
        try {
            CampBoardMod.restore(backupName);
            context.getSource().sendSuccess(() -> Component.literal("Restored Camp Board backup: " + backupName), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to restore Camp Board backup: " + exception.getMessage()));
            return 0;
        }
    }

    private static int getConfig(CommandContext<CommandSourceStack> context, String key) {
        String value = switch (key) {
            case "craftingEnabled" -> Boolean.toString(CampBoardMod.config().craftingEnabled());
            case "allBoardsGlobal" -> Boolean.toString(CampBoardMod.config().allBoardsGlobal());
            case "suggestionsEnabled" -> Boolean.toString(CampBoardMod.config().suggestionsEnabled());
            case "materialsEnabled" -> Boolean.toString(CampBoardMod.config().materialsEnabled());
            case "anyoneCanWithdrawMaterials" -> Boolean.toString(CampBoardMod.config().anyoneCanWithdrawMaterials());
            case "taskHelpingEnabled" -> Boolean.toString(CampBoardMod.config().taskHelpingEnabled());
            case "largeBoard" -> Boolean.toString(CampBoardMod.config().largeBoard());
            case "breakable" -> Boolean.toString(CampBoardMod.config().breakable());
            case "projectCreation" -> CampBoardMod.config().projectCreation();
            case "maxProjectLeaders" -> Integer.toString(CampBoardMod.config().maxProjectLeaders());
            case "maxDescriptionLength" -> Integer.toString(CampBoardMod.config().maxDescriptionLength());
            case "backupRetention" -> Integer.toString(CampBoardMod.config().backupRetention());
            default -> null;
        };

        if (value == null) {
            context.getSource().sendFailure(Component.literal("Unknown config key: " + key));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
        return 1;
    }

    private static int setConfig(CommandContext<CommandSourceStack> context, String key, String value) {
        try {
            switch (key) {
                case "craftingEnabled" -> CampBoardMod.config().setCraftingEnabled(Boolean.parseBoolean(value));
                case "allBoardsGlobal" -> CampBoardMod.config().setAllBoardsGlobal(Boolean.parseBoolean(value));
                case "suggestionsEnabled" -> CampBoardMod.config().setSuggestionsEnabled(Boolean.parseBoolean(value));
                case "materialsEnabled" -> CampBoardMod.config().setMaterialsEnabled(Boolean.parseBoolean(value));
                case "anyoneCanWithdrawMaterials" -> CampBoardMod.config().setAnyoneCanWithdrawMaterials(Boolean.parseBoolean(value));
                case "taskHelpingEnabled" -> CampBoardMod.config().setTaskHelpingEnabled(Boolean.parseBoolean(value));
                case "largeBoard" -> CampBoardMod.config().setLargeBoard(Boolean.parseBoolean(value));
                case "breakable" -> CampBoardMod.config().setBreakable(Boolean.parseBoolean(value));
                case "projectCreation" -> CampBoardMod.config().setProjectCreation(value);
                case "maxProjectLeaders" -> CampBoardMod.config().setMaxProjectLeaders(Integer.parseInt(value));
                case "maxDescriptionLength" -> CampBoardMod.config().setMaxDescriptionLength(Integer.parseInt(value));
                case "backupRetention" -> CampBoardMod.config().setBackupRetention(Integer.parseInt(value));
                default -> {
                    context.getSource().sendFailure(Component.literal("Unknown config key: " + key));
                    return 0;
                }
            }

            CampBoardMod.save();
            context.getSource().sendSuccess(() -> Component.literal("Updated " + key + " to " + value + "."), true);
            return 1;
        } catch (IOException | NumberFormatException exception) {
            context.getSource().sendFailure(Component.literal("Failed to update config: " + exception.getMessage()));
            return 0;
        }
    }

    private static int createProject(CommandContext<CommandSourceStack> context, String projectId, String title) {
        if (CampBoardMod.boardState().findProject(projectId).isPresent()) {
            context.getSource().sendFailure(Component.literal("Project already exists with id " + projectId + "."));
            return 0;
        }

        Project project = new Project(projectId, title, "", actor(context));
        project.log(actor(context), "Created project from command.");
        CampBoardMod.boardState().projects().add(project);
        CampBoardMod.saveQuietly();
        context.getSource().sendSuccess(() -> Component.literal("Created project " + projectId + "."), true);
        return 1;
    }

    private static int listProjects(CommandContext<CommandSourceStack> context) {
        if (CampBoardMod.boardState().projects().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No Camp Board projects exist yet."), false);
            return 0;
        }

        for (Project project : CampBoardMod.boardState().projects()) {
            context.getSource().sendSuccess(
                    () -> Component.literal(project.id() + " - " + project.title() + " [" + project.status().displayName() + "]"),
                    false
            );
        }
        return CampBoardMod.boardState().projects().size();
    }

    private static int archiveProject(CommandContext<CommandSourceStack> context, String projectId) {
        return CampBoardMod.boardState().findProject(projectId).map(project -> {
            project.setStatus(ProjectStatus.ARCHIVED);
            project.log(actor(context), "Archived project from command.");
            CampBoardMod.saveQuietly();
            context.getSource().sendSuccess(() -> Component.literal("Archived project " + projectId + "."), true);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.literal("No project found with id " + projectId + "."));
            return 0;
        });
    }

    private static int deleteProject(CommandContext<CommandSourceStack> context, String projectId) {
        Project project = CampBoardMod.boardState().findProject(projectId).orElse(null);
        if (project == null) {
            context.getSource().sendFailure(Component.literal("No project found with id " + projectId + "."));
            return 0;
        }

        boolean hasStoredMaterials = project.materials().values().stream().anyMatch(material -> material.stored() > 0);
        if (hasStoredMaterials) {
            context.getSource().sendFailure(Component.literal("Project has stored materials. Withdraw/refund them before deleting."));
            return 0;
        }

        CampBoardMod.boardState().projects().remove(project);
        CampBoardMod.saveQuietly();
        context.getSource().sendSuccess(() -> Component.literal("Deleted project " + projectId + "."), true);
        return 1;
    }

    private static int adjustMaterial(CommandContext<CommandSourceStack> context, String projectId, String itemId, int amount) {
        CampBoardMod.ProjectMatch match = CampBoardMod.findProject(projectId).orElse(null);
        if (match == null) {
            context.getSource().sendFailure(Component.literal("No project found with id " + projectId + "."));
            return 0;
        }

        Project project = match.project();
        MaterialStack material = project.materials().computeIfAbsent(itemId, key -> new MaterialStack(key, amount));
        material.adjustStored(amount);
        project.log(actor(context), "Adjusted " + itemId + " stored count to " + amount + ".");
        try {
            CampBoardMod.saveProjectMatch(match);
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to save material adjustment: " + exception.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Adjusted " + itemId + " to " + amount + " for " + projectId + "."), true);
        return amount;
    }

    private static int importMaterials(CommandContext<CommandSourceStack> context, String projectId, String fileName) {
        CampBoardMod.ProjectMatch match = CampBoardMod.findProject(projectId).orElse(null);
        if (match == null) {
            context.getSource().sendFailure(Component.literal("No project found with id " + projectId + "."));
            return 0;
        }

        Project project = match.project();
        Path imports = CampBoardMod.importDirectory(context.getSource().getServer()).normalize();
        Path file = imports.resolve(fileName).normalize();
        if (!file.startsWith(imports)) {
            context.getSource().sendFailure(Component.literal("Import file must be inside serverconfig/campboard/imports."));
            return 0;
        }

        try {
            List<String> lines = Files.readAllLines(file);
            int imported = 0;
            for (String line : lines) {
                MaterialRequest request = parseMaterialLine(line);
                if (request != null) {
                    MaterialStack material = project.materials().computeIfAbsent(request.itemId(), key -> new MaterialStack(key, 0));
                    material.adjustRequested(material.requested() + request.amount());
                    imported++;
                }
            }
            project.log(actor(context), "Imported " + imported + " material request(s) from " + file.getFileName() + ".");
            CampBoardMod.saveProjectMatch(match);
            int importedCount = imported;
            context.getSource().sendSuccess(() -> Component.literal("Imported " + importedCount + " material request(s) for " + projectId + "."), true);
            return importedCount;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Failed to import materials: " + exception.getMessage()));
            return 0;
        }
    }

    private static int removeTargetedBoard(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("A player must run this command while looking at a Camp Board."));
            return 0;
        }

        HitResult hitResult = player.pick(6.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            context.getSource().sendFailure(Component.literal("Look at a Camp Board to remove it."));
            return 0;
        }

        ServerLevel level = player.level();
        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(CampBoardMod.CAMP_BOARD_BLOCK)) {
            context.getSource().sendFailure(Component.literal("Look at a Camp Board to remove it."));
            return 0;
        }

        if (state.getValue(CampBoardBlock.LARGE)) {
            CampBoardBlock.removeLargeBoard(level, pos, state);
        } else {
            level.destroyBlock(pos, true);
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed targeted Camp Board."), true);
        return 1;
    }

    private static MaterialRequest parseMaterialLine(String rawLine) {
        String line = rawLine.split("#", 2)[0].trim().toLowerCase();
        if (line.isBlank()) {
            return null;
        }

        Matcher amountMatcher = INTEGER_PATTERN.matcher(line);
        if (!amountMatcher.find()) {
            return null;
        }

        int amount = Integer.parseInt(amountMatcher.group());
        Matcher itemMatcher = ITEM_ID_PATTERN.matcher(line);
        while (itemMatcher.find()) {
            String candidate = normalizeItemId(itemMatcher.group());
            if (isKnownItem(candidate)) {
                return new MaterialRequest(candidate, amount);
            }
        }
        return null;
    }

    private static String normalizeItemId(String value) {
        String itemId = value.replace('/', '_');
        return itemId.contains(":") ? itemId : "minecraft:" + itemId;
    }

    private static boolean isKnownItem(String itemId) {
        return BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemId));
    }

    private static UUID actor(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        return player == null ? new UUID(0L, 0L) : player.getUUID();
    }

    private record MaterialRequest(String itemId, int amount) {
    }
}
