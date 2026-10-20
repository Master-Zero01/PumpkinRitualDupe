package org.First.pumpkinRitualDupe;

import org.First.pumpkinRitualDupe.commands.ReloadCommand;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PumpkinRitualDupe provides a configurable, chance-based pumpkin ritual.
 *
 * <p>The ritual uses a per-player cooldown, ignores off-hand and sneaking
 * interactions, validates the eight surrounding blocks, and supports
 * configuration reloads through {@code /pumpkindupe reload}.</p>
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
            {-1, 0, 0},                  {1, 0, 0},
            {-1, 0, 1},  {0, 0, 1},  {1, 0, 1}
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigSettings();

        getServer().getPluginManager().registerEvents(this, this);

        // Keep cooldowns across reconnects, but periodically discard expired UUIDs.
        getServer().getScheduler().runTaskTimer(
                this, this::cleanupExpiredCooldowns, 20L * 60L, 20L * 60L * 5L);

        if (getCommand("pumpkindupe") != null) {
            ReloadCommand reload = new ReloadCommand(this);
            getCommand("pumpkindupe").setExecutor(reload);
            getCommand("pumpkindupe").setTabCompleter(reload);
        } else {
            // plugin.yml should always declare this command; log a clear error if it does not.
            getLogger().severe("Command 'pumpkindupe' is missing from plugin.yml; reload command is unavailable.");
        }

        getLogger().info(color(messages.getOrDefault("plugin-enabled",
                "&a🎃 PumpkinRitualDupe enabled! Centers: {center}, Surround: {surround}, Allowed items: {allowed}"))
                .replace("{center}", centerBlocks.toString())
                .replace("{surround}", surroundBlocks.toString())
                .replace("{allowed}", allowedItems.toString()));
    }

    @Override
    public void onDisable() {
        String disabledMessage = messages == null
                ? "&c🪦 PumpkinRitualDupe disabled."
                : messages.getOrDefault("plugin-disabled", "&c🪦 PumpkinRitualDupe disabled.");
        getLogger().info(color(disabledMessage));
    }

    // -------------------
    // Load configuration
    // -------------------
    public void loadConfigSettings() {
        centerBlocks = parseMaterials(
                getConfig().getStringList("ritual.center-blocks"), Material.JACK_O_LANTERN);
        surroundBlocks = parseMaterials(
                getConfig().getStringList("ritual.surrounding-blocks"), Material.CARVED_PUMPKIN);
        // Preserve the original fail-open fallback if this setting is empty or invalid.
        allowedItems = parseMaterials(
                getConfig().getStringList("ritual.allowed-items"),
                Arrays.stream(Material.values()).filter(m -> !m.isLegacy() && m.isItem() && !m.isAir()).collect(Collectors.toList()));

        double configuredChance = getConfig().getDouble("ritual.chance-success", 50.0);
        if (!Double.isFinite(configuredChance)) {
            getLogger().warning("ritual.chance-success must be a finite number; using 50.0.");
            successChance = 50.0;
        } else {
            successChance = clamp(configuredChance, 0.0, 100.0);
            if (successChance != configuredChance) {
                getLogger().warning("ritual.chance-success was outside 0-100 and has been clamped to "
                        + successChance + ".");
            }
        }

        requireNight = getConfig().getBoolean("ritual.require-nighttime", true);

        double configuredDamage = getConfig().getDouble("ritual.failure-damage", 2.0);
        if (!Double.isFinite(configuredDamage)) {
            getLogger().warning("ritual.failure-damage must be finite; using 2.0.");
            failureDamage = 2.0;
        } else {
            failureDamage = Math.max(0.0, configuredDamage);
            if (failureDamage != configuredDamage) {
                getLogger().warning("ritual.failure-damage cannot be negative; using 0.0.");
            }
        }

        long configuredCooldown = getConfig().getLong("cooldowns.player-cooldown-seconds", 300L);
        playerCooldownSeconds = Math.max(0L, configuredCooldown);
        if (playerCooldownSeconds != configuredCooldown) {
            getLogger().warning("cooldowns.player-cooldown-seconds cannot be negative; using 0.");
        }

        debug = getConfig().getBoolean("debug", false);

        // Load all messages and translate legacy ampersand color codes once.
        messages = new HashMap<>();
        if (getConfig().isConfigurationSection("messages")) {
            getConfig().getConfigurationSection("messages").getKeys(false).forEach(key ->
                    messages.put(key, color(getConfig().getString("messages." + key, ""))));
        }
    }

    private List<Material> parseMaterials(List<String> names, Material... fallback) {
        return parseMaterials(names, Arrays.asList(fallback));
    }

    private List<Material> parseMaterials(List<String> names, List<Material> fallback) {
        List<Material> list = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                getLogger().warning("Ignoring an empty material name in config.");
                continue;
            }

            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                list.add(material);
            } else {
                getLogger().warning("Invalid material in config: " + name);
            }
        }
        // Retain the existing behavior: an empty/invalid allow-list falls back to all materials.
        return list.isEmpty() ? new ArrayList<>(fallback) : list;
    }

    // -------------------
    // Ritual interaction
    // -------------------
    @EventHandler(ignoreCancelled = true)
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

        // Prevent vanilla behaviour (placing the offered block, opening the beacon GUI) from consuming the item.
        event.setCancelled(true);

        Location loc = event.getClickedBlock().getLocation();

        if (requireNight && !isNight(loc.getWorld())) {
            player.sendMessage(getMessage("night-only"));
            return;
        }

        long now = System.currentTimeMillis();
        Long available = playerCooldowns.get(player.getUniqueId());
        if (available != null && now < available) {
            long secondsRemaining = (available - now + 999L) / 1000L;
            player.sendMessage(getMessage("player-cooldown")
                    .replace("{time}", String.valueOf(secondsRemaining)));
            return;
        }
        if (available != null) {
            playerCooldowns.remove(player.getUniqueId());
        }

        if (!isValidCircle(loc)) {
            player.sendMessage(getMessage("incomplete-circle"));
            return;
        }

        if (!allowedItems.contains(item.getType())) {
            player.sendMessage(getMessage("item-not-allowed"));
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        World world = loc.getWorld();
        Location strikeLocation = loc.clone().add(0.5, 1.0, 0.5);

        if (debug) {
            String debugMessage = getMessage("debug-roll")
                    .replace("{player}", player.getName())
                    .replace("{location}", world.getName() + " " + loc.getBlockX() + ", "
                            + loc.getBlockY() + ", " + loc.getBlockZ())
                    .replace("{roll}", String.format(Locale.ROOT, "%.2f", roll));
            getLogger().info(ChatColor.stripColor(debugMessage));
        }

        world.strikeLightningEffect(strikeLocation);

        // A strict comparison makes 0% always fail and 100% always succeed.
        if (roll < successChance) {
            world.dropItemNaturally(loc.clone().add(0.0, 1.0, 0.0), item.clone());
            player.sendMessage(getMessage("success"));
        } else {
            if (failureDamage > 0.0) {
                player.damage(failureDamage);
            }
            player.sendMessage(getMessage("failure"));
        }

        long cooldownMillis = TimeUnit.SECONDS.toMillis(playerCooldownSeconds);
        long cooldownExpiry = cooldownMillis > Long.MAX_VALUE - now
                ? Long.MAX_VALUE
                : now + cooldownMillis;
        playerCooldowns.put(player.getUniqueId(), cooldownExpiry);
    }

    private void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    // -------------------
    // Ritual circle check
    // -------------------
    private boolean isValidCircle(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int[] offset : CIRCLE_OFFSETS) {
            Material material = world.getBlockAt(
                    centerX + offset[0],
                    centerY,
                    centerZ + offset[2]
            ).getType();

            if (!surroundBlocks.contains(material)) return false;
        }
        return true;
    }

    private boolean isNight(World world) {
        if (world == null) return false;
        long time = world.getTime();
        return time >= 13000L && time <= 23000L;
    }

    // -------------------
    // Helper: get messages from config
    // -------------------
    public String getMessage(String key) {
        return messages == null ? key : messages.getOrDefault(key, key);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }
}
