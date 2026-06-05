package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBoardPayload(String boardId, String boardJson) implements CustomPacketPayload {
    public static final Type<OpenBoardPayload> TYPE = new Type<>(CampBoardMod.id("open_board"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoardPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128),
            OpenBoardPayload::boardId,
            ByteBufCodecs.stringUtf8(32767),
            OpenBoardPayload::boardJson,
            OpenBoardPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
