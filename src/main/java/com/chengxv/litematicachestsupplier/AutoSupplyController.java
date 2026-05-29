package com.chengxv.litematicachestsupplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AutoSupplyController {
    private static BlockPos currentChestPos = null;
    private static int supplyCooldown = 0;
    private static int openCooldown = 0;
    private static final Set<Item> alertedMissingItems = new HashSet<>();
    private static final Set<Item> alertedInsufficientItems = new HashSet<>();
    private static final Map<Item, Integer> lastReportedMissing = new HashMap<>();
    private static boolean hasTakenThisSession = false;
    private static boolean wasScreenOpen = false;
    private static int handledScreenTicks = 0;
    private static int lastHandledSyncId = -1;
    private static final Map<Item, Integer> virtualInventoryCounts = new HashMap<>();

    public static void setEnabled(boolean value) {
        LcsConfig.getInstance().enabled = value;
        if (!value) {
            ChestHighlighter.clearChests();
        }
        LcsConfig.save();
    }

    public static boolean isEnabled() {
        return LcsConfig.getInstance().enabled;
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !isEnabled()) {
            return;
        }

        if (supplyCooldown > 0) supplyCooldown--;
        if (openCooldown > 0) openCooldown--;

        Map<Item, Integer> missingMaterials = LitematicaHelper.getMissingMaterials();
        Map<BlockPos, List<ItemStack>> matchingChests = ChestTrackerHelper.getChestsWithNeededItems(missingMaterials);
        ChestHighlighter.updateChests(matchingChests);

        for (Item item : missingMaterials.keySet()) {
            if (!alertedMissingItems.contains(item) && !hasChestsWithItem(matchingChests, item)) {
                sendChat(
                        "仓库记录中没有找到所需物品：\u00A7e" + getItemName(item) + "\u00A7f，已跳过。",
                        "Needed item not found in any Chest Tracker record: \u00A7e" + getItemName(item) + "\u00A7f. Skipping it."
                );
                alertedMissingItems.add(item);
            }
        }

        checkShortageSummary(missingMaterials);

        if (LcsConfig.getInstance().autoOpen && client.currentScreen == null && openCooldown == 0) {
            HitResult hit = client.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                if (matchingChests.containsKey(pos)) {
                    double dist = client.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos));
                    double maxDist = LcsConfig.getInstance().maxDistance;
                    if (dist < maxDist * maxDist) {
                        openCooldown = 40;
                        currentChestPos = pos;
                        hasTakenThisSession = false;

                        client.interactionManager.interactBlock(
                                client.player,
                                Hand.MAIN_HAND,
                                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)
                        );
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        }

        if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
            wasScreenOpen = true;
            if (lastHandledSyncId != handledScreen.getScreenHandler().syncId) {
                lastHandledSyncId = handledScreen.getScreenHandler().syncId;
                handledScreenTicks = 0;
            } else {
                handledScreenTicks++;
            }

            if (!missingMaterials.isEmpty() && handledScreenTicks >= 3 && supplyCooldown == 0) {
                supplyCooldown = LcsConfig.getInstance().supplyCooldown;
                autoTakeItems(handledScreen, missingMaterials, matchingChests);
            }
        } else if (wasScreenOpen) {
            currentChestPos = null;
            hasTakenThisSession = false;
            wasScreenOpen = false;
            handledScreenTicks = 0;
            lastHandledSyncId = -1;
            virtualInventoryCounts.clear();
        }
    }

    private static boolean hasChestsWithItem(Map<BlockPos, List<ItemStack>> chests, Item item) {
        for (List<ItemStack> list : chests.values()) {
            for (ItemStack stack : list) {
                if (stack.getItem() == item) return true;
            }
        }
        return false;
    }

    private static void autoTakeItems(
            HandledScreen<?> screen,
            Map<Item, Integer> missingMaterials,
            Map<BlockPos, List<ItemStack>> matchingChests) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<Slot> slots = screen.getScreenHandler().slots;
        int syncId = screen.getScreenHandler().syncId;
        Map<Item, Integer> startingInventoryCounts = getInventoryCounts(client);
        Map<Item, Integer> totalAvailableBeforeTaking = countTotalAvailable(startingInventoryCounts, matchingChests);

        boolean inventoryFull = false;
        boolean tookSomething = false;

        for (Map.Entry<Item, Integer> entry : startingInventoryCounts.entrySet()) {
            Item item = entry.getKey();
            int actual = entry.getValue();
            int virtual = virtualInventoryCounts.getOrDefault(item, 0);
            virtualInventoryCounts.put(item, Math.max(actual, virtual));
        }

        Map<Item, Integer> inventoryCounts = new HashMap<>(virtualInventoryCounts);
        for (Map.Entry<Item, Integer> entry : startingInventoryCounts.entrySet()) {
            inventoryCounts.put(entry.getKey(), Math.max(entry.getValue(), inventoryCounts.getOrDefault(entry.getKey(), 0)));
        }

        if (hasEnoughMaterials(missingMaterials, inventoryCounts)) {
            if (isAutoOpenedScreen()) {
                sendChat(
                        "背包内材料已经足够补齐当前已加载的缺失方块。",
                        "Inventory now has enough materials for the currently loaded missing blocks."
                );
                closeAutoOpenedScreen(client);
                openCooldown = 40;
            }
            return;
        }

        for (Slot slot : slots) {
            if (slot.inventory == client.player.getInventory()) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            Item item = stack.getItem();
            int neededTotal = missingMaterials.getOrDefault(item, 0);
            if (neededTotal <= 0) {
                continue;
            }

            int alreadyHas = inventoryCounts.getOrDefault(item, 0);
            int netNeeded = neededTotal - alreadyHas;
            if (netNeeded <= 0) {
                continue;
            }

            if (client.player.getInventory().getEmptySlot() == -1 && !hasMergeableSlot(screen, client, item)) {
                inventoryFull = true;
                break;
            }

            int slotCount = stack.getCount();
            if (netNeeded >= slotCount) {
                client.interactionManager.clickSlot(syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
                tookSomething = true;
                hasTakenThisSession = true;
                inventoryCounts.put(item, alreadyHas + slotCount);
                virtualInventoryCounts.put(item, alreadyHas + slotCount);
            } else {
                client.interactionManager.clickSlot(syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
                tookSomething = true;
                hasTakenThisSession = true;
                inventoryCounts.put(item, alreadyHas + slotCount);
                virtualInventoryCounts.put(item, alreadyHas + slotCount);
            }

            if (LcsConfig.getInstance().supplyCooldown > 0) {
                break;
            }
        }

        if (inventoryFull) {
            if (isAutoOpenedScreen()) {
                sendChat(
                        "\u00A7c背包已满！\u00A7f请先铺设背包里的材料，再回来继续拿取。",
                        "\u00A7cInventory is full!\u00A7f Place the items in your inventory, then return to the chest to continue."
                );
                closeAutoOpenedScreen(client);
                openCooldown = 60;
            }
        } else if (!tookSomething && (hasTakenThisSession || !hasUsefulContainerItems(screen, client, missingMaterials, inventoryCounts))) {
            if (isAutoOpenedScreen()) {
                reportInsufficientItemsIfNeeded(missingMaterials, inventoryCounts, totalAvailableBeforeTaking);
                sendChat(
                        "当前箱子里没有更多仍然需要的投影材料。",
                        "No more useful schematic items are available in this chest."
                );
                closeAutoOpenedScreen(client);
                openCooldown = 40;
            }
        }
    }

    private static boolean isAutoOpenedScreen() {
        return currentChestPos != null;
    }

    private static void closeAutoOpenedScreen(MinecraftClient client) {
        if (isAutoOpenedScreen() && client.player != null) {
            client.player.closeHandledScreen();
        }
    }

    private static boolean hasEnoughMaterials(Map<Item, Integer> missingMaterials, Map<Item, Integer> inventoryCounts) {
        if (missingMaterials.isEmpty()) {
            return true;
        }

        for (Map.Entry<Item, Integer> entry : missingMaterials.entrySet()) {
            if (inventoryCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUsefulContainerItems(
            HandledScreen<?> screen,
            MinecraftClient client,
            Map<Item, Integer> missingMaterials,
            Map<Item, Integer> inventoryCounts) {

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == client.player.getInventory()) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            Item item = stack.getItem();
            int stillNeeded = missingMaterials.getOrDefault(item, 0) - inventoryCounts.getOrDefault(item, 0);
            if (stillNeeded > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMergeableSlot(HandledScreen<?> screen, MinecraftClient client, Item item) {
        List<Slot> slots = screen.getScreenHandler().slots;
        for (Slot slot : slots) {
            if (slot.inventory == client.player.getInventory() && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void reportInsufficientItemsIfNeeded(
            Map<Item, Integer> missingMaterials,
            Map<Item, Integer> inventoryCounts,
            Map<Item, Integer> totalAvailableBeforeTaking) {

        for (Map.Entry<Item, Integer> entry : missingMaterials.entrySet()) {
            Item item = entry.getKey();
            int needed = entry.getValue();
            int totalAvailable = totalAvailableBeforeTaking.getOrDefault(item, 0);
            if (totalAvailable >= needed || alertedInsufficientItems.contains(item)) {
                continue;
            }

            int stillNeeded = Math.max(0, needed - inventoryCounts.getOrDefault(item, 0));
            sendChat(
                    "\u00A7c物品不足：\u00A7e" + getItemName(item) + "\u00A7f 总共需要 \u00A7c" + needed
                            + "\u00A7f 个，但背包和所有已记录箱子里只有 \u00A7e" + totalAvailable
                            + "\u00A7f 个，还差 \u00A7c" + (needed - totalAvailable) + "\u00A7f 个。",
                    "\u00A7cNot enough items: \u00A7e" + getItemName(item) + "\u00A7f needs \u00A7c" + needed
                            + "\u00A7f total, but your inventory and all tracked chests only have \u00A7e" + totalAvailable
                            + "\u00A7f. Still short by \u00A7c" + (needed - totalAvailable) + "\u00A7f."
            );
            if (stillNeeded > 0) {
                alertedInsufficientItems.add(item);
            }
        }
    }

    private static Map<Item, Integer> getInventoryCounts(MinecraftClient client) {
        Map<Item, Integer> counts = new HashMap<>();
        int invSize = client.player.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                Item item = stack.getItem();
                counts.put(item, counts.getOrDefault(item, 0) + stack.getCount());
            }
        }
        return counts;
    }

    private static Map<Item, Integer> countTotalAvailable(
            Map<Item, Integer> inventoryCounts,
            Map<BlockPos, List<ItemStack>> chests) {

        Map<Item, Integer> counts = new HashMap<>(inventoryCounts);
        for (List<ItemStack> stacks : chests.values()) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    Item item = stack.getItem();
                    counts.put(item, counts.getOrDefault(item, 0) + stack.getCount());
                }
            }
        }
        return counts;
    }

    private static void checkShortageSummary(Map<Item, Integer> missing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean hasGatheredItems = false;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && missing.containsKey(stack.getItem())) {
                hasGatheredItems = true;
                break;
            }
        }

        if (!hasGatheredItems && !missing.isEmpty()) {
            if (!missing.equals(lastReportedMissing)) {
                lastReportedMissing.clear();
                lastReportedMissing.putAll(missing);

                StringBuilder sb = new StringBuilder(AutoSupplyController.selectText(
                        "当前投影仍然缺少：",
                        "Current schematic still needs:"
                ));
                int count = 0;
                for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
                    sb.append("\n- \u00A7e").append(getItemName(entry.getKey()))
                            .append("\u00A7f: \u00A7c").append(entry.getValue()).append("\u00A7f");
                    count++;
                    if (count >= 10) {
                        sb.append(AutoSupplyController.selectText("\n- ... 以及更多物品", "\n- ... and more"));
                        break;
                    }
                }
                sendChat(sb.toString());
            }
        } else if (missing.isEmpty() && !lastReportedMissing.isEmpty()) {
            sendChat(
                    "\u00A7a\u00A7l完成！\u00A7f当前投影的所有方块都已铺设。",
                    "\u00A7a\u00A7lDone!\u00A7f All blocks in the current schematic have been placed."
            );
            lastReportedMissing.clear();
            alertedMissingItems.clear();
            alertedInsufficientItems.clear();
        }
    }

    public static void sendChat(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("\u00A77[\u00A7bLCS\u00A77] \u00A7f" + text), false);
        }
    }

    public static void sendChat(String zhText, String enText) {
        sendChat(selectText(zhText, enText));
    }

    public static String selectText(String zhText, String enText) {
        return isEnglishLanguage() ? enText : zhText;
    }

    public static boolean isEnglishLanguage() {
        try {
            String language = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            return language != null && language.toLowerCase(Locale.ROOT).startsWith("en");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String getItemName(Item item) {
        return item.getName().getString();
    }
}
