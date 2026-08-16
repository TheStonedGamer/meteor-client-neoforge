/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class GravityControl extends Module {
    private static final String ULTRA_SPACE = "pixelmon:ultra_space";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> ultraSpaceOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("ultra-space-only")
        .description("Only adjusts gravity in Pixelmon's Ultra Space dimension.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> downwardForce = sgGeneral.add(new DoubleSetting.Builder()
        .name("downward-force")
        .description("Downward velocity added each tick. 0.07 exactly cancels Pixelmon's Ultra Space lift.")
        .defaultValue(0.07)
        .min(-1)
        .max(1)
        .sliderMin(-0.2)
        .sliderMax(0.2)
        .build()
    );

    public GravityControl() {
        super(Categories.Movement, "gravity-control", "Adjusts gravity and restores normal movement in Pixelmon Ultra Space.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (ultraSpaceOnly.get() && !isUltraSpace()) return;

        // Match Pixelmon's UltraSpaceListener conditions so its +0.07 lift is
        // only counteracted on ticks where Pixelmon actually applies it.
        if (mc.player.isCreative() || mc.player.isSpectator() || mc.player.hasNoGravity()
            || mc.player.hasVehicle() || mc.player.isSneaking() || mc.player.isSwimming()) return;

        IVec3d velocity = (IVec3d) mc.player.getVelocity();
        velocity.setY(mc.player.getVelocity().y - downwardForce.get());
    }

    private boolean isUltraSpace() {
        return ULTRA_SPACE.equals(mc.world.getRegistryKey().getValue().toString());
    }
}
