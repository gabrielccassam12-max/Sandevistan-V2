package com.sandevistan.network;

import com.sandevistan.Sandevistan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/** Servidor -> clientes: estado do Sandevistan de um jogador (só enviado quando muda de fase). */
public record SyncPayload(UUID player, int phase, int remaining, int total,
                          int factor, int radius, String blacklist) implements CustomPayload {

    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Sandevistan.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(SyncPayload::write, SyncPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeUuid(player);
        buf.writeVarInt(phase);
        buf.writeVarInt(remaining);
        buf.writeVarInt(total);
        buf.writeVarInt(factor);
        buf.writeVarInt(radius);
        buf.writeString(blacklist);
    }

    private static SyncPayload read(RegistryByteBuf buf) {
        return new SyncPayload(buf.readUuid(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
