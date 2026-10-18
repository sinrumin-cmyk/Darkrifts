package com.darkrifts.client;

import com.darkrifts.ability.Abilities;
import com.darkrifts.ability.Ability;
import com.darkrifts.net.SelectAbilityPayload;
import com.darkrifts.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Radial ability menu: abilities sit on a circle, clockwise from the top. Move the cursor (or tap) onto one:
 * releasing the key or clicking selects it. Escape closes without changing anything.
 */
public class AbilityScreen extends Screen {
    private int hovered = -1;

    public AbilityScreen() {
        super(Component.translatable("key.darkrifts.abilities"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack icon(Ability a) {
        return switch (a) {
            case RIFT -> new ItemStack(ModItems.RIFT_BLADE.get());
            case SPIKES -> new ItemStack(Items.POINTED_DRIPSTONE);
            case STEP -> new ItemStack(Items.ENDER_PEARL);
            case PULL -> new ItemStack(Items.CRYING_OBSIDIAN);
            case CLOAK -> new ItemStack(Items.PHANTOM_MEMBRANE);
            case SEAL -> new ItemStack(Items.ENDER_EYE);
            case WAVE -> new ItemStack(Items.AMETHYST_SHARD);
            case SLASH -> new ItemStack(Items.NETHERITE_SWORD);
            case STRIKE -> new ItemStack(Items.LIGHTNING_ROD);
            case DRAIN -> new ItemStack(Items.FERMENTED_SPIDER_EYE);
            case SHIELD -> new ItemStack(Items.SHIELD);
        };
    }

    private int radius() {
        return Math.max(70, Math.min(width, height) * 38 / 100);
    }

    /** Half the size of an ability box: shrinks as more abilities share the circle. */
    private int half() {
        double slot = Math.PI * 2.0 * radius() / Ability.values().length;
        return (int) Math.max(13.0, Math.min(22.0, slot / 2.0 - 3.0));
    }

    private int boxX(int i, int n) {
        double a = -Math.PI / 2.0 + i * (Math.PI * 2.0 / n);
        return width / 2 + (int) Math.round(Math.cos(a) * radius());
    }

    private int boxY(int i, int n) {
        double a = -Math.PI / 2.0 + i * (Math.PI * 2.0 / n);
        return height / 2 + (int) Math.round(Math.sin(a) * radius());
    }

    private int pick(double mx, double my) {
        Ability[] all = Ability.values();
        for (int i = 0; i < all.length; i++) {
            if (Math.abs(mx - boxX(i, all.length)) <= half() + 3 && Math.abs(my - boxY(i, all.length)) <= half() + 3) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xAA05000C);
        Ability[] all = Ability.values();
        int n = all.length;
        hovered = pick(mouseX, mouseY);

        for (int i = 0; i < n; i++) {
            int bx = boxX(i, n);
            int by = boxY(i, n);
            boolean isSelected = all[i] == Abilities.clientSelected;
            boolean isHover = i == hovered;
            int fill = isHover ? 0xEE5B2A9A : 0xDD1A0F2E;
            int border = isSelected ? 0xFFE070FF : (isHover ? 0xFFB07CFF : 0xFF4A3A6A);
            int h = half();
            g.fill(bx - h, by - h, bx + h, by + h, fill);
            g.renderOutline(bx - h, by - h, h * 2, h * 2, border);
            g.renderItem(icon(all[i]), bx - 8, by - 8);
        }

        Ability shown = hovered >= 0 ? all[hovered] : Abilities.clientSelected;
        g.drawCenteredString(font, Component.translatable(shown.key()), width / 2, height / 2 - 10, 0xFFD9A8FF);
        g.drawCenteredString(font, Component.translatable(shown.descKey()), width / 2, height / 2 + 4, 0xFFAAAAAA);
    }

    private void choose(int index) {
        Abilities.clientSelected = Ability.values()[index];
        PacketDistributor.sendToServer(new SelectAbilityPayload(index));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = pick(mouseX, mouseY);
        if (i >= 0) {
            choose(i);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (AbilityKeys.OPEN.matches(keyCode, scanCode) && hovered >= 0) {
            choose(hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
