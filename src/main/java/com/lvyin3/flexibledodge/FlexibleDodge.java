package com.lvyin3.flexibledodge;

import org.slf4j.Logger;

import com.lvyin3.flexibledodge.config.ClientConfig;
import com.lvyin3.flexibledodge.config.ServerConfig;
import com.lvyin3.flexibledodge.dodge.DodgeSounds;
import com.lvyin3.flexibledodge.dodge.DodgeSystem;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Flexible Dodge —— 独立闪避模组。
 * <p>
 * 按键闪避（默认 Left Alt）：服务端权威位移 + 短暂无敌帧；成功闪避后连闪冷却按
 * {@code dodgeCooldownStep} 递增并封顶于 {@code dodgeCooldownMax}，冷却从位移完全结束起算。
 */
@Mod(FlexibleDodge.MODID)
public class FlexibleDodge {
    public static final String MODID = "flexibledodge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FlexibleDodge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        DodgeSounds.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }

    /** 服务端 tick 驱动闪避位移与冷却状态推进。 */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DodgeSystem.tickPlayer(serverPlayer);
        }
    }

    /**
     * 弹射物完美闪避检测。
     * <p>
     * 挂在弹射物自身 tick 之后（而非玩家 tick），此时 {@code xOld/yOld/zOld → position()} 才是本 tick
     * 的真实位移段，避免因实体 tick 顺序导致无敌帧最后一 tick 的穿越被漏判。
     * 绝大多数实体（非弹射物）在这里第一行就返回，开销可忽略。
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Projectile projectile) {
            DodgeSystem.checkProjectilePerfectDodge(projectile);
        }
    }

    /** 玩家登出清理闪避状态。 */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            DodgeSystem.onPlayerLoggedOut(serverPlayer);
        }
    }
}
