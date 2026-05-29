package com.chengxv.litematicachestsupplier;

import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.memory.MemoryBank;
import red.jackf.chesttracker.api.memory.MemoryBankAccess;
import red.jackf.chesttracker.api.memory.MemoryKey;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ChestTrackerHelper {

    /**
     * 根据缺失的材料列表，从 ChestTracker 中搜索包含这些材料的箱子和对应的物品堆。
     */
    public static Map<BlockPos, List<ItemStack>> getChestsWithNeededItems(Map<Item, Integer> missingItems) {
        Map<BlockPos, List<ItemStack>> matchingChests = new HashMap<>();
        if (missingItems == null || missingItems.isEmpty()) {
            return matchingChests;
        }

        // 获取 ChestTracker 当前已加载的内存数据库
        Optional<MemoryBank> loadedBank = MemoryBankAccess.INSTANCE.getLoaded();
        if (!loadedBank.isPresent()) {
            return matchingChests;
        }

        MemoryBank bank = loadedBank.get();
        Map<Identifier, MemoryKey> allMemories = bank.getAllMemories();
        if (allMemories == null) {
            return matchingChests;
        }

        for (Map.Entry<Identifier, MemoryKey> keyEntry : allMemories.entrySet()) {
            MemoryKey memoryKey = keyEntry.getValue();
            if (memoryKey == null) {
                continue;
            }

            // 获取该类型下记录的所有箱子位置及物品详情
            Map<BlockPos, Memory> memories = memoryKey.getMemories();
            if (memories == null) {
                continue;
            }

            for (Map.Entry<BlockPos, Memory> memoryEntry : memories.entrySet()) {
                BlockPos pos = memoryEntry.getKey();
                Memory memory = memoryEntry.getValue();
                if (memory == null || memory.isEmpty()) {
                    continue;
                }

                List<ItemStack> matchingItemsInChest = new ArrayList<>();
                List<ItemStack> items = memory.items();
                if (items != null) {
                    for (ItemStack stack : items) {
                        if (stack == null || stack.isEmpty()) {
                            continue;
                        }

                        Item item = stack.getItem();
                        // 如果箱子里的物品处于投影缺失列表中，则进行记录
                        if (missingItems.containsKey(item)) {
                            matchingItemsInChest.add(stack.copy());
                        }
                    }
                }

                if (!matchingItemsInChest.isEmpty()) {
                    matchingChests.put(pos, matchingItemsInChest);
                }
            }
        }

        return matchingChests;
    }
}
