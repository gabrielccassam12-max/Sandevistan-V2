package com.sandevistan;

import com.sandevistan.network.SyncPayload;
import com.sandevistan.network.TogglePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Lógica autoritativa (servidor): fases, energia/duração, recarga, buffs e eventos de combate. */
public final class SandevistanManager {
    private static final class State {
        Phase phase = Phase.READY;
        int remaining;
        int total;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static MinecraftServer server;

    private SandevistanManager() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            STATES.clear();
            TimeDilation.ACTIVE.clear();
        });

        ServerTickEvents.END_SERVER_TICK.register(SandevistanManager::tick);

        ServerPlayNetworking.registerGlobalReceiver(TogglePayload.ID, (payload, context) ->
                context.server().execute(() -> toggle(context.player())));

        // Quem entra no servidor precisa saber quem já está com o Sandevistan ligado.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            for (Map.Entry<UUID, State> e : STATES.entrySet()) {
                if (e.getValue().phase == Phase.ACTIVE) {
                    ServerPlayNetworking.send(handler.player, payload(e.getKey(), e.getValue()));
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> reset(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reset(newPlayer.getUuid()));

        // Reflexos aprimorados: chance de desviar de projéteis que acertariam o jogador.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p
                    && isActive(p.getUuid())
                    && source.getSource() instanceof ProjectileEntity
                    && p.getRandom().nextDouble() < Sandevistan.CONFIG.autoDodgeChance
                    && p.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getBodyY(0.5), p.getZ(), 20, 0.4, 0.6, 0.4, 0.3);
                sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        SoundCategory.PLAYERS, 0.6f, 2.0f);
                return false;
            }
            return true;
        });

        // Efeito de impacto quando o jogador acerta algo durante o Sandevistan.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (damage <= 0) return;
            if (source.getAttacker() instanceof ServerPlayerEntity attacker
                    && attacker != entity
                    && isActive(attacker.getUuid())
                    && entity.getWorld() instanceof ServerWorld sw) {
                double x = entity.getX(), y = entity.getBodyY(0.5), z = entity.getZ();
                sw.spawnParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 0.3, 0.4, 0.3, 0.6);
                sw.spawnParticles(ParticleTypes.CRIT, x, y, z, 15, 0.3, 0.4, 0.3, 0.5);
                sw.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.35f, 2.0f);
            }
        });
    }

    public static boolean isActive(UUID id) {
        State s = STATES.get(id);
        return s != null && s.phase == Phase.ACTIVE;
    }

    // ---------------------------------------------------------------- ciclo de vida

    private static void toggle(ServerPlayerEntity p) {
        State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        switch (s.phase) {
            case READY -> activate(p, s);
            case ACTIVE -> deactivate(p, s);
            case COOLDOWN -> p.sendMessage(Text.translatable("message.sandevistan.cooldown"), true);
        }
    }

    private static void tick(MinecraftServer srv) {
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            State s = e.getValue();
            ServerPlayerEntity p = srv.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                TimeDilation.ACTIVE.remove(e.getKey());
                it.remove();
                continue;
            }
            if (s.phase == Phase.ACTIVE) {
                if (!p.isAlive()) {
                    deactivate(p, s);
                } else if (s.total > 0 && --s.remaining <= 0) {
                    deactivate(p, s);
                }
            } else if (s.phase == Phase.COOLDOWN) {
                if (--s.remaining <= 0) {
                    s.phase = Phase.READY;
                    s.remaining = 0;
                    broadcast(e.getKey(), s);
                }
            }
        }
    }

    private static void activate(ServerPlayerEntity p, State s) {
        SandevistanConfig c = Sandevistan.CONFIG;
        s.phase = Phase.ACTIVE;
        // total == 0 significa duração infinita
        s.total = s.remaining = c.durationSeconds <= 0 ? 0 : Math.max(1, (int) Math.round(c.durationSeconds * 20.0));

        TimeDilation.ACTIVE.put(p.getUuid(),
                TimeDilation.makeSource(p.getUuid(), c.slowdownFactor(), c.affectRadius, String.join(",", c.entityBlacklist)));
        applyBuffs(p, c);

        if (p.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 2.0f);
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.9f);
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.5, 0.9, 0.5, 0.8);
        }
        broadcast(p.getUuid(), s);
    }

    private static void deactivate(ServerPlayerEntity p, State s) {
        SandevistanConfig c = Sandevistan.CONFIG;
        removeBuffs(p);
        TimeDilation.ACTIVE.remove(p.getUuid());

        int cd = (int) Math.round(c.cooldownSeconds * 20.0);
        if (cd > 0) {
            s.phase = Phase.COOLDOWN;
            s.total = s.remaining = cd;
        } else {
            s.phase = Phase.READY;
            s.total = s.remaining = 0;
        }
        if (p.isAlive() && p.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);
        }
        broadcast(p.getUuid(), s);
    }

    /** Desconexão / respawn: limpa tudo e avisa os clientes. */
    private static void reset(UUID id) {
        State removed = STATES.remove(id);
        TimeDilation.ACTIVE.remove(id);
        if (removed != null) broadcast(id, new State());
    }

    // ---------------------------------------------------------------- buffs

    private static void applyBuffs(ServerPlayerEntity p, SandevistanConfig c) {
        addMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, "speed", c.speedMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addMod(p, EntityAttributes.GENERIC_JUMP_STRENGTH, "jump", c.jumpMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addMod(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, "fall", c.extraSafeFallDistance, EntityAttributeModifier.Operation.ADD_VALUE);
        addMod(p, EntityAttributes.GENERIC_ATTACK_SPEED, "attack", c.attackSpeedMultiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void removeBuffs(ServerPlayerEntity p) {
        for (RegistryEntry<EntityAttribute> a : new RegistryEntry[]{
                EntityAttributes.GENERIC_MOVEMENT_SPEED, EntityAttributes.GENERIC_JUMP_STRENGTH,
                EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, EntityAttributes.GENERIC_ATTACK_SPEED}) {
            EntityAttributeInstance inst = p.getAttributeInstance(a);
            if (inst == null) continue;
            for (String n : new String[]{"speed", "jump", "fall", "attack"}) {
                inst.removeModifier(Sandevistan.id(n));
            }
        }
    }

    private static void addMod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, String name,
                               double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Sandevistan.id(name);
        inst.removeModifier(id);
        if (value == 0.0) return; // buff desligado na config
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }

    // ---------------------------------------------------------------- rede

    private static SyncPayload payload(UUID id, State s) {
        SandevistanConfig c = Sandevistan.CONFIG;
        return new SyncPayload(id, s.phase.ordinal(), s.remaining, s.total,
                c.slowdownFactor(), c.affectRadius, String.join(",", c.entityBlacklist));
    }

    private static void broadcast(UUID id, State s) {
        if (server == null) return;
        SyncPayload pl = payload(id, s);
        for (ServerPlayerEntity pl2 : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(pl2, pl);
        }
    }
}
