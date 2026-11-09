package com.lildorito.csgworldborder.mixin.client;

import com.lildorito.csgworldborder.client.compat.AtaMapBridge;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.synarsis.airtacticalarsenal.client.gui.MapBorder", remap = false)
public abstract class AtaMapBorderMixin
{
    @Inject(method = "shade(Lnet/minecraft/client/gui/GuiGraphics;IIIIDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void csg$shade(GuiGraphics g, int left, int top, int right, int bottom,
                                  double centerX, double centerY, double viewX, double viewZ, double scale, CallbackInfo ci)
    {
        if (AtaMapBridge.shade(g, left, top, right, bottom, centerX, centerY, viewX, viewZ, scale)) ci.cancel();
    }
}
