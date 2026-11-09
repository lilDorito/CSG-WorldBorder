package net.example.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Bullet
{
    private Bullet() {}

    public static BlockHitResult trace(Level level, Entity projectile, Vec3 from, Vec3 to)
    {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, projectile));
    }
}
