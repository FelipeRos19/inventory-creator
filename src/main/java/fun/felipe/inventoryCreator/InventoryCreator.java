package fun.felipe.inventoryCreator;

import org.bukkit.plugin.Plugin;

public final class InventoryCreator {
    private static InventoryCreator inventoryCreator;
    private Plugin plugin;

    public InventoryCreator(Plugin plugin) {
        this.plugin = plugin;
        inventoryCreator = this;
    }

    public static InventoryCreator getInstance() {
        return inventoryCreator;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
