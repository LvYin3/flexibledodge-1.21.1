package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.FlexibleDodge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S：客户端请求闪避。
 * 携带闪避方向在世界空间中的偏航角（度）。方向由客户端根据按下闪避键时的移动键输入与玩家朝向计算，
 * 无移动键时默认向后。服务端仅负责施加运动与校验冷却/边界。
 */
public record DodgePayload(float yaw) implements CustomPacketPayload {
    public static final Type<DodgePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FlexibleDodge.MODID, "dodge"));
    public static final StreamCodec<FriendlyByteBuf, DodgePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.yaw()),
            buf -> new DodgePayload(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
