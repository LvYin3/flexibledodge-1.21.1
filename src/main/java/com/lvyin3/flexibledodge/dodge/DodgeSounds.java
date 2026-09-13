package com.lvyin3.flexibledodge.dodge;

import com.lvyin3.flexibledodge.FlexibleDodge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组音效注册。音频位于 assets/flexibledodge/sounds/ 下（.ogg）。
 */
public final class DodgeSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FlexibleDodge.MODID);

    /** 闪避音效 */
    public static final DeferredHolder<SoundEvent, SoundEvent> DODGE = register("dodge");
    /** 完美闪避音效 */
    public static final DeferredHolder<SoundEvent, SoundEvent> PERFECT_DODGE = register("perfect_dodge");

    private DodgeSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FlexibleDodge.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
