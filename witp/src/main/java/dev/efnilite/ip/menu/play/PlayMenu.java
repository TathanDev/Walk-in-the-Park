package dev.efnilite.ip.menu.play;

import dev.efnilite.ip.ParkourOption;
import dev.efnilite.ip.config.Locales;
import dev.efnilite.ip.menu.DynamicMenu;
import dev.efnilite.ip.menu.Menus;
import dev.efnilite.vilib.inventory.Menu;
import dev.efnilite.vilib.inventory.animation.RandomAnimation;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The menu where players can join modes
 */
public class PlayMenu extends DynamicMenu {

    public PlayMenu() {
        registerMainItem(1, 0,
                (player, user) -> Locales.getItem(player, "play.single.item")
                        .click(event -> Menus.SINGLE.open(event.getPlayer())),
                ParkourOption.SINGLE::checkPermission);

        registerMainItem(1, 2,
                (player, user) -> Locales.getItem(player, "play.spectator.item")
                        .click(event -> Menus.SPECTATOR.open(event.getPlayer())),
                ParkourOption.SPECTATOR::checkPermission);

        registerMainItem(2, 0,
                (player, user) -> Locales.getItem(player, "other.close")
                        .click(event -> event.getPlayer().closeInventory()),
                player -> true);
    }

    public void open(Player player) {
        Menu menu = new Menu(3, Locales.getString(player, "play.name", false))
                .fillBackground(Material.GRAY_STAINED_GLASS_PANE)
                .animation(new RandomAnimation())
                .distributeRowsEvenly();

        display(player, menu);
    }
}