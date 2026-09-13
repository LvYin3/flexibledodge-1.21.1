package com.lvyin3.flexibledodge.dodge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 闪避成功事件（服务端，game 总线）。
 * 在 DodgeSystem 判定闪避成功并进入无敌帧时触发。
 */
public class DodgeEvent extends Event {
    private final ServerPlayer player;
    private final float yaw;

    public DodgeEvent(ServerPlayer player, float yaw) {
        this.player = player;
        this.yaw = yaw;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** 闪避方向偏航角（度，朝向）。 */
    public float getYaw() {
        return yaw;
    }
}
