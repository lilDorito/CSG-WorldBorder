package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderRaycast;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClipContext.class)
public abstract class ClipContextMixin
{
    @Shadow
    @Final
    private Vec3 from;

    @Shadow
    @Final
    private CollisionContext collisionContext;

    @Unique
    private Boolean csg$stopsAtBorder;

    @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
    private void csg$borderIsSolid(BlockState state, BlockGetter getter, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir)
    {
        if (csg$stopsAtBorder == null) csg$stopsAtBorder = BorderRaycast.applies(collisionContext, getter, from);
        if (csg$stopsAtBorder && BorderRaycast.outside(getter, pos)) cir.setReturnValue(Shapes.block());
    }
}
