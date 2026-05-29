package com.chengxv.litematicachestsupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class MissingBlockHighlightController {
    private static final double MOVED_THRESHOLD_SQ = 9.0;
    private static final int NEARBY_CACHE_TTL = 20;

    private static Vec3d lastPlayerPos = null;
    private static Map<Item, List<BlockPos>> nearbyCache = Collections.emptyMap();
    private static int nearbyCacheCooldown = 0;

    private static boolean alertedNoItemsInInventory = false;
    private static boolean alertedFullyBuilt = false;

    public static void forceRefreshAndReport(MinecraftClient client) {
        lastPlayerPos = null;
        nearbyCacheCooldown = 0;
        nearbyCache = Collections.emptyMap();
        alertedNoItemsInInventory = false;
        alertedFullyBuilt = false;

        if (client.player == null || client.world == null) {
            AutoSupplyController.sendChat(
                    "缺失方块高亮已\u00A7a开启\u00A7f，等待进入世界。",
                    "Missing block highlight is now \u00A7aenabled\u00A7f. Waiting for a world."
            );
            return;
        }

        Map<Item, List<BlockPos>> missingPosByItem = LitematicaHelper.getMissingBlockPositionsByItem(true);
        Set<Item> inventoryItems = getInventoryItems(client);

        int matchedTypes = 0;
        int totalMissingPositions = 0;
        for (Map.Entry<Item, List<BlockPos>> entry : missingPosByItem.entrySet()) {
            totalMissingPositions += entry.getValue().size();
            if (inventoryItems.contains(entry.getKey())) {
                matchedTypes++;
            }
        }

        StringBuilder sb = new StringBuilder(AutoSupplyController.selectText(
                "缺失方块高亮已\u00A7a开启\u00A7f。",
                "Missing block highlight is now \u00A7aenabled\u00A7f."
        ));
        sb.append(AutoSupplyController.selectText("\n\u00A77[诊断] \u00A7f已加载缺失方块：\u00A7e", "\n\u00A77[Diagnostic] \u00A7fMissing loaded blocks: \u00A7e"))
                .append(missingPosByItem.size())
                .append(AutoSupplyController.selectText("\u00A7f 种 / \u00A7e", "\u00A7f item types / \u00A7e"))
                .append(totalMissingPositions)
                .append(AutoSupplyController.selectText("\u00A7f 个位置", "\u00A7f positions"));
        sb.append(AutoSupplyController.selectText("\n\u00A77[诊断] \u00A7f背包匹配：\u00A7e", "\n\u00A77[Diagnostic] \u00A7fInventory matches: \u00A7e"))
                .append(matchedTypes)
                .append(AutoSupplyController.selectText("\u00A7f 种", "\u00A7f item types"));

        if (missingPosByItem.isEmpty()) {
            sb.append(AutoSupplyController.selectText(
                    "\n\u00A7c没有找到缺失方块。请检查投影是否已放置、启用，并且位于已加载区块内。",
                    "\n\u00A7cNo missing blocks were found. Check whether the schematic is placed, enabled, and inside loaded chunks."
            ));
        } else if (matchedTypes == 0) {
            sb.append(AutoSupplyController.selectText(
                    "\n\u00A7c背包中没有与缺失方块匹配的物品。",
                    "\n\u00A7cNo inventory items match the missing schematic blocks."
            ));
            sb.append(AutoSupplyController.selectText("\n\u00A77缺失方块：", "\n\u00A77Missing blocks: "));
            int count = 0;
            for (Item item : missingPosByItem.keySet()) {
                if (count > 0) sb.append("\u00A77, ");
                sb.append("\u00A7e").append(item.getName().getString());
                if (++count >= 5) {
                    sb.append("\u00A77...");
                    break;
                }
            }
        } else {
            sb.append(AutoSupplyController.selectText("\n\u00A7a已找到匹配物品。橙色框应出现在附近 \u00A7e", "\n\u00A7aFound matches. Orange outlines should appear within \u00A7e"))
                    .append((int) LcsConfig.getInstance().highlightDistance)
                    .append(AutoSupplyController.selectText("\u00A7a 格内。", "\u00A7a blocks."));
        }

        AutoSupplyController.sendChat(sb.toString());
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (!MissingBlockHighlighter.isEnabled()) {
            alertedNoItemsInInventory = false;
            alertedFullyBuilt = false;
            return;
        }

        Map<Item, List<BlockPos>> missingPosByItem = LitematicaHelper.getMissingBlockPositionsByItem();
        if (missingPosByItem.isEmpty()) {
            clearHighlight();
            if (!alertedFullyBuilt) {
                AutoSupplyController.sendChat(
                        "没有检测到缺失方块，当前投影可能已经完成。",
                        "No missing blocks were detected. The current schematic may already be complete."
                );
                alertedFullyBuilt = true;
            }
            alertedNoItemsInInventory = false;
            return;
        }
        alertedFullyBuilt = false;

        Set<Item> inventoryItems = getInventoryItems(client);
        Map<Item, List<BlockPos>> toHighlight = new HashMap<>();
        for (Item item : inventoryItems) {
            List<BlockPos> positions = missingPosByItem.get(item);
            if (positions != null && !positions.isEmpty()) {
                toHighlight.put(item, positions);
            }
        }

        if (toHighlight.isEmpty()) {
            clearHighlight();
            if (!alertedNoItemsInInventory) {
                AutoSupplyController.sendChat(
                        "背包里没有与缺失方块匹配的物品。橙色高亮会暂时隐藏，直到你拿到材料。",
                        "Your inventory has no items matching missing schematic blocks. Orange highlights are hidden until you collect materials."
                );
                alertedNoItemsInInventory = true;
            }
            return;
        }
        alertedNoItemsInInventory = false;

        Vec3d playerPos = client.player.getPos();
        boolean needsRefresh = nearbyCacheCooldown <= 0
                || lastPlayerPos == null
                || lastPlayerPos.squaredDistanceTo(playerPos) > MOVED_THRESHOLD_SQ;

        if (needsRefresh) {
            nearbyCache = buildNearbyCache(toHighlight, playerPos);
            lastPlayerPos = playerPos;
            nearbyCacheCooldown = NEARBY_CACHE_TTL;
        } else {
            nearbyCacheCooldown--;
        }

        MissingBlockHighlighter.updatePositions(nearbyCache);
    }

    private static class BlockPosWithDistance {
        final Item item;
        final BlockPos pos;
        final double distSq;

        BlockPosWithDistance(Item item, BlockPos pos, double distSq) {
            this.item = item;
            this.pos = pos;
            this.distSq = distSq;
        }
    }

    private static Map<Item, List<BlockPos>> buildNearbyCache(Map<Item, List<BlockPos>> all, Vec3d playerPos) {
        LcsConfig config = LcsConfig.getInstance();
        double renderDistance = config.highlightDistance;
        double renderDistSq = renderDistance * renderDistance;
        int maxRenderCount = config.highlightMaxCount;

        PriorityQueue<BlockPosWithDistance> nearest = new PriorityQueue<>(
                (a, b) -> Double.compare(b.distSq, a.distSq)
        );
        for (Map.Entry<Item, List<BlockPos>> entry : all.entrySet()) {
            Item item = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                double dx = pos.getX() + 0.5 - playerPos.x;
                double dy = pos.getY() + 0.5 - playerPos.y;
                double dz = pos.getZ() + 0.5 - playerPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= renderDistSq) {
                    BlockPosWithDistance candidate = new BlockPosWithDistance(item, pos, distSq);
                    if (nearest.size() < maxRenderCount) {
                        nearest.offer(candidate);
                    } else if (nearest.peek() != null && distSq < nearest.peek().distSq) {
                        nearest.poll();
                        nearest.offer(candidate);
                    }
                }
            }
        }

        Map<Item, List<BlockPos>> nearby = new HashMap<>();
        for (BlockPosWithDistance entry : nearest) {
            nearby.computeIfAbsent(entry.item, k -> new ArrayList<>()).add(entry.pos);
        }

        return nearby;
    }

    private static void clearHighlight() {
        MissingBlockHighlighter.updatePositions(Collections.emptyMap());
        nearbyCache = Collections.emptyMap();
        lastPlayerPos = null;
    }

    private static Set<Item> getInventoryItems(MinecraftClient client) {
        Set<Item> items = new HashSet<>();
        int invSize = client.player.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
        return items;
    }
}
