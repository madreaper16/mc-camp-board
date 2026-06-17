package com.clockworktown.campboard.block;

import com.clockworktown.campboard.CampBoardMod;
import com.clockworktown.campboard.network.OpenBoardPayload;
import com.clockworktown.campboard.storage.BoardJson;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CampBoardBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<CampBoardBlock> CODEC = simpleCodec(CampBoardBlock::new);
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    public static final BooleanProperty LARGE = BooleanProperty.create("large");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    private static final VoxelShape STANDING_NORTH_SOUTH_SHAPE = Shapes.or(
            box(1.0, 3.0, 7.0, 15.0, 15.0, 9.0),
            box(7.0, 0.0, 7.25, 9.0, 3.0, 8.75)
    );
    private static final VoxelShape STANDING_EAST_WEST_SHAPE = Shapes.or(
            box(7.0, 3.0, 1.0, 9.0, 15.0, 15.0),
            box(7.25, 0.0, 7.0, 8.75, 3.0, 9.0)
    );
    private static final VoxelShape WALL_NORTH_SHAPE = box(1.0, 3.0, 14.0, 15.0, 15.0, 16.0);
    private static final VoxelShape WALL_SOUTH_SHAPE = box(1.0, 3.0, 0.0, 15.0, 15.0, 2.0);
    private static final VoxelShape WALL_WEST_SHAPE = box(14.0, 3.0, 1.0, 16.0, 15.0, 15.0);
    private static final VoxelShape WALL_EAST_SHAPE = box(0.0, 3.0, 1.0, 2.0, 15.0, 15.0);
    private static final VoxelShape LARGE_STANDING_NORTH_SOUTH_SHAPE = Shapes.or(
            box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0),
            box(7.0, 0.0, 7.25, 9.0, 3.0, 8.75)
    );
    private static final VoxelShape LARGE_STANDING_EAST_WEST_SHAPE = Shapes.or(
            box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0),
            box(7.25, 0.0, 7.0, 8.75, 3.0, 9.0)
    );
    private static final VoxelShape LARGE_WALL_NORTH_SHAPE = box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);
    private static final VoxelShape LARGE_WALL_SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);
    private static final VoxelShape LARGE_WALL_WEST_SHAPE = box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape LARGE_WALL_EAST_SHAPE = box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);

    public CampBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false).setValue(LARGE, false).setValue(PART, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        boolean wallMounted = clickedFace.getAxis().isHorizontal();
        Direction facing = wallMounted ? clickedFace : context.getHorizontalDirection().getOpposite();
        boolean large = CampBoardMod.config().largeBoard();
        if (large && !canPlaceLargeBoard(context, facing)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(WALL, wallMounted)
                .setValue(LARGE, large)
                .setValue(PART, 1);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !state.getValue(LARGE) || state.getValue(PART) != 1) {
            return;
        }

        String boardId = "";
        if (level.getBlockEntity(pos) instanceof CampBoardBlockEntity boardEntity) {
            boardId = boardEntity.boardId(level);
        }

        Direction across = acrossDirection(state);
        for (int part = 0; part < 9; part++) {
            if (part == 1) {
                continue;
            }
            BlockPos partPos = largePartPos(pos, across, part);
            level.setBlock(partPos, state.setValue(PART, part), 3);
            if (level.getBlockEntity(partPos) instanceof CampBoardBlockEntity boardEntity) {
                boardEntity.setBoardId(boardId);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CampBoardBlockEntity boardEntity) {
                String boardId = CampBoardMod.config().allBoardsGlobal() ? "global" : boardEntity.boardId(level);
                ServerPlayNetworking.send(serverPlayer, new OpenBoardPayload(boardId, BoardJson.toJson(CampBoardMod.boardState(boardId))));
            } else {
                player.sendSystemMessage(Component.literal("Camp Board is available."));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(LARGE)) {
            return largeShape(state);
        }

        if (!state.getValue(WALL)) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? STANDING_EAST_WEST_SHAPE : STANDING_NORTH_SOUTH_SHAPE;
        }

        return switch (state.getValue(FACING)) {
            case SOUTH -> WALL_SOUTH_SHAPE;
            case WEST -> WALL_WEST_SHAPE;
            case EAST -> WALL_EAST_SHAPE;
            default -> WALL_NORTH_SHAPE;
        };
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return CampBoardMod.config().breakable() ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(LARGE)) {
            removeLargeBoard(level, pos, state, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(new Property[]{FACING, WALL, LARGE, PART});
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CampBoardBlockEntity(pos, state);
    }

    private VoxelShape largeShape(BlockState state) {
        if (!state.getValue(WALL)) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? LARGE_STANDING_EAST_WEST_SHAPE : LARGE_STANDING_NORTH_SOUTH_SHAPE;
        }

        return switch (state.getValue(FACING)) {
            case SOUTH -> LARGE_WALL_SOUTH_SHAPE;
            case WEST -> LARGE_WALL_WEST_SHAPE;
            case EAST -> LARGE_WALL_EAST_SHAPE;
            default -> LARGE_WALL_NORTH_SHAPE;
        };
    }

    public static void removeLargeBoard(Level level, BlockPos pos, BlockState state) {
        removeLargeBoard(level, pos, state, null);
    }

    private static void removeLargeBoard(Level level, BlockPos pos, BlockState state, BlockPos skippedPos) {
        if (!state.getValue(LARGE)) {
            return;
        }

        Direction across = acrossDirection(state);
        BlockPos origin = largeOrigin(pos, across, state.getValue(PART));
        for (int part = 0; part < 9; part++) {
            BlockPos partPos = largePartPos(origin, across, part);
            if (partPos.equals(skippedPos)) {
                continue;
            }
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof CampBoardBlock
                    && partState.getValue(LARGE)
                    && partState.getValue(FACING) == state.getValue(FACING)
                    && partState.getValue(WALL) == state.getValue(WALL)) {
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    private static boolean canPlaceLargeBoard(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        Direction across = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        for (int part = 0; part < 9; part++) {
            BlockPos partPos = largePartPos(origin, across, part);
            if (!partPos.equals(origin) && !level.getBlockState(partPos).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static Direction acrossDirection(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    private static BlockPos largeOrigin(BlockPos pos, Direction across, int part) {
        int col = part % 3;
        int row = part / 3;
        return pos.relative(across, 1 - col).below(row);
    }

    private static BlockPos largePartPos(BlockPos origin, Direction across, int part) {
        int col = part % 3;
        int row = part / 3;
        return origin.relative(across, col - 1).above(row);
    }
}
