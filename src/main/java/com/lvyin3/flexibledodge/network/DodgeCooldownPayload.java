package com.lvyin3.flexibledodge.network;

import com.lvyin3.flexibledodge.FlexibleDodge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：闪避被服务端接受后同步冷却信息，用于校正客户端的本地镜像与驱动冷却条。
 *
 * @param streak       服务端权威连闪计数（含本次闪避）
 * @param readyInTicks 从收到本包起，还需多少 tick 才允许下一次闪避（位移余量 + 冷却）
 * @param cdTicks      <b>本次</b>闪避位移结束后要走的冷却长度（tick）：
 *                     决定"位移期间是否显示空条"与"位移结束后涨满读条的总时长"
 */
public record DodgeCooldownPayload(int streak, int readyInTicks, int cdTicks) implements CustomPacketPayload {
    public static final Type<DodgeCooldownPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FlexibleDodge.MODID, "dodge_cooldown"));
    public static final StreamCodec<FriendlyByteBuf, DodgeCooldownPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.streak());
                buf.writeVarInt(payload.readyInTicks());
                buf.writeVarInt(payload.cdTicks());
            },
            buf -> new DodgeCooldownPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
