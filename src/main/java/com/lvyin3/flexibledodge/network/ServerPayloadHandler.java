package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.dodge.DodgeSystem;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 payload 处理器。C2S 包在此处理，所有逻辑在服务端主线程执行。
 */
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    public static void handleDodge(final DodgePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                DodgeSystem.tryDodge(serverPlayer, payload.yaw());
            }
        });
    }
}
