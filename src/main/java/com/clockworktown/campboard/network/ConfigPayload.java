package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConfigPayload(String json, boolean editable) implements CustomPacketPayload {
    public static final Type<ConfigPayload> TYPE = new Type<>(CampBoardMod.id("server_config_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(8192), ConfigPayload::json,
            ByteBufCodecs.BOOL, ConfigPayload::editable, ConfigPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
