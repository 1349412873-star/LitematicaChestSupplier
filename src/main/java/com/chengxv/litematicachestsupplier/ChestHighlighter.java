package com.chengxv.litematicachestsupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestHighlighter implements fi.dy.masa.malilib.interfaces.IRenderer {
    private static Map<BlockPos, List<ItemStack>> activeChests = new HashMap<>();

    public static void updateChests(Map<BlockPos, List<ItemStack>> chests) {
        activeChests = chests;
    }

    public static void clearChests() {
        activeChests = new HashMap<>();
    }

    public static Map<BlockPos, List<ItemStack>> getActiveChests() {
        return activeChests;
    }

    @Override
    public void onRenderWorldLast(org.joml.Matrix4f positionMatrix, org.joml.Matrix4f projectionMatrix) {
        render();
    }

    /**
     * 渲染 3D 渐变透视高亮框。
     */
    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || activeChests.isEmpty()) {
            return;
        }

        // 动态呼吸灯脉冲计算
        float time = (float) (System.currentTimeMillis() % 2000) / 2000.0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2 * Math.PI);

        for (BlockPos pos : activeChests.keySet()) {
            // 蓝绿渐变到紫红色的动态色彩过渡
            float red = 0.1f + 0.4f * pulse;
            float green = 0.8f - 0.3f * pulse;
            float blue = 1.0f;
            float alpha = 0.6f + 0.4f * pulse;

            fi.dy.masa.malilib.util.data.Color4f color = new fi.dy.masa.malilib.util.data.Color4f(red, green, blue, alpha);
            fi.dy.masa.malilib.render.RenderUtils.renderBlockOutline(pos, 0.002f, 4.0f, color, true);
        }
    }
}
