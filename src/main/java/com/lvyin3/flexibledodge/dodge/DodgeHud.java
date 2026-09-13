package com.lvyin3.flexibledodge.dodge;

import com.lvyin3.flexibledodge.FlexibleDodge;
import com.lvyin3.flexibledodge.config.ClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 准星区域的闪避 HUD：
 * <ul>
 *   <li>闪避指示标记：普通闪避 {@code < + >}（向两侧略微移动并淡出）；完美闪避 {@code <<< + >>>}（从内到外依次淡入淡出）。</li>
 *   <li>闪避冷却条：位于原版攻击冷却条正下方，<b>自绘的半透明细长条</b>——不使用原版的剑形进度贴图
 *       （其左端 4px 是剑柄，低填充时会露出棕褐色一团），只沿用其配色，并采用与原版准星/攻击冷却条
 *       相同的反相混合（{@code ONE_MINUS_DST_COLOR} / {@code ONE_MINUS_SRC_COLOR}），
 *       使其在任意明暗背景上都呈半透明可读。</li>
 * </ul>
 * 冷却条规则：
 * <ol>
 *   <li>冷却从"上一次闪避位移完全结束"的那一刻起算。</li>
 *   <li>位移进行期间：本次冷却不为 0 → 显示空条（只有底槽）；本次冷却为 0 → 不显示。</li>
 *   <li>位移结束后：读条从空涨满，涨满即隐藏。</li>
 *   <li>完美闪避：以满条状态整体闪一下后消失。</li>
 * </ol>
 * <p>
 * 实现要点：所有不透明度都直接烘进顶点色（{@code fill} 的 ARGB），<b>不依赖 {@code GuiGraphics#setColor}
 * 的 ColorModulator</b>——后者只在 {@code drawManaged} 托管刷新时才能保证在绘制前生效，
 * 而 {@code RenderGuiEvent.Post} 阶段是非托管模式（{@code flushIfManaged()} 为空操作），
 * 靠它做淡出会静默失效（这正是此前"完美闪避闪光看不到"的原因）。
 */
@EventBusSubscriber(modid = FlexibleDodge.MODID, value = Dist.CLIENT)
public final class DodgeHud {
    /** 冷却条外框尺寸（与原版准星攻击冷却条同宽）。 */
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 4;
    /** 与原版攻击冷却条（guiHeight/2 + 9 起，高 4）间隔 2px。 */
    private static final int BAR_GAP = 2;

    /** 底槽 RGB：取自原版 minecraft:hud/crosshair_attack_indicator_background 的不透明深灰 #3A3B3C。 */
    private static final int TRACK_RGB = 0x3A3B3C;
    /** 填充 RGB：取自原版剑形进度条的刃部两段色（上 #D7D9EA、下 #FFFFFF），去掉剑柄部分。 */
    private static final int FILL_TOP_RGB = 0xD7D9EA;
    private static final int FILL_BOTTOM_RGB = 0xFFFFFF;
    /** 半透明度：底槽更透、填充更实，兼顾"半透明"与可读性。 */
    private static final int TRACK_ALPHA = 150;
    private static final int FILL_ALPHA = 235;

    /** 完美闪避闪光时长曲线的保持比例（前 40% 全亮，确保一眼能看见，再线性淡出）。 */
    private static final float FLASH_HOLD = 0.4F;

    private DodgeHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        renderMarkers(gui, mc);
        renderCooldownBar(gui);
    }

    // ---- 闪避指示标记 ----

    private static void renderMarkers(GuiGraphics gui, Minecraft mc) {
        if (!ClientConfig.DODGE_MARKER_ENABLED.get()) {
            return;
        }
        long duration = DodgeClient.markerDurationMs();
        if (duration <= 0L) {
            return;
        }
        double p = (Util.getMillis() - DodgeClient.markerStartMs()) / (double) duration;
        if (p < 0.0 || p >= 1.0) {
            return;
        }
        int cx = gui.guiWidth() / 2;
        int cy = gui.guiHeight() / 2;
        Font font = mc.font;

        if (DodgeClient.isPerfectMarker()) {
            drawPerfectMarker(gui, font, cx, cy, p);
        } else {
            drawNormalMarker(gui, font, cx, cy, p);
        }
    }

    private static void drawNormalMarker(GuiGraphics gui, Font font, int cx, int cy, double p) {
        int alpha = (int) ((1.0 - p) * 200.0);
        int color = (alpha << 24) | 0xFFFFFF;
        int offset = 8 + (int) (p * 10.0);
        int vy = cy - font.lineHeight / 2;
        gui.drawCenteredString(font, "<", cx - offset, vy, color);
        gui.drawCenteredString(font, ">", cx + offset, vy, color);
    }

    private static void drawPerfectMarker(GuiGraphics gui, Font font, int cx, int cy, double p) {
        int vy = cy - font.lineHeight / 2;
        for (int i = 0; i < 3; i++) {
            // 每个括号 i 在 [i/3, (i+1)/3] 窗口内淡入淡出（从内到外依次）
            double q = p * 3.0 - i;
            if (q < 0.0 || q > 1.0) {
                continue;
            }
            double alpha = Math.sin(q * Math.PI);
            int color = ((int) (alpha * 220.0) << 24) | 0xFFFFFF;
            int offset = 8 + i * 5 + (int) (p * 8.0);
            gui.drawCenteredString(font, "<", cx - offset, vy, color);
            gui.drawCenteredString(font, ">", cx + offset, vy, color);
        }
    }

    // ---- 闪避冷却条 ----

    private static void renderCooldownBar(GuiGraphics gui) {
        if (!ClientConfig.DODGE_COOLDOWN_BAR_ENABLED.get()) {
            return;
        }
        int x = gui.guiWidth() / 2 - BAR_WIDTH / 2;
        // 原版攻击冷却条：y = guiHeight/2 - 7 + 16 = guiHeight/2 + 9，高 4 → 下缘在 guiHeight/2 + 13，
        // 本条紧贴其下方留 BAR_GAP 间距。第一/第三人称都绘制，便于在 F5 观察闪避与闪光。
        int y = gui.guiHeight() / 2 + 13 + BAR_GAP;

        float flash = DodgeClient.perfectFlash01();
        if (flash >= 0.0F) {
            // 完美闪避：满条 + 白色高光，整体淡出（"闪一下消失"）。
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float fade = flashAlpha(flash);
            drawBar(gui, x, y, BAR_WIDTH, fade);
            // 满条高光：让"闪"明显区别于常驻底槽
            gui.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, ((int) (200.0F * fade) << 24) | 0xFFFFFF);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            return; // 闪光期间不绘制其它状态（保持"整套一起闪"的观感）
        }

        float progress = DodgeClient.cooldownBarProgress();
        if (progress < 0.0F) {
            return; // 就绪 / 本次无冷却：不显示
        }
        // 与原版准星、攻击冷却条相同的反相混合（半透明、随背景明暗自适应）。
        // 注：若该 GL 状态在刷新时被 RenderType 覆盖，顶点色里烘好的 alpha 仍能保证半透明观感。
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        // progress == 0 → 只画底槽（位移进行中且本次有冷却：空条）
        int filled = progress <= 0.0F ? 0 : (int) Math.ceil(progress * BAR_WIDTH);
        drawBar(gui, x, y, Math.min(filled, BAR_WIDTH), 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * 自绘冷却条：半透明深灰底槽 + 居中 2px 填充条。
     * 不使用原版的剑形进度贴图——其左端 4px 是棕色剑柄，低填充时会露出剑柄而显得杂乱。
     *
     * @param filled 填充宽度（像素，0 = 只有底槽）
     * @param fade   整体淡出系数（1 = 常态；完美闪避闪光时由 1 降到 0）
     */
    private static void drawBar(GuiGraphics gui, int x, int y, int filled, float fade) {
        float f = Math.max(0.0F, Math.min(1.0F, fade));
        int trackAlpha = (int) (TRACK_ALPHA * f);
        int fillAlpha = (int) (FILL_ALPHA * f);
        gui.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, (trackAlpha << 24) | TRACK_RGB);
        if (filled <= 0) {
            return;
        }
        int right = x + Math.min(filled, BAR_WIDTH);
        gui.fill(x, y + 1, right, y + 2, (fillAlpha << 24) | FILL_TOP_RGB);
        gui.fill(x, y + 2, right, y + 3, (fillAlpha << 24) | FILL_BOTTOM_RGB);
    }

    /** 闪光不透明度曲线：前 {@link #FLASH_HOLD} 保持全亮，之后线性淡出到 0。 */
    private static float flashAlpha(float p) {
        if (p <= FLASH_HOLD) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (p - FLASH_HOLD) / (1.0F - FLASH_HOLD));
    }
}
