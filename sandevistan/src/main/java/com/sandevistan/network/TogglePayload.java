package com.sandevistan.network;

import com.sandevistan.Sandevistan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Cliente -> servidor: o jogador apertou a tecla. */
public record TogglePayload() implements CustomPayload {
    public static final CustomPayload.Id<TogglePayload> ID = new CustomPayload.Id<>(Sandevistan.id("toggle"));
    public static final PacketCodec<RegistryByteBuf, TogglePayload> CODEC = PacketCodec.unit(new TogglePayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
