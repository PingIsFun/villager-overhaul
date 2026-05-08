package io.github.pingisfun.mixin;

import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import io.github.pingisfun.villager.CureDataHolder;
import io.github.pingisfun.villager.LibrarianTradeService;
import io.github.pingisfun.villager.VillagerGossipAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(Villager.class)
public abstract class VillagerEntityMixin extends AbstractVillager implements CureDataHolder, VillagerGossipAccess {
    @Unique
    private static final String VILLAGER_REBALANCE_KEY = "villager_rebalance";
    @Unique
    private static final String CURE_COUNT_KEY = "cure_count";
    @Unique
    private static final String REROLL_LOCKS_KEY = "reroll_locks";
    @Unique
    private static final String LOCKED_PROFESSION_KEY = "profession";
    @Unique
    private static final String LOCKED_OFFERS_KEY = "offers";
    @Unique
    private static final String OFFER_SOURCE_KEY = "source";
    @Unique
    private final Map<String, RerollLock> villager_rebalance$rerollLocks = new HashMap<>();
    @Unique
    private int villager_rebalance$cureCount;
    @Unique
    private int villager_rebalance$previousOfferCount;

    @Shadow
    private int villagerXp;

    @Shadow
    @Final
    private GossipContainer gossips;

    private VillagerEntityMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    @Shadow
    public abstract VillagerData getVillagerData();

    @Shadow
    public abstract void setOffers(MerchantOffers offers);

    @Override
    public int villager_rebalance$getCureCount() {
        return villager_rebalance$cureCount;
    }

    @Override
    public void villager_rebalance$setCureCount(int cureCount) {
        villager_rebalance$cureCount = Math.max(0, cureCount);
    }

    @Override
    public GossipContainer villager_rebalance$getGossips() {
        return gossips;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void villager_rebalance$writeCureData(ValueOutput view, CallbackInfo ci) {
        ValueOutput data = view.child(VILLAGER_REBALANCE_KEY);
        data.putInt(CURE_COUNT_KEY, villager_rebalance$cureCount);
        if (!villager_rebalance$rerollLocks.isEmpty()) {
            ValueOutput.ValueOutputList locks = data.childrenList(REROLL_LOCKS_KEY);
            for (Map.Entry<String, RerollLock> entry : villager_rebalance$rerollLocks.entrySet()) {
                RerollLock lock = entry.getValue();
                ValueOutput lockOutput = locks.addChild();
                lockOutput.putString(LOCKED_PROFESSION_KEY, entry.getKey());
                lockOutput.store(LOCKED_OFFERS_KEY, MerchantOffers.CODEC, lock.offers());
                lockOutput.putIntArray(OFFER_SOURCE_KEY, new int[]{
                        lock.source().getX(),
                        lock.source().getY(),
                        lock.source().getZ()
                });
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void villager_rebalance$readCureData(ValueInput view, CallbackInfo ci) {
        ValueInput data = view.childOrEmpty(VILLAGER_REBALANCE_KEY);
        villager_rebalance$cureCount = Math.max(0, data.getIntOr(CURE_COUNT_KEY, 0));
        villager_rebalance$rerollLocks.clear();
        for (ValueInput lockInput : data.childrenListOrEmpty(REROLL_LOCKS_KEY)) {
            String profession = lockInput.getString(LOCKED_PROFESSION_KEY).orElse(null);
            MerchantOffers offers = lockInput.read(LOCKED_OFFERS_KEY, MerchantOffers.CODEC).orElse(null);
            int[] source = lockInput.getIntArray(OFFER_SOURCE_KEY).orElse(null);
            if (profession != null && offers != null && source != null && source.length == 3) {
                villager_rebalance$rerollLocks.put(profession, new RerollLock(offers, new BlockPos(source[0], source[1], source[2])));
            }
        }
    }

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void villager_rebalance$clearRerollLockWhenProfessionChanges(VillagerData data, CallbackInfo ci) {
        if (villager_rebalance$rerollLocks.isEmpty()) {
            return;
        }

        Identifier current = BuiltInRegistries.VILLAGER_PROFESSION.getKey(this.getVillagerData().profession().value());
        Identifier next = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.profession().value());
        if (current.equals(next)) {
            return;
        }

        int radius = VillagerOverhaul.config().rerollPrevention.memoryRadius;
        double maxDistanceSquared = (double) radius * radius;
        villager_rebalance$rerollLocks.values().removeIf(lock -> this.blockPosition().distSqr(lock.source()) > maxDistanceSquared);
    }

    @Inject(method = "rewardTradeXp", at = @At("TAIL"))
    private void villager_rebalance$clearRerollLockAfterTrade(MerchantOffer offer, CallbackInfo ci) {
        villager_rebalance$clearOfferLock();
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villager_rebalance$restoreLockedOffers(ServerLevel level, CallbackInfo ci) {
        villager_rebalance$previousOfferCount = this.offers == null ? 0 : this.offers.size();
        if (!villager_rebalance$canUseRerollLock()) {
            return;
        }

        String profession = villager_rebalance$professionId();
        RerollLock lock = villager_rebalance$rerollLocks.get(profession);
        if (lock != null) {
            this.setOffers(lock.offers().copy());
            ci.cancel();
        }
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void villager_rebalance$storeAndAdjustNewOffers(ServerLevel level, CallbackInfo ci) {
        LibrarianTradeService.adjustNewOffers(level, (Villager) (Object) this, villager_rebalance$previousOfferCount);
        String profession = villager_rebalance$professionId();
        if (!villager_rebalance$canUseRerollLock() || villager_rebalance$rerollLocks.containsKey(profession) || this.offers == null || this.offers.isEmpty()) {
            return;
        }

        BlockPos source = this.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos)
                .orElse(this.blockPosition());
        villager_rebalance$rerollLocks.put(profession, new RerollLock(this.offers.copy(), source));
    }

    @Unique
    private boolean villager_rebalance$canUseRerollLock() {
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        return config.rerollPrevention.enabled
                && villagerXp <= 0
                && !this.getVillagerData().profession().is(VillagerProfession.NONE)
                && config.rerollPrevention.affectedProfessions.contains(villager_rebalance$professionId())
                && (this.offers == null || this.offers.stream().noneMatch(offer -> offer.getUses() > 0));
    }

    @Unique
    private String villager_rebalance$professionId() {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(this.getVillagerData().profession().value()).toString();
    }

    @Unique
    private void villager_rebalance$clearOfferLock() {
        villager_rebalance$rerollLocks.clear();
    }

    @Unique
    private record RerollLock(MerchantOffers offers, BlockPos source) {
    }
}
