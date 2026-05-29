package com.chengxv.litematicachestsupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 渲染缺失方块位置的 3D 橙色/金黄渐变高亮框。
 * 供 MissingBlockHighlightController 使用，与箱子高亮系统（ChestHighlighter）独立运行。
 */
public class MissingBlockHighlighter implements fi.dy.masa.malilib.interfaces.IRenderer {

    /** 当前激活的高亮坐标集合（Item -> 该 Item 所有缺失位置） */
    private static Map<Item, List<BlockPos>> activePositions = Collections.emptyMap();

    /** 高亮功能是否开启 */
    private static boolean enabled = false;

    private static final Object LOCK = new Object();

    // ── 公共接口 ──────────────────────────────────────────────

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            // 关闭时立即清空，避免渲染残留
            synchronized (LOCK) {
                activePositions = Collections.emptyMap();
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 更新当前需要高亮的坐标列表。
     * 由 MissingBlockHighlightController 每 Tick 调用。
     */
    public static void updatePositions(Map<Item, List<BlockPos>> positions) {
        synchronized (LOCK) {
            // 用 HashMap 深拷贝，防止 Tick 线程与渲染线程并发修改冲突
            activePositions = new java.util.HashMap<>(positions);
        }
    }

    @Override
    public void onRenderWorldLast(org.joml.Matrix4f positionMatrix, org.joml.Matrix4f projectionMatrix) {
        render();
    }

    // ── 渲染 ─────────────────────────────────────────────────

    /**
     * 渲染所有激活的缺失方块高亮框。
     * 渲染的坐标列表已由 MissingBlockHighlightController 预过滤（距离 + 数量上限），
     * 此处直接遍历即可，无需再做距离判断。
     */
    public static void render() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.world == null || client.player == null) {
                return;
            }

            // 线程安全地复制一份高亮数据进行渲染
            Map<Item, List<BlockPos>> positionsCopy;
            synchronized (LOCK) {
                if (activePositions.isEmpty()) {
                    return;
                }
                positionsCopy = new java.util.HashMap<>(activePositions);
            }

            // 橙色 → 金黄色的动态呼吸灯脉冲
            float time = (float) (System.currentTimeMillis() % 2000) / 2000.0f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2 * Math.PI);

            // 坐标已由控制器预过滤，直接遍历
            for (List<BlockPos> posList : positionsCopy.values()) {
                for (BlockPos pos : posList) {
                    float r = 1.0f;
                    float g = 0.4f + 0.45f * pulse;   // 橙→黄（0.4 ~ 0.85）
                    float b = 0.0f;
                    float a = 0.55f + 0.35f * pulse;   // 0.55 ~ 0.90

                    fi.dy.masa.malilib.util.data.Color4f color = new fi.dy.masa.malilib.util.data.Color4f(r, g, b, a);
                    fi.dy.masa.malilib.render.RenderUtils.renderBlockOutline(pos, 0.003f, 4.0f, color, true);
                }
            }
        } catch (Throwable t) {
            System.err.println("[MissingBlockHighlighter] Error rendering: " + t.getMessage());
            t.printStackTrace();
            AutoSupplyController.sendChat(
                    "\u00A7c[Highlight render error] " + t,
                    "\u00A7c[Highlight render error] " + t
            );
        }
    }
}
