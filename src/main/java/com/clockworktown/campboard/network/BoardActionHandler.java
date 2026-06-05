package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import com.clockworktown.campboard.data.BoardLocation;
import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.data.MaterialStack;
import com.clockworktown.campboard.data.Project;
import com.clockworktown.campboard.data.ProjectStatus;
import com.clockworktown.campboard.data.ProjectTask;
import com.clockworktown.campboard.data.Suggestion;
import com.clockworktown.campboard.storage.BoardJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class BoardActionHandler {
    private BoardActionHandler() {
    }

    public static void handle(BoardActionPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        JsonObject action = JsonParser.parseString(payload.actionJson()).getAsJsonObject();
        String boardId = text(action, "boardId");
        try {
            process(player, action);
            CampBoardMod.saveBoard(boardId);
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Camp Board action failed: " + exception.getMessage()));
        }
        refresh(player, boardId);
    }

    private static void process(ServerPlayer player, JsonObject action) {
        String type = text(action, "type");
        switch (type) {
            case "create_project" -> createProject(player, action);
            case "edit_project" -> editProject(player, action);
            case "set_status" -> setStatus(player, action);
            case "set_location" -> setLocation(player, action);
            case "add_material" -> addMaterial(player, action);
            case "contribute" -> contribute(player, action);
            case "withdraw" -> withdraw(player, action);
            case "add_task" -> addTask(player, action);
            case "toggle_help" -> toggleHelp(player, action);
            case "complete_task" -> completeTask(player, action);
            case "remove_helper" -> removeHelper(player, action);
            case "assign_leader" -> assignLeader(player, action);
            case "remove_leader" -> removeLeader(player, action);
            case "create_suggestion" -> createSuggestion(player, action);
            case "support_suggestion" -> supportSuggestion(player, action);
            case "approve_suggestion" -> approveSuggestion(player, action);
            case "delete_suggestion" -> deleteSuggestion(player, action);
            case "archive_project" -> archiveProject(player, action);
            case "delete_project" -> deleteProject(player, action);
            default -> throw new IllegalArgumentException("Unknown action " + type);
        }
    }

    private static void createProject(ServerPlayer player, JsonObject action) {
        requireProjectCreator(player);
        String id = safeId(text(action, "id"));
        if (state(action).findProject(id).isPresent()) {
            throw new IllegalArgumentException("Project id already exists.");
        }
        Project project = new Project(id, text(action, "title"), trimDescription(text(action, "description")), player.getUUID());
        project.log(player.getUUID(), "Created project.");
        state(action).projects().add(project);
    }

    private static void editProject(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireEditor(player, project);
        project.setTitle(text(action, "title"));
        project.setDescription(trimDescription(text(action, "description")));
        project.log(player.getUUID(), "Edited project details.");
    }

    private static void setStatus(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireEditor(player, project);
        project.setStatus(ProjectStatus.valueOf(text(action, "status")));
        project.log(player.getUUID(), "Changed status to " + project.status().displayName() + ".");
    }

    private static void setLocation(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireEditor(player, project);
        project.setPrimaryLocation(new BoardLocation(text(action, "dimension"), integer(action, "x"), integer(action, "y"), integer(action, "z")));
        project.log(player.getUUID(), "Updated project location.");
    }

    private static void addMaterial(ServerPlayer player, JsonObject action) {
        requireMaterialsEnabled();
        Project project = project(action);
        requireEditor(player, project);
        String itemId = text(action, "itemId");
        int requested = integer(action, "requested");
        if (itemId.isBlank() || requested <= 0) {
            throw new IllegalArgumentException("Material item and amount are required.");
        }
        project.materials().computeIfAbsent(itemId, key -> new MaterialStack(key, requested)).adjustRequested(requested);
        project.log(player.getUUID(), "Set material request " + itemId + " to " + requested + ".");
    }

    private static void contribute(ServerPlayer player, JsonObject action) {
        requireMaterialsEnabled();
        Project project = project(action);
        MaterialStack material = material(project, action);
        Item item = item(material.itemId());
        int wanted = Math.min(integer(action, "amount"), material.remaining());
        int removed = removeItems(player, item, wanted);
        if (removed > 0) {
            material.contribute(removed);
            project.log(player.getUUID(), "Contributed " + removed + " " + material.itemId() + ".");
        }
    }

    private static void withdraw(ServerPlayer player, JsonObject action) {
        requireMaterialsEnabled();
        Project project = project(action);
        if (!CampBoardMod.config().anyoneCanWithdrawMaterials()) {
            requireEditor(player, project);
        }
        MaterialStack material = material(project, action);
        Item item = item(material.itemId());
        int removed = material.withdraw(integer(action, "amount"));
        if (removed > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(item, removed));
            project.log(player.getUUID(), "Withdrew " + removed + " " + material.itemId() + ".");
        }
    }

    private static void addTask(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireEditor(player, project);
        project.tasks().add(new ProjectTask(safeId(UUID.randomUUID().toString().substring(0, 8)), text(action, "title")));
        project.log(player.getUUID(), "Added task.");
    }

    private static void toggleHelp(ServerPlayer player, JsonObject action) {
        if (!CampBoardMod.config().taskHelpingEnabled()) {
            throw new IllegalArgumentException("Task helping is disabled.");
        }
        Project project = project(action);
        ProjectTask task = task(project, action);
        if (task.helpers().contains(player.getUUID())) {
            task.removeHelper(player.getUUID());
            project.log(player.getUUID(), "Stopped helping task: " + task.title() + ".");
        } else {
            task.addHelper(player.getUUID());
            project.log(player.getUUID(), "Started helping task: " + task.title() + ".");
        }
    }

    private static void completeTask(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireEditor(player, project);
        ProjectTask task = task(project, action);
        task.setCompleted(!task.completed());
        project.log(player.getUUID(), (task.completed() ? "Completed" : "Reopened") + " task: " + task.title() + ".");
    }

    private static void removeHelper(ServerPlayer player, JsonObject action) {
        Project project = project(action);
        requireAdmin(player);
        task(project, action).removeHelper(UUID.fromString(text(action, "helper")));
        project.log(player.getUUID(), "Removed a task helper.");
    }

    private static void assignLeader(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        Project project = project(action);
        String leaderName = text(action, "player");
        if (leaderName.isBlank()) {
            throw new IllegalArgumentException("Leader name is required.");
        }
        UUID leaderId = UUID.nameUUIDFromBytes(("campboard-leader:" + leaderName).getBytes(StandardCharsets.UTF_8));
        if (!project.addLeader(leaderId, leaderName, CampBoardMod.config().maxProjectLeaders())) {
            throw new IllegalArgumentException("Projects can have at most " + CampBoardMod.config().maxProjectLeaders() + " leader(s).");
        }
        project.log(player.getUUID(), "Assigned leader " + leaderName + ".");
    }

    private static void removeLeader(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        Project project = project(action);
        project.removeLeader(UUID.fromString(text(action, "leader")));
        project.log(player.getUUID(), "Removed a project leader.");
    }

    private static void createSuggestion(ServerPlayer player, JsonObject action) {
        requireSuggestionsEnabled();
        String id = safeId(UUID.randomUUID().toString().substring(0, 8));
        state(action).suggestions().add(new Suggestion(id, text(action, "title"), trimDescription(text(action, "description")), player.getUUID()));
    }

    private static void supportSuggestion(ServerPlayer player, JsonObject action) {
        requireSuggestionsEnabled();
        Suggestion suggestion = suggestion(action);
        if (!suggestion.supporters().add(player.getUUID())) {
            suggestion.supporters().remove(player.getUUID());
        }
    }

    private static void approveSuggestion(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        Suggestion suggestion = suggestion(action);
        Project project = new Project(safeId(suggestion.title()), suggestion.title(), suggestion.description(), player.getUUID());
        project.log(player.getUUID(), "Approved from suggestion.");
        state(action).projects().add(project);
        state(action).suggestions().remove(suggestion);
    }

    private static void deleteSuggestion(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        state(action).suggestions().remove(suggestion(action));
    }

    private static void archiveProject(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        Project project = project(action);
        project.setStatus(ProjectStatus.ARCHIVED);
        project.log(player.getUUID(), "Archived project.");
    }

    private static void deleteProject(ServerPlayer player, JsonObject action) {
        requireAdmin(player);
        Project project = project(action);
        boolean hasMaterials = project.materials().values().stream().anyMatch(material -> material.stored() > 0);
        if (hasMaterials) {
            throw new IllegalArgumentException("Withdraw stored materials before deleting.");
        }
        state(action).projects().remove(project);
    }

    private static void refresh(ServerPlayer player, String boardId) {
        ServerPlayNetworking.send(player, new OpenBoardPayload(boardId, BoardJson.toJson(CampBoardMod.boardState(boardId))));
    }

    private static int removeItems(ServerPlayer player, Item item, int count) {
        int remaining = Math.max(0, count);
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
        return count - remaining;
    }

    private static Item item(String itemId) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        if (item == null) {
            throw new IllegalArgumentException("Unknown item " + itemId);
        }
        return item;
    }

    private static Project project(JsonObject action) {
        return state(action).findProject(text(action, "projectId")).orElseThrow(() -> new IllegalArgumentException("Project not found."));
    }

    private static MaterialStack material(Project project, JsonObject action) {
        MaterialStack material = project.materials().get(text(action, "itemId"));
        if (material == null) {
            throw new IllegalArgumentException("Material not found.");
        }
        return material;
    }

    private static ProjectTask task(Project project, JsonObject action) {
        String taskId = text(action, "taskId");
        return project.tasks().stream().filter(task -> task.id().equals(taskId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Task not found."));
    }

    private static Suggestion suggestion(JsonObject action) {
        String suggestionId = text(action, "suggestionId");
        return state(action).suggestions().stream().filter(suggestion -> suggestion.id().equals(suggestionId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Suggestion not found."));
    }

    private static BoardState state(JsonObject action) {
        return CampBoardMod.boardState(text(action, "boardId"));
    }

    private static void requireAdmin(ServerPlayer player) {
        if (!player.level().getServer().getPlayerList().isOp(player.nameAndId())) {
            throw new IllegalArgumentException("Admin permission required.");
        }
    }

    private static void requireProjectCreator(ServerPlayer player) {
        if (!"ANYONE".equalsIgnoreCase(CampBoardMod.config().projectCreation())) {
            requireAdmin(player);
        }
    }

    private static void requireEditor(ServerPlayer player, Project project) {
        if (!player.level().getServer().getPlayerList().isOp(player.nameAndId()) && !project.leaders().contains(player.getUUID())) {
            throw new IllegalArgumentException("Project leader or admin permission required.");
        }
    }

    private static void requireSuggestionsEnabled() {
        if (!CampBoardMod.config().suggestionsEnabled()) {
            throw new IllegalArgumentException("Suggestions are disabled.");
        }
    }

    private static void requireMaterialsEnabled() {
        if (!CampBoardMod.config().materialsEnabled()) {
            throw new IllegalArgumentException("Materials are disabled.");
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString().trim() : "";
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static String safeId(String value) {
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
        return id.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : id;
    }

    private static String trimDescription(String value) {
        String description = value == null ? "" : value.trim();
        int max = CampBoardMod.config().maxDescriptionLength();
        return description.length() <= max ? description : description.substring(0, max);
    }
}
