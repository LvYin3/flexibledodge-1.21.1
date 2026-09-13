package com.lvyin3.flexibledodge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。纯客户端表现相关：闪避指示标记与准星下方的闪避冷却条。
 */
public class ClientConfig {
    public static final ModConfigSpec SPEC;

    /** 闪避指示标记（&lt; &gt; / &lt;&lt;&lt; &gt;&gt;&gt;）开关。默认开启 */
    public static final ModConfigSpec.BooleanValue DODGE_MARKER_ENABLED;
    /** 闪避指示标记时长（秒）：标记从出现到完全淡出的持续时间。默认 0.2 */
    public static final ModConfigSpec.DoubleValue DODGE_MARKER_TIME;
    /** 准星下方闪避冷却条开关（含完美闪避闪光）。默认开启 */
    public static final ModConfigSpec.BooleanValue DODGE_COOLDOWN_BAR_ENABLED;
    /** 完美闪避满条闪一下的时长（秒）。默认 0.15 */
    public static final ModConfigSpec.DoubleValue DODGE_COOLDOWN_BAR_FLASH_TIME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        DODGE_MARKER_ENABLED = builder
                .comment(" 显示闪避指示标记（< > 与完美闪避的 <<< >>>）。默认开启。")
                .comment(" Show the dodge indicator markers (< >, and <<< >>> for a perfect dodge). Default: true.")
                .translation("config.flexibledodge.dodgeMarkerEnabled")
                .define("dodgeMarkerEnabled", true);

        DODGE_MARKER_TIME = builder
                .comment(" 闪避指示标记时长（秒）。< > 标记从出现到完全淡出的持续时间。默认 0.2。")
                .comment(" Dodge indicator marker duration in seconds. How long the < > markers stay visible. Default: 0.2.")
                .translation("config.flexibledodge.dodgeMarkerTime")
                .defineInRange("dodgeMarkerTime", 0.2, 0.05, 2.0);

        DODGE_COOLDOWN_BAR_ENABLED = builder
                .comment(" 在准星下方显示闪避冷却条（样式与原版攻击冷却条一致）。默认开启。")
                .comment(" Show the dodge cooldown bar below the crosshair (styled after the vanilla attack indicator). Default: true.")
                .translation("config.flexibledodge.dodgeCooldownBarEnabled")
                .define("dodgeCooldownBarEnabled", true);

        DODGE_COOLDOWN_BAR_FLASH_TIME = builder
                .comment(" 完美闪避时冷却条以满条状态闪一下的时长（秒）。默认 0.15。")
                .comment(" Duration of the full-bar flash shown on a perfect dodge, in seconds. Default: 0.15.")
                .translation("config.flexibledodge.dodgeCooldownBarFlashTime")
                .defineInRange("dodgeCooldownBarFlashTime", 0.15, 0.05, 1.0);

        SPEC = builder.build();
    }
}
