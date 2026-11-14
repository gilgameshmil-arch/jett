package dev.openai.jettmobility;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** HUD das habilidades: coluna na lateral direita da tela, só aparece depois do primeiro desbloqueio. */
public final class JettHud implements HudElement {
    private static final int W = 64;
    private static final int H = 22;
    private static final int GAP = 3;

    private static final int COLOR_BG = 0xB0101820;
    private static final int COLOR_READY = 0xFF7FE0FF;
    private static final int COLOR_COOLDOWN = 0xFF8899AA;
    private static final int COLOR_LOCKED = 0xFF555555;
    private static final int COLOR_SHADE = 0x90000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFF8A8A8A;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !JettClientState.anyUnlocked()) return;

        int total = Ability.VALUES.length * H + (Ability.VALUES.length - 1) * GAP;
        int x = mc.getWindow().getGuiScaledWidth() - W - 6;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - total / 2;

        for (Ability ability : Ability.VALUES) {
            boolean unlocked = JettClientState.isUnlocked(ability);
            int remaining = unlocked ? JettClientState.remainingTicks(ability) : 0;

            int border = !unlocked ? COLOR_LOCKED : remaining > 0 ? COLOR_COOLDOWN : COLOR_READY;
            graphics.fill(x, y, x + W, y + H, COLOR_BG);
            if (remaining > 0) {
                float fraction = Math.min(1f, remaining / (float) ability.cooldownTicks);
                graphics.fill(x + 1, y + 1, x + W - 1, y + 1 + Math.round((H - 2) * fraction), COLOR_SHADE);
            }
            graphics.fill(x, y, x + W, y + 1, border);
            graphics.fill(x, y + H - 1, x + W, y + H, border);
            graphics.fill(x, y, x + 1, y + H, border);
            graphics.fill(x + W - 1, y, x + W, y + H, border);

            int textColor = unlocked ? COLOR_TEXT : COLOR_TEXT_DIM;
            graphics.drawString(mc.font, Component.translatable(ability.nameKey()), x + 4, y + 3, textColor, false);

            Component status;
            if (!unlocked) {
                status = Component.translatable("hud.jettmobility.locked");
            } else if (remaining > 0) {
                status = Component.literal(String.format(Locale.ROOT, "%.1fs", remaining / 20f));
            } else {
                status = Component.translatable("hud.jettmobility.ready");
            }
            Component line = Component.literal("[")
                    .append(JettMobilityClient.keyFor(ability).getTranslatedKeyMessage())
                    .append("] ")
                    .append(status);
            graphics.drawString(mc.font, line, x + 4, y + 12, textColor, false);

            y += H + GAP;
        }
    }
}
