package com.lildorito.csgworldborder.mixin.client;

import com.lildorito.csgworldborder.client.compat.XaeroBorderBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.alazi.xaeroworldborder.client.map.WorldBorderElementProvider", remap = false)
public abstract class XaeroWorldBorderMixin
{
    @Inject(method = "begin(ILdev/alazi/xaeroworldborder/client/map/WorldBorderRenderContext;)V", at = @At("TAIL"), require = 0)
    private void csg$mapOutline(int location, @Coerce Object context, CallbackInfo ci)
    {
        if (location == 3) XaeroBorderBridge.replace(context);
    }
}
