package com.lvyin3.flexibledodge.dodge;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.lvyin3.flexibledodge.FlexibleDodge;
import com.lvyin3.flexibledodge.config.ServerConfig;
import com.lvyin3.flexibledodge.network.DodgeCooldownPayload;
import com.lvyin3.flexibledodge.network.PerfectDodgePayload;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 闪避系统（服务端逻辑）。
 * <p>
 * 职责：闪避请求校验与施加运动、无敌帧黑名单判定、完美闪避触发、闪避冷却管理。
 * 运动与伤害均为服务端权威；客户端只负责输入与表现（指示标记、冷却条、音效）。
 * <p>
 * 冷却规则：
 * <ul>
 *   <li>第 1 次闪避无冷却，此后每次连闪 +{@code dodgeCooldownStep}，直到 {@code dodgeCooldownMax} 封顶。</li>
 *   <li><b>冷却从闪避位移完全结束的那一 tick 开始计算</b>。</li>
 *   <li>距上一次位移结束超过 {@code dodgeCooldownResetTime} 后再次闪避，连闪计数归零。</li>
 *   <li>完美闪避取消<b>本次</b>闪避的冷却，但不会重置连闪计数。</li>
 * </ul>
 */
public final class DodgeSystem {
    /** 环境伤害黑名单：这些伤害在无敌帧期间不可躲避（其余伤害均可）。 */
    private static final Set<ResourceKey<DamageType>> ENVIRONMENT_DAMAGE = Set.of(
            DamageTypes.FALL,
            DamageTypes.LAVA,
            DamageTypes.FELL_OUT_OF_WORLD,
            DamageTypes.DROWN,
            DamageTypes.IN_WALL,
            DamageTypes.STARVE,
            DamageTypes.CACTUS,
            DamageTypes.SWEET_BERRY_BUSH,
            DamageTypes.FREEZE,
            DamageTypes.HOT_FLOOR,
            DamageTypes.CAMPFIRE,
            DamageTypes.IN_FIRE,
            DamageTypes.ON_FIRE,
            DamageTypes.FALLING_BLOCK,
            DamageTypes.FALLING_ANVIL,
            DamageTypes.FALLING_STALACTITE,
            DamageTypes.LIGHTNING_BOLT,
            DamageTypes.DRY_OUT);

    /**
     * 玩家闪避状态。冷却与连闪计数保存在同一张表里，但<b>与"位移是否进行中"解耦</b>：
     * 位移结束后条目仍会保留到连闪窗口过期，避免冷却被提前丢弃。
     */
    private static final Map<UUID, DodgeState> DODGES = new HashMap<>();

    /** 速度低于此值（格/tick）视为停止。 */
    private static final double STOP_THRESHOLD = 0.05;

    private DodgeSystem() {
    }

    /** 客户端请求闪避（服务端接收 C2S DodgePayload 后调用）。 */
    public static void tryDodge(ServerPlayer player, float yaw) {
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        // 旁观模式下不可闪避
        if (player.isSpectator()) {
            return;
        }
        // 空中（脱离地面且非水中）不可闪避；水中可闪避
        if (!player.onGround() && !player.isInWater()) {
            return;
        }
        long tick = level.getGameTime();
        DodgeState prev = DODGES.get(player.getUUID());
        // 位移进行中，或冷却尚未走完 → 拒绝
        if (prev != null && (prev.active || tick < prev.readyAt)) {
            return;
        }
        // 连闪计数：距上一次位移结束超过重置窗口则归零
        int streak = (prev == null || tick - prev.lastDodgeEndTick > resetTicks()) ? 0 : prev.streak;
        int cdTicks = cooldownTicks(streak);

        double speed = peakSpeed(); // 峰值速度（格/tick）
        double distance = ServerConfig.DODGE_DISTANCE.get(); // 总滑行距离（格）
        // 闪避 = 瞬间速度脉冲 + 全程指数衰减：v(t) = v0 × friction^t，
        // 总位移 = v0 / (1 - friction) = distance → friction = 1 - v0/distance。
        double friction = friction(speed, distance);

        DodgeState state = new DodgeState();
        state.active = true;
        state.perfectThisDodge = false;
        state.cdTicks = cdTicks;
        state.streak = streak + 1;
        state.friction = friction;
        state.threshold = STOP_THRESHOLD;
        // 无敌帧不超过闪避持续时间：持续时间更短时，无敌帧被压到与位移同时结束
        state.invulnUntil = tick + invulnerabilityTicks();
        state.direction = Vec3.directionFromRotation(0.0F, yaw);
        state.speed = speed;
        // 位移时长 = ln(threshold/v0) / ln(friction)；
        // readyAt 先按该值占位（位移期间拒绝重复请求），位移真正结束时会在 tickPlayer 里按
        // 实际结束 tick 重新锚定，冷却始终从"上一次位移完全结束"起算。
        long durationTicks = dodgeDurationTicks();
        state.readyAt = tick + durationTicks + cdTicks;
        DODGES.put(player.getUUID(), state);

        NeoForge.EVENT_BUS.post(new DodgeEvent(player, yaw));
        player.setDeltaMovement(state.direction.scale(state.speed));
        player.hasImpulse = true;
        // 闪避音效由客户端本地播放（DodgeClient.onDodgeKey 即时触发），服务端不广播：
        // 本地已播、再广播会双播；且闪避声为自我感知音效。
        PacketDistributor.sendToPlayer(player, new DodgeCooldownPayload(state.streak, (int) (durationTicks + cdTicks), cdTicks));
    }

    /**
     * 每 tick 驱动闪避位移（服务端 PlayerTickEvent.Post）。
     * 闪避 = 瞬间速度脉冲 + 全程指数衰减（v(t) = v0 × friction^t）：一开始瞬间加速，
     * 随后逐渐减速停止。撞墙即停；速度衰减到阈值以下视为停止。
     * 客户端 {@code DodgeClient} 镜像同款数学。
     */
    public static void tickPlayer(ServerPlayer player) {
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        DodgeState state = DODGES.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!state.active) {
            // 位移已结束：条目需要保留到「冷却走完」与「连闪窗口过期」两者中较晚的时刻，
            // 否则冷却或连闪计数会被提前丢弃（resetTicks 配置为 0 时尤其明显）。
            long retainUntil = Math.max(state.readyAt, state.lastDodgeEndTick + resetTicks());
            if (level.getGameTime() > retainUntil) {
                DODGES.remove(player.getUUID());
            }
            return;
        }
        long tick = level.getGameTime();
        // 撞墙、撞障碍物，或速度已衰减到阈值以下 → 位移结束。
        // 冷却从这一 tick 起算（若本次已被完美闪避取消则为 0）。
        if (player.horizontalCollision || state.speed < state.threshold) {
            state.active = false;
            state.lastDodgeEndTick = tick;
            state.readyAt = tick + (state.perfectThisDodge ? 0 : state.cdTicks);
            // 只清水平速度，保留 y，避免把下落/跳跃速度一并清零
            player.setDeltaMovement(new Vec3(0.0, player.getDeltaMovement().y, 0.0));
            return;
        }
        Vec3 current = player.getDeltaMovement();
        Vec3 dir = state.direction;
        player.setDeltaMovement(new Vec3(dir.x * state.speed, current.y, dir.z * state.speed));
        state.speed *= state.friction; // 指数衰减
    }

    /**
     * 无敌帧伤害拦截（由 LivingEntityMixin 在 hurt 头部调用）。
     * <p>
     * 在 {@code hurt} 方法最前端拦截，可绕过原版 invulnerableTime（约 1 秒免伤）的早期返回，
     * 从而解决"完美闪避触发后仍被原版免伤保护、导致攻击丢失"的叠加问题。
     * <p>
     * 这里处理的是<b>近战与爆炸等直接伤害</b>。弹射物不走这条路径：无敌帧内玩家对弹射物无碰撞箱
     * （见 {@link #isInvulnerableFrame}），箭/三叉戟等直接穿过、不会命中，其完美闪避由
     * {@link #checkProjectilePerfectDodge} 单独判定——因此也不会触发原版 {@code AbstractArrow}
     * 的反弹（{@code deflect(REVERSE)}）。
     *
     * @return true 表示本次伤害被闪避取消（触发完美闪避）；false 表示不拦截，走原版流程
     */
    public static boolean tryInterceptDamage(Player player, DamageSource source) {
        if (player.level().isClientSide) {
            return false;
        }
        DodgeState state = DODGES.get(player.getUUID());
        if (state == null) {
            return false;
        }
        long tick = player.level().getGameTime();
        if (tick >= state.invulnUntil) {
            return false; // 无敌帧窗口已过
        }
        if (isEnvironmentDamage(source)) {
            return false; // 环境伤害不可躲避
        }
        if (player.isInvulnerableTo(source)) {
            return false;
        }
        // 同一次无敌帧内可能被多次命中（多发弹射物、穿刺箭等）：表现只触发一次，
        // 后续命中仍照常取消伤害（不重复播音效/闪光）。
        if (!state.perfectThisDodge) {
            triggerPerfectDodge(player, state);
        }
        return true;
    }

    /**
     * 触发完美闪避：取消<b>本次</b>闪避的冷却（连闪计数照常累加）、通知客户端播放闪光与音效。
     */
    private static void triggerPerfectDodge(Player player, DodgeState state) {
        state.perfectThisDodge = true;
        state.readyAt = player.level().getGameTime();
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new PerfectDodgePayload());
            // 完美闪避音效由客户端本地播放（DodgeClient.onPerfectDodge 即时触发），服务端不广播（避免双播）。
            // debug 级日志：开发环境（run/logs/debug.log）可见，用于排查"完美闪避表现未生效"。
            FlexibleDodge.LOGGER.debug("[flexibledodge] perfect dodge: payload sent to {}", serverPlayer.getScoreboardName());
            NeoForge.EVENT_BUS.post(new PerfectDodgeEvent(serverPlayer));
        }
    }

    private static boolean isEnvironmentDamage(DamageSource source) {
        for (ResourceKey<DamageType> key : ENVIRONMENT_DAMAGE) {
            if (source.is(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否处于闪避无敌帧内（服务端判定）。
     * <p>
     * 由 {@code EntityMixin} 用于让玩家在此窗口内对弹射物"无碰撞箱"（{@code canBeHitByProjectile = false}），
     * 箭/三叉戟/火球等直接穿过而非命中后被弹开。
     */
    public static boolean isInvulnerableFrame(Player player) {
        if (player.level().isClientSide) {
            return false;
        }
        DodgeState state = DODGES.get(player.getUUID());
        return state != null && player.level().getGameTime() < state.invulnUntil;
    }

    /**
     * 弹射物完美闪避检测（由 {@code FlexibleDodge} 在 {@code EntityTickEvent.Post} 调用）。
     * <p>
     * 无敌帧内弹射物被 {@link #isInvulnerableFrame} 判定为"无碰撞箱"而穿过玩家，不会走到 {@code hurt}，
     * 因此这里补一次"若不过滤、原版会不会命中你"的判定：几何与 {@code AbstractArrow.findHitEntity}
     * 完全一致（同样的扫掠盒、同样的 0.3 容差），只是把过滤器换成"只找该玩家"，从而绕过穿透。
     * <p>
     * 必须挂在弹射物<b>自身 tick 之后</b>：此时 {@code xOld/yOld/zOld → position()} 才是本 tick 的真实位移段；
     * 挂在玩家 tick 上会因实体 tick 顺序可能晚一 tick，而漏掉无敌帧最后一 tick 的穿越。
     */
    public static void checkProjectilePerfectDodge(Projectile projectile) {
        Level level = projectile.level();
        if (level.isClientSide) {
            return;
        }
        long tick = level.getGameTime();
        for (Player player : level.players()) {
            if (!(player instanceof ServerPlayer target)) {
                continue;
            }
            DodgeState state = DODGES.get(target.getUUID());
            if (state == null || state.perfectThisDodge) {
                continue; // 没有闪避状态，或本次无敌帧已经触发过表现
            }
            if (tick >= state.invulnUntil) {
                continue; // 不在无敌帧内
            }
            Entity owner = projectile.getOwner();
            if (owner == target) {
                continue; // 自己的弹射物不算
            }
            if (owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(target)) {
                continue; // 与原版一致：PvP 关闭 / 同队玩家的弹射物本来就不会命中
            }
            Vec3 start = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
            Vec3 end = projectile.position();
            Vec3 move = end.subtract(start);
            if (move.lengthSqr() < 1.0E-7) {
                // 本 tick 几乎没动（例如刚生成在玩家身上）：退化为盒相交判定
                if (projectile.getBoundingBox().intersects(target.getBoundingBox())) {
                    triggerPerfectDodge(target, state);
                }
                continue;
            }
            // 注意：本方法在弹射物 tick 之后调用，position()/getBoundingBox() 已是"移动后"的位置，
            // 而原版 AbstractArrow 是在移动前调用 findHitEntity 的。因此扫掠盒必须朝"移动前"的方向
            // （start - end）扩展，才能覆盖本 tick 走过的整段体积；若照抄原版朝 move 扩展，
            // 玩家位于弹射物落点后方时会掉出候选盒，导致漏判。
            AABB broad = projectile.getBoundingBox().expandTowards(start.subtract(end)).inflate(1.0);
            if (ProjectileUtil.getEntityHitResult(level, projectile, start, end, broad, e -> e == target) != null) {
                triggerPerfectDodge(target, state);
            }
        }
    }

    /** 玩家登出时清理状态，避免内存泄漏。 */
    public static void onPlayerLoggedOut(ServerPlayer player) {
        DODGES.remove(player.getUUID());
    }

    /**
     * 第 {@code streak + 1} 次闪避结束后应施加的冷却（tick）。
     * streak 为"已连续闪避次数"：第 1 次 streak=0 → 0；第 2 次 streak=1 → 1 步；依此类推并封顶。
     */
    public static int cooldownTicks(int streak) {
        double step = ServerConfig.DODGE_COOLDOWN_STEP.get();
        double max = ServerConfig.DODGE_COOLDOWN_MAX.get();
        if (step <= 0.0) {
            return 0;
        }
        int stepTicks = Math.max(1, (int) Math.round(step * 20.0));
        int maxSteps = Math.max(1, (int) Math.round(max / step));
        return Math.min(maxSteps, Math.max(0, streak)) * stepTicks;
    }

    /** 连闪计数重置窗口（tick），从"上一次闪避位移结束"起算。 */
    public static int resetTicks() {
        return (int) Math.round(ServerConfig.DODGE_COOLDOWN_RESET_TIME.get() * 20.0);
    }

    /** 闪避峰值速度（格/tick）。 */
    private static double peakSpeed() {
        return ServerConfig.DODGE_SPEED.get() / 20.0;
    }

    /** 每 tick 摩擦系数（0.3~0.95，由峰值速度与总距离推导）。 */
    private static double friction(double speed, double distance) {
        return Math.min(0.95, Math.max(0.3, 1.0 - speed / Math.max(distance, 0.01)));
    }

    /**
     * 闪避位移持续时长（tick），<b>包含 {@code tryDodge} 施加的初始脉冲那一 tick</b>。
     * <p>
     * 位移 = 瞬间速度脉冲 + 全程指数衰减，v(t) = v0 × friction^t，到 v &lt; {@link #STOP_THRESHOLD}
     * 视为停止。被施加的速度序列为 {@code v0}（初始脉冲）以及 {@code v0 × friction^k}（k = 0..K，
     * K = floor(ln(threshold / v0) / ln(friction))），因此位移 tick 数 = 1 + (K + 1)。
     * <p>
     * 注意时长不是独立配置项，而是由 {@code dodgeSpeed}（峰值速度）与 {@code dodgeDistance}
     * 共同决定：距离相对峰值速度越小 → friction 越小 → 衰减越快 → 时长越短。
     * 当前默认（速度 10 格/秒、距离 1.0 格）下 friction = 0.5，时长 = 5 tick = 0.25 秒，
     * 实际滑行约 1.4 格（初始脉冲那一 tick 也在按峰值速度位移）。
     */
    public static int dodgeDurationTicks() {
        double speed = peakSpeed();
        double friction = friction(speed, ServerConfig.DODGE_DISTANCE.get());
        double k = Math.log(STOP_THRESHOLD / Math.max(speed, 1e-4)) / Math.log(friction);
        return (int) Math.max(1L, (long) Math.floor(k) + 2L);
    }

    /**
     * 闪避无敌帧时长（tick）。配置值若长于闪避持续时间，则被压到与位移同时结束。
     */
    public static int invulnerabilityTicks() {
        int configured = (int) Math.round(ServerConfig.DODGE_INVULNERABILITY_TIME.get() * 20.0);
        return Math.max(0, Math.min(configured, dodgeDurationTicks()));
    }

    private static final class DodgeState {
        /** 位移是否进行中。 */
        boolean active;
        /** 本次闪避是否已被完美闪避取消冷却。 */
        boolean perfectThisDodge;
        /** 本次闪避位移结束后应施加的冷却（tick）。 */
        int cdTicks;
        long invulnUntil;
        /** 允许下一次闪避的 tick（位移结束 tick + 冷却）。 */
        long readyAt;
        /** 连闪计数锚点：上一次位移真正结束的 tick。 */
        long lastDodgeEndTick;
        /** 已连续闪避次数（含本次）。 */
        int streak;
        Vec3 direction;
        /** 当前速度（格/tick），每 tick × friction 指数衰减。 */
        double speed;
        /** 每 tick 摩擦系数（0.3~0.95，由峰值速度与总距离推导）。 */
        double friction;
        /** 速度低于此值视为停止。 */
        double threshold;
    }
}
