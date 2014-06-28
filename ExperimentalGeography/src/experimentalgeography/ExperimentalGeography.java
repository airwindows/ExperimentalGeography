/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import java.util.*;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * This is the main plugin class for the plugin; it listens to events.
 *
 * @author DanJ
 */
public class ExperimentalGeography extends JavaPlugin implements Listener {

    private ChunkPopulationSchedule lazyPopulationSchedule;

    private ChunkPopulationSchedule getPopulationSchedule(long seed) {
        if (lazyPopulationSchedule == null) {
            lazyPopulationSchedule = new ChunkPopulationSchedule(this, seed);
        }

        return lazyPopulationSchedule;
    }

    /**
     * This method is called once per chunk, when the chunk is first
     * initialized. It can add extra blocks to the chunk.
     *
     * @param chunk The chunk to populate.
     */
    private void populateChunk(ChunkPosition where) {
        World world = where.getWorld();
        ChunkPopulationSchedule populationSchedule = getPopulationSchedule(world.getSeed());

        ChunkPosition[] adjacent = {
            new ChunkPosition(where.x - 1, where.z, where.worldName),
            new ChunkPosition(where.x + 1, where.z, where.worldName),
            new ChunkPosition(where.x, where.z - 1, where.worldName),
            new ChunkPosition(where.x, where.z + 1, where.worldName)
        };

        //first is X offset increasing, second is inverse X offset
        //third is Z offset increasing, fourth is inverse Z offset
        //combine to have 'density of nearby offset nodes at this chunk'

        OriginalChunkInfo whereInfo = populationSchedule.getOriginalChunkInfo(where);
        Location start = perturbNode(world, where, whereInfo.nodeY);

        Location[] ends = new Location[adjacent.length];

        for (int i = 0; i < ends.length; ++i) {
            ChunkPosition dest = adjacent[i];
            OriginalChunkInfo destInfo = populationSchedule.getOriginalChunkInfo(dest);
            int destY = destInfo.nodeY;

            ends[i] = perturbNode(world, dest, destY);
        }

        Connector connector = new Connector(where.getChunk());
        connector.connect(start, ends);
    }

    public static Location perturbNode(World world, ChunkPosition where, int y) {
        Random whereRandomOffset = getChunkRandom(world, where);
        whereRandomOffset.nextInt(16);
        int whereOffsetX = whereRandomOffset.nextInt(16);
        int whereOffsetZ = whereRandomOffset.nextInt(16);
        return new Location(world, where.x * 16 + whereOffsetX, y, where.z * 16 + whereOffsetZ);
    }

    public static Random getChunkRandom(World world, ChunkPosition where) {
        int seedx = where.x;
        int seedz = where.z;
        if (seedx == 0) {
            seedx = 64;
        }
        if (seedz == 0) {
            seedz = 64;
        }
        return new Random((seedx * seedz) + world.getSeed());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (lazyPopulationSchedule != null) {
            lazyPopulationSchedule.save();
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) {
            World world = e.getWorld();
            ChunkPopulationSchedule populationSchedule = getPopulationSchedule(world.getSeed());
            populationSchedule.schedule(e.getChunk());

            for (ChunkPosition next : populationSchedule.next()) {
                populateChunk(next);
            }

            populationSchedule.saveLater();
        }
    }
}
