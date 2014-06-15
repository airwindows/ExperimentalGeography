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

        //first is X offset increasing, second is inverse X offset
        //third is Z offset increasing, fourth is inverse Z offset
        //combine to have 'density of nearby offset nodes at this chunk'

        OriginalChunkInfo whereInfo = populationSchedule.getOriginalChunkInfo(where);
        World world = where.getWorld();

        for (ChunkPosition dest : adjacent) {
            OriginalChunkInfo destInfo = populationSchedule.getOriginalChunkInfo(dest);
            int whereY = whereInfo.nodeY;
            int destY = destInfo.nodeY;

            Location start = perturbNode(world, where, whereY);
            Location end = perturbNode(world, dest, destY);
            linkBlocks(where, start, end, Material.SMOOTH_BRICK);
            start.add(0, 2, 0);
            end.add(0, 2, 0);
            linkBlocks(where, start, end, Material.AIR);
        }
    }

    public static Location perturbNode(World world, ChunkPosition where, int y) {
        Random whereRandomOffset = getChunkRandom(world, where);
        int dummyOffset = whereRandomOffset.nextInt(16);
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
        int size = (int) (Math.cbrt(Math.max(0, 32 - dist)) * 2.1);
        if (size == 1) {
            size = 0;
        }
        if (dist > 0.0) {
            World world = start.getWorld();

            for (double i = dist; i >= 0; --i) {
                double s = i / dist;
                double e = 1.0 - s;

                for (int dx = 0; dx < size; ++dx) {
                    for (int dy = 0; dy < size; ++dy) {
                        for (int dz = 0; dz < size; ++dz) {
                            int x = (int) (start.getX() * s + end.getX() * e) + dx - (size / 2);
                            int y = (int) (start.getY() * s + end.getY() * e) + dy;
                            int z = (int) (start.getZ() * s + end.getZ() * e) + dz - (size / 2);
                            if (target.contains(x, z)) {
                                Block block = world.getBlockAt(x, y, z);
                                if (block.getType() != Material.DIAMOND_ORE) {
                                    block.setType(material);
                                }
                            }
                        }
                    }
                }
            }
            //here we tack on the glowstone ceiling lights, and/or call the spawner/loot math
            int x = (int) start.getX();
            int y = (int) start.getY();
            int z = (int) start.getZ();


            //we'll be passing in variations on this by biome
            int darkness = 64;
            //distance between nodes has to be smaller than this to place a light
            //if it's a big distance it will be darker: large caves get lit
            //16 is fairly dark, 32 still has some darkness

            if (material == Material.AIR && dist < darkness) {
                if (target.contains(x, z)) {
                    for (; y < 255; ++y) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() != Material.AIR) {
                            block.setType(Material.GLOWSTONE);
                            break;
                        }
                    }
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
