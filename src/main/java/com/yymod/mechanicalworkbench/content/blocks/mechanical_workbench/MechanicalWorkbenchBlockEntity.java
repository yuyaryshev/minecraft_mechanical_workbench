package com.yymod.mechanicalworkbench.content.blocks.mechanical_workbench;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

public class MechanicalWorkbenchBlockEntity extends KineticBlockEntity implements MenuProvider {

    public static final int MAX_ROTATIONS = 100;
    public static final int ROTATIONS_PER_CRAFT = 20;
    public static final int FE_PER_ROTATION = 10;
    public static final int MAX_FE_EXTRACT_PER_TICK = 100;

    private float rotationBuffer;

    public MechanicalWorkbenchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        rotationBuffer = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        float speed = Math.abs(getSpeed());
        if (speed <= 0)
            return;

        float rotationsThisTick = speed / 1200f;
        if (rotationsThisTick <= 0f)
            return;

        float updated = Math.min(MAX_ROTATIONS, rotationBuffer + rotationsThisTick);
        if (updated != rotationBuffer) {
            rotationBuffer = updated;
            setChanged();
        }

        pullEnergyFromNeighbors();
    }

    public boolean canCraft() {
        return rotationBuffer >= ROTATIONS_PER_CRAFT;
    }

    public void consumeCraftingRotations(int crafts) {
        if (crafts <= 0)
            return;
        rotationBuffer = Math.max(0f, rotationBuffer - (ROTATIONS_PER_CRAFT * crafts));
        setChanged();
        sendData();
    }

    public void addManualRotations(int rotations) {
        if (rotations <= 0)
            return;
        rotationBuffer = Math.min(MAX_ROTATIONS, rotationBuffer + rotations);
        setChanged();
        sendData();
    }

    public float getRotationBuffer() {
        return rotationBuffer;
    }

    private void pullEnergyFromNeighbors() {
        if (level == null || level.isClientSide)
            return;
        if (rotationBuffer >= MAX_ROTATIONS)
            return;

        int rotationsNeeded = Mth.ceil(MAX_ROTATIONS - rotationBuffer);
        int feNeeded = rotationsNeeded * FE_PER_ROTATION;
        int feToExtract = Math.min(feNeeded, MAX_FE_EXTRACT_PER_TICK);
        if (feToExtract <= 0)
            return;

        int extracted = 0;
        for (Direction direction : Direction.values()) {
            if (extracted >= feToExtract)
                break;
            var neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor == null)
                continue;
            var cap = neighbor.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite());
            if (!cap.isPresent())
                continue;
            IEnergyStorage storage = cap.orElse(null);
            if (storage == null || !storage.canExtract())
                continue;
            int request = feToExtract - extracted;
            int got = storage.extractEnergy(request, false);
            if (got > 0)
                extracted += got;
        }

        if (extracted > 0) {
            rotationBuffer = Math.min(MAX_ROTATIONS, rotationBuffer + (extracted / (float) FE_PER_ROTATION));
            setChanged();
            sendData();
        }
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        compound.putFloat("RotationBuffer", rotationBuffer);
        super.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        rotationBuffer = compound.getFloat("RotationBuffer");
        super.read(compound, clientPacket);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.yy_mechanical_workbench.mechanical_workbench");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MechanicalWorkbenchMenu(id, playerInventory, ContainerLevelAccess.create(level, worldPosition), this);
    }
}



