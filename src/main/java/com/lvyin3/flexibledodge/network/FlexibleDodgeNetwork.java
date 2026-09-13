package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.FlexibleDodge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络层基础设施：注册所有自定义 payload。
 * 协议版本用于双端协商，升级协议时递增。
 */
@EventBusSubscriber(modid = FlexibleDodge.MODID)
public class FlexibleDodgeNetwork {
    public static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // C2S：闪避请求（携带闪避方向）
        registrar.playToServer(DodgePayload.TYPE, DodgePayload.STREAM_CODEC, ServerPayloadHandler::handleDodge);

        // S2C：闪避冷却同步（连闪计数 / 剩余可再闪 tick / 本次冷却长度）
        registrar.playToClient(DodgeCooldownPayload.TYPE, DodgeCooldownPayload.STREAM_CODEC, ClientPayloadHandler::handleCooldownSync);

        // S2C：完美闪避通知（满条闪光 + 取消本次冷却 + 完美闪避音效）
        registrar.playToClient(PerfectDodgePayload.TYPE, PerfectDodgePayload.STREAM_CODEC, ClientPayloadHandler::handlePerfectDodge);
    }
}
