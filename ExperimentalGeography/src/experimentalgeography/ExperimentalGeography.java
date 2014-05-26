/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author DanJ
 */
public class ExperimentalGeography extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        super.onEnable();

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) {
            Chunk chunk = e.getChunk();
            int centerX = chunk.getX() * 16 + 8;
            int centerZ = chunk.getZ() * 16 + 8;

            int y = chunk.getWorld().getHighestBlockYAt(centerX, centerZ);

            Block block = chunk.getBlock(8, y, 8);

            block.setType(Material.BEDROCK);
        }
    }
}
