/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.addons;

import meteordevelopment.meteorclient.MeteorClient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

public class AddonManager {
    public static final List<MeteorAddon> ADDONS = new ArrayList<>();

    public static void init() {
        ADDONS.clear();

        // Meteor pseudo addon
        {
            MeteorClient.ADDON = new MeteorAddon() {
                @Override
                public void onInitialize() {}

                @Override
                public String getPackage() {
                    return "meteordevelopment.meteorclient";
                }

                @Override
                public String getWebsite() {
                    return "https://meteorclient.com";
                }

                @Override
                public GithubRepo getRepo() {
                    return new GithubRepo("MeteorDevelopment", "meteor-client");
                }

                @Override
                public String getCommit() {
                    String commit = System.getProperty("meteor.commit", "");
                    return commit.isEmpty() ? null : commit;
                }
            };

            MeteorClient.ADDON.name = MeteorClient.NAME;
            MeteorClient.ADDON.authors = new String[] { "MineGame159", "squidoodly", "seasnail" };
            MeteorClient.ADDON.color.parse("145,61,226");
        }

        Set<Class<?>> discovered = new HashSet<>();

        // Standard Java provider discovery. Addons can declare
        // META-INF/services/meteordevelopment.meteorclient.addons.MeteorAddon.
        try {
            for (MeteorAddon addon : ServiceLoader.load(MeteorAddon.class, Thread.currentThread().getContextClassLoader())) {
                add(addon, null, discovered);
            }
        } catch (ServiceConfigurationError error) {
            MeteorClient.LOG.error("Failed to discover a Meteor addon service.", error);
        }

        // NeoForge-native entrypoints declared in neoforge.mods.toml:
        // [modproperties.<modid>]
        // meteor_addon = "com.example.ExampleAddon"
        for (IModInfo mod : ModList.get().getMods()) {
            Map<String, Object> properties = mod.getModProperties();
            Object entrypoint = first(properties, "meteor_addon", "meteor:addon", "meteor-addon");
            if (entrypoint == null) continue;

            for (String className : entrypoints(entrypoint)) {
                try {
                    Class<?> klass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                    if (!MeteorAddon.class.isAssignableFrom(klass)) {
                        throw new IllegalArgumentException(className + " does not extend MeteorAddon");
                    }

                    MeteorAddon addon = (MeteorAddon) klass.getDeclaredConstructor().newInstance();
                    add(addon, mod, discovered);
                } catch (Throwable throwable) {
                    throw new RuntimeException("Exception during addon init \"%s\" (%s).".formatted(mod.getDisplayName(), className), throwable);
                }
            }
        }
    }

    private static void add(MeteorAddon addon, IModInfo mod, Set<Class<?>> discovered) {
        if (!discovered.add(addon.getClass())) return;

        if (mod != null) {
            addon.name = mod.getDisplayName();

            Object authors = mod.getConfig().getConfigElement("authors").orElse(null);
            addon.authors = strings(authors);

            Object color = first(mod.getModProperties(), "meteor_color", "meteor:color", "meteor-color", "meteor_client:color");
            if (color != null) addon.color.parse(color.toString());
        } else {
            if (addon.name == null || addon.name.isBlank()) addon.name = addon.getClass().getSimpleName();
            if (addon.authors == null) addon.authors = new String[0];
        }

        ADDONS.add(addon);
        MeteorClient.LOG.info("Discovered Meteor addon: {} ({})", addon.name, addon.getClass().getName());
    }

    private static Object first(Map<String, Object> properties, String... keys) {
        for (String key : keys) {
            Object value = properties.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private static List<String> entrypoints(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) result.add(entry.toString());
            return result;
        }

        return List.of(value.toString());
    }

    private static String[] strings(Object value) {
        if (value == null) return new String[0];
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) result.add(entry.toString());
            return result.toArray(String[]::new);
        }

        String text = value.toString().trim();
        if (text.isEmpty()) return new String[0];
        return new String[] { text };
    }
}
