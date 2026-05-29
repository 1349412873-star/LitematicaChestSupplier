package com.chengxv.litematicachestsupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestHighlighter implements fi.dy.masa.malilib.interfaces.IRenderer {
    private static final double MAX_RENDER_DISTANCE = 128.0;
    private static final int MAX_RENDER_COUNT = 256;
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

    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || activeChests.isEmpty()) {
            return;
        }

        float time = (float) (System.currentTimeMillis() % 2000) / 2000.0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2 * Math.PI);
        double maxDistanceSq = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
        int rendered = 0;

        for (BlockPos pos : activeChests.keySet()) {
            if (client.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos)) > maxDistanceSq) {
                continue;
            }
            if (rendered++ >= MAX_RENDER_COUNT) {
                break;
            }

            float red = 0.1f + 0.4f * pulse;
            float green = 0.8f - 0.3f * pulse;
            float blue = 1.0f;
            float alpha = 0.6f + 0.4f * pulse;

            fi.dy.masa.malilib.util.data.Color4f color = new fi.dy.masa.malilib.util.data.Color4f(red, green, blue, alpha);
            fi.dy.masa.malilib.render.RenderUtils.renderBlockOutline(pos, 0.002f, 4.0f, color, true);
        }
    }
}
