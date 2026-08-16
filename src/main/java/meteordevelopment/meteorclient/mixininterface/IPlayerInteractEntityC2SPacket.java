/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixininterface;

import net.minecraft.entity.Entity;

public interface IPlayerInteractEntityC2SPacket {
    boolean isAttack();

    boolean isInteractAt();

    Entity getEntity();
}
