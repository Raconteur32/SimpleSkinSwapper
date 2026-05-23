package fr.raconteur.simpleskinswapper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

public class SimpleSkinSwapperConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile SimpleSkinSwapperConfig instance;

    /**
     * Per-server skin commands. Key = server address, value = command to send.
     * An empty string means no command is configured for that server.
     */
    public Map<String, String> serverCommands = defaultServerCommands();

    private static Map<String, String> defaultServerCommands() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("example-server.com", "/reloadskin");
        return map;
    }

    /**
     * Returns the command for the given server address, or null if the server is not registered.
     */
    public String getCommandForServer(String address) {
        if (address == null) return null;
        return serverCommands.get(address);
    }

    /**
     * Adds an entry with an empty command for the server if not already registered,
     * then persists the config.
     */
    public void registerServerIfAbsent(String address) {
        if (address == null) return;
        if (!serverCommands.containsKey(address)) {
            serverCommands.put(address, "");
            save();
        }
    }

    public static SimpleSkinSwapperConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void save() {
        if (instance == null) return;
        try {
            File configFile = getConfigFile();
            configFile.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(configFile)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            SimpleSkinSwapper.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private static SimpleSkinSwapperConfig load() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                SimpleSkinSwapperConfig loaded = GSON.fromJson(reader, SimpleSkinSwapperConfig.class);
                if (loaded != null) {
                    if (loaded.serverCommands == null) {
                        loaded.serverCommands = defaultServerCommands();
                    }
                    return loaded;
                }
            } catch (IOException e) {
                SimpleSkinSwapper.LOGGER.error("Failed to load config: {}", e.getMessage());
            }
        }
        return new SimpleSkinSwapperConfig();
    }

    private static File getConfigFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("simpleskinswapper.json")
                .toFile();
    }
}
