package com.pedrodalben.ecosystem.core;

import com.pedrodalben.ecosystem.command.EcoCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(EcoSystemMod.MOD_ID)
public class EcoSystemMod {
        public static final String MOD_ID = "ecosystem";

        public EcoSystemMod(IEventBus modEventBus, ModContainer modContainer) {
                // Register config
                modContainer.registerConfig(ModConfig.Type.SERVER, EcoSystemConfig.SPEC);

                // Register server events
                NeoForge.EVENT_BUS.register(ServerEvents.class);

                // Register commands
                NeoForge.EVENT_BUS.addListener(EcoCommand::register);
        }
}
