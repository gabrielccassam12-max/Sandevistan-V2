package com.sandevistan;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Núcleo da manipulação temporal. A MESMA decisão é usada no servidor (que de fato simula as
 * entidades) e no cliente (que simula animação/interpolação/projéteis), então os dois ficam coerentes.
 *
 * Mecanismo real: uma entidade afetada só executa o tick() dela 1 vez a cada N ticks do mundo.
 * Movimento, IA, cooldown de ataque, gravidade, projéteis, etc. rodam N vezes mais devagar.
 * O custo por tick de uma entidade pulada é praticamente zero (economiza CPU em vez de gastar).
 */
public final class TimeDilation {
    public record Source(UUID player, int factor, double radiusSq, Set<Identifier> blacklist) {}

    /** Jogadores com Sandevistan ativo. ConcurrentHashMap: em singleplayer cliente e servidor compartilham a JVM. */
    public static final Map<UUID, Source> ACTIVE = new ConcurrentHashMap<>();

    private TimeDilation() {}

    public static Source makeSource(UUID player, int factor, int radius, String blacklistCsv) {
        Set<Identifier> bl = new HashSet<>();
        if (blacklistCsv != null && !blacklistCsv.isBlank()) {
            for (String s : blacklistCsv.split(",")) {
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null) bl.add(id);
            }
        }
        return new Source(player, Math.max(1, factor), (double) radius * radius, bl);
    }

    /** Fator de lentidão aplicado a esta entidade (1 = não afetada). */
    public static int factorFor(World world, Entity entity) {
        if (ACTIVE.isEmpty()) return 1;
        if (entity instanceof PlayerEntity) return 1; // ninguém (nem outros jogadores) é desacelerado
        for (Entity passenger : entity.getPassengerList()) {
            if (passenger instanceof PlayerEntity) return 1; // montaria de jogador
        }

        int best = 1;
        Identifier typeId = null;
        for (Source s : ACTIVE.values()) {
            PlayerEntity owner = world.getPlayerByUuid(s.player());
            if (owner == null) continue; // outra dimensão / fora do alcance de render
            if (s.radiusSq() > 0 && owner.squaredDistanceTo(entity) > s.radiusSq()) continue;
            if (entity instanceof ProjectileEntity pr && pr.getOwner() == owner) continue; // seus próprios projéteis
            if (!s.blacklist().isEmpty()) {
                if (typeId == null) typeId = Registries.ENTITY_TYPE.getId(entity.getType());
                if (s.blacklist().contains(typeId)) continue;
            }
            best = Math.max(best, s.factor());
        }
        return best;
    }

    /** true = este tick da entidade deve ser pulado. Offset por id espalha a carga entre os ticks. */
    public static boolean shouldSkip(World world, Entity entity) {
        // Cliente: mobs não simulam IA/física lá, só interpolam a posição vinda do servidor.
        // Se pulássemos o tick deles, a interpolação (suave) travaria e voltaria o "soluço".
        if (world.isClient && entity instanceof LivingEntity) return false;
        int f = factorFor(world, entity);
        if (f <= 1) return false;
        return (world.getTime() + entity.getId()) % f != 0;
    }

    /** Chamado quando um tick foi pulado. */
    public static void onSkipped(Entity entity) {
        // Quadros de invulnerabilidade pós-dano seguem em tempo REAL; senão o mob ficaria
        // imune por 20 ticks "lentos" e o jogador não conseguiria dar golpes consecutivos.
        if (entity.timeUntilRegen > 0) entity.timeUntilRegen--;
    }
}
