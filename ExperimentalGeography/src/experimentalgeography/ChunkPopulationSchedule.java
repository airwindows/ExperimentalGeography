/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;

/**
 * This is a class to tell us when to populate chunks; it ensures that we
 * populate any chunk only when it, and all its neighbors, have loaded with
 * vanilla content.
 *
 * @author DanJ
 */
public class ChunkPopulationSchedule {

    private final Plugin plugin;
    private final File chunkScheduleFile;
    private final Set<ChunkPosition> loadedChunks = Sets.newHashSet();
    private final Set<ChunkPosition> pendingChunks = Sets.newHashSet();
    private BukkitRunnable deferredSaver;

    public ChunkPopulationSchedule(Plugin plugin) {
        this(plugin, new File("experimentalgeography.txt"));
    }

    public ChunkPopulationSchedule(Plugin plugin, File chunkScheduleFile) {
        this.plugin = Preconditions.checkNotNull(plugin);
        this.chunkScheduleFile = Preconditions.checkNotNull(chunkScheduleFile);

        if (chunkScheduleFile.exists()) {
            MapFileMap map = MapFileMap.read(chunkScheduleFile);
            loadedChunks.addAll(map.getList("loadedChunks", ChunkPosition.class));
            pendingChunks.addAll(map.getList("pendingChunks", ChunkPosition.class));
        }
    }

    /**
     * This method saves the current state of the scheduler to a file; when we
     * construct a new schedule later, we'll reload this state.
     */
    public void save() {
        MapFileMap map = new MapFileMap();
        map.put("loadedChunks", loadedChunks);
        map.put("pendingChunks", pendingChunks);
        MapFileMap.write(chunkScheduleFile, map);
    }

    /**
     * This method arranges to call save(), in 5 seconds. If called repeatedly,
     * only the first call counts- we don't keep deferring the save further and
     * further.
     */
    public void saveLater() {
        if (deferredSaver == null) {
            deferredSaver = new BukkitRunnable() {
                @Override
                public void run() {
                    deferredSaver = null;
                    save();
                }
            };

            deferredSaver.runTaskLater(plugin, 5 * 20);
        }
    }

    /**
     * This schedules a chunk to be populated later; we call this whenever a new
     * chunk loads. Once enough chunks are scheduled, they'll be returend from
     * next() in future.
     *
     * @param pos A newly loaded chunk.
     */
    public void schedule(ChunkPosition pos) {
        loadedChunks.add(pos);
        pendingChunks.add(pos);
    }

    /**
     * This returns the chunks that can now be populated. There can be more than
     * one at a time, since this returns only chunks whose neighbors have
     * loaded.
     *
     * The chunks returned are removed from the schedule, so they won't be
     * returned again.
     *
     * @return The chunks that need to be populated now; may be empty.
     */
    public List<ChunkPosition> next() {
        List<ChunkPosition> ready = Lists.newArrayList();

        for (ChunkPosition candidate : pendingChunks) {
            if (loadedChunks.containsAll(candidate.neighbors())) {
                ready.add(candidate);
            }
        }

        pendingChunks.removeAll(ready);
        return ready;
    }
}
