package fun.felipe.inventoryCreator.core.actions;

import org.bukkit.event.inventory.InventoryCloseEvent;

@FunctionalInterface
public interface InventoryCloseAction {

    void onClose(InventoryCloseEvent event);
}
