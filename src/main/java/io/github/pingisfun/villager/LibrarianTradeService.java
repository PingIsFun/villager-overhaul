package io.github.pingisfun.villager;

import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LibrarianTradeService {
    private LibrarianTradeService() {
    }

    public static void adjustNewOffers(ServerLevel level, Villager villager, int previousOfferCount) {
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        if (!config.librarians.enabled || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        for (int i = Math.max(0, previousOfferCount); i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (offer.getResult().is(Items.ENCHANTED_BOOK)) {
                Optional<Holder<Enchantment>> enchantment = pickEnchantment(level, villager, offer, config);
                if (enchantment.isPresent()) {
                    offers.set(i, replaceBook(offer, enchantment.get()));
                }
            }
        }
    }

    public static Optional<Identifier> bookEnchantmentId(ServerLevel level, ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return Optional.empty();
        }

        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return enchantments.entrySet().stream()
            .findFirst()
            .flatMap(entry -> entry.getKey().unwrapKey().map(key -> key.identifier()))
            .or(() -> enchantments.entrySet().stream().findFirst().map(entry -> registry.getKey(entry.getKey().value())));
    }

    private static Optional<Holder<Enchantment>> pickEnchantment(
        ServerLevel level,
        Villager villager,
        MerchantOffer offer,
        VillagerOverhaulConfig config
    ) {
        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Set<Identifier> rareIds = parseRareIds(config);
        List<Holder<Enchantment>> rare = new ArrayList<>();

        for (Holder<Enchantment> enchantment : registry.getTagOrEmpty(EnchantmentTags.TRADEABLE)) {
            Optional<Identifier> id = enchantment.unwrapKey().map(key -> key.identifier());
            if (id.isPresent() && rareIds.contains(id.get())) {
                rare.add(enchantment);
            }
        }

        if (!preferRare(villager.getVillagerData().level(), level.getRandom()) || rare.isEmpty()) {
            return Optional.empty();
        }

        List<Holder<Enchantment>> preferred = rare;
        if (config.librarians.rareDuplicatePreventionEnabled) {
            List<Holder<Enchantment>> filtered = withoutNearbyRareDuplicates(level, villager, rare, rareIds, config.librarians.duplicateSearchRadius);
            if (!filtered.isEmpty()) {
                preferred = filtered;
            }
        }

        if (preferred.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(preferred.get(level.getRandom().nextInt(preferred.size())));
    }

    private static boolean preferRare(int level, RandomSource random) {
        return switch (level) {
            case 3 -> random.nextFloat() < 0.25F;
            case 4 -> random.nextFloat() < 0.50F;
            case 5 -> random.nextFloat() < 0.75F;
            default -> false;
        };
    }

    private static List<Holder<Enchantment>> withoutNearbyRareDuplicates(
        ServerLevel level,
        Villager villager,
        List<Holder<Enchantment>> candidates,
        Set<Identifier> rareIds,
        int radius
    ) {
        Set<Identifier> nearby = new HashSet<>();
        AABB bounds = villager.getBoundingBox().inflate(radius);
        for (Villager other : level.getEntities(EntityType.VILLAGER, bounds, other -> other != villager)) {
            for (MerchantOffer offer : other.getOffers()) {
                if (offer.getResult().is(Items.ENCHANTED_BOOK)) {
                    bookEnchantmentId(level, offer.getResult()).filter(rareIds::contains).ifPresent(nearby::add);
                }
            }
        }

        List<Holder<Enchantment>> filtered = new ArrayList<>();
        for (Holder<Enchantment> candidate : candidates) {
            Optional<Identifier> id = candidate.unwrapKey().map(key -> key.identifier());
            if (id.isPresent() && !nearby.contains(id.get())) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static MerchantOffer replaceBook(MerchantOffer original, Holder<Enchantment> enchantment) {
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(result, enchantments -> enchantments.set(enchantment, enchantment.value().getMaxLevel()));
        return new MerchantOffer(
            original.getItemCostA(),
            original.getItemCostB(),
            result,
            original.getUses(),
            original.getMaxUses(),
            original.getXp(),
            original.getPriceMultiplier(),
            original.getDemand()
        );
    }

    private static Set<Identifier> parseRareIds(VillagerOverhaulConfig config) {
        Set<Identifier> ids = new HashSet<>();
        for (String raw : config.librarians.rareEnchantments) {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }
}
