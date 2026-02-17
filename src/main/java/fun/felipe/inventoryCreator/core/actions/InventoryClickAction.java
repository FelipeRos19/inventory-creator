package fun.felipe.inventoryCreator.core.actions;

import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface InventoryClickAction {

    void onClick(InventoryClickEvent event);
}
