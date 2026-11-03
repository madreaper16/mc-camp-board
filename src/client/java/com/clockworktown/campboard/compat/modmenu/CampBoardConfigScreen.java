package com.clockworktown.campboard.compat.modmenu;

import com.clockworktown.campboard.client.BoardClientState;
import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.network.BoardActionPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public class CampBoardConfigScreen extends Screen {
    private final Screen parent;
    private final Map<AbstractWidget, Integer> controls = new LinkedHashMap<>();
    private CampBoardConfig config;
    private Button done;
    private int scroll;
    private int row;
    private boolean pending;

    public CampBoardConfigScreen(Screen parent) {
        super(Component.literal("Camp Board Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshConfig();
        BoardClientState.requestConfig();
    }

    public void refreshConfig() {
        config = CampBoardConfig.fromJson(BoardClientState.config().toJson());
        pending = false;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        controls.clear();
        row = 78;
        toggle("Crafting", config.craftingEnabled(), () -> config.setCraftingEnabled(!config.craftingEnabled()));
        toggle("All Boards Global", config.allBoardsGlobal(), () -> config.setAllBoardsGlobal(!config.allBoardsGlobal()));
        toggle("Large Boards", config.largeBoard(), () -> config.setLargeBoard(!config.largeBoard()));
        toggle("Breakable", config.breakable(), () -> config.setBreakable(!config.breakable()));
        addControl("Project Creation: " + ("ANYONE".equals(config.projectCreation()) ? "Anyone" : "Admins"),
                () -> config.setProjectCreation("ANYONE".equals(config.projectCreation()) ? "ADMIN_ONLY" : "ANYONE"));
        toggle("Suggestions", config.suggestionsEnabled(), () -> config.setSuggestionsEnabled(!config.suggestionsEnabled()));
        toggle("Materials", config.materialsEnabled(), () -> config.setMaterialsEnabled(!config.materialsEnabled()));
        toggle("Anyone Can Withdraw", config.anyoneCanWithdrawMaterials(), () -> config.setAnyoneCanWithdrawMaterials(!config.anyoneCanWithdrawMaterials()));
        toggle("Task Helping", config.taskHelpingEnabled(), () -> config.setTaskHelpingEnabled(!config.taskHelpingEnabled()));
        stepper("Max Leaders: " + config.maxProjectLeaders(),
                () -> config.setMaxProjectLeaders(config.maxProjectLeaders() - 1),
                () -> config.setMaxProjectLeaders(config.maxProjectLeaders() + 1));
        stepper("Description Length: " + config.maxDescriptionLength(),
                () -> config.setMaxDescriptionLength(config.maxDescriptionLength() - 25),
                () -> config.setMaxDescriptionLength(config.maxDescriptionLength() + 25));
        stepper("Backup Retention: " + config.backupRetention(),
                () -> config.setBackupRetention(config.backupRetention() - 1),
                () -> config.setBackupRetention(config.backupRetention() + 1));
        done = addRenderableWidget(Button.builder(Component.literal("Done"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
        layoutControls();
    }

    private void toggle(String name, boolean enabled, Runnable change) {
        addControl(name + ": " + (enabled ? "Enabled" : "Disabled"), change);
    }

    private void addControl(String name, Runnable change) {
        add(name, width / 2 - controlWidth() / 2, row, controlWidth(), change);
        row += 26;
    }

    private void stepper(String name, Runnable decrease, Runnable increase) {
        int left = width / 2 - controlWidth() / 2;
        add("-", left, row, 24, decrease);
        add(name, left + 28, row, controlWidth() - 56, null);
        add("+", left + controlWidth() - 24, row, 24, increase);
        row += 26;
    }

    private void add(String name, int x, int y, int w, Runnable change) {
        Button button = addRenderableWidget(Button.builder(Component.literal(name), pressed -> {
            if (!BoardClientState.editable() || pending || change == null) return;
            change.run();
            JsonObject action = new JsonObject();
            action.addProperty("type", "update_config");
            action.addProperty("expectedConfig", BoardClientState.config().toJson());
            action.add("config", JsonParser.parseString(config.toJson()));
            ClientPlayNetworking.send(new BoardActionPayload(action.toString()));
            pending = true;
            layoutControls();
        }).bounds(x, y, w, 20).build());
        button.active = change != null && BoardClientState.editable() && !pending;
        controls.put(button, y);
    }

    private int controlWidth() { return Math.min(300, width - 24); }

    private void layoutControls() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, row - (height - 40))));
        for (var entry : controls.entrySet()) {
            var widget = entry.getKey();
            widget.setY(entry.getValue() - scroll);
            widget.visible = widget.getY() >= 76 && widget.getY() + widget.getHeight() <= height - 40;
            if (pending) widget.active = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= 76 && mouseY < height - 40) {
            scroll -= (int) Math.signum(scrollY) * 26;
            layoutControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEF171717);
        centered(graphics, "Camp Board", 22);
        centered(graphics, "Projects, tasks, and shared materials", 40);
        centered(graphics, !BoardClientState.connected() ? "Server settings unavailable" : BoardClientState.editable() ? "Server Settings" : "Server Settings (Read Only)", 58);
        graphics.enableScissor(0, 76, width, height - 40);
        for (var widget : controls.keySet()) widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        done.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void centered(GuiGraphicsExtractor graphics, String value, int y) {
        graphics.text(font, value, (width - font.width(value)) / 2, y, 0xFFFFFFFF, false);
    }
}
