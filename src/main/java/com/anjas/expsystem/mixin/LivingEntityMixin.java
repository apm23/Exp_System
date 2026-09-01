package com.anjas.expsystem.mixin;

import java.util.Locale;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    private static final ThreadLocal<Context> EXP_SYSTEM_CONTEXT = new ThreadLocal<>();

    @Inject(method = "dropExperience", at = @At("HEAD"))
    private void expSystem$captureContext(ServerLevel level, Entity killer, CallbackInfo ci) {
        EXP_SYSTEM_CONTEXT.set(new Context((LivingEntity) (Object) this, level, killer));
    }

    @ModifyArg(
            method = "dropExperience",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
            index = 2
    )
    private int expSystem$boostTaczXp(int original) {
        Context context = EXP_SYSTEM_CONTEXT.get();
        if (context == null || original <= 0 || context.level.getDifficulty() != Difficulty.HARD) return original;
        if (!expSystem$isTaczKill(context.victim, context.killer)) return original;

        double multiplier = expSystem$isBoss(context.victim) ? 1.5D : 2.5D;
        int boosted = Math.max(original, (int) Math.round(original * multiplier));
        System.out.println("[Exp System] TACZ Hard XP boost: "
                + context.victim.getType() + " " + original + " -> " + boosted
                + " (x" + multiplier + ")");
        return boosted;
    }

    @Inject(method = "dropExperience", at = @At("RETURN"))
    private void expSystem$clearContext(ServerLevel level, Entity killer, CallbackInfo ci) {
        EXP_SYSTEM_CONTEXT.remove();
    }

    private static boolean expSystem$isTaczKill(LivingEntity victim, Entity killer) {
        DamageSource source = victim.getLastDamageSource();
        if (source == null) return false;

        Entity direct = source.getDirectEntity();
        if (expSystem$isTaczEntity(direct)) return true;
        if (expSystem$isTaczEntity(source.getEntity())) return true;
        if (killer != null && expSystem$isTaczEntity(killer)) return true;

        // Fallback for TACZ versions/targets that replace the direct projectile cause
        // (for example special damage-source handling). Avoid a hard TACZ dependency.
        return source.toString().toLowerCase(Locale.ROOT).contains("tacz");
    }

    private static boolean expSystem$isTaczEntity(Entity entity) {
        if (entity == null) return false;
        String name = entity.getClass().getName();
        return name.startsWith("com.tacz.");
    }

    private static boolean expSystem$isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss;
    }

    private record Context(LivingEntity victim, ServerLevel level, Entity killer) {}
}
