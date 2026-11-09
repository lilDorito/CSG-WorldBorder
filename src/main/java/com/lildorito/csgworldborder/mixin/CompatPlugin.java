package com.lildorito.csgworldborder.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompatPlugin implements IMixinConfigPlugin
{
    private static final Map<String, String> REQUIRED_MODS = Map.of(
            "com.lildorito.csgworldborder.mixin.compat.CanaryCollisionsMixin", "canary",
            "com.lildorito.csgworldborder.mixin.compat.SuperbWarfareBlocksMixin", "superbwarfare",
            "com.lildorito.csgworldborder.mixin.compat.MhWarfareBlocksMixin", "mhwarfare",
            "com.lildorito.csgworldborder.mixin.client.XaeroWorldBorderMixin", "xaeroworldborder",
            "com.lildorito.csgworldborder.mixin.client.AtaMapBorderMixin", "ata");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        String mod = REQUIRED_MODS.get(mixinClassName);
        if (mod == null || Boolean.getBoolean("csgworldborder.compatTest")) return true;
        LoadingModList mods = LoadingModList.get();
        return mods != null && mods.getModFileById(mod) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
