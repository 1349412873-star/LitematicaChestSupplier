package com.chengxv.litematicachestsupplier;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PasteLayerBehavior;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;
import fi.dy.masa.litematica.world.ChunkManagerSchematic;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LitematicaHelper {

    private static Map<Item, Integer> cachedMissing = new HashMap<>();
    private static int cacheCooldown = 0;

    // 按物品类型分组的缺失坐标缓存（供方块高亮功能使用）
    private static Map<Item, List<BlockPos>> cachedMissingPosByItem = new HashMap<>();
    private static int posCacheCooldown = 0;

    /**
     * 实时获取当前投影缺失的物品材料列表（排除了已经摆放正确的方块）。
     * 提供默认的重载版本，用于 Tick 周期内降低性能开销。
     */
    public static Map<Item, Integer> getMissingMaterials() {
        return getMissingMaterials(false);
    }

    /**
     * 实时获取当前投影缺失的物品材料列表（排除了已经摆放正确的方块）。
     * force 为 true 时强制重新扫描，否则在 tick 周期内限流（缓存 10 Ticks / 0.5 秒）。
     */
    public static Map<Item, Integer> getMissingMaterials(boolean force) {
        if (!force && cacheCooldown > 0) {
            cacheCooldown--;
            return cachedMissing;
        }

        Map<Item, Integer> missing = new HashMap<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return missing;
        }

        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) {
            cachedMissing = missing;
            cacheCooldown = 10;
            return missing;
        }

        // 获取当前加载的所有投影区域
        Collection<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (placements == null || placements.isEmpty()) {
            cachedMissing = missing;
            cacheCooldown = 10;
            return missing;
        }

        // 缓存本次扫描中区块的加载状态，避免重复调用 isClientChunkLoaded
        Map<Long, Boolean> chunkLoadedCache = new HashMap<>();

        for (SchematicPlacement placement : placements) {
            if (placement == null || !placement.isEnabled() || !placement.isRenderingEnabled()) {
                continue;
            }

            com.google.common.collect.ImmutableMap<String, fi.dy.masa.litematica.selection.Box> boxes =
                    placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED);
            if (boxes == null || boxes.isEmpty()) {
                continue;
            }

            for (fi.dy.masa.litematica.selection.Box box : boxes.values()) {
                if (box == null) {
                    continue;
                }

                BlockPos p1 = box.getPos1();
                BlockPos p2 = box.getPos2();
                int minX = Math.min(p1.getX(), p2.getX());
                int minY = Math.min(p1.getY(), p2.getY());
                int minZ = Math.min(p1.getZ(), p2.getZ());
                int maxX = Math.max(p1.getX(), p2.getX());
                int maxY = Math.max(p1.getY(), p2.getY());
                int maxZ = Math.max(p1.getZ(), p2.getZ());

                for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                    for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                        long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                        Boolean isLoaded = chunkLoadedCache.get(chunkKey);
                        if (isLoaded == null) {
                            isLoaded = fi.dy.masa.litematica.util.WorldUtils.isClientChunkLoaded(client.world, cx, cz);
                            chunkLoadedCache.put(chunkKey, isLoaded);
                        }

                        if (!isLoaded) {
                            continue;
                        }

                        // 确保虚拟投影世界对应的区块已加载并填充
                        ChunkManagerSchematic provider = schematicWorld.getChunkProvider();
                        ChunkSchematic chunk = provider.getChunkIfExists(cx, cz);
                        if (chunk == null) {
                            provider.loadChunk(cx, cz);
                            SchematicPlacingUtils.placeToWorldWithinChunk(
                                    schematicWorld,
                                    new ChunkPos(cx, cz),
                                    placement,
                                    (ReplaceBehavior) Configs.Generic.PLACEMENT_REPLACE_BEHAVIOR.getOptionListValue(),
                                    (PasteLayerBehavior) Configs.Generic.PASTE_LAYER_BEHAVIOR.getOptionListValue(),
                                    false
                            );
                        }

                        int chunkMinX = Math.max(minX, cx << 4);
                        int chunkMaxX = Math.min(maxX, (cx << 4) + 15);
                        int chunkMinZ = Math.max(minZ, cz << 4);
                        int chunkMaxZ = Math.min(maxZ, (cz << 4) + 15);

                        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                                for (int y = minY; y <= maxY; y++) {
                                    mutablePos.set(x, y, z);
                                    if (DataManager.getRenderLayerRange() != null && !DataManager.getRenderLayerRange().isPositionWithinRange(mutablePos)) {
                                        continue;
                                    }
                                    BlockState schematicState = schematicWorld.getBlockState(mutablePos);
                                    if (schematicState == null || schematicState.isAir()) {
                                        continue;
                                    }

                                    BlockState worldState = client.world.getBlockState(mutablePos);
                                    if (!isMatching(schematicState, worldState)) {
                                        Item requiredItem = getRequiredItem(schematicState);
                                        if (requiredItem != Items.AIR) {
                                            missing.put(requiredItem, missing.getOrDefault(requiredItem, 0) + 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        cachedMissing = missing;
        cacheCooldown = 10;
        return missing;
    }

    /**
     * 获取当前投影按物品类型分组的缺失方块世界坐标列表。
     * 供"缺失方块位置高亮"功能使用。
     */
    public static Map<Item, List<BlockPos>> getMissingBlockPositionsByItem() {
        return getMissingBlockPositionsByItem(false);
    }

    /**
     * 获取当前投影按物品类型分组的缺失方块世界坐标列表。
     * force=true 时强制重新扫描，否则使用 10 Tick 缓存。
     */
    public static Map<Item, List<BlockPos>> getMissingBlockPositionsByItem(boolean force) {
        if (!force && posCacheCooldown > 0) {
            posCacheCooldown--;
            return cachedMissingPosByItem;
        }

        Map<Item, List<BlockPos>> result = new HashMap<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return result;
        }

        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) {
            cachedMissingPosByItem = result;
            posCacheCooldown = 10;
            return result;
        }

        Collection<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (placements == null || placements.isEmpty()) {
            cachedMissingPosByItem = result;
            posCacheCooldown = 10;
            return result;
        }

        // 缓存本次扫描中区块的加载状态，避免重复调用 isClientChunkLoaded
        Map<Long, Boolean> chunkLoadedCache = new HashMap<>();

        for (SchematicPlacement placement : placements) {
            if (placement == null || !placement.isEnabled() || !placement.isRenderingEnabled()) {
                continue;
            }

            com.google.common.collect.ImmutableMap<String, fi.dy.masa.litematica.selection.Box> boxes =
                    placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED);
            if (boxes == null || boxes.isEmpty()) {
                continue;
            }

            for (fi.dy.masa.litematica.selection.Box box : boxes.values()) {
                if (box == null) {
                    continue;
                }

                BlockPos p1 = box.getPos1();
                BlockPos p2 = box.getPos2();
                int minX = Math.min(p1.getX(), p2.getX());
                int minY = Math.min(p1.getY(), p2.getY());
                int minZ = Math.min(p1.getZ(), p2.getZ());
                int maxX = Math.max(p1.getX(), p2.getX());
                int maxY = Math.max(p1.getY(), p2.getY());
                int maxZ = Math.max(p1.getZ(), p2.getZ());

                for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                    for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                        long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                        Boolean isLoaded = chunkLoadedCache.get(chunkKey);
                        if (isLoaded == null) {
                            isLoaded = fi.dy.masa.litematica.util.WorldUtils.isClientChunkLoaded(client.world, cx, cz);
                            chunkLoadedCache.put(chunkKey, isLoaded);
                        }

                        if (!isLoaded) {
                            continue;
                        }

                        // 确保虚拟投影世界对应的区块已加载并填充
                        ChunkManagerSchematic provider = schematicWorld.getChunkProvider();
                        ChunkSchematic chunk = provider.getChunkIfExists(cx, cz);
                        if (chunk == null) {
                            provider.loadChunk(cx, cz);
                            SchematicPlacingUtils.placeToWorldWithinChunk(
                                    schematicWorld,
                                    new ChunkPos(cx, cz),
                                    placement,
                                    (ReplaceBehavior) Configs.Generic.PLACEMENT_REPLACE_BEHAVIOR.getOptionListValue(),
                                    (PasteLayerBehavior) Configs.Generic.PASTE_LAYER_BEHAVIOR.getOptionListValue(),
                                    false
                            );
                        }

                        int chunkMinX = Math.max(minX, cx << 4);
                        int chunkMaxX = Math.min(maxX, (cx << 4) + 15);
                        int chunkMinZ = Math.max(minZ, cz << 4);
                        int chunkMaxZ = Math.min(maxZ, (cz << 4) + 15);

                        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                                for (int y = minY; y <= maxY; y++) {
                                    mutablePos.set(x, y, z);
                                    if (DataManager.getRenderLayerRange() != null && !DataManager.getRenderLayerRange().isPositionWithinRange(mutablePos)) {
                                        continue;
                                    }
                                    BlockState schematicState = schematicWorld.getBlockState(mutablePos);
                                    if (schematicState == null || schematicState.isAir()) {
                                        continue;
                                    }

                                    BlockState worldState = client.world.getBlockState(mutablePos);
                                    if (!isMatching(schematicState, worldState)) {
                                        Item requiredItem = getRequiredItem(schematicState);
                                        if (requiredItem != Items.AIR) {
                                            result.computeIfAbsent(requiredItem, k -> new ArrayList<>()).add(mutablePos.toImmutable());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        cachedMissingPosByItem = result;
        posCacheCooldown = 10;
        return result;
    }

    private static boolean isMatching(BlockState schematic, BlockState world) {
        return schematic.getBlock() == world.getBlock();
    }

    private static Item getRequiredItem(BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            // 对没有直接物品映射的特殊方块进行硬件映射
            if (state.isOf(Blocks.REDSTONE_WIRE)) return Items.REDSTONE;
            if (state.isOf(Blocks.TRIPWIRE)) return Items.STRING;
            if (state.isOf(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
            if (state.isOf(Blocks.CARROTS)) return Items.CARROT;
            if (state.isOf(Blocks.POTATOES)) return Items.POTATO;
            if (state.isOf(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
            if (state.isOf(Blocks.COCOA)) return Items.COCOA_BEANS;
            if (state.isOf(Blocks.PUMPKIN_STEM)) return Items.PUMPKIN_SEEDS;
            if (state.isOf(Blocks.MELON_STEM)) return Items.MELON_SEEDS;
            if (state.isOf(Blocks.ATTACHED_PUMPKIN_STEM)) return Items.PUMPKIN_SEEDS;
            if (state.isOf(Blocks.ATTACHED_MELON_STEM)) return Items.MELON_SEEDS;
            if (state.isOf(Blocks.SWEET_BERRY_BUSH)) return Items.SWEET_BERRIES;
        }
        return item;
    }

    private static BlockPos correctWorldPos(BlockPos worldPos, SchematicPlacement placement) {
        return worldPos;
    }
}
