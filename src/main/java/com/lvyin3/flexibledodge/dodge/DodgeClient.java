package com.lvyin3.flexibledodge.dodge;

import com.lvyin3.flexibledodge.FlexibleDodge;
import com.lvyin3.flexibledodge.config.ClientConfig;
import com.lvyin3.flexibledodge.config.ServerConfig;
import com.lvyin3.flexibledodge.network.DodgePayload;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 闪避客户端逻辑：计算闪避方向、发送 C2S 请求、驱动本地指示标记、本地闪避预测、闪避音效，
 * 并镜像服务端的冷却状态以驱动准星下方的闪避冷却条。
 * <p>
 * 指示标记：普通闪避为 {@code < + >}，完美闪避为 {@code <<< + >>>}（从内到外依次淡入淡出），
 * 时长由 {@code dodgeMarkerTime} 控制（默认 0.2 秒）。
 * <p>
 * 本地闪避预测：服务端移动的是 ServerPlayer，客户端渲染的却是 LocalPlayer（由客户端本地预测驱动、
 * 不跟随服务端 setDeltaMovement）。因此这里在按下闪避键时给 LocalPlayer 施加相同速度脉冲，
 * 并每 tick 重设速度（位移数学与服务端 DodgeSystem.tickPlayer 完全一致）。
 * <p>
 * 冷却条镜像：冷却从"位移完全结束"那一刻起算，服务端经 DodgeCooldownPayload 下发权威值校正。
 */
@EventBusSubscriber(modid = FlexibleDodge.MODID, value = Dist.CLIENT)
public final class DodgeClient {
    // ---- 指示标记 ----
    private static long markerStartMs;
    private static long markerDurationMs;
    private static boolean perfectMarker;

    // ---- 冷却条镜像 ----
    /** 本地输入守卫：早于该时刻忽略按键（位移 + 冷却）。 */
    private static long localReadyAtMs;
    /** 服务端权威连闪计数（含本次闪避）的本地镜像。 */
    private static int localStreak;
    /** 连闪计数锚点：上一次位移结束的本地时刻。 */
    private static long lastDodgeEndMs;
    /** 本次闪避位移结束后要走的冷却长度（毫秒）。0 = 本次无冷却。 */
    private static int thisDodgeCdMs;
    /** 冷却起算时刻（= 位移结束时刻）。0 = 位移进行中。 */
    private static long cdStartMs;
    /** 本次位移结束的本地时刻。0 = 位移进行中。 */
    private static long motionEndMs;
    /** 完美闪避满条闪光起始时刻。0 = 无闪光。 */
    private static long perfectFlashStartMs;
    /**
     * 本次闪避是否已被服务端确认（收到 DodgeCooldownPayload）。
     * 冷却条只在确认后绘制：若按键被服务端拒绝（守卫/冷却判定差异），就不会画出没有实际冷却的条。
     */
    private static boolean cdSynced;
    /** 本次闪避是否已被完美闪避取消冷却（避免随后到达的冷却同步包把冷却又加回来）。 */
    private static boolean perfectThisDodge;

    /** 当前闪避音效实例（普通/完美）。完美闪避确认时需先停止普通闪避音、只播完美音（不叠播）。 */
    private static SoundInstance currentDodgeSound;

    // ---- 本地闪避预测状态（与服务端 tickPlayer 同构） ----
    private static boolean localDodging;
    private static Vec3 localDodgeDir;
    private static double localDodgeSpeed;
    private static double localDodgeFriction;
    private static double localDodgeThreshold;

    private DodgeClient() {
    }

    /** 客户端 tick：推进本地位移镜像，并消费闪避按键。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        tick(player);
        while (DodgeKeybinds.DODGE.consumeClick()) {
            onDodgeKey(player);
        }
    }

    /** 按下闪避键时调用（客户端）。 */
    public static void onDodgeKey(Player player) {
        long now = Util.getMillis();
        if (now < localReadyAtMs) {
            return;
        }
        // 镜像服务端 tryDodge 守卫：旁观/空中（非水中）不可闪避，避免客户端预测与服务端权威脱节
        if (player.isSpectator() || (!player.onGround() && !player.isInWater())) {
            return;
        }
        // 连闪计数重置：距上一次位移结束超过重置窗口则归零（与服务端同一规则）
        if (now - lastDodgeEndMs > resetMs()) {
            localStreak = 0;
        }
        thisDodgeCdMs = cdMs(localStreak);
        cdStartMs = 0L;
        motionEndMs = 0L;
        cdSynced = false;
        perfectThisDodge = false;
        // 本地守卫覆盖"位移时长 + 本次冷却"，服务端随后会用权威值校正
        localReadyAtMs = now + estimateDurationMs() + thisDodgeCdMs;

        float yaw = computeDodgeYaw(player);
        markerStartMs = now;
        markerDurationMs = (long) (ClientConfig.DODGE_MARKER_TIME.get() * 1000.0);
        perfectMarker = false;

        // 客户端本地播放闪避音效（即时）：服务端不广播（避免双播）。
        // 先停掉上一次闪避音（若因低 CD / 连续按键仍在播），避免叠播。
        stopCurrentDodgeSound();
        currentDodgeSound = playDodgeSound(player, false);

        startLocalDodge(player, yaw); // 本地闪避预测：立即给 LocalPlayer 施加速度
        PacketDistributor.sendToServer(new DodgePayload(yaw));
    }

    /**
     * 每 tick 驱动本地位移（由 onClientTick 调用）。
     * 位移数学与服务端 {@code DodgeSystem.tickPlayer} 完全一致：瞬间脉冲 + 全程指数衰减，
     * 撞墙即停（保留 y），速度衰减到阈值以下视为停止。
     * <p>
     * 位移结束的那一刻即冷却起算时刻，同时记录连闪计数锚点。
     */
    public static void tick(Player player) {
        if (!localDodging) {
            return;
        }
        boolean ended = false;
        if (player.horizontalCollision || localDodgeSpeed < localDodgeThreshold) {
            // 撞墙或已衰减到阈值以下：位移结束，只清水平速度（保留 y）
            ended = true;
            player.setDeltaMovement(new Vec3(0.0, player.getDeltaMovement().y, 0.0));
        } else {
            Vec3 current = player.getDeltaMovement();
            player.setDeltaMovement(new Vec3(localDodgeDir.x * localDodgeSpeed, current.y, localDodgeDir.z * localDodgeSpeed));
            localDodgeSpeed *= localDodgeFriction; // 指数衰减（与服务端一致）
        }
        if (ended) {
            localDodging = false;
            long now = Util.getMillis();
            motionEndMs = now;
            cdStartMs = now;
            lastDodgeEndMs = now;
        }
    }

    /** 服务端确认闪避后同步冷却信息（收到 DodgeCooldownPayload）。 */
    public static void onCooldownSync(int streak, int readyInTicks, int cdTicks) {
        localStreak = streak;
        if (perfectThisDodge) {
            // 本次闪避已被完美闪避取消冷却：不再接受该次闪避的冷却与守卫，避免冷却"复活"
            cdSynced = true;
            return;
        }
        long now = Util.getMillis();
        cdSynced = true;
        thisDodgeCdMs = cdTicks * 50;
        localReadyAtMs = now + readyInTicks * 50L;
        // 位移已结束时保留正在走的读条（不重置 cdStartMs），避免读条回跳
    }

    /** 服务端确认完美闪避后调用（收到 PerfectDodgePayload）。 */
    public static void onPerfectDodge() {
        long now = Util.getMillis();
        markerStartMs = now;
        perfectMarker = true;
        perfectThisDodge = true;
        perfectFlashStartMs = now;
        if (motionEndMs != 0L) {
            // 位移已结束：本次冷却取消，立即可再闪
            localReadyAtMs = now;
        } else {
            // 位移进行中：去掉本次冷却那一段，位移一结束即可再闪
            localReadyAtMs = Math.max(now, localReadyAtMs - thisDodgeCdMs);
        }
        thisDodgeCdMs = 0;
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // 完美闪避：先停止普通闪避音（避免叠播），再播放完美闪避音。
            stopCurrentDodgeSound();
            currentDodgeSound = playDodgeSound(player, true);
        }
        // debug 级日志：开发环境（run/logs/debug.log）可见，用于排查"完美闪避闪光未生效"。
        FlexibleDodge.LOGGER.debug("[flexibledodge] perfect dodge received: full-bar flash started");
    }

    // ---- 冷却条状态 ----

    /**
     * 冷却条填充进度。
     *
     * @return {@code -1} 表示不显示；{@code 0} 表示显示为空条（位移进行中且本次有冷却）；
     *         其余为 {@code 0..1} 的填充比例
     */
    public static float cooldownBarProgress() {
        if (!cdSynced) {
            // 服务端尚未确认本次闪避：不绘制，避免出现没有实际冷却的"幽灵读条"
            return -1.0F;
        }
        if (localDodging) {
            // 位移进行中：本次冷却不为 0 → 空条；本次无冷却 → 不显示
            return thisDodgeCdMs > 0 ? 0.0F : -1.0F;
        }
        if (motionEndMs == 0L || thisDodgeCdMs <= 0) {
            return -1.0F;
        }
        float p = (Util.getMillis() - cdStartMs) / (float) thisDodgeCdMs;
        if (p >= 1.0F) {
            return -1.0F; // 冷却结束 → 隐藏
        }
        return Math.max(0.0F, p);
    }

    /**
     * 完美闪避满条闪光进度。
     *
     * @return {@code -1} 表示无闪光；其余为 {@code 0..1} 的动画进度（不透明度 = 1 - 进度）
     */
    public static float perfectFlash01() {
        if (perfectFlashStartMs == 0L) {
            return -1.0F;
        }
        long duration = (long) (ClientConfig.DODGE_COOLDOWN_BAR_FLASH_TIME.get() * 1000.0);
        if (duration <= 0L) {
            perfectFlashStartMs = 0L;
            return -1.0F;
        }
        long elapsed = Util.getMillis() - perfectFlashStartMs;
        if (elapsed < 0L || elapsed >= duration) {
            perfectFlashStartMs = 0L;
            return -1.0F;
        }
        return elapsed / (float) duration;
    }

    public static long markerStartMs() {
        return markerStartMs;
    }

    public static long markerDurationMs() {
        return markerDurationMs;
    }

    public static boolean isPerfectMarker() {
        return perfectMarker;
    }

    // ---- 内部工具 ----

    /** 客户端本地播放闪避音效（普通/完美）。返回播放实例。 */
    private static SoundInstance playDodgeSound(Player player, boolean perfect) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        Player p = player != null ? player : mc.player;
        if (p == null) {
            return null;
        }
        SimpleSoundInstance instance = new SimpleSoundInstance(
                perfect ? DodgeSounds.PERFECT_DODGE.get() : DodgeSounds.DODGE.get(),
                SoundSource.PLAYERS, 0.6F, 1.0F, p.getRandom(),
                p.getX(), p.getY(), p.getZ());
        mc.getSoundManager().play(instance);
        return instance;
    }

    /** 停止当前闪避音效实例（若在播）。 */
    private static void stopCurrentDodgeSound() {
        Minecraft mc = Minecraft.getInstance();
        if (currentDodgeSound != null) {
            mc.getSoundManager().stop(currentDodgeSound);
            currentDodgeSound = null;
        }
    }

    /** 启动本地闪避预测：给 LocalPlayer 施加与服务端相同的速度脉冲与衰减参数。 */
    private static void startLocalDodge(Player player, float yaw) {
        localDodgeDir = Vec3.directionFromRotation(0.0F, yaw);
        // setDeltaMovement 速度单位是格/tick，与服务端 state 完全一致（格/秒 ÷ 20）
        localDodgeSpeed = ServerConfig.DODGE_SPEED.get() / 20.0;
        localDodgeFriction = Math.min(0.95, Math.max(0.3,
                1.0 - localDodgeSpeed / Math.max(ServerConfig.DODGE_DISTANCE.get(), 0.01)));
        localDodgeThreshold = 0.05;
        localDodging = true;
        player.setDeltaMovement(localDodgeDir.scale(localDodgeSpeed));
        player.hasImpulse = true;
    }

    /**
     * 根据按下闪避键时的移动键输入计算闪避方向的世界偏航角（度）。
     * 八向闪避；无移动键时默认向后。
     */
    private static float computeDodgeYaw(Player player) {
        // input 字段只存在于客户端 LocalPlayer 上
        Input input = ((net.minecraft.client.player.LocalPlayer) player).input;
        float forward = input.forwardImpulse;
        float left = input.leftImpulse;
        float yaw = player.getYRot();
        if (forward == 0.0F && left == 0.0F) {
            return yaw + 180.0F;
        }
        double rad = Math.toRadians(yaw);
        double cosY = Math.cos(rad);
        double sinY = Math.sin(rad);
        // 与 Entity.getInputVector 相同的世界空间旋转
        double wx = left * cosY - forward * sinY;
        double wz = left * sinY + forward * cosY;
        return (float) Math.toDegrees(Math.atan2(-wx, wz));
    }

    /** 预估闪避位移时长（毫秒）：与服务端 {@code DodgeSystem.dodgeDurationTicks()} 共用同一套参数。 */
    private static long estimateDurationMs() {
        return DodgeSystem.dodgeDurationTicks() * 50L;
    }

    /** 本次闪避的冷却长度（毫秒）——与服务端共用同一套规则，避免双端漂移。 */
    private static int cdMs(int streak) {
        return DodgeSystem.cooldownTicks(streak) * 50;
    }

    /** 连闪计数重置窗口（毫秒）。 */
    private static long resetMs() {
        return DodgeSystem.resetTicks() * 50L;
    }
}
