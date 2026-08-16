/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Random;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Unique
    private static final Random RANDOM = new Random();

    @ModifyVariable(method = "getModelOffset", at = @At("HEAD"), argsOnly = true)
    private BlockPos modifyPos(BlockPos pos) {
        if (Modules.get() == null) return pos;

        if (Modules.get().get(NoRender.class).noTextureRotations()) return pos.multiply(RANDOM.nextInt());
        return pos;
    }

    // NeoForge/Lithium bypass BlockCollisionSpliterator, where Meteor normally
    // posts this event. Hooking the state query keeps player collision modules
    // such as Jesus Solid working with either collision implementation.
    @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;", at = @At("RETURN"))
    private VoxelShape meteor$collisionShape(VoxelShape original, BlockView world, BlockPos pos, ShapeContext context) {
        if (MeteorClient.mc == null || world != MeteorClient.mc.world) return original;
        if (!(context instanceof EntityShapeContext entityContext) || entityContext.getEntity() != MeteorClient.mc.player) return original;

        CollisionShapeEvent event = MeteorClient.EVENT_BUS.post(CollisionShapeEvent.get((BlockState) (Object) this, pos, original));
        return event.isCancelled() ? VoxelShapes.empty() : event.shape;
    }
}
