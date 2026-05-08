package io.github.pingisfun.village;

import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import io.github.pingisfun.villager.VillagerGossipAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageRuntimeService {
    private static final Set<Block> JOB_SITE_BLOCKS = Set.of(
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.CARTOGRAPHY_TABLE,
        Blocks.BREWING_STAND,
        Blocks.COMPOSTER,
        Blocks.BARREL,
        Blocks.FLETCHING_TABLE,
        Blocks.CAULDRON,
        Blocks.LECTERN,
        Blocks.STONECUTTER,
        Blocks.LOOM,
        Blocks.SMITHING_TABLE,
        Blocks.GRINDSTONE
    );

    private static final Map<UUID, Long> WELFARE_REWARD_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> WELFARE_PENALTY_COOLDOWNS = new HashMap<>();

    private VillageRuntimeService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(VillageRuntimeService::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            tickWelfare(level);
        }
    }

    private static void tickWelfare(ServerLevel level) {
        VillagerOverhaulConfig.Welfare config = VillagerOverhaul.config().welfare;
        if (!config.enabled) {
            return;
        }

        int interval = Math.max(20, config.rewardCooldownTicks / 4);
        if (level.getGameTime() % interval != 0) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (isMaintainedVillage(level, player.blockPosition(), config)) {
                if (config.rewardEnabled
                    && config.rewardReputation > 0
                    && WELFARE_REWARD_COOLDOWNS.getOrDefault(player.getUUID(), 0L) <= level.getGameTime()) {
                    rewardWelfare(level, player, config);
                    WELFARE_REWARD_COOLDOWNS.put(player.getUUID(), level.getGameTime() + config.rewardCooldownTicks);
                }
            } else if (config.penaltyEnabled
                && config.penaltyReputation > 0
                && hasNearbyVillagers(level, player.blockPosition(), config.scanRadius)
                && WELFARE_PENALTY_COOLDOWNS.getOrDefault(player.getUUID(), 0L) <= level.getGameTime()) {
                penalizeWelfare(level, player, config);
                WELFARE_PENALTY_COOLDOWNS.put(player.getUUID(), level.getGameTime() + config.penaltyCooldownTicks);
            }
        }
    }

    private static boolean isMaintainedVillage(ServerLevel level, BlockPos center, VillagerOverhaulConfig.Welfare config) {
        AABB bounds = around(center, config.scanRadius);
        if (level.getEntities(EntityType.VILLAGER, bounds, Villager::isAlive).isEmpty()) {
            return false;
        }

        int beds = 0;
        int jobSites = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-config.scanRadius, -8, -config.scanRadius),
            center.offset(config.scanRadius, 8, config.scanRadius)
        )) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.BEDS) && level.getMaxLocalRawBrightness(pos) >= config.minSafeLight) {
                beds++;
            }
            if (JOB_SITE_BLOCKS.contains(state.getBlock())) {
                jobSites++;
            }
            if (beds >= config.minBeds && jobSites >= config.minJobSites) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasNearbyVillagers(ServerLevel level, BlockPos center, int radius) {
        return !level.getEntities(EntityType.VILLAGER, around(center, radius), Villager::isAlive).isEmpty();
    }

    private static void rewardWelfare(ServerLevel level, ServerPlayer player, VillagerOverhaulConfig.Welfare config) {
        AABB bounds = around(player.blockPosition(), config.scanRadius);
        for (Villager villager : level.getEntities(EntityType.VILLAGER, bounds, Villager::isAlive)) {
            ((VillagerGossipAccess) villager).villager_overhaul$getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, config.rewardReputation);
        }
        player.sendSystemMessage(Component.literal("Village welfare improved nearby trades.")
            .withStyle(ChatFormatting.GREEN)
            .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(
                "Nearby villagers received positive gossip for you."
            ).withStyle(ChatFormatting.GRAY)))), true);
    }

    private static void penalizeWelfare(ServerLevel level, ServerPlayer player, VillagerOverhaulConfig.Welfare config) {
        AABB bounds = around(player.blockPosition(), config.scanRadius);
        for (Villager villager : level.getEntities(EntityType.VILLAGER, bounds, Villager::isAlive)) {
            ((VillagerGossipAccess) villager).villager_overhaul$getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, config.penaltyReputation);
        }
        player.sendSystemMessage(Component.literal("Village welfare declined nearby trades.")
            .withStyle(ChatFormatting.RED)
            .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(
                "Nearby villagers received minor negative gossip because welfare checks failed."
            ).withStyle(ChatFormatting.GRAY)))), true);
    }

    private static AABB around(BlockPos center, int radius) {
        return new AABB(center).inflate(radius);
    }
}
