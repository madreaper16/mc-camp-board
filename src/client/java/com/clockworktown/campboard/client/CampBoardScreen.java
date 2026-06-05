package com.clockworktown.campboard.client;

import com.clockworktown.campboard.CampBoardMod;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private static final int TAB_COLOR = 0xFFE4BE72;
    private static final int ACTIVE_TAB_COLOR = 0xFFFFE3A8;
    private static final int TAB_BORDER = 0xFF6B421F;

    private final String boardId;
    private BoardState boardState;
    private Tab tab = Tab.PROJECTS;
    private Mode mode = Mode.LIST;
    private String selectedProjectId;
    private String selectedSuggestionId;
    private String selectedMaterialId;
    private String transferType;
    private DetailPage detailPage = DetailPage.INFO;
    private int materialPage;
    private int taskPage;
    private final List<EditBox> fields = new ArrayList<>();

    public CampBoardScreen(String boardId, BoardState boardState) {
        super(Component.translatable("screen.camp_board.title"));
        this.boardId = boardId;
        this.boardState = boardState;
    }

    public void updateBoardState(String boardId, BoardState boardState) {
        if (!this.boardId.equals(boardId)) {
            return;
        }
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
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        int left = panelLeft();
        int top = panelTop();
        int width = panelWidth();
        int tabLeft = left + (width - 324) / 2;
        addButton("Projects", tabLeft, top + 44, 96, 24, () -> switchTab(Tab.PROJECTS));
        addButton("Suggestions", tabLeft + 104, top + 44, 116, 24, () -> switchTab(Tab.SUGGESTIONS));
        addButton("Archive", tabLeft + 228, top + 44, 96, 24, () -> switchTab(Tab.ARCHIVE));

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
            description.setMaxLength(CampBoardMod.config().maxDescriptionLength());
            addButton("Save", left + width - 72, top + 224, 56, 20, () -> sendProjectForm(id.getValue(), title.getValue(), description.getValue()));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, this::backToList);
        } else if (mode == Mode.SUGGESTION_FORM) {
            EditBox title = field(x, y, 240, "Title"); y += 30;
            EditBox description = field(x, y, 300, "Description");
            description.setMaxLength(CampBoardMod.config().maxDescriptionLength());
            addButton("Submit", left + width - 72, top + 224, 56, 20, () -> sendSuggestionForm(title.getValue(), description.getValue()));
            addButton("Cancel", left + width - 136, top + 224, 56, 20, this::backToList);
        } else if (mode == Mode.MATERIAL_FORM) {
            EditBox itemId = field(x, y, 220, "minecraft:spruce_planks"); y += 30;
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
            addButton("Assign", left + width - 72, top + 224, 56, 20, () -> action("assign_leader", selectedProjectId, object -> object.addProperty("player", name.getValue())));
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
        drawTabs(graphics, left + (width - 324) / 2, top + 44);
        graphics.fill(left, top + 78, left + width, top + 81, DIVIDER_COLOR);

        if (mode == Mode.LIST) {
            drawList(graphics, left + 16, top + 88, width - 32);
        } else {
            drawForm(graphics, left + 16, top + 88);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
        text(graphics, project.title() + " [" + project.status().displayName() + "]", left, top, HEADER_TEXT);
        text(graphics, "Leaders: " + leaderText(project), left + width - 150, top, MUTED_TEXT_COLOR);
        if (detailPage == DetailPage.INFO) {
            drawInfoPage(graphics, left, top + 28, width, project);
        } else if (detailPage == DetailPage.MATERIALS) {
            drawMaterialsPage(graphics, left, top + 28, width, project);
        } else {
            drawTasksPage(graphics, left, top + 28, width, project);
        }
        if (!project.activity().isEmpty()) {
            ActivityEntry entry = project.activity().get(project.activity().size() - 1);
            text(graphics, "Last: " + entry.action(), left, top + 176, MUTED_TEXT_COLOR);
        }
    }

    private void drawInfoPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        text(graphics, project.description().isBlank() ? "No description." : project.description(), left, top, TEXT_COLOR);
        BoardLocation loc = project.primaryLocation();
        if (loc != null) {
            BoardLocation pair = loc.convertedPair();
            text(graphics, loc.dimension() + ": " + loc.x() + ", " + loc.y() + ", " + loc.z(), left, top + 18, MUTED_TEXT_COLOR);
            text(graphics, pair.dimension() + ": " + pair.x() + ", " + pair.y() + ", " + pair.z(), left, top + 30, MUTED_TEXT_COLOR);
        }
    }

    private void drawMaterialsPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        int y = top;
        text(graphics, "Materials", left, y, HEADER_TEXT);
        y += 24;
        List<MaterialStack> materials = visibleMaterials(project);
        for (MaterialStack material : materials.stream().skip(materialPage * materialsPerPage()).limit(materialsPerPage()).toList()) {
            text(graphics, fit(material.itemId() + " " + material.stored() + "/" + material.requested(), width - 24), left, y, TEXT_COLOR);
            y += 42;
        }
        drawPageLabel(graphics, left + width - 78, top + 156, materialPage, pageCount(materials.size(), materialsPerPage()));
    }

    private void drawTasksPage(GuiGraphicsExtractor graphics, int left, int top, int width, Project project) {
        int y = top;
        text(graphics, "Tasks", left, y, HEADER_TEXT);
        y += 24;
        List<ProjectTask> tasks = project.tasks();
        for (ProjectTask task : tasks.stream().skip(taskPage * tasksPerPage()).limit(tasksPerPage()).toList()) {
            text(graphics, fit((task.completed() ? "[x] " : "[ ] ") + task.title() + " (" + task.helpers().size() + " helping)", width - 24), left, y, TEXT_COLOR);
            y += 42;
        }
        drawPageLabel(graphics, left + width - 78, top + 156, taskPage, pageCount(tasks.size(), tasksPerPage()));
    }

    private void drawSuggestionDetail(GuiGraphicsExtractor graphics, int left, int top, int width) {
        Suggestion suggestion = findSuggestion(selectedSuggestionId);
        if (suggestion == null) return;
        text(graphics, suggestion.title(), left, top, HEADER_TEXT);
        text(graphics, suggestion.description().isBlank() ? "No description." : suggestion.description(), left, top + 14, TEXT_COLOR);
        text(graphics, suggestion.supporters().size() + " supporter(s)", left, top + 30, MUTED_TEXT_COLOR);
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
            text(graphics, "Requested amount", left, y + 30, MUTED_TEXT_COLOR);
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
        }
    }

    private void openProject(String projectId) {
        selectedProjectId = projectId;
        selectedSuggestionId = null;
        mode = Mode.LIST;
        detailPage = DetailPage.INFO;
        materialPage = 0;
        taskPage = 0;
        rebuild();
    }

    private void openSuggestion(String suggestionId) {
        selectedSuggestionId = suggestionId;
        selectedProjectId = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void addProjectDetailButtons(int left, int top, int width) {
        int footerY = top + panelHeight() + 6;
        addButton("Back", left + 16, footerY, 44, 20, this::backToList);
        Project project = findProject(selectedProjectId);
        if (project == null) return;
        boolean readOnly = tab == Tab.ARCHIVE || project.status() == ProjectStatus.ARCHIVED || project.status() == ProjectStatus.COMPLETED;
        addButton("Info", left + 68, footerY, 42, 20, () -> switchDetailPage(DetailPage.INFO));
        addButton("Materials", left + 118, footerY, 68, 20, () -> switchDetailPage(DetailPage.MATERIALS));
        addButton("Tasks", left + 194, footerY, 50, 20, () -> switchDetailPage(DetailPage.TASKS));
        if (!readOnly) {
            addButton("Archive", left + 8, top + 3, 58, 18, () -> action("archive_project", selectedProjectId, object -> {}));
            addButton("Delete", left + width - 66, top + 3, 54, 18, () -> action("delete_project", selectedProjectId, object -> {}));
            if (detailPage == DetailPage.INFO) {
                addButton("Status", left + 252, footerY, 54, 20, () -> openForm(Mode.STATUS_FORM, selectedProjectId, null));
                addButton("Location", left + 314, footerY, 62, 20, () -> openForm(Mode.LOCATION_FORM, selectedProjectId, null));
                addButton("Leader", left + 252, footerY + 24, 54, 18, () -> openForm(Mode.LEADER_FORM, selectedProjectId, null));
            } else if (detailPage == DetailPage.MATERIALS) {
                addButton("Add Material", left + 252, footerY, 92, 20, () -> openForm(Mode.MATERIAL_FORM, selectedProjectId, null));
            } else if (detailPage == DetailPage.TASKS) {
                addButton("Add Task", left + 252, footerY, 70, 20, () -> openForm(Mode.TASK_FORM, selectedProjectId, null));
            }
        }
        if (detailPage == DetailPage.MATERIALS) {
            List<MaterialStack> materials = visibleMaterials(project);
            int start = materialPage * materialsPerPage();
            int y = top + 158;
            for (MaterialStack material : materials.stream().skip(start).limit(materialsPerPage()).toList()) {
                String itemId = material.itemId();
                if (!readOnly) {
                    addButton("+", left + 8, y, 16, 12, () -> openTransfer("contribute", itemId));
                    addButton("-", left + 30, y, 16, 12, () -> openTransfer("withdraw", itemId));
                }
                y += 42;
            }
            addPagerButtons(left, top, width, materialPage, pageCount(materials.size(), materialsPerPage()), true);
        } else if (detailPage == DetailPage.TASKS) {
            List<ProjectTask> tasks = project.tasks();
            int start = taskPage * tasksPerPage();
            int y = top + 158;
            for (ProjectTask task : tasks.stream().skip(start).limit(tasksPerPage()).toList()) {
                if (!readOnly) {
                    addButton("Help", left + 8, y, 28, 12, () -> taskAction("toggle_help", task.id(), null));
                    addButton("Done", left + 42, y, 28, 12, () -> taskAction("complete_task", task.id(), null));
                }
                y += 42;
            }
            addPagerButtons(left, top, width, taskPage, pageCount(tasks.size(), tasksPerPage()), false);
        }
    }

    private void addSuggestionDetailButtons(int left, int top) {
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
        selectedProjectId = null;
        selectedSuggestionId = null;
        mode = Mode.LIST;
        rebuild();
    }

    private void openForm(Mode next, String projectId, String suggestionId) {
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
        detailPage = page;
        rebuild();
    }

    private void addPagerButtons(int left, int top, int width, int currentPage, int pageCount, boolean materials) {
        if (pageCount <= 1) {
            return;
        }
        int y = top + panelHeight() - 28;
        addButton("<", left + width - 84, y, 28, 18, () -> {
            if (materials) {
                materialPage = Math.max(0, materialPage - 1);
            } else {
                taskPage = Math.max(0, taskPage - 1);
            }
            rebuild();
        });
        addButton(">", left + width - 48, y, 28, 18, () -> {
            if (materials) {
                materialPage = Math.min(pageCount - 1, materialPage + 1);
            } else {
                taskPage = Math.min(pageCount - 1, taskPage + 1);
            }
            rebuild();
        });
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
        EditBox field = new EditBox(font, x, y, width, 18, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setTextShadow(false);
        addRenderableWidget(field);
        fields.add(field);
        return field;
    }

    private void addButton(String label, int x, int y, int width, int height, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, width, height).build());
    }

    private List<Project> activeProjects() {
        return boardState.projects().stream().filter(project -> project.status() != ProjectStatus.ARCHIVED && project.status() != ProjectStatus.COMPLETED).toList();
    }

    private List<Project> archivedProjects() {
        return boardState.projects().stream().filter(project -> project.status() == ProjectStatus.ARCHIVED || project.status() == ProjectStatus.COMPLETED).toList();
    }

    private List<MaterialStack> visibleMaterials(Project project) {
        return project.materials().values().stream()
                .filter(material -> material.requested() > 0 && material.itemId() != null && !material.itemId().isBlank())
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

    private int pageCount(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private int materialsPerPage() {
        return 3;
    }

    private int tasksPerPage() {
        return 3;
    }

    private void drawPageLabel(GuiGraphicsExtractor graphics, int left, int y, int page, int pageCount) {
        if (pageCount > 1) {
            text(graphics, "Page " + (page + 1) + "/" + pageCount, left, y, MUTED_TEXT_COLOR);
        }
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

    private void drawTabs(GuiGraphicsExtractor graphics, int left, int top) {
        drawTab(graphics, Tab.PROJECTS, left, top, 96);
        drawTab(graphics, Tab.SUGGESTIONS, left + 104, top, 116);
        drawTab(graphics, Tab.ARCHIVE, left + 228, top, 96);
    }

    private void drawTab(GuiGraphicsExtractor graphics, Tab target, int left, int top, int tabWidth) {
        int fillColor = tab == target ? ACTIVE_TAB_COLOR : TAB_COLOR;
        graphics.fill(left, top, left + tabWidth, top + 24, TAB_BORDER);
        graphics.fill(left + 2, top + 2, left + tabWidth - 2, top + 22, fillColor);
        centeredText(graphics, target.label(), left + tabWidth / 2, top + 8, HEADER_TEXT);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, false);
    }

    private void centeredText(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, false);
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
        return Math.min(390, width - 28);
    }

    private int panelHeight() {
        return Math.min(280, height - 54);
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
