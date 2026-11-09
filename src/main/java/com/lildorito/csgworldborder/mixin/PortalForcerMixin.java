package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin
{
    @Shadow
    @Final
    protected ServerLevel level;

    @Redirect(method = "createPortal",
            slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalForcer;canPortalReplaceBlock(Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean csg$columnCheck(WorldBorder border, BlockPos pos)
    {
        Volume volume = BorderVolume.active(border);
        if (volume == null) return border.isWithinBounds(pos);
        return volume.intersectsColumns(pos.getX(), pos.getX() + 1, pos.getZ(), pos.getZ() + 1);
    }

    @Redirect(method = "createPortal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
    private int csg$forcedHeight(int value, int min, int max, BlockPos origin, Direction.Axis axis)
    {
        int clamped = Mth.clamp(value, min, max);
        Volume volume = BorderVolume.active(level.getWorldBorder());
        if (volume == null) return clamped;
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        if (csg$platformInside(volume, origin.getX(), clamped, origin.getZ(), direction)) return clamped;
        int lowest = level.getMinBuildHeight() + 1;
        for (int d = 1; d <= max - lowest; d++)
        {
            int up = value + d;
            int down = value - d;
            if (up <= max && csg$platformInside(volume, origin.getX(), up, origin.getZ(), direction)) return up;
            if (down >= lowest && down <= max && csg$platformInside(volume, origin.getX(), down, origin.getZ(), direction)) return down;
        }
        return clamped;
    }

    @Unique
    private static boolean csg$platformInside(Volume volume, int x, int y, int z, Direction direction)
    {
        Direction side = direction.getClockWise();
        for (int along = -1; along <= 2; along++)
        {
            for (int across = -1; across <= 1; across++)
            {
                for (int dy = -1; dy <= 3; dy++)
                {
                    int px = x + direction.getStepX() * along + side.getStepX() * across;
                    int pz = z + direction.getStepZ() * along + side.getStepZ() * across;
                    if (!volume.contains(px, y + dy, pz)) return false;
                }
            }
        }
        return true;
    }

    @Inject(method = "canHostFrame", at = @At("RETURN"), cancellable = true)
    private void csg$frameInside(BlockPos origin, BlockPos.MutableBlockPos cursor, Direction direction, int offset,
                                 CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValueZ()) return;
        Volume volume = BorderVolume.active(level.getWorldBorder());
        if (volume == null) return;
        Direction side = direction.getClockWise();
        for (int i = -1; i < 3; i++)
        {
            for (int j = -1; j < 4; j++)
            {
                int x = origin.getX() + direction.getStepX() * i + side.getStepX() * offset;
                int y = origin.getY() + j;
                int z = origin.getZ() + direction.getStepZ() * i + side.getStepZ() * offset;
                if (!volume.contains(x, y, z))
                {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
