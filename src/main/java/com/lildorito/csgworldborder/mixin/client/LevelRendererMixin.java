package com.lildorito.csgworldborder.mixin.client;

import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.client.BorderRenderer;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin
{
    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true)
    private void csg$renderBorder(Camera camera, CallbackInfo ci)
    {
        if (level == null) return;
        boolean show = BorderSettings.client().showBorder();
        Volume volume = BorderVolume.active(level.getWorldBorder());
        if (volume != null && show) BorderRenderer.renderMain(camera, volume, level.getWorldBorder());
        BorderRenderer.renderPreview(camera);
        if (volume != null || !show) ci.cancel();
    }
}
