package com.lvyin3.flexibledodge.mixin;

import com.lvyin3.flexibledodge.dodge.DodgeSystem;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 闪避无敌帧内玩家对弹射物“无碰撞箱”：{@code canBeHitByProjectile} 返回 false。
 * <p>
 * 作用点在 {@code Projectile.canHitEntity} → {@code target.canBeHitByProjectile()}：命中检测的
 * <b>过滤器</b>会把玩家排除，于是箭/三叉戟/火球等直接穿过玩家，既不结算伤害、也不会触发
 * {@code AbstractArrow} 的反弹（{@code deflect(REVERSE)}）、不减速、不插身。
 * <p>
 * 弹射物因此不会走到 {@code hurt}，完美闪避改由 {@link DodgeSystem#checkProjectilePerfectDodge}
 * 用“若不过滤则原版会不会命中你”的同几何判定来触发。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void flexibledodge$phaseThroughOnDodge(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && DodgeSystem.isInvulnerableFrame(player)) {
            cir.setReturnValue(false);
        }
    }
}