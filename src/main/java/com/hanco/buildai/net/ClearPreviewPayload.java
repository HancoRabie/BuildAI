package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authoritative signal to retire only BuildAI's ghost placement overlay. */
public record ClearPreviewPayload() implements CustomPayload {
    public static final Id<ClearPreviewPayload> ID =
        new Id<>(Identifier.of("buildai","clear_preview"));
    public static final PacketCodec<RegistryByteBuf, ClearPreviewPayload> CODEC =
        PacketCodec.of((value, buffer) -> {}, buffer -> new ClearPreviewPayload());

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
