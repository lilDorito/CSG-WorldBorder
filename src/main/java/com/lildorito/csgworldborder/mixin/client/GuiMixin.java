package com.lildorito.csgworldborder.mixin.client;

import com.lildorito.csgworldborder.border.BorderSettings;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class GuiMixin
{
    @Redirect(method = "renderVignette", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D"))
    private double csg$insideWarning(WorldBorder border, Entity entity)
    {
        double distance = border.getDistanceToBorder(entity);
        if (distance > 0.0 && !BorderSettings.client().insideWarning()) return 1.0E9;
        return distance;
    }
}
