package org.First.pumpkinRitualDupe;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🎃 PumpkinRitualDupe — per-player cooldown only,
 * prevents off-hand + sneak activation, supports config reload via ReloadCommand,
 * optimized circle check, and all messages configurable.
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
    private Map<String, String> messages;

    // -------------------
    // Cooldowns
    // -------------------
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();

    // -------------------
    // Circle offsets (static for performance)
    // -------------------
    private static final int[][] CIRCLE_OFFSETS = {
            {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
            {-1, 0, 0},             {1, 0, 0},
            {-1, 0, 1}, {0, 0, 1},  {1, 0, 1}
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigSettings();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(color(messages.getOrDefault("plugin-enabled",
                "&a🎃 PumpkinRitualDupe enabled! Centers: {center}, Surround: {surround}, Allowed items: {allowed}"))
                .replace("{center}", centerBlocks.toString())
                .replace("{surround}", surroundBlocks.toString())
                .replace("{allowed}", allowedItems.toString()));
    }

    @Override
    public void onDisable() {
        getLogger().info(color(messages.getOrDefault("plugin-disabled", "&c🪦 PumpkinRitualDupe disabled.")));
    }

    // -------------------
    // Load configuration
    // -------------------
    public void loadConfigSettings() {
        centerBlocks = parseMaterials(getConfig().getStringList("ritual.center-blocks"), Material.JACK_O_LANTERN);
        surroundBlocks = parseMaterials(getConfig().getStringList("ritual.surrounding-blocks"), Material.CARVED_PUMPKIN);
        allowedItems = parseMaterials(getConfig().getStringList("ritual.allowed-items"), Arrays.asList(Material.values()));

        successChance = getConfig().getDouble("ritual.chance-success", 50.0);
        requireNight = getConfig().getBoolean("ritual.require-nighttime", true);
        failureDamage = getConfig().getDouble("ritual.failure-damage", 2.0);
        playerCooldownSeconds = getConfig().getLong("cooldowns.player-cooldown-seconds", 300);
        debug = getConfig().getBoolean("debug", false);

        // Load all messages
        messages = new HashMap<>();
        if (getConfig().isConfigurationSection("messages")) {
            getConfig().getConfigurationSection("messages").getKeys(false).forEach(key ->
                    messages.put(key, color(getConfig().getString("messages." + key, ""))));
        }
    }

    private List<Material> parseMaterials(List<String> names, Material... fallback) {
        List<Material> list = new ArrayList<>();
        for (String name : names) {
            Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (mat != null) list.add(mat);
            else getLogger().warning("Invalid material in config: " + name);
        }
        if (list.isEmpty()) list.addAll(Arrays.asList(fallback));
        return list;
    }

    private List<Material> parseMaterials(List<String> names, List<Material> fallback) {
        List<Material> list = new ArrayList<>();
        for (String name : names) {
            Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (mat != null) list.add(mat);
            else getLogger().warning("Invalid material in config: " + name);
        }
        return list.isEmpty() ? fallback : list;
    }

    // -------------------
    // Ritual interaction
    // -------------------
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (!centerBlocks.contains(event.getClickedBlock().getType())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(getMessage("no-item"));
            return;
        }

        Location loc = event.getClickedBlock().getLocation();

        if (requireNight && !isNight(loc.getWorld())) {
            player.sendMessage(getMessage("night-only"));
            return;
        }

        long now = System.currentTimeMillis();
        Long available = playerCooldowns.get(player.getUniqueId());
        if (available != null && now < available) {
            long secs = (available - now + 999) / 1000;
            player.sendMessage(getMessage("player-cooldown").replace("{time}", String.valueOf(secs)));
            return;
        }

        if (!isValidCircle(loc)) {
            player.sendMessage(getMessage("incomplete-circle"));
            return;
        }

        if (!allowedItems.contains(item.getType())) {
            player.sendMessage(getMessage("item-not-allowed"));
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
        World world = loc.getWorld();
        Location strikeLoc = loc.clone().add(0.5, 1, 0.5);

        if (debug) getLogger().info(player.getName() + " attempted ritual at " + loc + " (roll: " + roll + "%)");

        world.strikeLightningEffect(strikeLoc);

        if (roll <= successChance) {
            world.dropItemNaturally(loc.clone().add(0, 1, 0), item.clone());
            player.sendMessage(getMessage("success"));
        } else {
            player.damage(failureDamage);
            player.sendMessage(getMessage("failure"));
        }

        playerCooldowns.put(player.getUniqueId(), now + playerCooldownSeconds * 1000);
    }

    // -------------------
    // Ritual circle check
    // -------------------
    private boolean isValidCircle(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int[] off : CIRCLE_OFFSETS) {
            Material mat = world.getBlockAt(cx + off[0], cy, cz + off[2]).getType();
            if (!surroundBlocks.contains(mat)) return false;
        }
        return true;
    }

    private boolean isNight(World world) {
        if (world == null) return false;
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    // -------------------
    // Helper: get messages from config
    // -------------------
    public String getMessage(String key) {
        return messages.getOrDefault(key, key);
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }
}
