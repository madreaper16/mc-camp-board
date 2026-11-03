package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBoardPayload(String boardId, String transferId, boolean opening, int index, int count, String fragment) implements CustomPacketPayload {
    public static final Type<OpenBoardPayload> TYPE = new Type<>(CampBoardMod.id("board_snapshot_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoardPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128),
            OpenBoardPayload::boardId,
            ByteBufCodecs.stringUtf8(36),
            OpenBoardPayload::transferId,
            ByteBufCodecs.BOOL,
            OpenBoardPayload::opening,
            ByteBufCodecs.VAR_INT,
            OpenBoardPayload::index,
            ByteBufCodecs.VAR_INT,
            OpenBoardPayload::count,
            ByteBufCodecs.stringUtf8(BoardTransfer.CHUNK_SIZE),
            OpenBoardPayload::fragment,
            OpenBoardPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
