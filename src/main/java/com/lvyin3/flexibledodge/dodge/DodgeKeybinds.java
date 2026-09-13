package com.lvyin3.flexibledodge.dodge;

import com.lvyin3.flexibledodge.FlexibleDodge;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 模组按键绑定。闪避键默认 Left Alt。
 */
@EventBusSubscriber(modid = FlexibleDodge.MODID, value = Dist.CLIENT)
public final class DodgeKeybinds {
    public static final String CATEGORY = "key.categories.flexibledodge";

    public static final KeyMapping DODGE = new KeyMapping(
            "key.flexibledodge.dodge",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LALT,
            CATEGORY);

    private DodgeKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
    }
}
