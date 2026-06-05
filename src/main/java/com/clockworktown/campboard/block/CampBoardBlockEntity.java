package com.clockworktown.campboard.block;

import com.clockworktown.campboard.CampBoardMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CampBoardBlockEntity extends BlockEntity {
    private String boardId = "";

    public CampBoardBlockEntity(BlockPos pos, BlockState state) {
        super(CampBoardMod.CAMP_BOARD_BLOCK_ENTITY, pos, state);
    }

    public String boardId() {
        return boardId;
    }

    public String boardId(Level level) {
        if (boardId == null || boardId.isBlank()) {
            String dimension = level.dimension().toString().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            boardId = dimension + "_" + worldPosition.getX() + "_" + worldPosition.getY() + "_" + worldPosition.getZ();
            setChanged();
        }
        return boardId;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        boardId = input.getStringOr("board_id", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (boardId != null && !boardId.isBlank()) {
            output.putString("board_id", boardId);
        }
    }
}
