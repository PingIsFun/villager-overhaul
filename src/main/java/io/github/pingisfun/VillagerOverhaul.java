package io.github.pingisfun;

import io.github.pingisfun.command.VillagerOverhaulCommands;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import io.github.pingisfun.village.VillageRuntimeService;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillagerOverhaul implements DedicatedServerModInitializer {
    public static final String MOD_ID = "villager-rebalance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static VillagerOverhaulConfig config;

    public static VillagerOverhaulConfig config() {
        if (config == null) {
            config = VillagerOverhaulConfig.load();
        }
        return config;
    }

    public static VillagerOverhaulConfig reloadConfig() {
        config = VillagerOverhaulConfig.load();
        return config;
    }

    @Override
    public void onInitializeServer() {
        config = VillagerOverhaulConfig.load();
        VillagerOverhaulCommands.register();
        VillageRuntimeService.register();
    }
}
