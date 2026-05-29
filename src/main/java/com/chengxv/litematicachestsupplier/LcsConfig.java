package com.chengxv.litematicachestsupplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class LcsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // 配置参数与默认值
    public boolean enabled = true;
    public int supplyCooldown = 4;
    public double maxDistance = 3.5;
    public boolean autoOpen = true;
    public double highlightDistance = 128.0; // 缺失方块高亮的最大渲染距离
    public int highlightMaxCount = 1000;    // 缺失方块的最大高亮渲染数量上限

    private static LcsConfig instance;

    public static LcsConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "litematicachestsupplier.json");

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                instance = GSON.fromJson(reader, LcsConfig.class);
                if (instance == null) {
                    instance = new LcsConfig();
                } else {
                    // 安全兼容性校验：防止旧的配置文件中缺少新字段导致被反序列化为 0
                    boolean needSave = false;
                    if (instance.highlightDistance <= 0.0) {
                        instance.highlightDistance = 128.0;
                        needSave = true;
                    }
                    if (instance.highlightMaxCount <= 0) {
                        instance.highlightMaxCount = 1000;
                        needSave = true;
                    }
                    if (needSave) {
                        save();
                    }
                }
            } catch (Exception e) {
                System.err.println("[LcsConfig] Failed to load config, resetting to default: " + e.getMessage());
                instance = new LcsConfig();
            }
        } else {
            instance = new LcsConfig();
            save();
        }
    }

    public static void save() {
        if (instance == null) {
            instance = new LcsConfig();
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            System.err.println("[LcsConfig] Failed to save config: " + e.getMessage());
        }
    }
}
