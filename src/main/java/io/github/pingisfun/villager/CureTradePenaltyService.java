package io.github.pingisfun.villager;

import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CureTradePenaltyService {
    private CureTradePenaltyService() {
    }

    public static void onCured(ServerLevel world, Villager villager) {
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        CureDataHolder data = (CureDataHolder) villager;

        int cureCount = data.villager_overhaul$getCureCount() + 1;
        data.villager_overhaul$setCureCount(cureCount);

        if (!config.curing.enabled || cureCount <= config.curing.maxSafeCures) {
            return;
        }
        if (world.getRandom().nextDouble() * 100.0D >= config.curing.penaltyChancePercent) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        Optional<Integer> selectedIndex = selectOfferIndex(offers, world.getRandom(), config);
        if (selectedIndex.isEmpty()) {
            VillagerOverhaul.LOGGER.debug("Skipped cure trade penalty for villager {} because no eligible trade was found", villager.getUUID());
            return;
        }

        int index = selectedIndex.get();
        offers.remove(index);
    }

    private static Optional<Integer> selectOfferIndex(MerchantOffers offers, RandomSource random, VillagerOverhaulConfig config) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (isEligible(offer)) {
                eligible.add(i);
            }
        }

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        return switch (config.curing.slotSelection) {
            case RANDOM -> Optional.of(eligible.get(random.nextInt(eligible.size())));
            case LAST_SLOT -> eligible.stream().max(Integer::compareTo);
            case HIGHEST_VALUE ->
                    eligible.stream().max(Comparator.comparingInt(index -> scoreOffer(offers.get(index))));
        };
    }

    private static boolean isEligible(MerchantOffer offer) {
        return !offer.isOutOfStock();
    }

    private static int scoreOffer(MerchantOffer offer) {
        ItemStack sellItem = offer.getResult();
        if (sellItem.is(Items.ENCHANTED_BOOK)) {
            return 100;
        }
        if (sellItem.is(Items.DIAMOND_CHESTPLATE) || sellItem.is(Items.DIAMOND_LEGGINGS)
                || sellItem.is(Items.DIAMOND_BOOTS) || sellItem.is(Items.DIAMOND_HELMET)
                || sellItem.is(Items.DIAMOND_AXE) || sellItem.is(Items.DIAMOND_PICKAXE)
                || sellItem.is(Items.DIAMOND_SHOVEL) || sellItem.is(Items.DIAMOND_SWORD)
                || sellItem.is(Items.DIAMOND_HOE)) {
            return 90;
        }
        if (sellItem.isEnchanted()) {
            return 75;
        }
        if (sellItem.is(Items.EMERALD)) {
            return 60;
        }
        return 10 + Math.min(32, sellItem.getCount());
    }
}
