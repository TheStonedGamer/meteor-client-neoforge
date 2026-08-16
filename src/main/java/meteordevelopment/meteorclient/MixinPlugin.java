/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient;

import meteordevelopment.meteorclient.asm.Asm;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {
    private static final String mixinPackage = "meteordevelopment.meteorclient.mixin";

    private static boolean loaded;

    private static boolean isOriginsPresent;
    private static boolean isIndigoPresent;
    public static boolean isSodiumPresent;
    private static boolean isCanvasPresent;
    private static boolean isLithiumPresent;
    public static boolean isIrisPresent;
    private static boolean isIndiumPresent;
    private static boolean isVFPPresent;

    @Override
    public void onLoad(String mixinPackage) {
        if (loaded) return;

        // Meteor's additional transformer is coupled to Fabric's Knot class loader.
        // NeoForge applies the regular Meteor mixins through ModLauncher instead.

        isIndigoPresent = isModLoaded("fabric-renderer-indigo");
        isOriginsPresent = isModLoaded("origins");
        isSodiumPresent = isModLoaded("sodium");
        isCanvasPresent = isModLoaded("canvas");
        isLithiumPresent = isModLoaded("lithium");
        isIrisPresent = isModLoaded("iris");
        isIndiumPresent = isModLoaded("indium");
        isVFPPresent = isModLoaded("viafabricplus");

        loaded = true;
    }

    private static boolean isModLoaded(String id) {
        LoadingModList mods = LoadingModList.get();
        return mods != null && mods.getModFileById(id) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(mixinPackage)) {
            throw new RuntimeException("Mixin " + mixinClassName + " is not in the mixin package");
        }
        else if (mixinClassName.endsWith("PlayerEntityRendererMixin")) {
            return !isOriginsPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".sodium")) {
            return isSodiumPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".indigo")) {
            return isIndigoPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".canvas")) {
            return isCanvasPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".lithium")) {
            return isLithiumPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".indium")) {
            return isIndiumPresent;
        }
        else if (mixinClassName.startsWith(mixinPackage + ".viafabricplus")) {
            return isVFPPresent;
        }


        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
