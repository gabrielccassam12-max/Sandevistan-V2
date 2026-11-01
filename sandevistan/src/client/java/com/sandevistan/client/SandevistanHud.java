package com.sandevistan.client;

import com.sandevistan.Phase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.Random;

/** Efeitos de tela (tinta, vinheta, linhas de velocidade, glitch, animação de ativação) e barra de energia. */
public final class SandevistanHud {
    private static final int CYAN = 0x00E5FF;
    private static final int YELLOW = 0xFFD500;

    private SandevistanHud() {}

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

        if (ClientState.phase == Phase.ACTIVE) {
            renderEffects(ctx, mc, w, h);
        }
        renderBar(ctx, mc, w, h);
    }

    private static void renderEffects(DrawContext ctx, MinecraftClient mc, int w, int h) {
        long ms = Util.getMeasuringTimeMs();
        float pulse = 0.5f + 0.5f * (float) Math.sin(ms / 180.0);
        int t = ClientState.sinceActivation;

        // Tinta azulada que pulsa suavemente.
        ctx.fill(0, 0, w, h, argb(22 + (int) (14 * pulse), CYAN));

        // Vinheta nas bordas, deslizando de ciano para amarelo.
        int edge = lerpColor(CYAN, YELLOW, 0.15f + 0.35f * pulse);
        int thickV = Math.max(12, h / 6), thickH = Math.max(12, w / 8);
        ctx.fillGradient(0, 0, w, thickV, argb(150, edge), argb(0, edge));
        ctx.fillGradient(0, h - thickV, w, h, argb(0, edge), argb(150, edge));
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int a = (int) (130 * k * k);
            int x0 = i * thickH / steps, x1 = (i + 1) * thickH / steps;
            if (x1 <= x0) x1 = x0 + 1;
            ctx.fill(x0, 0, x1, h, argb(a, edge));
            ctx.fill(w - x1, 0, w - x0, h, argb(a, edge));
        }

        // Glitch sutil: barras horizontais deslocadas que piscam.
        Random g = new Random(ms / 90);
        if (g.nextInt(3) == 0) {
            for (int i = 0; i < 2; i++) {
                int y = g.nextInt(Math.max(1, h - 4));
                int off = g.nextInt(7) - 3;
                ctx.fill(off, y, w + off, y + 1 + g.nextInt(3), argb(28, g.nextBoolean() ? CYAN : 0xFF2D95));
            }
        }

        // Linhas de velocidade: intensidade depende da velocidade horizontal real.
        double speed = mc.player.getVelocity().horizontalLength();
        float intensity = (float) Math.min(1.0, speed / 0.35);
        if (intensity > 0.05f) {
            Random r = new Random(ms / 45);
            int lines = 6 + (int) (36 * intensity);
            for (int i = 0; i < lines; i++) {
                boolean left = r.nextBoolean();
                int y = r.nextInt(h);
                int len = 20 + r.nextInt(70);
                int inset = r.nextInt(Math.max(1, w / 4));
                int x = left ? inset : w - inset - len;
                ctx.fill(x, y, x + len, y + 1, argb(40 + r.nextInt(70), 0xFFFFFF));
            }
        }

        // Sequência de ativação: flash, varredura e texto "implante ligando".
        if (t < 60) {
            int flash = Math.max(0, 210 - t * 12);
            if (flash > 0) ctx.fill(0, 0, w, h, argb(flash, 0xE6FBFF));
            if (t < 24) {
                int y = (int) (h * (t / 24f));
                ctx.fill(0, y, w, y + 3, argb(200, YELLOW));
                ctx.fill(0, y + 3, w, y + 18, argb(40, CYAN));
            }
            int alpha = t < 40 ? 255 : Math.max(5, 255 - (t - 40) * 12);
            Random j = new Random(ms / 60);
            float jitter = t < 14 ? (j.nextFloat() - 0.5f) * 8f : 0f;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(w / 2f + jitter, h * 0.27f, 0);
            m.scale(2.2f, 2.2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("hud.sandevistan.online"), 0, 0, argb(alpha, t % 6 < 3 ? CYAN : YELLOW));
            m.pop();
        }
    }

    private static void renderBar(DrawContext ctx, MinecraftClient mc, int w, int h) {
        Phase ph = ClientState.phase;
        float frac;
        int color;
        Text label;
        String secs = String.format("%.1f", ClientState.remaining / 20.0);

        switch (ph) {
            case ACTIVE -> {
                if (ClientState.total <= 0) { // duração infinita
                    frac = 1f;
                    color = 0xFF00E5FF;
                    label = Text.translatable("hud.sandevistan.infinite");
                } else {
                    frac = ClientState.remaining / (float) ClientState.total;
                    color = frac > 0.3f ? 0xFF00E5FF : 0xFFFFD500;
                    label = Text.translatable("hud.sandevistan.active", secs);
                }
            }
            case COOLDOWN -> {
                frac = ClientState.total > 0 ? 1f - ClientState.remaining / (float) ClientState.total : 1f;
                color = 0xFFFF6A00;
                label = Text.translatable("hud.sandevistan.cooldown", secs);
            }
            default -> {
                frac = 1f;
                color = 0xFF3DFF8A;
                label = Text.translatable("hud.sandevistan.ready", SandevistanClient.TOGGLE_KEY.getBoundKeyLocalizedText());
            }
        }

        int bw = 120, bh = 8;
        int x = w - bw - 12, y = h - 24;
        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xAA000000);
        ctx.fill(x, y, x + bw, y + bh, 0xFF181818);
        ctx.fill(x, y, x + (int) (bw * Math.max(0f, Math.min(1f, frac))), y + bh, color);
        ctx.drawTextWithShadow(mc.textRenderer, label, x, y - 11, color);
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
