package com.lildorito.csgworldborder;

import com.lildorito.csgworldborder.network.CsgNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CsgWorldBorder.MODID)
public class CsgWorldBorder
{
    public static final String MODID = "csgworldborder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CsgWorldBorder(FMLJavaModLoadingContext context)
    {
        context.getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(CsgNetwork::register);
    }
}
