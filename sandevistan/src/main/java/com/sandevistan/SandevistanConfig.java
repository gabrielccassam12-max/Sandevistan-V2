package com.sandevistan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuração salva em config/sandevistan.json (lida pelo SERVIDOR; o cliente recebe o necessário por pacote). */
public class SandevistanConfig {
    /** Duração em segundos. 0 (ou negativo) = INFINITO: só desliga quando você apertar a tecla. */
    public double durationSeconds = 0.0;
    /** Recarga após desligar, em segundos. 0 = sem recarga. */
    public double cooldownSeconds = 0.0;
    /** Tick rate do MUNDO enquanto o Sandevistan está ativo (você continua em 20). 5 = mundo a 1/4 da velocidade. */
    public double worldTickRate = 5.0;
    /** Buffs opcionais. 1.0 = desligado (padrão). Ex.: 2.0 = dobro. */
    public double speedMultiplier = 1.0;
    public double jumpMultiplier = 1.0;
    public double attackSpeedMultiplier = 1.0;
    /** Blocos extras de queda sem dano (0 = desligado). */
    public double extraSafeFallDistance = 0.0;
    /** Raio (em blocos) em volta do jogador onde o tempo é alterado. 0 = dimensão inteira. */
    public int affectRadius = 128;
    /** Chance (0..1) de desviar automaticamente de um projétil que te atingiria. */
    public double autoDodgeChance = 0.6;
    /** Destaca (outline) mobs e projéteis lentos para quem ativou (client). */
    public boolean highlightSlowedEntities = true;
    /** Ids de entidades que NUNCA devem ser desaceleradas, ex.: "minecraft:ender_dragon". */
    public List<String> entityBlacklist = new ArrayList<>();

    /** Quantos ticks do jogador equivalem a 1 tick do mundo (20 / tickRate do mundo). */
    public int slowdownFactor() {
        return Math.max(1, (int) Math.round(20.0 / worldTickRate));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static SandevistanConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("sandevistan.json");
        SandevistanConfig cfg = new SandevistanConfig();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                SandevistanConfig loaded = GSON.fromJson(r, SandevistanConfig.class);
                if (loaded != null) cfg = loaded;
            } catch (Exception e) {
                Sandevistan.LOGGER.error("Falha ao ler sandevistan.json, usando padrões", e);
            }
        }
        cfg.sanitize();
        try {
            Files.writeString(path, GSON.toJson(cfg));
        } catch (Exception e) {
            Sandevistan.LOGGER.error("Falha ao salvar sandevistan.json", e);
        }
        return cfg;
    }

    private void sanitize() {
        durationSeconds = Math.max(0.0, durationSeconds);
        cooldownSeconds = Math.max(0.0, cooldownSeconds);
        worldTickRate = Math.max(0.2, Math.min(20.0, worldTickRate));
        speedMultiplier = Math.max(1.0, Math.min(20.0, speedMultiplier));
        jumpMultiplier = Math.max(1.0, Math.min(6.0, jumpMultiplier));
        extraSafeFallDistance = Math.max(0.0, extraSafeFallDistance);
        attackSpeedMultiplier = Math.max(1.0, Math.min(20.0, attackSpeedMultiplier));
        affectRadius = Math.max(0, affectRadius);
        autoDodgeChance = Math.max(0.0, Math.min(1.0, autoDodgeChance));
        if (entityBlacklist == null) entityBlacklist = new ArrayList<>();
    }
}
