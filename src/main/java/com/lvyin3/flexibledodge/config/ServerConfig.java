package com.lvyin3.flexibledodge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端配置。闪避的运动、无敌帧与冷却规则全部在此，经 NeoForge 的 server config
 * 自动同步到客户端，避免判定不一致。
 */
public class ServerConfig {
    public static final ModConfigSpec SPEC;

    /** 闪避无敌帧时长（秒）。默认 0.2 */
    public static final ModConfigSpec.DoubleValue DODGE_INVULNERABILITY_TIME;
    /** 闪避移动速度（格/秒）。默认 10.0 */
    public static final ModConfigSpec.DoubleValue DODGE_SPEED;
    /** 闪避距离（格）。默认 3.0 */
    public static final ModConfigSpec.DoubleValue DODGE_DISTANCE;
    /** 连闪递增的冷却步长（秒）。默认 0.1 */
    public static final ModConfigSpec.DoubleValue DODGE_COOLDOWN_STEP;
    /** 连闪冷却上限（秒）。默认 0.6 */
    public static final ModConfigSpec.DoubleValue DODGE_COOLDOWN_MAX;
    /** 连闪计数重置时间（秒）：距上一次闪避位移结束超过该时长则计数归零。默认 2.0 */
    public static final ModConfigSpec.DoubleValue DODGE_COOLDOWN_RESET_TIME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        DODGE_INVULNERABILITY_TIME = builder
                .comment(" 闪避无敌帧时长（秒）。默认 0.2。若长于闪避位移持续时间，会被压缩到与位移同时结束。")
                .comment(" Dodge invulnerability frames duration in seconds. Default: 0.2. If longer than the dodge's movement duration it is clamped to end with the movement.")
                .translation("config.flexibledodge.dodgeInvulnerabilityTime")
                .defineInRange("dodgeInvulnerabilityTime", 0.2, 0.0, 10.0);

        DODGE_SPEED = builder
                .comment(" 闪避移动速度（格/秒）。默认 10.0。")
                .comment(" Dodge movement speed in blocks per second. Default: 10.0.")
                .translation("config.flexibledodge.dodgeSpeed")
                .defineInRange("dodgeSpeed", 10.0, 0.0, 50.0);

        DODGE_DISTANCE = builder
                .comment(" 闪避距离（格）。默认 1.0。这是指数衰减的理论总位移；由于初始脉冲与随后一 tick 都按峰值速度施加，")
                .comment(" 实际滑行会略多于该值（1.0 时约 1.4 格）。该值与 dodgeSpeed 共同决定闪避持续时间：距离相对峰值速度越小，衰减越快、时长越短。")
                .comment(" Dodge distance in blocks. Default: 1.0. This is the geometric-decay total distance; the actual dash travels slightly more")
                .comment(" (about 1.4 blocks at 1.0) because the initial pulse and the following tick are both applied at peak speed.")
                .comment(" Together with dodgeSpeed this determines the dodge duration: a smaller distance relative to the peak speed decays faster and lasts shorter.")
                .translation("config.flexibledodge.dodgeDistance")
                .defineInRange("dodgeDistance", 1.0, 0.0, 20.0);

        DODGE_COOLDOWN_STEP = builder
                .comment(" 连闪递增冷却步长（秒）。第 1 次闪避无冷却，此后每次 +该值，直到冷却上限。默认 0.1。")
                .comment(" Cooldown step per consecutive dodge in seconds: the 1st dodge has no cooldown, each following dodge adds this amount up to the cap. Default: 0.1.")
                .translation("config.flexibledodge.dodgeCooldownStep")
                .defineInRange("dodgeCooldownStep", 0.1, 0.0, 10.0);

        DODGE_COOLDOWN_MAX = builder
                .comment(" 连闪冷却上限（秒）。默认 0.6。")
                .comment(" Upper bound of the consecutive dodge cooldown in seconds. Default: 0.6.")
                .translation("config.flexibledodge.dodgeCooldownMax")
                .defineInRange("dodgeCooldownMax", 0.6, 0.0, 10.0);

        DODGE_COOLDOWN_RESET_TIME = builder
                .comment(" 连闪计数重置时间（秒）：距上一次闪避位移结束超过该时长后再次闪避，冷却重新从第 1 次算起。默认 2.0。")
                .comment(" Consecutive dodge counter reset time in seconds, counted from the end of the previous dodge's movement: dodging after this much time restarts the cooldown ramp. Default: 2.0.")
                .translation("config.flexibledodge.dodgeCooldownResetTime")
                .defineInRange("dodgeCooldownResetTime", 2.0, 0.0, 60.0);

        SPEC = builder.build();
    }
}
