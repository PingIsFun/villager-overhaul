package io.github.pingisfun.mixin;

import io.github.pingisfun.villager.CureTradePenaltyService;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerEntityMixin {
    @Inject(method = "lambda$finishConversion$0", at = @At("TAIL"))
    private void villager_overhaul$afterCureConversion(ServerLevel world, Villager villager, CallbackInfo ci) {
        CureTradePenaltyService.onCured(world, villager);
    }
}
