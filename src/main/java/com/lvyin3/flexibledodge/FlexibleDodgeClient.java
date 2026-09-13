package com.lvyin3.flexibledodge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// 纯客户端入口类：仅在客户端加载。
// 负责注册 NeoForge 自带 ConfigurationScreen，使配置项可在 模组列表→配置 界面内修改。
@Mod(value = FlexibleDodge.MODID, dist = Dist.CLIENT)
public class FlexibleDodgeClient {
    public FlexibleDodgeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
