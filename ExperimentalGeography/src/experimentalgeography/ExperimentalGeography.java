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

    private final File chunkScheduleFile = new File("experimentalgeography.txt");
    private final Set<ChunkPosition> loadedChunks = Sets.newHashSet();
    private final Set<ChunkPosition> pendingChunks = Sets.newHashSet();
    private BukkitRunnable deferredSaver;

    private void loadChunkSchedule() {
        loadedChunks.clear();
        pendingChunks.clear();

        if (chunkScheduleFile.exists()) {
            MapFileMap map = MapFileMap.read(chunkScheduleFile);
            loadedChunks.addAll(map.getList("loadedChunks", ChunkPosition.class));
            pendingChunks.addAll(map.getList("pendingChunks", ChunkPosition.class));
        }
    }

    private void saveChunkSchedule() {
        MapFileMap map = new MapFileMap();
        map.put("loadedChunks", loadedChunks);
        map.put("pendingChunks", pendingChunks);
        MapFileMap.write(chunkScheduleFile, map);
    }

    private void saveChunkScheduleLater() {
        if (deferredSaver == null) {
            deferredSaver = new BukkitRunnable() {
                @Override
                public void run() {
                    deferredSaver = null;
                    saveChunkSchedule();
                }
            };

            deferredSaver.runTaskLater(this, 5 * 20);
        }
    }

    private void markNewChunk(ChunkPosition pos) {
        loadedChunks.add(pos);
        pendingChunks.add(pos);
    }

    private List<ChunkPosition> nextChunksToPopulate() {
        List<ChunkPosition> ready = Lists.newArrayList();

        for (ChunkPosition candidate : pendingChunks) {
            if (loadedChunks.containsAll(candidate.neighbors())) {
                ready.add(candidate);
            }
        }

        pendingChunks.removeAll(ready);
        return ready;
    }

    /**
     * This method is called once per chunk, when the chunk is first
     * initialized. It can add extra blocks to the chunk.
     *
     * @param chunk The chunk to populate.
     */
    private void populateChunk(Chunk chunk) {
        int centerX = chunk.getX() * 16 + 8;
        int centerZ = chunk.getZ() * 16 + 8;

        int y = chunk.getWorld().getHighestBlockYAt(centerX, centerZ);

        Block block = chunk.getBlock(8, y, 8);

        block.setType(Material.BEDROCK);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        loadChunkSchedule();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        saveChunkSchedule();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) {
            markNewChunk(ChunkPosition.of(e.getChunk()));

            for (ChunkPosition next : nextChunksToPopulate()) {
                populateChunk(next.getChunk());
            }
            
            saveChunkScheduleLater();
        }
    }
}
