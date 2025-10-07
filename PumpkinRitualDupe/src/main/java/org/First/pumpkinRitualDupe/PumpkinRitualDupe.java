package org.First.pumpkinRitualDupe;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public final class PumpkinRitualDupe extends JavaPlugin implements Listener {

    private double successChance;
    private Material centerBlock;
    private Material surroundBlock;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("🎃 PumpkinRitualDupe enabled! Center block: " + centerBlock + " | Surround: " + surroundBlock + " | Success: " + successChance + "%");
    }

    private void loadConfigSettings() {
        FileConfiguration cfg = getConfig();
        successChance = cfg.getDouble("success-chance", 25.0);
        centerBlock = Material.matchMaterial(cfg.getString("center-block", "JACK_O_LANTERN"));
        surroundBlock = Material.matchMaterial(cfg.getString("surround-block", "CARVED_PUMPKIN"));

        if (centerBlock == null) centerBlock = Material.JACK_O_LANTERN;
        if (surroundBlock == null) surroundBlock = Material.CARVED_PUMPKIN;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) return;

        // Must click configured center block
        if (event.getClickedBlock().getType() != centerBlock) return;

        Location loc = event.getClickedBlock().getLocation();

        if (!isValidRitualCircle(loc)) {
            player.sendMessage(ChatColor.DARK_RED + "The ritual circle is incomplete!");
            return;
        }

        // Chance roll
        double roll = new Random().nextDouble() * 100;
        if (roll > successChance) {
            player.sendMessage(ChatColor.RED + "💀 The ritual failed...");
            return;
        }

        // Success!
        World world = loc.getWorld();
        world.strikeLightningEffect(loc.clone().add(0.5, 1, 0.5));
        world.dropItemNaturally(loc.clone().add(0, 1, 0), itemInHand.clone());
        player.sendMessage(ChatColor.GOLD + "⚡ The ritual succeeds! Your offering multiplies!");
    }

    private boolean isValidRitualCircle(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();

        int[][] offsets = {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0, 0},              {1, 0, 0},
                {-1, 0, 1},  {0, 0, 1},  {1, 0, 1}
        };

        for (int[] offset : offsets) {
            Material mat = world.getBlockAt(x + offset[0], y, z + offset[2]).getType();
            if (mat != surroundBlock) {
                return false;
            }
        }
        return true;
    }
}
