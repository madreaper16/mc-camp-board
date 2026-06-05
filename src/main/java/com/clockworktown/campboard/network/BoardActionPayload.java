package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BoardActionPayload(String actionJson) implements CustomPacketPayload {
    public static final Type<BoardActionPayload> TYPE = new Type<>(CampBoardMod.id("board_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoardActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32767),
            BoardActionPayload::actionJson,
            BoardActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
