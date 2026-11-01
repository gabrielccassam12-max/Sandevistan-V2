package com.sandevistan.client;

import com.sandevistan.Phase;
import com.sandevistan.Sandevistan;
import com.sandevistan.TimeDilation;
import com.sandevistan.network.SyncPayload;
import com.sandevistan.network.TogglePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class SandevistanClient implements ClientModInitializer {
    public static KeyBinding TOGGLE_KEY;

    @Override
    public void onInitializeClient() {
        TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sandevistan.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.sandevistan"));

        // Estado vindo do servidor.
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> {
            Phase ph = Phase.of(payload.phase());
            if (ph == Phase.ACTIVE) {
                TimeDilation.ACTIVE.put(payload.player(),
                        TimeDilation.makeSource(payload.player(), payload.factor(), payload.radius(), payload.blacklist()));
            } else {
                TimeDilation.ACTIVE.remove(payload.player());
            }
            MinecraftClient mc = context.client();
            if (mc.player != null && mc.player.getUuid().equals(payload.player())) {
                ClientState.update(ph, payload.remaining(), payload.total());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TimeDilation.ACTIVE.clear();
            ClientState.reset();
        });

        // Tecla + contagem da UI.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(new TogglePayload());
                }
            }
            if (!client.isPaused()) ClientState.tick();
        });

        // Partículas e rastro para todo jogador com Sandevistan ativo (inclusive os outros).
        ClientTickEvents.END_WORLD_TICK.register(SandevistanClient::spawnParticles);

        HudRenderCallback.EVENT.register(SandevistanHud::render);
    }

    private static void spawnParticles(ClientWorld world) {
        if (TimeDilation.ACTIVE.isEmpty() || MinecraftClient.getInstance().isPaused()) return;
        Random rng = world.random;
        for (UUID id : TimeDilation.ACTIVE.keySet()) {
            PlayerEntity p = world.getPlayerByUuid(id);
            if (p == null) continue;

            // Faíscas de energia saindo dos pés.
            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        p.getX() + (rng.nextDouble() - 0.5) * 0.5, p.getY() + 0.05, p.getZ() + (rng.nextDouble() - 0.5) * 0.5,
                        (rng.nextDouble() - 0.5) * 0.1, 0.05 + rng.nextDouble() * 0.05, (rng.nextDouble() - 0.5) * 0.1);
            }

            // Rastro luminoso: pontos interpolados entre a posição anterior e a atual.
            double dx = p.getX() - p.prevX, dy = p.getY() - p.prevY, dz = p.getZ() - p.prevZ;
            if (dx * dx + dz * dz > 0.0025 || dy * dy > 0.0025) {
                int steps = 4;
                for (int s = 0; s < steps; s++) {
                    double t = s / (double) steps;
                    double x = p.prevX + dx * t, y = p.prevY + dy * t, z = p.prevZ + dz * t;
                    world.addParticle(ParticleTypes.END_ROD, x, y + 0.2, z, 0, 0, 0);
                    world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.9, z, 0, 0, 0);
                    if (rng.nextInt(2) == 0) world.addParticle(ParticleTypes.END_ROD, x, y + 1.6, z, 0, 0, 0);
                }
            }
        }
    }

    /** Percepção temporal: destaca (outline) o que está lento, só para quem ativou. */
    public static boolean shouldHighlight(Entity e) {
        if (!Sandevistan.CONFIG.highlightSlowedEntities || ClientState.phase != Phase.ACTIVE) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == mc.player) return false;
        if (!(e instanceof LivingEntity || e instanceof ProjectileEntity)) return false;
        return TimeDilation.factorFor(e.getWorld(), e) > 1;
    }
}
