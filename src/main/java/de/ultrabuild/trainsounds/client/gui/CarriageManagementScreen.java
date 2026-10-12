package de.ultrabuild.trainsounds.client.gui;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.ultrabuild.trainsounds.logic.EngineToggleCarrier;
import de.ultrabuild.trainsounds.network.packet.ToggleCarriageEngineC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CarriageManagementScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0xEE1a1a2e;
    private static final int GRID_COLOR = 0xFF1a4a7a;
    private static final int BORDER_COLOR = 0xFF4A90E2;

    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 70;

    private final Screen parent;
    private final List<CarriageContraptionEntity> carriages;
    private int currentCarriageIndex = 0;
    private Button toggleButton;

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;

    public CarriageManagementScreen(Screen parent, List<CarriageContraptionEntity> carriages, int startIndex) {
        super(Component.literal("TrainSounds"));
        this.parent = parent;
        this.carriages = new ArrayList<>(carriages);
        this.currentCarriageIndex = Math.max(0, Math.min(startIndex, this.carriages.size() - 1));
    }

    @Override
    protected void init() {
        // Calcul des coordonnées du petit rectangle centré au milieu de l'écran
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        panelRight = panelLeft + PANEL_WIDTH;
        panelBottom = panelTop + PANEL_HEIGHT;

        int centerX = this.width / 2;
        int buttonsY = panelBottom - 30;

        // Bouton précédent "<-"
        this.addRenderableWidget(Button.builder(Component.literal("<-"), button -> {
            if (currentCarriageIndex > 0) {
                currentCarriageIndex--;
                updateToggleButton();
            }
        }).bounds(panelLeft + 12, buttonsY, 35, 20).build());

        // Bouton ON/OFF au centre
        toggleButton = this.addRenderableWidget(Button.builder(Component.literal("Engine"), button -> {
            toggleCurrentCarriageEngine();
        }).bounds(centerX - 42, buttonsY, 84, 20).build());

        // Bouton suivant "->"
        this.addRenderableWidget(Button.builder(Component.literal("->"), button -> {
            if (currentCarriageIndex < carriages.size() - 1) {
                currentCarriageIndex++;
                updateToggleButton();
            }
        }).bounds(panelRight - 47, buttonsY, 35, 20).build());

        updateToggleButton();
    }

    private void updateToggleButton() {
        if (currentCarriageIndex >= 0 && currentCarriageIndex < carriages.size()) {
            CarriageContraptionEntity carriage = carriages.get(currentCarriageIndex);
            if (carriage instanceof EngineToggleCarrier carrier) {
                boolean isEngineOn = carrier.trainsounds$isEngineBuiltIn();
                Component text = isEngineOn
                        ? Component.literal("Engine: ON").withStyle(ChatFormatting.GREEN)
                        : Component.literal("Engine: OFF").withStyle(ChatFormatting.RED);
                toggleButton.setMessage(text);
            }
        }
    }

    private void toggleCurrentCarriageEngine() {
        if (currentCarriageIndex >= 0 && currentCarriageIndex < carriages.size()) {
            CarriageContraptionEntity carriage = carriages.get(currentCarriageIndex);
            if (carriage instanceof EngineToggleCarrier carrier) {
                sendTogglePacket(carriage.getId());
                carrier.trainsounds$toggleEngineBuiltIn();
                updateToggleButton();
            }
        }
    }

    private void sendTogglePacket(int entityId) {
        ToggleCarriageEngineC2SPacket packet = new ToggleCarriageEngineC2SPacket(entityId);
        PacketDistributor.sendToServer(packet);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // On dessine uniquement le petit rectangle "blueprint" sans flouter le reste de
        // l'écran
        drawBlueprintBox(context, panelLeft, panelTop, panelRight, panelBottom);
        drawBlueprintBorder(context, panelLeft, panelTop, panelRight, panelBottom);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Affichage du numéro du carriage centré dans la partie haute du rectangle
        if (!carriages.isEmpty()) {
            Component carriageText = Component
                    .literal("Voiture " + (currentCarriageIndex + 1) + " / " + carriages.size());
            context.drawCenteredString(this.font, carriageText, this.width / 2, panelTop + 14, 0xFFFFFF);
        }
    }

    private void drawBlueprintBox(GuiGraphics context, int x1, int y1, int x2, int y2) {
        context.fill(x1, y1, x2, y2, BACKGROUND_COLOR);

        // Quadrillage limité à l'intérieur du rectangle
        int gridSize = 15;
        for (int x = x1 + gridSize; x < x2; x += gridSize) {
            context.fill(x, y1, x + 1, y2, GRID_COLOR);
        }
        for (int y = y1 + gridSize; y < y2; y += gridSize) {
            context.fill(x1, y, x2, y + 1, GRID_COLOR);
        }
    }

    private void drawBlueprintBorder(GuiGraphics context, int x1, int y1, int x2, int y2) {
        // Bordure extérieure
        context.fill(x1 - 2, y1 - 2, x2 + 2, y1, BORDER_COLOR);
        context.fill(x1 - 2, y2, x2 + 2, y2 + 2, BORDER_COLOR);
        context.fill(x1 - 2, y1 - 2, x1, y2 + 2, BORDER_COLOR);
        context.fill(x2, y1 - 2, x2 + 2, y2 + 2, BORDER_COLOR);

        // Bordure intérieure
        context.fill(x1, y1, x2, y1 + 1, BORDER_COLOR);
        context.fill(x1, y2 - 1, x2, y2, BORDER_COLOR);
        context.fill(x1, y1, x1 + 1, y2, BORDER_COLOR);
        context.fill(x2 - 1, y1, x2, y2, BORDER_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        // Empêche le jeu de se mettre en pause lorsque l'interface est ouverte
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (currentCarriageIndex > 0) {
                currentCarriageIndex--;
                updateToggleButton();
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (currentCarriageIndex < carriages.size() - 1) {
                currentCarriageIndex++;
                updateToggleButton();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}