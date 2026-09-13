package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.FlexibleDodge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：通知客户端发生了完美闪避。
 * 客户端收到后：显示满条闪一下、取消本次冷却的读条、播放完美闪避音效（替换普通闪避音）。
 */
public record PerfectDodgePayload() implements CustomPacketPayload {
    public static final Type<PerfectDodgePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FlexibleDodge.MODID, "perfect_dodge"));
    public static final StreamCodec<FriendlyByteBuf, PerfectDodgePayload> STREAM_CODEC = StreamCodec.unit(new PerfectDodgePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
