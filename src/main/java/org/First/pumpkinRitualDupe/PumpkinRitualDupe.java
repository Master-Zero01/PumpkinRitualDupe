package org.First.pumpkinRitualDupe;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 🎃 PumpkinRitualDupe
 * A configurable Halloween-themed ritual dupe plugin.
 * Players can perform a ritual surrounded by pumpkins for a chance
 * to duplicate the item in their hand. Failure damages the player.
 */
public final class PumpkinRitualDupe extends JavaPlugin implements Listener, CommandExecutor {

    // Configurable settings
    private double successChance;
    private Material centerBlock;
    private Material surroundBlock;
    private boolean requireNight;
    private double failureDamage;
    private long playerCooldownSeconds;
    private long serverCooldownSeconds;
    private boolean debug;

    // Messages
    private String msgIncompleteCircle;
    private String msgNoItem;
    private String msgNightOnly;
    private String msgServerCooldown;
    private String msgPlayerCooldown;
    private String msgFail;
    private String msgSuccess;
    private String msgReloaded;
    private String msgNoPermission;

    // Cooldowns
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private long nextGlobalUse = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigSettings();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("pumpkindupe") != null) {
            Objects.requireNonNull(getCommand("pumpkindupe")).setExecutor(this);
        } else {
            getLogger().warning("Command 'pumpkindupe' not found in plugin.yml!");
        }

        getLogger().info("🎃 PumpkinRitualDupe enabled! Center: " + centerBlock + ", Surround: " + surroundBlock);
    }

    @Override
    public void onDisable() {
        getLogger().info("🪦 PumpkinRitualDupe disabled.");
    }

    // --------------------------------------------------------
    // Config Loading
    // --------------------------------------------------------
    private void loadConfigSettings() {
        FileConfiguration cfg = getConfig();

        // Ritual settings
        centerBlock = Material.matchMaterial(cfg.getString("ritual.block", "JACK_O_LANTERN"));
        surroundBlock = Material.matchMaterial(cfg.getString("ritual.surrounding-block", "CARVED_PUMPKIN"));
        successChance = cfg.getDouble("ritual.chance-success", 25.0);
        requireNight = cfg.getBoolean("ritual.require-nighttime", true);
        failureDamage = cfg.getDouble("ritual.failure-damage", 2.0);

        // Cooldowns
        playerCooldownSeconds = cfg.getLong("cooldowns.player-cooldown-seconds", 300);
        serverCooldownSeconds = cfg.getLong("cooldowns.server-cooldown-seconds", 120);

        // Debug
        debug = cfg.getBoolean("debug", false);

        // Messages
        msgIncompleteCircle = color(cfg.getString("messages.incomplete-circle", "&4The ritual circle is incomplete!"));
        msgNoItem = color(cfg.getString("messages.no-item", "&cYou must offer an item to the ritual!"));
        msgNightOnly = color(cfg.getString("messages.night-only", "&7The ritual only works under the moonlight..."));
        msgServerCooldown = color(cfg.getString("messages.server-cooldown", "&8The ritual’s energy hasn’t recovered yet... &7({time}s)"));
        msgPlayerCooldown = color(cfg.getString("messages.player-cooldown", "&cThe spirits demand patience... &7({time}s)"));
        msgFail = color(cfg.getString("messages.failure", "&c💀 The ritual failed... you feel the wrath of the spirits!"));
        msgSuccess = color(cfg.getString("messages.success", "&6⚡ The ritual succeeds! Your offering multiplies!"));
        color(cfg.getString("messages.invalid-block", "&cThis is not a valid ritual site!"));
        msgReloaded = color("&aPumpkinRitualDupe config reloaded.");
        msgNoPermission = color("&cYou don't have permission to do that.");

        if (centerBlock == null) {
            centerBlock = Material.JACK_O_LANTERN;
            getLogger().warning("Invalid ritual.block in config.yml — defaulted to JACK_O_LANTERN");
        }
        if (surroundBlock == null) {
            surroundBlock = Material.CARVED_PUMPKIN;
            getLogger().warning("Invalid ritual.surrounding-block in config.yml — defaulted to CARVED_PUMPKIN");
        }

        if (debug) getLogger().info("Config reloaded successfully.");
    }

    // --------------------------------------------------------
    // Ritual Logic
    // --------------------------------------------------------
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Location loc = event.getClickedBlock().getLocation();

        // Must click the configured ritual block
        if (event.getClickedBlock().getType() != centerBlock) return;

        // Must hold an item
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(msgNoItem);
            return;
        }

        // Must be nighttime
        if (requireNight && !isNight(loc.getWorld())) {
            player.sendMessage(msgNightOnly);
            return;
        }

        long now = System.currentTimeMillis();

        // Global cooldown
        if (now < nextGlobalUse) {
            long secs = (nextGlobalUse - now + 999) / 1000;
            player.sendMessage(msgServerCooldown.replace("{time}", String.valueOf(secs)));
            return;
        }

        // Player cooldown
        Long playerCooldown = playerCooldowns.get(player.getUniqueId());
        if (playerCooldown != null && now < playerCooldown) {
            long secs = (playerCooldown - now + 999) / 1000;
            player.sendMessage(msgPlayerCooldown.replace("{time}", String.valueOf(secs)));
            return;
        }

        // Check ritual structure
        if (!isValidRitualCircle(loc)) {
            player.sendMessage(msgIncompleteCircle);
            return;
        }

        // Roll success chance
        double roll = new Random().nextDouble() * 100.0;
        World world = loc.getWorld();

        if (debug) {
            getLogger().info(player.getName() + " attempted ritual at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (" + roll + "%)");
        }

        if (roll <= successChance) {
            // Success
            world.strikeLightningEffect(loc.clone().add(0.5, 1, 0.5));
            world.dropItemNaturally(loc.clone().add(0, 1, 0), item.clone());
            player.sendMessage(msgSuccess);
        } else {
            // Failure
            world.strikeLightningEffect(loc.clone().add(0.5, 1, 0.5));
            player.damage(failureDamage);
            player.sendMessage(msgFail);
        }

        // Apply cooldowns
        playerCooldowns.put(player.getUniqueId(), now + (playerCooldownSeconds * 1000));
        nextGlobalUse = now + (serverCooldownSeconds * 1000);
    }

    // --------------------------------------------------------
    // Command: /pumpkindupe reload
    // --------------------------------------------------------
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pumpkindupe.reload") && !sender.isOp()) {
                sender.sendMessage(msgNoPermission);
                return true;
            }

            try {
                reloadConfig();
                loadConfigSettings();
                sender.sendMessage(msgReloaded);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to reload config: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(color("&ePumpkinRitualDupe Commands:"));
        sender.sendMessage(color(" &7/pumpkindupe reload &8- Reloads the configuration."));
        return true;
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private boolean isValidRitualCircle(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        int[][] offsets = {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0, 0},               {1, 0, 0},
                {-1, 0, 1},  {0, 0, 1},  {1, 0, 1}
        };

        for (int[] off : offsets) {
            if (world.getBlockAt(x + off[0], y + off[1], z + off[2]).getType() != surroundBlock) {
                return false;
            }
        }
        return true;
    }

    private boolean isNight(World world) {
        if (world == null) return false;
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }
}
