package org.First.pumpkinRitualDupe;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 🎃 PumpkinRitualDupe — per-player cooldown only,
 * prevents off-hand + sneak activation, and supports config reload.
 */
public final class PumpkinRitualDupe extends JavaPlugin implements Listener {

    // -------------------
    // Config settings
    // -------------------
    private List<Material> centerBlocks;
    private List<Material> surroundBlocks;
    private List<Material> allowedItems;
    private double successChance;
    private boolean requireNight;
    private double failureDamage;
    private long playerCooldownSeconds;
    private boolean debug;

    // -------------------
    // Messages
    // -------------------
    private String msgIncompleteCircle;
    private String msgNoItem;
    private String msgNightOnly;
    private String msgPlayerCooldown;
    private String msgFail;
    private String msgSuccess;
    private String msgReloaded;
    private String msgNoPermission;
    private String msgItemNotAllowed;

    // -------------------
    // Cooldowns
    // -------------------
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigSettings();
        getServer().getPluginManager().registerEvents(new RitualListener(), this);

        if (getCommand("pumpkindupe") != null) {
            Objects.requireNonNull(getCommand("pumpkindupe")).setExecutor(this);
        }

        getLogger().info("🎃 PumpkinRitualDupe enabled! Centers: " + centerBlocks + ", Surround: " + surroundBlocks + ", Allowed items: " + allowedItems);
    }

    @Override
    public void onDisable() {
        getLogger().info("🪦 PumpkinRitualDupe disabled.");
    }

    // -------------------
    // Load configuration
    // -------------------
    private void loadConfigSettings() {
        FileConfiguration cfg = getConfig();

        // Center blocks
        centerBlocks = new ArrayList<>();
        for (String name : cfg.getStringList("ritual.center-blocks")) {
            Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).trim());
            if (mat != null) centerBlocks.add(mat);
        }
        if (centerBlocks.isEmpty()) centerBlocks.add(Material.JACK_O_LANTERN);

        // Surrounding blocks
        surroundBlocks = new ArrayList<>();
        for (String name : cfg.getStringList("ritual.surrounding-blocks")) {
            Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).trim());
            if (mat != null) surroundBlocks.add(mat);
        }
        if (surroundBlocks.isEmpty()) surroundBlocks.add(Material.CARVED_PUMPKIN);

        // Allowed items
        allowedItems = new ArrayList<>();
        for (String name : cfg.getStringList("ritual.allowed-items")) {
            Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT).trim());
            if (mat != null) allowedItems.add(mat);
            else getLogger().warning("Invalid material in allowed-items config: " + name);
        }
        if (allowedItems.isEmpty()) {
            getLogger().warning("No valid allowed-items found in config, defaulting to all items allowed.");
            allowedItems = Arrays.asList(Material.values());
        }

        // Other settings
        successChance = cfg.getDouble("ritual.chance-success", 50.0);
        requireNight = cfg.getBoolean("ritual.require-nighttime", true);
        failureDamage = cfg.getDouble("ritual.failure-damage", 2.0);
        playerCooldownSeconds = cfg.getLong("cooldowns.player-cooldown-seconds", 300);
        debug = cfg.getBoolean("debug", false);

        // Messages
        msgIncompleteCircle = color(cfg.getString("messages.incomplete-circle", "&4The ritual circle is incomplete!"));
        msgNoItem = color(cfg.getString("messages.no-item", "&cYou must offer an item to the ritual!"));
        msgNightOnly = color(cfg.getString("messages.night-only", "&7The ritual only works under the moonlight..."));
        msgPlayerCooldown = color(cfg.getString("messages.player-cooldown", "&cThe spirits demand patience... &7({time}s)"));
        msgFail = color(cfg.getString("messages.failure", "&c💀 The ritual failed... you feel the wrath of the spirits!"));
        msgSuccess = color(cfg.getString("messages.success", "&6⚡ The ritual succeeds! Your offering multiplies!"));
        msgReloaded = color(cfg.getString("messages.reloaded", "&aPumpkinRitualDupe config reloaded."));
        msgNoPermission = color(cfg.getString("messages.no-permission", "&cYou don't have permission to do that."));
        msgItemNotAllowed = color(cfg.getString("messages.item-not-allowed", "&cThis item cannot be duplicated by the ritual!"));
    }

    // -------------------
    // Command: /pumpkindupe
    // -------------------
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull[] args) {
        if (!command.getName().equalsIgnoreCase("pumpkindupe")) return false;

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pumpkindupe.reload") && !sender.isOp()) {
                sender.sendMessage(msgNoPermission);
                return true;
            }

            reloadConfig();
            loadConfigSettings();
            sender.sendMessage(msgReloaded);
            return true;
        }

        sender.sendMessage(color("&ePumpkinRitualDupe Commands:"));
        sender.sendMessage(color(" &7/pumpkindupe reload &8- Reload configuration (permission: pumpkindupe.reload)"));
        return true;
    }

    // -------------------
    // Ritual listener
    // -------------------
    private class RitualListener implements Listener {

        @EventHandler
        public void onRightClick(PlayerInteractEvent event) {
            // Prevent double-activation and sneak interaction
            if (event.getHand() != EquipmentSlot.HAND) return;
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            Player player = event.getPlayer();
            if (player.isSneaking()) return; // ignore sneak-right-clicks (e.g., building)

            // Only respond to ritual center blocks
            if (!centerBlocks.contains(event.getClickedBlock().getType())) return;

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                player.sendMessage(msgNoItem);
                return;
            }

            Location loc = event.getClickedBlock().getLocation();

            // Nighttime check
            if (requireNight && !isNight(loc.getWorld())) {
                player.sendMessage(msgNightOnly);
                return;
            }

            // Per-player cooldown
            long now = System.currentTimeMillis();
            Long playerAvailable = playerCooldowns.get(player.getUniqueId());
            if (playerAvailable != null && now < playerAvailable) {
                long secs = (playerAvailable - now + 999) / 1000;
                player.sendMessage(msgPlayerCooldown.replace("{time}", String.valueOf(secs)));
                return;
            }

            // Check ritual formation
            if (!isValidCircle(loc)) {
                player.sendMessage(msgIncompleteCircle);
                return;
            }

            // Whitelist check
            if (!allowedItems.contains(item.getType())) {
                player.sendMessage(msgItemNotAllowed);
                return;
            }

            double roll = new Random().nextDouble() * 100.0;
            World world = loc.getWorld();

            if (debug) getLogger().info(player.getName() + " attempted ritual at " + loc + " (roll: " + roll + "%)");

            if (roll <= successChance) {
                world.strikeLightningEffect(loc.clone().add(0.5, 1, 0.5));
                world.dropItemNaturally(loc.clone().add(0, 1, 0), item.clone());
                player.sendMessage(msgSuccess);
            } else {
                world.strikeLightningEffect(loc.clone().add(0.5, 1, 0.5));
                player.damage(failureDamage);
                player.sendMessage(msgFail);
            }

            // Apply cooldown
            playerCooldowns.put(player.getUniqueId(), now + (playerCooldownSeconds * 1000));
        }

        private boolean isValidCircle(Location center) {
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
                Material mat = world.getBlockAt(x + off[0], y + off[1], z + off[2]).getType();
                if (!surroundBlocks.contains(mat)) return false;
            }
            return true;
        }

        private boolean isNight(World world) {
            if (world == null) return false;
            long time = world.getTime();
            return time >= 13000 && time <= 23000;
        }
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }
}
