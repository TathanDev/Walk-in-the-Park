package dev.efnilite.ip.internal.style;

import dev.efnilite.ip.api.StyleType;
import dev.efnilite.vilib.inventory.item.Item;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DefaultStyleType extends StyleType {

    @Override
    public @NotNull String getName() {
        return "default";
    }

    @Override
    public @NotNull Item getItem(String locale) { // todo add config
        return new Item(Material.POPPY, "<#348EDB><bold>Default");
    }

    @Override
    public Material get(String style) {
        List<Material> materials = styles.get(style);
        return materials.get(ThreadLocalRandom.current().nextInt(materials.size()));
    }
}