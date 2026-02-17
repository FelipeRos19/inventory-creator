package fun.felipe.inventoryCreator.core.models;

import fun.felipe.inventoryCreator.InventoryCreator;
import fun.felipe.inventoryCreator.core.actions.InventoryClickAction;
import fun.felipe.inventoryCreator.core.actions.InventoryCloseAction;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public abstract class InventoryModel implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final Map<Integer, ItemStack> items;
    private final Map<Integer, Map<ClickType, InventoryClickAction>> actions;
    private InventoryCloseAction closeAction;

    private final Set<Integer> inputSlots;
    private final Map<Integer, Predicate<ItemStack>> inputFilters;

    public InventoryModel(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        Bukkit.getServer().getPluginManager().registerEvents(this, InventoryCreator.getInstance().getPlugin());
        this.items = new HashMap<>();
        this.actions = new HashMap<>();
        this.inputSlots = new HashSet<>();
        this.inputFilters = new HashMap<>();
    }

    public InventoryModel(InventoryType type, Component title) {
        this.inventory = Bukkit.createInventory(this, type, title);
        Bukkit.getServer().getPluginManager().registerEvents(this, InventoryCreator.getInstance().getPlugin());
        this.items = new HashMap<>();
        this.actions = new HashMap<>();
        this.inputSlots = new HashSet<>();
        this.inputFilters = new HashMap<>();
    }

    public Map<Integer, ItemStack> getItems() {
        return this.items;
    }

    public void setItem(int slot, ItemStack item) {
        this.items.put(slot, item);
        this.inventory.setItem(slot, item);
    }

    public void canInput(int slot) {
        this.inputSlots.add(slot);
    }

    public void setInputFilter(int slot, Predicate<ItemStack> filter) {
        this.inputSlots.add(slot);
        this.inputFilters.put(slot, filter);
    }

    public void clearInputSlots() {
        for (int slot : this.inputSlots) {
            this.inventory.setItem(slot, null);
        }

        this.scheduleInputUpdate();
    }

    public void render() {
        for (int i = 0 ; i < this.inventory.getSize() ; i++) {
            if (this.isInputSlot(i)) continue;
            this.inventory.setItem(i, this.items.get(i));
        }
    }

    public void setAction(int slot, ClickType clickType, InventoryClickAction action) {
        this.actions.computeIfAbsent(slot, k -> new HashMap<>()).put(clickType, action);
    }

    public void setCloseAction(InventoryCloseAction closeAction) {
        this.closeAction = closeAction;
    }

    public void openInventory(Player player) {
        player.openInventory(this.inventory);
        this.render();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    protected void onInputChanged() {}

    @EventHandler
    public void onClickEvent(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof InventoryModel)) return;
        event.setCancelled(true);


        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot < topSize;

        if (clickedTop) {
            int slot = event.getSlot();

            if (this.isInputSlot(slot)) {
                if (this.isBlockedAction(event.getAction())) {
                    event.setCancelled(true);
                    return;
                }

                ItemStack cursor = event.getCursor();
                if (!this.canPlaceInSlot(slot, cursor)) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(false);
                this.scheduleInputUpdate();
                return;
            }

            this.runActionIfExists(event);
            return;
        }

        if (event.getClick().isShiftClick()) {
            event.setCancelled(true);

            ItemStack moving = event.getCurrentItem();
            if (this.isAir(moving)) return;

            ItemStack clone = moving.clone();
            this.moveToInput(event.getView().getTopInventory(), clone);

            int remaining = clone.getAmount();
            if (remaining <= 0) {
                event.setCurrentItem(null);
            } else {
                moving.setAmount(remaining);
                event.setCurrentItem(moving);
            }


            this.scheduleInputUpdate();
            return;
        }

        event.setCancelled(false);
    }

    @EventHandler
    public void onCloseEvent(InventoryCloseEvent event) {
        if (this.closeAction != null) this.closeAction.onClose(event);

        HandlerList.unregisterAll(this);
    }

    private boolean isInputSlot(int slot) {
        return this.inputSlots.contains(slot);
    }

    private boolean canPlaceInSlot(int slot, ItemStack item) {
        if (this.isAir(item)) return true;
        Predicate<ItemStack> filter = this.inputFilters.get(slot);
        return filter == null || filter.test(item);
    }

    private boolean isAir(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private void runActionIfExists(InventoryClickEvent event) {
        int slot = event.getSlot();
        Map<ClickType, InventoryClickAction> clickActions = this.actions.get(slot);
        if (clickActions == null) return;

        InventoryClickAction action = clickActions.get(event.getClick());
        if (action != null)
            action.onClick(event);
    }

    private boolean isBlockedAction(InventoryAction action) {
        return switch (action) {
            case COLLECT_TO_CURSOR,
                 HOTBAR_SWAP,
                 HOTBAR_MOVE_AND_READD,
                 MOVE_TO_OTHER_INVENTORY, // shift-click
                 CLONE_STACK,
                 UNKNOWN -> true;
            default -> false;
        };
    }

    private void scheduleInputUpdate() {
        Bukkit.getScheduler().runTask(InventoryCreator.getInstance().getPlugin(), this::onInputChanged);
    }

    private void moveToInput(Inventory topInventory, ItemStack itemStack) {
        if (this.isAir(itemStack)) return;

        for (int slot : this.inputSlots) {
            if (!canPlaceInSlot(slot, itemStack)) continue;

            ItemStack current = topInventory.getItem(slot);
            if (this.isAir(current)) continue;
            if (!current.isSimilar(itemStack)) continue;

            int max = current.getMaxStackSize();
            int space = max - current.getAmount();
            if (space <= 0) continue;

            int move = Math.min(space, itemStack.getAmount());
            current.setAmount(current.getAmount() + move);
            itemStack.setAmount(itemStack.getAmount() - move);

            topInventory.setItem(slot, current);
            if (itemStack.getAmount() <= 0) return;
        }

        for (int slot : this.inputSlots) {
            if (!this.canPlaceInSlot(slot, itemStack)) continue;

            ItemStack current = topInventory.getItem(slot);
            if (!this.isAir(current)) continue;

            int move = Math.min(itemStack.getAmount(), itemStack.getMaxStackSize());
            ItemStack placed = itemStack.clone();
            placed.setAmount(move);

            topInventory.setItem(slot, placed);
            itemStack.setAmount(itemStack.getAmount() - move);

            if (itemStack.getAmount() <= 0) return;
        }
    }
}
