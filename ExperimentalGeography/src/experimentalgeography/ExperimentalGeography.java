/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import java.util.*;
import java.io.*;
import com.google.common.collect.*;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * This is the main plugin class for the plugin; it listens to events.
 *
 * @author DanJ
 */
public class ExperimentalGeography extends JavaPlugin implements Listener {

    private final ChunkPopulationSchedule populationSchedule = new ChunkPopulationSchedule(this);

    /**
     * This method is called once per chunk, when the chunk is first
     * initialized. It can add extra blocks to the chunk.
     *
     * @param chunk The chunk to populate.
     */
    private void populateChunk(ChunkPosition where) {
        OriginalChunkInfo info = populationSchedule.getOriginalChunkInfo(where);

        ChunkPosition[] adjacent = {
            new ChunkPosition(where.x - 1, where.z, where.worldName),
            new ChunkPosition(where.x + 1, where.z, where.worldName),
            new ChunkPosition(where.x, where.z - 1, where.worldName),
            new ChunkPosition(where.x, where.z + 1, where.worldName)
        };

        OriginalChunkInfo whereInfo = populationSchedule.getOriginalChunkInfo(where);
        World world = where.getWorld();

        for (ChunkPosition dest : adjacent) {
            OriginalChunkInfo destInfo = populationSchedule.getOriginalChunkInfo(dest);

            Location start = new Location(world, where.x * 16 + 8, whereInfo.highestBlockY, where.z * 16 + 8);
            Location end = new Location(world, dest.x * 16 + 8, destInfo.highestBlockY, dest.z * 16 + 8);

            linkBlocks(where, start, end, Material.BEDROCK);
        }
    }

    /**
     * This is a line drawing function in minecraft blocks; it's kinda lame but
     * it does okay. It draws a line in three-space between two points, but only
     * touches the blocks of the chunk specified. This is needed because if we
     * touch a chunk not loaded, it will be loaded, and this leads to infinite
     * regress.
     *
     * @param target The chunk we are generating. We change only its blocks.
     * @param start The start point of the line.
     * @param end The end point of the line.
     */
    private void linkBlocks(ChunkPosition target, Location start, Location end, Material material) {
        double dist = start.distance(end);

        if (dist > 0.0) {
            World world = start.getWorld();

            for (double i = dist; i >= 0; --i) {
                double s = i / dist;
                double e = 1.0 - s;
                int x = (int) (start.getX() * s + end.getX() * e);
                int y = (int) (start.getY() * s + end.getY() * e);
                int z = (int) (start.getZ() * s + end.getZ() * e);

                if (target.contains(x, z)) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(material);
                }
            }
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        populationSchedule.save();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) {
            populationSchedule.schedule(e.getChunk());

            for (ChunkPosition next : populationSchedule.next()) {
                populateChunk(next);
            }

            populationSchedule.saveLater();
        }
    }
}
