package com.chengxv.litematicachestsupplier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class LitematicaChestSupplier implements ClientModInitializer {
    public static final String MOD_ID = "litematicachestsupplier";
    private static KeyBinding toggleKey;
    private static KeyBinding highlightKey;
    private static boolean sentWelcomeMessage = false;

    @Override
    public void onInitializeClient() {
        LcsConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                sentWelcomeMessage = false;
                return;
            }

            if (!sentWelcomeMessage) {
                AutoSupplyController.sendChat(
                        "欢迎使用 Litematica Chest Supplier，模组作者 xiaolao。",
                        "Welcome to Litematica Chest Supplier. Mod author: xiaolao."
                );
                sentWelcomeMessage = true;
            }

            while (toggleKey.wasPressed()) {
                toggleAutoSupply();
            }

            while (highlightKey.wasPressed()) {
                toggleMissingBlockHighlight(client);
            }

            AutoSupplyController.tick();
            MissingBlockHighlightController.tick();
        });

        fi.dy.masa.malilib.event.RenderEventHandler.getInstance().registerWorldLastRenderer(new ChestHighlighter());
        fi.dy.masa.malilib.event.RenderEventHandler.getInstance().registerWorldLastRenderer(new MissingBlockHighlighter());

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematicachestsupplier.toggle",
                GLFW.GLFW_KEY_O,
                "category.litematicachestsupplier"
        ));

        highlightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematicachestsupplier.highlight_missing",
                GLFW.GLFW_KEY_P,
                "category.litematicachestsupplier"
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("lcs")
                    .then(ClientCommandManager.literal("toggle")
                            .executes(context -> {
                                toggleAutoSupply();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("status")
                            .executes(context -> {
                                sendStatus();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("highlight")
                            .executes(context -> {
                                toggleMissingBlockHighlight(MinecraftClient.getInstance());
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("reload")
                            .executes(context -> {
                                LcsConfig.load();
                                sendReloadStatus();
                                return 1;
                            }))
            );
        });

        System.out.println("[LitematicaChestSupplier] Client side initialization finished successfully!");
    }

    private static void toggleAutoSupply() {
        boolean state = !AutoSupplyController.isEnabled();
        AutoSupplyController.setEnabled(state);
        AutoSupplyController.sendChat(
                "自动补货已" + (state ? "\u00A7a开启" : "\u00A7c关闭"),
                "Auto-supply is now " + (state ? "\u00A7aenabled" : "\u00A7cdisabled")
        );
    }

    private static void toggleMissingBlockHighlight(MinecraftClient client) {
        boolean state = !MissingBlockHighlighter.isEnabled();
        MissingBlockHighlighter.setEnabled(state);
        if (state) {
            MissingBlockHighlightController.forceRefreshAndReport(client);
        } else {
            AutoSupplyController.sendChat(
                    "缺失方块高亮已\u00A7c关闭",
                    "Missing block highlight is now \u00A7cdisabled"
            );
        }
    }

    private static void sendStatus() {
        var missing = LitematicaHelper.getMissingMaterials(true);
        if (missing.isEmpty()) {
            AutoSupplyController.sendChat(
                    "当前投影没有缺失材料。",
                    "The current schematic has no missing materials."
            );
            return;
        }

        StringBuilder sb = new StringBuilder(AutoSupplyController.selectText(
                "当前投影缺失材料：",
                "Current missing schematic materials:"
        ));
        for (var entry : missing.entrySet()) {
            sb.append("\n- \u00A7e")
                    .append(entry.getKey().getName().getString())
                    .append(AutoSupplyController.selectText("\u00A7f: 还需要 \u00A7c", "\u00A7f: needs \u00A7c"))
                    .append(entry.getValue())
                    .append("\u00A7f");
        }
        AutoSupplyController.sendChat(sb.toString());
    }

    private static void sendReloadStatus() {
        LcsConfig cfg = LcsConfig.getInstance();
        AutoSupplyController.sendChat(
                "\u00A7a配置已重新加载。\u00A7f当前设置：\n" +
                        "- 自动拿取冷却：\u00A7e" + cfg.supplyCooldown + "\u00A7f tick\n" +
                        "- 自动开箱距离：\u00A7e" + cfg.maxDistance + "\u00A7f 格\n" +
                        "- 自动开箱：\u00A7e" + (cfg.autoOpen ? "开启" : "关闭") + "\u00A7f\n" +
                        "- 缺失高亮距离：\u00A7e" + cfg.highlightDistance + "\u00A7f 格\n" +
                        "- 缺失高亮最大数量：\u00A7e" + cfg.highlightMaxCount,
                "\u00A7aConfig reloaded.\u00A7f Current settings:\n" +
                        "- Supply cooldown: \u00A7e" + cfg.supplyCooldown + "\u00A7f ticks\n" +
                        "- Auto-open distance: \u00A7e" + cfg.maxDistance + "\u00A7f blocks\n" +
                        "- Auto-open: \u00A7e" + (cfg.autoOpen ? "enabled" : "disabled") + "\u00A7f\n" +
                        "- Missing highlight distance: \u00A7e" + cfg.highlightDistance + "\u00A7f blocks\n" +
                        "- Missing highlight max count: \u00A7e" + cfg.highlightMaxCount
        );
    }
}
