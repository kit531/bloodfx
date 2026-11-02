package com.kit531.bloodfx;

import com.kit531.bloodfx.gui.BloodFxConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the settings button for this mod to Mod Menu. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BloodFxConfigScreen::new;
    }
}
