package com.lvyin3.flexibledodge.dodge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 完美闪避事件（服务端，game 总线）。
 * 在 DodgeSystem 触发完美闪避（本次闪避冷却被取消）时触发。
 */
public class PerfectDodgeEvent extends Event {
    private final ServerPlayer player;

    public PerfectDodgeEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
