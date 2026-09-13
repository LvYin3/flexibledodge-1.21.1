package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.dodge.DodgeClient;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 payload 处理器。S2C 包在此处理。
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleCooldownSync(final DodgeCooldownPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> DodgeClient.onCooldownSync(payload.streak(), payload.readyInTicks(), payload.cdTicks()));
    }

    public static void handlePerfectDodge(final PerfectDodgePayload payload, final IPayloadContext context) {
        context.enqueueWork(DodgeClient::onPerfectDodge);
    }
}
