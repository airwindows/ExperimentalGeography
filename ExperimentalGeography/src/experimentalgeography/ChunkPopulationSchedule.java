/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
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
    private final Map<ChunkPosition, OriginalChunkInfo> originalChunkInfos = Maps.newHashMap();
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

            for (OriginalChunkInfo info : map.getList("loadedChunks", OriginalChunkInfo.class)) {
                originalChunkInfos.put(info.position, info);
            }

            pendingChunks.addAll(map.getList("pendingChunks", ChunkPosition.class));
        }
    }

    /**
     * This method saves the current state of the scheduler to a file; when we
     * construct a new schedule later, we'll reload this state.
     */
    public void save() {
        MapFileMap map = new MapFileMap();
        map.put("loadedChunks", originalChunkInfos.values());
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
    public void schedule(Chunk chunk) {
        ChunkPosition pos = ChunkPosition.of(chunk);
        originalChunkInfos.put(pos, new OriginalChunkInfo(chunk));
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

        Set<ChunkPosition> loaded = originalChunkInfos.keySet();

        for (ChunkPosition candidate : pendingChunks) {
            if (loaded.containsAll(candidate.neighbors())) {
                ready.add(candidate);
            }
        }

        pendingChunks.removeAll(ready);
        return ready;
    }

    /**
     * This returns the original chunk data that was captured when that chunk
     * was first loaded, before we populated it.
     *
     * @param pos The chunk whose data is needed.
     * @return The chunk info object for the chunk.
     * @throws IllegalArgumentException if the chunk specified has not yet
     * loaded.
     */
    public OriginalChunkInfo getOriginalChunkInfo(ChunkPosition pos) {
        OriginalChunkInfo info = originalChunkInfos.get(pos);

        if (info == null) {
            throw new IllegalArgumentException(String.format(
                    "The chunk at %s has not loaded yet.",
                    pos));
        }
        return info;
    }
}
