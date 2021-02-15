package dev.efnilite.witp.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.efnilite.witp.WITP;
import dev.efnilite.witp.api.gamemode.Gamemode;
import dev.efnilite.witp.events.PlayerLeaveEvent;
import dev.efnilite.witp.util.Util;
import dev.efnilite.witp.util.Verbose;
import dev.efnilite.witp.util.config.Option;
import dev.efnilite.witp.util.fastboard.FastBoard;
import dev.efnilite.witp.util.inventory.InventoryBuilder;
import dev.efnilite.witp.util.sql.InvalidStatementException;
import dev.efnilite.witp.util.sql.SelectStatement;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Class to envelop every user in WITP.
 */
public abstract class ParkourUser {

    public InventoryBuilder.OpenInventoryData openInventory;
    protected final Player player;
    protected FastBoard board;
    protected GameMode previousGamemode;
    protected Location previousLocation;
    protected HashMap<Integer, ItemStack> previousInventory;

    protected static final HashMap<String, ParkourUser> users = new HashMap<>();
    protected static final HashMap<Player, ParkourPlayer> players = new HashMap<>();
    protected static HashMap<UUID, Highscore> scoreMap = new LinkedHashMap<>();
    protected static HashMap<UUID, Integer> highScores = new LinkedHashMap<>();
    protected static final Gson gson = new GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create();

    public ParkourUser(@NotNull Player player) {
        this.player = player;
        saveInventory();
        this.previousLocation = player.getLocation().clone();
        this.previousGamemode = player.getGameMode();
        this.board = new FastBoard(player);
        // remove duplicates
        users.put(player.getName(), this);
    }

    /**
     * Unregisters a ParkourPlayer
     *
     * @param   player
     *          The ParkourPlayer
     *
     * @throws  IOException
     *          When saving the player's file goes wrong
     */
    public static void unregister(@NotNull ParkourUser player, boolean sendBack, boolean kickIfBungee) throws IOException, InvalidStatementException {
        new PlayerLeaveEvent(player).call();
        Player pl = player.getPlayer();
        if (!player.getBoard().isDeleted()) {
            player.getBoard().delete();
        }
        if (player instanceof ParkourPlayer) {
            ParkourPlayer pp = (ParkourPlayer) player;
            pp.getGenerator().reset(false);
            pp.save();
            WITP.getDivider().leave(pp);
            players.remove(pl);
            for (ParkourSpectator spectator : pp.spectators.values()) {
                try {
                    ParkourPlayer.register(spectator.getPlayer());
                } catch (IOException | SQLException ex) {
                    ex.printStackTrace();
                    Verbose.error("Error while trying to register player" + player.getPlayer().getName());
                }
            }
            pp.spectators.clear();
            pp.nullify();
        } else if (player instanceof ParkourSpectator) {
            ParkourSpectator spectator = (ParkourSpectator) player;
            spectator.watching.removeSpectators(spectator);
        }
        users.remove(pl.getName());

        if (sendBack) {
            if (Option.BUNGEECORD && kickIfBungee) {
                Util.sendPlayer(pl, WITP.getConfiguration().getString("config", "bungeecord.return_server"));
            } else {
                if (Option.GO_BACK) {
                    player.teleport(Option.GO_BACK_LOC);
                } else {
                    player.teleport(player.previousLocation);
                }
                WITP.getVersionManager().setWorldBorder(player.player, new Vector().zero(), 29999984);
                pl.setGameMode(player.previousGamemode);
                if (Option.INVENTORY_HANDLING) {
                    pl.getInventory().clear();
                    for (int slot : player.previousInventory.keySet()) {
                        pl.getInventory().setItem(slot, player.previousInventory.get(slot));
                    }
                }
                pl.resetPlayerTime();
            }
        }
    }

    public boolean checkPermission(String perm) {
        if (Option.PERMISSIONS) {
            boolean check = player.hasPermission(perm);
            if (!check) {
                sendTranslated("cant-do");
            }
            return check;
        }
        return true;
    }

    /**
     * Teleports the player asynchronously, which helps with unloaded chunks (?)
     *
     * @param   to
     *          Where the player will be teleported to
     */
    public void teleport(@NotNull Location to) {
        if (to.getWorld() != null) {
            to.getWorld().getChunkAt(to);
        }
        player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    /**
     * Saves the inventory to cache, so if the player leaves the player gets their items back
     */
    protected void saveInventory() {
        this.previousInventory = new HashMap<>();
        if (Option.INVENTORY_HANDLING) {
            int index = 0;
            Inventory inventory = player.getInventory();
            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    previousInventory.put(index, item);
                }
                index++;
            }
        }
    }

    /**
     * Gets a user from a Bukkit Player
     *
     * @param   player
     *          The Bukkit Player
     *
     * @return the associated {@link ParkourUser}
     */
    public static @Nullable ParkourUser getUser(@NotNull Player player) {
        for (ParkourUser user : users.values()) {
            if (user.player.getUniqueId() == player.getUniqueId()) {
                return user;
            }
        }
        return null;
    }

    /**
     * Updates the scoreboard
     */
    protected abstract void updateScoreboard();

    /**
     * Gets the highscores of all player
     *
     * @throws  IOException
     *          When creating the file reader goes wrong
     */
    public static void fetchHighScores() throws IOException, SQLException {
        if (Option.SQL) {
            SelectStatement per = new SelectStatement(WITP.getDatabase(), "players").addColumns("uuid", "name", "highscore", "hstime");
            HashMap<String, List<Object>> stats = per.fetch();
            if (stats != null && stats.size() > 0) {
                for (String string : stats.keySet()) {
                    List<Object> values = stats.get(string);
                    UUID uuid = UUID.fromString(string);
                    String name = (String) values.get(0);
                    int highScore = Integer.parseInt((String) values.get(1));
                    String highScoreTime = (String) values.get(2);
                    highScores.put(uuid, highScore);
                    scoreMap.put(uuid, new Highscore(name, highScoreTime));
                }
            }
        } else {
            File folder = new File(WITP.getInstance().getDataFolder() + "/players/");
            if (!(folder.exists())) {
                folder.mkdirs();
                return;
            }
            for (File file : folder.listFiles()) {
                FileReader reader = new FileReader(file);
                ParkourPlayer from = gson.fromJson(reader, ParkourPlayer.class);
                String name = file.getName();
                UUID uuid = UUID.fromString(name.substring(0, name.lastIndexOf('.')));
                highScores.put(uuid, from.highScore);
                scoreMap.put(uuid, new Highscore(from.name, from.highScoreTime));
            }
        }
    }

    /**
     * Initializes the high scores
     */
    public static void initHighScores() {
        if (highScores.isEmpty()) {
            try {
                fetchHighScores();
            } catch (IOException | SQLException ex) {
                ex.printStackTrace();
                Verbose.error("Error while trying to fetch the high scores!");
            }
            highScores = Util.sortByValue(highScores);
        }
    }

    /**
     * Sends a message or array of it - coloured allowed, using '&'
     *
     * @param   messages
     *          The message
     */
    public void send(String... messages) {
        for (String msg : messages) {
            player.sendMessage(Util.color(msg));
        }
    }

    /**
     * Opens the gamemode menu
     */
    public void gamemode() {
        WITP.getRegistry().close();
        InventoryBuilder gamemode = new InventoryBuilder(this, 3, "Gamemode").open();
        List<Gamemode> gamemodes = WITP.getRegistry().getGamemodes();

        InventoryBuilder.DynamicInventory dynamic = new InventoryBuilder.DynamicInventory(gamemodes.size(), 1);
        for (Gamemode gm : gamemodes) {
            gamemode.setItem(dynamic.next(), gm.getItem(), (t, e) -> gm.handleItemClick(player, this, gamemode));
        }
        gamemode.setItem(25, WITP.getConfiguration().getFromItemData("gamemodes.search"), (t2, e2) -> {
            player.closeInventory();
            BaseComponent[] send = new ComponentBuilder().append(getTranslated("click-search"))
                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/witp search ")).create();
            player.spigot().sendMessage(send);
        });
        gamemode.setItem(26, WITP.getConfiguration().getFromItemData("general.close"), (t2, e2) -> player.closeInventory());
        gamemode.build();
    }

    /**
     * Shows the leaderboard (as a chat message)
     */
    public void leaderboard(int page) {
        initHighScores();

        int lowest = page * 10;
        int highest = (page - 1) * 10;
        if (page < 1) {
            return;
        }
        if (page > 1 && highest > highScores.size()) {
            return;
        }

        HashMap<UUID, Integer> sorted = Util.sortByValue(highScores);
        highScores = sorted;
        List<UUID> uuids = new ArrayList<>(sorted.keySet());

        send("", "", "", "", "", "", "", "");
        sendTranslated("divider");
        for (int i = highest; i < lowest; i++) {
            if (i == uuids.size()) {
                break;
            }
            @Nullable UUID uuid = uuids.get(i);
            if (uuid == null) {
                continue;
            }
            @Nullable Highscore highscore = scoreMap.get(uuid);
            if (highscore == null) {
                continue;
            }
            @Nullable String name = highscore.name;
            if (name == null || name.equals("null")) {
                name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null || name.equals("null")) {
                    continue;
                }
            }
            @Nullable String time = highscore.time;
            if (time == null || time.equals("null")) {
                time = "N/A";
            }
            int rank = i + 1;
            send("&a#" + rank + ". &7" + name + " &f- " + highScores.get(uuid) + " &7(" + time + ")");
        }

        UUID uuid = player.getUniqueId();
        Integer person = highScores.get(uuid);
        sendTranslated("your-rank", Integer.toString(getRank(uuid)), person != null ? person.toString() : "0");
        send("");

        int prevPage = page - 1;
        int nextPage = page + 1;
        BaseComponent[] previous = new ComponentBuilder()
                .append(getTranslated("previous-page"))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/witp leaderboard " + prevPage))
                .append(" | ").color(net.md_5.bungee.api.ChatColor.GRAY)
                .event((ClickEvent) null)
                .append(getTranslated("next-page"))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/witp leaderboard " + nextPage))
                .create();

        player.spigot().sendMessage(previous);
        sendTranslated("divider");
    }

    /**
     * Gets the rank of a certain player
     *
     * @param   player
     *          The player
     *
     * @return the rank (starts at 1.)
     */
    protected int getRank(UUID player) {
        return new ArrayList<>(highScores.keySet()).indexOf(player) + 1;
    }

    /**
     * Gets a message from lang.yml
     *
     * @param   path
     *          The path name in lang.yml (for example: 'time-preference')
     *
     * @param   replaceable
     *          What can be replaced (for example: %s to yes)
     */
    public void sendTranslated(String path, String... replaceable) {
        path = "messages.en." + path;
        String string = WITP.getConfiguration().getString("lang", path);
        if (string == null) {
            Verbose.error("Unknown path: " + path + " - try deleting the config");
            return;
        }
        for (String s : replaceable) {
            string = string.replaceFirst("%[a-z]", s);
        }
        send(string);
    }

    /**
     * Same as {@link #sendTranslated(String, String...)}, but without sending the text (used in GUIs)
     *
     * @param   path
     *          The path
     *
     * @param   replaceable
     *          Things that can be replaced
     *
     * @return the coloured and replaced string
     */
    public String getTranslated(String path, String... replaceable) {
        path = "messages.en." + path;
        String string = WITP.getConfiguration().getString("lang", path);
        if (string == null) {
            Verbose.error("Unknown path: " + path + " - try deleting the config");
            return "";
        }
        for (String s : replaceable) {
            string = string.replaceFirst("%[a-z]", s);
        }
        return string;
    }

    public static List<ParkourUser> getUsers() {
        return new ArrayList<>(users.values());
    }

    public static List<ParkourPlayer> getActivePlayers() {
        return new ArrayList<>(players.values());
    }

    /**
     * Gets the scoreboard of the player
     *
     * @return the {@link FastBoard} of the player
     */
    public FastBoard getBoard() {
        return board;
    }

    /**
     * Gets the Bukkit version of the player
     *
     * @return the player
     */
    public @NotNull Player getPlayer() {
        return player;
    }
}