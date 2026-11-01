package com.sandevistan;

import com.sandevistan.network.SyncPayload;
import com.sandevistan.network.TogglePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sandevistan implements ModInitializer {
    public static final String MOD_ID = "sandevistan";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SandevistanConfig CONFIG = new SandevistanConfig();

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CONFIG = SandevistanConfig.load();
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TogglePayload.ID, TogglePayload.CODEC);
        SandevistanManager.init();
        LOGGER.info("Sandevistan carregado: tick rate do mundo = {} (jogador = 20)", CONFIG.worldTickRate);
    }
}
