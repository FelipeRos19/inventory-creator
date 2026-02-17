package fun.felipe.inventoryCreator.core.buttons;

import fun.felipe.inventoryCreator.core.actions.InventoryClickAction;
import org.bukkit.inventory.ItemStack;

public record SystemButton(int slot, ItemStack itemIcon, InventoryClickAction action) {

    public SystemButton setAction(InventoryClickAction action) {
        return new SystemButton(slot, itemIcon, action);
    }
}
