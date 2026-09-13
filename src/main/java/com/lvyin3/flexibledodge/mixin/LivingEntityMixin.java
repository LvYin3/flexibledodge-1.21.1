package com.lvyin3.flexibledodge.mixin;

import com.lvyin3.flexibledodge.dodge.DodgeSystem;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 {@link LivingEntity#hurt} 最前端拦截玩家伤害，实现无敌帧黑名单判定与完美闪避触发。
 * <p>
 * 必须在 {@code hurt} 头部注入：原版 {@code invulnerableTime > 10} 会在更深处直接返回 false，
 * 从头部拦截可绕过该叠加问题，保证完美闪避在受击免伤期间也能正常触发。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void flexibledodge$interceptDodge(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && DodgeSystem.tryInterceptDamage(player, source)) {
            cir.setReturnValue(false);
        }
    }
}
