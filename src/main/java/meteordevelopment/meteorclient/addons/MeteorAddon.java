/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.addons;

import meteordevelopment.meteorclient.utils.render.color.Color;

public abstract class MeteorAddon {
    /** Assigned from NeoForge mod metadata when discovered through meteor_addon. */
    public String name;

    /** Assigned from NeoForge mod metadata when available. */
    public String[] authors;

    /** Assigned from the meteor_color property in neoforge.mods.toml when available. */
    public final Color color = new Color(255, 255, 255);

    public abstract void onInitialize();

    public void onRegisterCategories() {}

    public abstract String getPackage();

    public String getWebsite() {
        return null;
    }

    public GithubRepo getRepo() {
        return null;
    }

    public String getCommit() {
        return null;
    }
}
