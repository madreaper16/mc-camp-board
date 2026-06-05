package com.clockworktown.campboard.compat.modmenu;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CampBoardConfigScreen extends Screen {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFBDBDBD;
    private final Screen parent;

    public CampBoardConfigScreen(Screen parent) {
        super(Component.literal("Camp Board Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int center = width / 2;
        int y = 78;

        addToggle(center, y, craftingLabel(), () -> {
            CampBoardMod.config().setCraftingEnabled(!CampBoardMod.config().craftingEnabled());
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, globalLabel(), () -> {
            CampBoardMod.config().setAllBoardsGlobal(!CampBoardMod.config().allBoardsGlobal());
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, projectCreationLabel(), () -> {
            CampBoardMod.config().setProjectCreation("ANYONE".equals(CampBoardMod.config().projectCreation()) ? "ADMIN_ONLY" : "ANYONE");
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, suggestionsLabel(), () -> {
            CampBoardMod.config().setSuggestionsEnabled(!CampBoardMod.config().suggestionsEnabled());
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, materialsLabel(), () -> {
            CampBoardMod.config().setMaterialsEnabled(!CampBoardMod.config().materialsEnabled());
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, withdrawLabel(), () -> {
            CampBoardMod.config().setAnyoneCanWithdrawMaterials(!CampBoardMod.config().anyoneCanWithdrawMaterials());
            saveAndRebuild();
        });
        y += 24;

        addToggle(center, y, taskHelpingLabel(), () -> {
            CampBoardMod.config().setTaskHelpingEnabled(!CampBoardMod.config().taskHelpingEnabled());
            saveAndRebuild();
        });
        y += 28;

        addStepper(center, y, "Max Leaders: " + CampBoardMod.config().maxProjectLeaders(), () -> {
            CampBoardMod.config().setMaxProjectLeaders(CampBoardMod.config().maxProjectLeaders() - 1);
            saveAndRebuild();
        }, () -> {
            CampBoardMod.config().setMaxProjectLeaders(CampBoardMod.config().maxProjectLeaders() + 1);
            saveAndRebuild();
        });
        y += 26;

        addStepper(center, y, "Description Length: " + CampBoardMod.config().maxDescriptionLength(), () -> {
            CampBoardMod.config().setMaxDescriptionLength(CampBoardMod.config().maxDescriptionLength() - 25);
            saveAndRebuild();
        }, () -> {
            CampBoardMod.config().setMaxDescriptionLength(CampBoardMod.config().maxDescriptionLength() + 25);
            saveAndRebuild();
        });
        y += 26;

        addStepper(center, y, backupLabel(), () -> {
            CampBoardMod.config().setBackupRetention(CampBoardMod.config().backupRetention() - 1);
            saveAndRebuild();
        }, () -> {
            CampBoardMod.config().setBackupRetention(CampBoardMod.config().backupRetention() + 1);
            saveAndRebuild();
        });

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> minecraft.setScreen(parent))
                .bounds(center - 50, height - 32, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
        centeredText(graphics, "Camp Board", width / 2, 24, TEXT_COLOR);
        centeredText(graphics, "Physical project boards for builds, tasks, suggestions, locations, and shared material needs.", width / 2, 44, MUTED_TEXT_COLOR);
        centeredText(graphics, "Config is saved to the current world/server when one is loaded.", width / 2, 58, MUTED_TEXT_COLOR);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void addToggle(int center, int y, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(center - 125, y, 250, 20)
                .build());
    }

    private void addStepper(int center, int y, String label, Runnable decrease, Runnable increase) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> decrease.run())
                .bounds(center - 125, y, 28, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(label), button -> {})
                .bounds(center - 91, y, 182, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> increase.run())
                .bounds(center + 97, y, 28, 20)
                .build());
    }

    private void saveAndRebuild() {
        CampBoardMod.saveQuietly();
        rebuild();
    }

    private String craftingLabel() {
        return "Crafting: " + (CampBoardMod.config().craftingEnabled() ? "Enabled" : "Disabled");
    }

    private String globalLabel() {
        return "All Boards Global: " + (CampBoardMod.config().allBoardsGlobal() ? "Enabled" : "Disabled");
    }

    private String projectCreationLabel() {
        return "Project Creation: " + ("ANYONE".equals(CampBoardMod.config().projectCreation()) ? "Anyone" : "Admin Only");
    }

    private String suggestionsLabel() {
        return "Suggestions: " + (CampBoardMod.config().suggestionsEnabled() ? "Enabled" : "Disabled");
    }

    private String materialsLabel() {
        return "Materials: " + (CampBoardMod.config().materialsEnabled() ? "Enabled" : "Disabled");
    }

    private String withdrawLabel() {
        return "Material Withdrawal: " + (CampBoardMod.config().anyoneCanWithdrawMaterials() ? "Anyone" : "Leaders/Admins");
    }

    private String taskHelpingLabel() {
        return "Task Helping: " + (CampBoardMod.config().taskHelpingEnabled() ? "Enabled" : "Disabled");
    }

    private String backupLabel() {
        return "Backup Retention: " + CampBoardMod.config().backupRetention();
    }

    private void centeredText(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, false);
    }
}
