package com.clockworktown.campboard.client;

import com.clockworktown.campboard.data.ActivityEntry;
import com.clockworktown.campboard.data.BoardLocation;
import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.data.MaterialStack;
import com.clockworktown.campboard.data.Project;
import com.clockworktown.campboard.data.ProjectStatus;
import com.clockworktown.campboard.data.ProjectTask;
import com.clockworktown.campboard.data.Suggestion;
import com.clockworktown.campboard.network.BoardActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

public class CampBoardScreen extends Screen {
    private static final int PANEL_COLOR = 0xFFF1DCA8;
    private static final int PANEL_SHADOW = 0xAA1E140C;
    private static final int HEADER_BAR = 0xFF4A2E18;
    private static final int HEADER_BAR_DARK = 0xFF3B2110;
    private static final int HEADER_BAR_LIGHT = 0xFF6A3E1D;
    private static final int HEADER_TEXT = 0xFF2A170B;
    private static final int TEXT_COLOR = 0xFF2B1B10;
    private static final int MUTED_TEXT_COLOR = 0xFF5F4932;
    private static final int DIVIDER_COLOR = 0xFFB7833A;

    private final String boardId;
    private BoardState boardState;
    private Tab tab = Tab.PROJECTS;
    private Mode mode = Mode.LIST;
    private String selectedProjectId;
    private String selectedSuggestionId;
    private String selectedMaterialId;
    private String transferType;
    private DetailPage detailPage = DetailPage.INFO;
    private EditBox materialItemField;
    private List<String> materialSuggestions = List.of();
    private final List<EditBox> fields = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> contentWidgets = new LinkedHashMap<>();
    private boolean buildingContent;
    private int scrollOffset;

    public CampBoardScreen(String boardId, BoardState boardState) {
        super(Component.translatable("screen.camp_board.title"));
        this.boardId = boardId;
        this.boardState = boardState;
    }

    public void updateBoardState(String boardId, BoardState boardState) {
        if (!this.boardId.equals(boardId)) {
            return;
        }
        List<String> draft = mode == Mode.LIST ? List.of() : fields.stream().map(EditBox::getValue).toList();
        int focused = -1;
        for (int index = 0; index < fields.size(); index++) if (fields.get(index).isFocused()) focused = index;
        this.boardState = boardState;
        if (selectedProjectId != null && findProject(selectedProjectId) == null) {
            selectedProjectId = null;
            mode = Mode.LIST;
        }
        if (selectedSuggestionId != null && findSuggestion(selectedSuggestionId) == null) {
            selectedSuggestionId = null;
            mode = Mode.LIST;
        }
        rebuild();
        for (int index = 0; index < Math.min(draft.size(), fields.size()); index++) fields.get(index).setValue(draft.get(index));
        if (focused >= 0 && focused < fields.size()) setFocused(fields.get(focused));
    }

    public String boardId() { return boardId; }

    @Override
    public void removed() {
        setFocused(null);
        if (BoardClientState.connected()) {
            JsonObject close = base("close_board");
            ClientPlayNetworking.send(new BoardActionPayload(close.toString()));
        }
        super.removed();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        fixedWidgets.clear();
        contentWidgets.clear();
        buildingContent = false;
        fields.clear();
        materialItemField = null;
        materialSuggestions = List.of();
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        int tabWidth = (width - 32) / 3;
        addButton("Projects", left + 8, top + 44, tabWidth, 24, () -> switchTab(Tab.PROJECTS));
        addButton("Suggestions", left + 16 + tabWidth, top + 44, tabWidth, 24, () -> switchTab(Tab.SUGGESTIONS));
        addButton("Archive", left + 24 + tabWidth * 2, top + 44, tabWidth, 24, () -> switchTab(Tab.ARCHIVE));
        buildingContent = true;

        if (mode == Mode.LIST) {
            if (selectedProjectId != null) {
                addProjectDetailButtons(left, top, width);
            } else if (selectedSuggestionId != null) {
                addSuggestionDetailButtons(left, top);
            } else {
                addListButtons(left, top, width);
            }
        } else {
            addFormWidgets(left, top, width);
        }
        int footerX = left + 8;
        int footerY = top + panelHeight() + 6;
        int rowY = footerY;
        for (AbstractWidget widget : fixedWidgets) {
            if (widget.getY() < footerY) continue;
            if (footerX + widget.getWidth() > left + width - 8) { footerX = left + 8; rowY += 24; }
            widget.setX(footerX);
            widget.setY(rowY);
            footerX += widget.getWidth() + 6;
        }
        layoutContent();
    }

    private void addListButtons(int left, int top, int width) {
        int y = top + 112;
        if (tab == Tab.PROJECTS) {
            addButton("New Project", left + width - 116, top + 88, 100, 20, () -> openForm(Mode.PROJECT_FORM, null, null));
            for (Project project : activeProjects()) {
                addButton(project.title(), left + 16, y, 136, 20, () -> openProject(project.id()));
                y += 24;
            }
        } else if (tab == Tab.SUGGESTIONS) {
            addButton("New Suggestion", left + width - 132, top + 88, 116, 20, () -> openForm(Mode.SUGGESTION_FORM, null, null));
            for (Suggestion suggestion : boardState.suggestions()) {
                addButton(suggestion.title(), left + 16, y, 136, 20, () -> openSuggestion(suggestion.id()));
                y += 24;
            }
        } else {
            for (Project project : archivedProjects()) {
                addButton(project.title(), left + 16, y, 136, 20, () -> openProject(project.id()));
                y += 24;
            }
        }
    }

    private void addFormWidgets(int left, int top, int width) {
        int x = left + 16;
        int y = top + 116;
        if (mode == Mode.PROJECT_FORM) {
            EditBox id = field(x, y, 130, "project_id"); y += 30;
            EditBox title = field(x, y, 240, "Title"); y += 30;
            EditBox description = field(x, y, 300, "Description");
            description.setMaxLength(BoardClientState.config().maxDescriptionLength());
            addButton("Save", left + width - 72, top + 224, 56, 20, () -> sendProjectForm(id.getValue(), title.getValue(), description.getValue()));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, this::backToList);
        } else if (mode == Mode.SUGGESTION_FORM) {
            EditBox title = field(x, y, 240, "Title"); y += 30;
            EditBox description = field(x, y, 300, "Description");
            description.setMaxLength(BoardClientState.config().maxDescriptionLength());
            addButton("Submit", left + width - 72, top + 224, 56, 20, () -> sendSuggestionForm(title.getValue(), description.getValue()));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, this::backToList);
        } else if (mode == Mode.MATERIAL_FORM) {
            EditBox itemId = field(x, y, 300, "minecraft:spruce_planks");
            materialItemField = itemId;
            itemId.setResponder(value -> materialSuggestions = itemSuggestions(value));
            materialSuggestions = itemSuggestions(itemId.getValue());
            y += 88;
            EditBox amount = field(x, y, 80, "128");
            addButton("Save", left + width - 72, top + 224, 56, 20, () -> action("add_material", selectedProjectId, object -> {
                object.addProperty("itemId", itemId.getValue());
                object.addProperty("requested", parseInt(amount.getValue()));
            }));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, () -> openProject(selectedProjectId));
        } else if (mode == Mode.MATERIAL_TRANSFER_FORM) {
            MaterialStack material = currentMaterial();
            int max = material == null ? 0 : ("withdraw".equals(transferType) ? material.stored() : material.remaining());
            EditBox amount = field(x, y, 100, "1-" + max);
            addButton("Confirm", left + width - 82, top + 224, 66, 20, () -> action(transferType, selectedProjectId, object -> {
                object.addProperty("itemId", selectedMaterialId);
                object.addProperty("amount", Math.max(1, Math.min(parseInt(amount.getValue()), max)));
            }));
            addButton("Cancel", left + width - 146, top + 224, 56, 20, () -> openProject(selectedProjectId));
        } else if (mode == Mode.TASK_FORM) {
            EditBox task = field(x, y, 260, "Task title");
            addButton("Save", left + width - 72, top + 224, 56, 20, () -> action("add_task", selectedProjectId, object -> object.addProperty("title", task.getValue())));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, () -> openProject(selectedProjectId));
        } else if (mode == Mode.LOCATION_FORM) {
            EditBox dim = field(x, y, 180, "minecraft:overworld"); y += 30;
            EditBox xField = field(x, y, 64, "x");
            EditBox yField = field(x + 72, y, 64, "y");
            EditBox zField = field(x + 144, y, 64, "z");
            addButton("Save", left + width - 72, top + 224, 56, 20, () -> action("set_location", selectedProjectId, object -> {
                object.addProperty("dimension", dim.getValue());
                object.addProperty("x", parseInt(xField.getValue()));
                object.addProperty("y", parseInt(yField.getValue()));
                object.addProperty("z", parseInt(zField.getValue()));
            }));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, () -> openProject(selectedProjectId));
        } else if (mode == Mode.LEADER_FORM) {
            EditBox name = field(x, y, 160, "Leader name");
            EditBox identity = field(x, y + 36, 260, "Minecraft name or UUID");
            addButton("Assign", left + width - 72, top + 224, 56, 20, () -> action("assign_leader", selectedProjectId, object -> {
                object.addProperty("player", name.getValue());
                object.addProperty("identity", identity.getValue());
            }));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, () -> openProject(selectedProjectId));
        } else if (mode == Mode.STATUS_FORM) {
            int buttonY = y;
            for (ProjectStatus status : ProjectStatus.values()) {
                addButton(status.displayName(), x, buttonY, 120, 20, () -> {
                    mode = Mode.LIST;
                    action("set_status", selectedProjectId, object -> object.addProperty("status", status.name()));
                });
                buttonY += 24;
            }
            addButton("Cancel", left + width - 72, top + 224, 56, 20, () -> openProject(selectedProjectId));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        int height = panelHeight();
        graphics.fill(left + 4, top + 4, left + width + 4, top + height + 4, PANEL_SHADOW);
        graphics.fill(left, top, left + width, top + height, PANEL_COLOR);
        drawHeader(graphics, left, top, width);
        centeredText(graphics, title.getString(), this.width / 2, top + 8, 0xFFFFF2D0);
        graphics.fill(left, top + 78, left + width, top + 81, DIVIDER_COLOR);
        graphics.enableScissor(left + 4, contentTop(), left + width - 4, contentBottom());
        if (mode == Mode.LIST) {
            drawList(graphics, left + 16, top + 88 - scrollOffset, width - 32);
        } else {
            drawForm(graphics, left + 16, top + 88 - scrollOffset);
        }
        for (var widget : contentWidgets.keySet()) widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawMaterialSuggestions(graphics);
        graphics.disableScissor();
        for (var widget : fixedWidgets) widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (maxContentScroll() > 0) {
            int track = contentBottom() - contentTop();
            int thumb = Math.max(12, track * track / (track + maxContentScroll()));
            int y = contentTop() + (track - thumb) * scrollOffset / maxContentScroll();
            graphics.fill(left + width - 5, y, left + width - 3, y + thumb, DIVIDER_COLOR);
        }
    }

    private void drawList(GuiGraphicsExtractor graphics, int left, int top, int width) {
        if (selectedProjectId != null) {
            drawProjectDetail(graphics, left, top, width);
            return;
        }
        if (selectedSuggestionId != null) {
            drawSuggestionDetail(graphics, left, top, width);
            return;
        }
        text(graphics, tab.label(), left, top, HEADER_TEXT);
        if (tab == Tab.PROJECTS && activeProjects().isEmpty()) text(graphics, "No official projects yet.", left, top + 18, MUTED_TEXT_COLOR);
        if (tab == Tab.SUGGESTIONS && boardState.suggestions().isEmpty()) text(graphics, "No suggestions yet.", left, top + 18, MUTED_TEXT_COLOR);
        if (tab == Tab.ARCHIVE && archivedProjects().isEmpty()) text(graphics, "No archived projects yet.", left, top + 18, MUTED_TEXT_COLOR);
    }

    private void drawProjectDetail(GuiGraphicsExtractor graphics, int left, int top, int width) {
        Project project = findProject(selectedProjectId);
        if (project == null) return;
        text(graphics, fit(project.title() + " [" + project.status().displayName() + "]", width), left, top, HEADER_TEXT);
        text(graphics, fit("Leaders: " + leaderText(project), width), left, top + 14, MUTED_TEXT_COLOR);
        if (detailPage == DetailPage.INFO) {
            drawInfoPage(graphics, left, top + 28, width, project);
        } else if (detailPage == DetailPage.MATERIALS) {
            drawMaterialsPage(graphics, left, top + 28, width, project);
        } else {
            drawTasksPage(graphics, left, top + 28, width, project);
        }
        if (!project.activity().isEmpty()) {
            ActivityEntry entry = project.activity().get(project.activity().size() - 1);
            int rows = detailPage == DetailPage.MATERIALS ? visibleMaterials(project).size() : project.tasks().size();
            int lastY = detailPage == DetailPage.INFO ? top + 108 : top + 76 + rows * 42;
            text(graphics, fit("Last: " + entry.action(), width), left, lastY, MUTED_TEXT_COLOR);
        }
    }

    private void drawInfoPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        int lineY = top;
        for (var line : font.split(Component.literal(project.description().isBlank() ? "No description." : project.description()), width)) {
            graphics.text(font, line, left, lineY, TEXT_COLOR, false);
            lineY += 10;
        }
        BoardLocation loc = project.primaryLocation();
        if (loc != null) {
            BoardLocation pair = loc.convertedPair();
            text(graphics, fit(loc.dimension() + ": " + loc.x() + ", " + loc.y() + ", " + loc.z(), width), left, lineY + 8, MUTED_TEXT_COLOR);
            text(graphics, fit(pair.dimension() + ": " + pair.x() + ", " + pair.y() + ", " + pair.z(), width), left, lineY + 20, MUTED_TEXT_COLOR);
        }
    }

    private void drawMaterialsPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        int y = top;
        text(graphics, "Materials", left, y, HEADER_TEXT);
        y += 24;
        List<MaterialStack> materials = visibleMaterials(project);
        for (MaterialStack material : materials) {
            text(graphics, fit(material.itemId() + " " + material.stored() + "/" + material.requested(), width - 24), left, y, TEXT_COLOR);
            y += 42;
        }
    }

    private void drawTasksPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        int y = top;
        text(graphics, "Tasks", left, y, HEADER_TEXT);
        y += 24;
        List<ProjectTask> tasks = project.tasks();
        for (ProjectTask task : tasks) {
            text(graphics, fit((task.completed() ? "[x] " : "[ ] ") + task.title() + " (" + task.helpers().size() + " helping)", width - 24), left, y, TEXT_COLOR);
            y += 42;
        }
    }

    private void drawSuggestionDetail(GuiGraphicsExtractor graphics, int left, int top, int width) {
        Suggestion suggestion = findSuggestion(selectedSuggestionId);
        if (suggestion == null) return;
        text(graphics, suggestion.title(), left, top, HEADER_TEXT);
        int y = top + 14;
        for (var line : font.split(Component.literal(suggestion.description().isBlank() ? "No description." : suggestion.description()), width)) {
            graphics.text(font, line, left, y, TEXT_COLOR, false);
            y += 10;
        }
        text(graphics, suggestion.supporters().size() + " supporter(s)", left, y + 8, MUTED_TEXT_COLOR);
    }

    private void drawForm(GuiGraphicsExtractor graphics, int left, int top) {
        String title = switch (mode) {
            case PROJECT_FORM -> "Create Project";
            case SUGGESTION_FORM -> "Create Suggestion";
            case MATERIAL_FORM -> "Add Material";
            case MATERIAL_TRANSFER_FORM -> "withdraw".equals(transferType) ? "Remove Material From Camp Board" : "Add Material From Inventory";
            case TASK_FORM -> "Add Task";
            case LOCATION_FORM -> "Set Location";
            case LEADER_FORM -> "Assign Leader";
            case STATUS_FORM -> "Set Status";
            default -> "";
        };
        text(graphics, title, left, top, HEADER_TEXT);
        int y = top + 18;
        if (mode == Mode.PROJECT_FORM) {
            text(graphics, "Project ID", left, y, MUTED_TEXT_COLOR);
            text(graphics, "Title", left, y + 30, MUTED_TEXT_COLOR);
            text(graphics, "Description", left, y + 60, MUTED_TEXT_COLOR);
        } else if (mode == Mode.SUGGESTION_FORM) {
            text(graphics, "Title", left, y, MUTED_TEXT_COLOR);
            text(graphics, "Description", left, y + 30, MUTED_TEXT_COLOR);
        } else if (mode == Mode.MATERIAL_FORM) {
            text(graphics, "Item ID", left, y, MUTED_TEXT_COLOR);
            text(graphics, "Requested amount", left, y + 88, MUTED_TEXT_COLOR);
        } else if (mode == Mode.MATERIAL_TRANSFER_FORM) {
            MaterialStack material = currentMaterial();
            int max = material == null ? 0 : ("withdraw".equals(transferType) ? material.stored() : material.remaining());
            text(graphics, selectedMaterialId + " available: " + max, left, y, MUTED_TEXT_COLOR);
            text(graphics, "Amount", left, y + 18, MUTED_TEXT_COLOR);
        } else if (mode == Mode.TASK_FORM) {
            text(graphics, "Task title", left, y, MUTED_TEXT_COLOR);
        } else if (mode == Mode.LOCATION_FORM) {
            text(graphics, "Dimension", left, y, MUTED_TEXT_COLOR);
            text(graphics, "Coordinates", left, y + 30, MUTED_TEXT_COLOR);
        } else if (mode == Mode.LEADER_FORM) {
            text(graphics, "Leader name", left, y, MUTED_TEXT_COLOR);
            text(graphics, "Player identity (optional)", left, y + 36, MUTED_TEXT_COLOR);
        }
    }

    private void openProject(String projectId) {
        scrollOffset = 0;
        selectedProjectId = projectId;
        selectedSuggestionId = null;
        mode = Mode.LIST;
        detailPage = DetailPage.INFO;
        rebuild();
    }

    private void openSuggestion(String suggestionId) {
        scrollOffset = 0;
        selectedSuggestionId = suggestionId;
        selectedProjectId = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void addProjectDetailButtons(int left, int top, int width) {
        buildingContent = false;
        int footerY = top + panelHeight() + 6;
        addButton("Back", left + 16, footerY, 44, 20, this::backToList);
        Project project = findProject(selectedProjectId);
        if (project == null) return;
        boolean readOnly = tab == Tab.ARCHIVE || project.status() == ProjectStatus.ARCHIVED || project.status() == ProjectStatus.COMPLETED;
        addButton("Info", left + 68, footerY, 42, 20, () -> switchDetailPage(DetailPage.INFO));
        addButton("Materials", left + 118, footerY, 68, 20, () -> switchDetailPage(DetailPage.MATERIALS));
        addButton("Tasks", left + 194, footerY, 50, 20, () -> switchDetailPage(DetailPage.TASKS));
        if (readOnly && BoardClientState.editable()) {
            addButton("Status", left + 252, footerY, 54, 20, () -> openForm(Mode.STATUS_FORM, selectedProjectId, null));
            addButton("Delete", left + width - 66, top + 3, 54, 18, () -> action("delete_project", selectedProjectId, object -> {}));
        }
        if (!readOnly) {
            addButton("Archive", left + 8, top + 3, 58, 18, () -> action("archive_project", selectedProjectId, object -> {}));
            addButton("Delete", left + width - 66, top + 3, 54, 18, () -> action("delete_project", selectedProjectId, object -> {}));
            if (detailPage == DetailPage.INFO) {
                addButton("Status", left + 252, footerY, 54, 20, () -> openForm(Mode.STATUS_FORM, selectedProjectId, null));
                addButton("Location", left + 314, footerY, 62, 20, () -> openForm(Mode.LOCATION_FORM, selectedProjectId, null));
                addButton("Leader", left + 16, footerY + 24, 54, 18, () -> openForm(Mode.LEADER_FORM, selectedProjectId, null));
            } else if (detailPage == DetailPage.MATERIALS) {
                addButton("Add Material", left + 252, footerY, 92, 20, () -> openForm(Mode.MATERIAL_FORM, selectedProjectId, null));
            } else if (detailPage == DetailPage.TASKS) {
                addButton("Add Task", left + 252, footerY, 70, 20, () -> openForm(Mode.TASK_FORM, selectedProjectId, null));
            }
        }
        buildingContent = true;
        if (detailPage == DetailPage.MATERIALS) {
            List<MaterialStack> materials = visibleMaterials(project);
            int y = top + 158;
            for (MaterialStack material : materials) {
                String itemId = material.itemId();
                addButton("-", left + 8, y, 16, 12, () -> openTransfer("withdraw", itemId));
                if (!readOnly) {
                    addButton("+", left + 30, y, 16, 12, () -> openTransfer("contribute", itemId));
                }
                y += 42;
            }
        } else if (detailPage == DetailPage.TASKS) {
            List<ProjectTask> tasks = project.tasks();
            int y = top + 158;
            for (ProjectTask task : tasks) {
                if (!readOnly) {
                    addButton("Help", left + 16, y, 34, 14, () -> taskAction("toggle_help", task.id(), null));
                    addButton("Done", left + 56, y, 36, 14, () -> taskAction("complete_task", task.id(), null));
                }
                y += 42;
            }
        }
    }

    private void addSuggestionDetailButtons(int left, int top) {
        buildingContent = false;
        top = panelTop() + panelHeight() + 6 - 202;
        addButton("Back", left + 16, top + 202, 44, 20, this::backToList);
        addButton("Support", left + 68, top + 202, 62, 20, () -> suggestionAction("support_suggestion"));
        addButton("Approve", left + 138, top + 202, 62, 20, () -> suggestionAction("approve_suggestion"));
        addButton("Delete", left + 208, top + 202, 54, 20, () -> suggestionAction("delete_suggestion"));
    }

    private void switchTab(Tab next) {
        tab = next;
        backToList();
    }

    private void backToList() {
        scrollOffset = 0;
        selectedProjectId = null;
        selectedSuggestionId = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void openForm(Mode next, String projectId, String suggestionId) {
        scrollOffset = 0;
        mode = next;
        selectedProjectId = projectId;
        selectedSuggestionId = suggestionId;
        rebuild();
    }

    private void openTransfer(String type, String itemId) {
        transferType = type;
        selectedMaterialId = itemId;
        openForm(Mode.MATERIAL_TRANSFER_FORM, selectedProjectId, null);
    }

    private void switchDetailPage(DetailPage page) {
        scrollOffset = 0;
        detailPage = page;
        rebuild();
    }

    private void sendProjectForm(String id, String title, String description) {
        JsonObject object = base("create_project");
        object.addProperty("id", id);
        object.addProperty("title", title);
        object.addProperty("description", description);
        mode = Mode.LIST;
        send(object);
    }

    private void sendSuggestionForm(String title, String description) {
        JsonObject object = base("create_suggestion");
        object.addProperty("title", title);
        object.addProperty("description", description);
        mode = Mode.LIST;
        send(object);
    }

    private void taskAction(String type, String taskId, UUID helper) {
        action(type, selectedProjectId, object -> {
            object.addProperty("taskId", taskId);
            Project project = findProject(selectedProjectId);
            ProjectTask task = project.tasks().stream().filter(value -> value.id().equals(taskId)).findFirst().orElseThrow();
            if ("complete_task".equals(type)) object.addProperty("completed", !task.completed());
            if ("toggle_help".equals(type)) object.addProperty("helping", !task.helpers().contains(minecraft.player.getUUID()));
            if (helper != null) object.addProperty("helper", helper.toString());
        });
    }

    private void suggestionAction(String type) {
        JsonObject object = base(type);
        object.addProperty("suggestionId", selectedSuggestionId);
        send(object);
    }

    private void action(String type, String projectId, java.util.function.Consumer<JsonObject> customizer) {
        JsonObject object = base(type);
        object.addProperty("projectId", projectId);
        customizer.accept(object);
        if (!"archive_project".equals(type) && !"delete_project".equals(type)) {
            mode = Mode.LIST;
        }
        send(object);
    }

    private JsonObject base(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("boardId", boardId);
        return object;
    }

    private void send(JsonObject object) {
        ClientPlayNetworking.send(new BoardActionPayload(object.toString()));
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox field = new EditBox(font, x, y, Math.max(20, Math.min(width, panelLeft() + panelWidth() - 16 - x)), 18, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setMaxLength(256);
        field.setTextShadow(false);
        track(field, true);
        fields.add(field);
        return field;
    }

    private void addButton(String label, int x, int y, int width, int height, Runnable action) {
        boolean formFooter = mode != Mode.LIST && List.of("Save", "Cancel", "Assign", "Confirm", "Submit").contains(label);
        if (formFooter) y = panelTop() + panelHeight() + 6;
        int right = panelLeft() + panelWidth() - 8;
        x = Math.min(x, right - width);
        track(Button.builder(Component.literal(fit(label, width - 6)), button -> action.run()).bounds(x, y, width, height).build(), buildingContent && !formFooter);
    }

    private void track(AbstractWidget widget, boolean content) {
        addRenderableWidget(widget);
        if (content) contentWidgets.put(widget, widget.getY());
        else fixedWidgets.add(widget);
    }

    private int contentTop() { return panelTop() + 84; }
    private int contentBottom() { return panelTop() + panelHeight() - 4; }

    private int maxContentScroll() {
        int bottom = contentTop();
        for (var entry : contentWidgets.entrySet()) bottom = Math.max(bottom, entry.getValue() + entry.getKey().getHeight() + 8);
        if (mode == Mode.LIST && selectedProjectId != null) {
            Project project = findProject(selectedProjectId);
            if (project != null) {
                int rows = detailPage == DetailPage.MATERIALS ? visibleMaterials(project).size() : project.tasks().size();
                bottom = Math.max(bottom, panelTop() + (detailPage == DetailPage.INFO ? 260 : 180 + rows * 42));
            }
        }
        if (mode == Mode.LIST && selectedSuggestionId != null) bottom = Math.max(bottom, panelTop() + 210);
        return Math.max(0, bottom - contentBottom());
    }

    private void layoutContent() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxContentScroll()));
        for (var entry : contentWidgets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            widget.setY(entry.getValue() - scrollOffset);
            widget.visible = widget.getY() >= contentTop() && widget.getY() + widget.getHeight() <= contentBottom();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= contentTop() && mouseY <= contentBottom()) {
            scrollOffset -= (int) Math.signum(scrollY) * 28;
            layoutContent();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (selectMaterialSuggestion(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_TAB && completeMaterialSuggestion()) {
            return true;
        }
        return super.keyPressed(event);
    }

    private List<Project> activeProjects() {
        return boardState.projects().stream().filter(project -> project.status() != ProjectStatus.ARCHIVED && project.status() != ProjectStatus.COMPLETED).toList();
    }

    private List<Project> archivedProjects() {
        return boardState.projects().stream().filter(project -> project.status() == ProjectStatus.ARCHIVED || project.status() == ProjectStatus.COMPLETED).toList();
    }

    private List<MaterialStack> visibleMaterials(Project project) {
        return project.materials().values().stream()
                .filter(material -> (material.requested() > 0 || material.stored() > 0) && material.itemId() != null && !material.itemId().isBlank())
                .toList();
    }

    private Project findProject(String id) {
        return boardState.findProject(id).orElse(null);
    }

    private Suggestion findSuggestion(String id) {
        return boardState.suggestions().stream().filter(suggestion -> suggestion.id().equals(id)).findFirst().orElse(null);
    }

    private MaterialStack currentMaterial() {
        Project project = findProject(selectedProjectId);
        return project == null || selectedMaterialId == null ? null : project.materials().get(selectedMaterialId);
    }

    private String leaderText(Project project) {
        if (project.leaders().isEmpty()) {
            return "None";
        }
        return project.leaders().stream()
                .limit(3)
                .map(id -> project.leaderNames().getOrDefault(id, id.toString().substring(0, 8)))
                .reduce((left, right) -> left + ", " + right)
                .orElse("None");
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String current = value;
        while (!current.isEmpty() && font.width(current) + suffixWidth > maxWidth) {
            current = current.substring(0, current.length() - 1);
        }
        return current + suffix;
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int left, int top, int panelWidth) {
        graphics.fill(left, top, left + panelWidth, top + 4, HEADER_BAR_DARK);
        graphics.fill(left, top + 4, left + panelWidth, top + 8, HEADER_BAR);
        graphics.fill(left, top + 8, left + panelWidth, top + 12, HEADER_BAR_LIGHT);
        graphics.fill(left, top + 12, left + panelWidth, top + 16, HEADER_BAR);
        graphics.fill(left, top + 16, left + panelWidth, top + 20, HEADER_BAR);
        graphics.fill(left, top + 20, left + panelWidth, top + 24, HEADER_BAR_DARK);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, false);
    }

    private void centeredText(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    private List<String> itemSuggestions(String value) {
        String query = value == null ? "" : value.trim().toLowerCase();
        if (query.length() < 2) {
            return List.of();
        }
        return BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString)
                .filter(id -> id.contains(query))
                .limit(5)
                .toList();
    }

    private void drawMaterialSuggestions(GuiGraphicsExtractor graphics) {
        if (mode != Mode.MATERIAL_FORM || materialItemField == null || materialSuggestions.isEmpty()) {
            return;
        }
        int left = materialSuggestionLeft();
        int top = materialItemField.getY() + 22;
        int width = materialSuggestionWidth();
        int y = top;
        for (String suggestion : materialSuggestions) {
            graphics.fill(left, y, left + width, y + 12, 0xEE2B1B10);
            text(graphics, fit(suggestion, width - 6), left + 3, y + 2, 0xFFFFE3A8);
            y += 12;
        }
    }

    private boolean selectMaterialSuggestion(double mouseX, double mouseY) {
        if (mouseY < contentTop() || mouseY > contentBottom()) return false;
        if (mode != Mode.MATERIAL_FORM || materialItemField == null || materialSuggestions.isEmpty()) {
            return false;
        }
        int left = materialSuggestionLeft();
        int top = materialItemField.getY() + 22;
        int width = materialSuggestionWidth();
        for (int index = 0; index < materialSuggestions.size(); index++) {
            int y = top + index * 12;
            if (mouseX >= left && mouseX <= left + width && mouseY >= y && mouseY <= y + 12) {
                materialItemField.setValue(materialSuggestions.get(index));
                materialSuggestions = List.of();
                return true;
            }
        }
        return false;
    }

    private int materialSuggestionLeft() {
        return materialItemField.getX();
    }

    private int materialSuggestionWidth() {
        return materialItemField.getWidth();
    }

    private boolean completeMaterialSuggestion() {
        if (mode != Mode.MATERIAL_FORM || materialItemField == null || materialSuggestions.isEmpty()) {
            return false;
        }
        materialItemField.setValue(materialSuggestions.getFirst());
        materialSuggestions = List.of();
        return true;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return 12;
    }

    private int panelWidth() {
        return Math.min(430, width - 16);
    }

    private int panelHeight() {
        return Math.max(110, height - 76);
    }

    private enum Tab {
        PROJECTS("Projects"),
        SUGGESTIONS("Suggestions"),
        ARCHIVE("Archive");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum Mode {
        LIST,
        PROJECT_FORM,
        SUGGESTION_FORM,
        MATERIAL_FORM,
        TASK_FORM,
        LOCATION_FORM,
        LEADER_FORM,
        STATUS_FORM,
        MATERIAL_TRANSFER_FORM
    }

    private enum DetailPage {
        INFO("Info"),
        MATERIALS("Materials"),
        TASKS("Tasks");

        private final String label;

        DetailPage(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }
}
