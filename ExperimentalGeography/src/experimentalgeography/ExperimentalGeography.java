/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerRespawnEvent;

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
     * This method is called once per chunk, when the chunk is first initialized. It can add extra blocks to the chunk.
     *
     * @param chunk The chunk to populate.
     */
    private void populateChunk(ChunkPosition where) {
        World world = where.getWorld();
        ChunkPopulationSchedule populationSchedule = getPopulationSchedule(world.getSeed());

        // the 8 surrounding chunks; we'll have connection to these, or amoung
        // them.
        ChunkPosition[] surroundingChunks = {
            new ChunkPosition(where.x - 1, where.z - 1, where.worldName),
            new ChunkPosition(where.x, where.z - 1, where.worldName),
            new ChunkPosition(where.x + 1, where.z - 1, where.worldName),
            new ChunkPosition(where.x - 1, where.z, where.worldName),
            new ChunkPosition(where.x + 1, where.z, where.worldName),
            new ChunkPosition(where.x - 1, where.z + 1, where.worldName),
            new ChunkPosition(where.x, where.z + 1, where.worldName),
            new ChunkPosition(where.x + 1, where.z + 1, where.worldName)
        };

        Location[] surroundingNodes = new Location[surroundingChunks.length];
        for (int i = 0; i < surroundingNodes.length; ++i) {
            ChunkPosition dest = surroundingChunks[i];
            OriginalChunkInfo destInfo = populationSchedule.getOriginalChunkInfo(dest);
            surroundingNodes[i] = perturbNode(world, dest, destInfo.nodeY);
        }

        // These are the locations of the nodes to the north, west, east, and south;
        // we connecdt directly to these.
        Location[] adjacentNodes = {
            surroundingNodes[1],
            surroundingNodes[3],
            surroundingNodes[4],
            surroundingNodes[6]
        };

        // The start location; this connects to the adjacent nodes.
        OriginalChunkInfo whereInfo = populationSchedule.getOriginalChunkInfo(where);
        Location start = perturbNode(world, where, whereInfo.nodeY);

        Connector connector = new Connector(where.getChunk(), start, adjacentNodes, surroundingNodes, whereInfo.highestBlockY);
        connector.connect();
        connector.decorate();
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
    public void onLoad() {
        super.onLoad();

        List<String> toNuke = Arrays.asList(""); //Arrays.asList("experimentalgeography.txt", "world", "world_nether", "world_the_end");
       //List<String> toNuke = Arrays.asList("experimentalgeography.txt", "world", "world_nether", "world_the_end");
        //we know which ones we want gone, and this mod is a plug-and-go mod, intended for repeated cycling with random seeds
        //so that every experience can be novel and different. Directory nuke code by Dan
        //If no world nuking, we will feed this a blank list

        for (String victim : toNuke) {
            File file = new File(victim);

            if (file.exists()) {
                if (file.isDirectory()) {
                    getLogger().info(String.format("Deleting directory %s", victim));
                    deleteRecursively(file);
                } else if (getFileExtension(file).equalsIgnoreCase("json")) {
                    getLogger().info(String.format("Clearing file %s", victim));
                    clearJsonFile(file);
                } else {
                    getLogger().info(String.format("Deleting file %s", victim));
                    file.delete();
                }
            }
        }
    }

    /**
     * This deletes a directory and all its contents, because Java does not provide that. Stupid Java!
     *
     * @param directory The directory (or file) to delete.
     */
    private static void deleteRecursively(File directory) {
        String[] listedFiles = directory.list();

        if (listedFiles != null) {
            for (String subfile : listedFiles) {
                File sf = new File(directory, subfile);
                deleteRecursively(sf);
            }
        }

        directory.delete();
    }

    /**
     * This method removes the content of a JSON file, which we need to do because when we are loading, it's too late for
     * Minecraft to recreate such a file. So we just empty it before it is read.
     *
     * @param file The JSON file to overwrite with empty content.
     */
    private static void clearJsonFile(File file) {
        try {
            Files.write("[]", file, Charsets.US_ASCII);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    } 
    
    /**
     * This extracts the file extension from the file given. The extension returned does not include the '.' preceeding it. If the
     * file has no extension, this method returns "".
     *
     * @param file The file whose extension is wanted.
     * @return The extension, without the '.'.
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        int pos = name.lastIndexOf(".");

        if (pos < 0) {
            return "";
        } else {
            return name.substring(pos + 1);
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

        if (lazyPopulationSchedule != null) {
            lazyPopulationSchedule.save();
        }
    }

   /* @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        World world = e.getPlayer().getWorld();
        Location loc = world.getSpawnLocation();
        e.getPlayer().getEnderChest().clear();
        e.setRespawnLocation(loc);
        //Permadeath. This game is a Roguelike, so it has infinite terrain generation and puts you back at the start if you die.        
    } */

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
