package com.chengxv.litematicachestsupplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.memory.MemoryBank;
import red.jackf.chesttracker.api.memory.MemoryBankAccess;
import red.jackf.chesttracker.api.memory.MemoryKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChestTrackerHelper {
    private static Map<BlockPos, List<ItemStack>> cachedMatchingChests = new HashMap<>();
    private static int cacheCooldown = 0;
    private static int cachedMissingSignature = 0;

    public static Map<BlockPos, List<ItemStack>> getChestsWithNeededItems(Map<Item, Integer> missingItems) {
        Map<BlockPos, List<ItemStack>> matchingChests = new HashMap<>();
        if (missingItems == null || missingItems.isEmpty()) {
            updateCache(matchingChests, 0);
            return matchingChests;
        }

        int missingSignature = missingItems.hashCode();
        if (cacheCooldown > 0 && missingSignature == cachedMissingSignature) {
            cacheCooldown--;
            return cachedMatchingChests;
        }

        Optional<MemoryBank> loadedBank = MemoryBankAccess.INSTANCE.getLoaded();
        if (loadedBank.isEmpty()) {
            updateCache(matchingChests, missingSignature);
            return matchingChests;
        }

        MemoryBank bank = loadedBank.get();
        Map<Identifier, MemoryKey> allMemories = bank.getAllMemories();
        if (allMemories == null) {
            updateCache(matchingChests, missingSignature);
            return matchingChests;
        }

        for (MemoryKey memoryKey : allMemories.values()) {
            if (memoryKey == null) {
                continue;
            }

            Map<BlockPos, Memory> memories = memoryKey.getMemories();
            if (memories == null) {
                continue;
            }

            for (Map.Entry<BlockPos, Memory> memoryEntry : memories.entrySet()) {
                Memory memory = memoryEntry.getValue();
                if (memory == null || memory.isEmpty()) {
                    continue;
                }

                List<ItemStack> matchingItemsInChest = new ArrayList<>();
                List<ItemStack> items = memory.items();
                if (items != null) {
                    for (ItemStack stack : items) {
                        if (stack != null && !stack.isEmpty() && missingItems.containsKey(stack.getItem())) {
                            matchingItemsInChest.add(stack.copy());
                        }
                    }
                }

                if (!matchingItemsInChest.isEmpty()) {
                    matchingChests.put(memoryEntry.getKey(), matchingItemsInChest);
                }
            }
        }

        updateCache(matchingChests, missingSignature);
        return matchingChests;
    }

    private static void updateCache(Map<BlockPos, List<ItemStack>> matchingChests, int missingSignature) {
        cachedMatchingChests = matchingChests;
        cachedMissingSignature = missingSignature;
        cacheCooldown = 10;
    }
}
