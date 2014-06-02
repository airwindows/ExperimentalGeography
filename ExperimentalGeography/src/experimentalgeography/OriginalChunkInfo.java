/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import java.util.Map;
import org.bukkit.*;

/**
 * This class holds onto data about a chunk that is captured when the chunk is
 * loaded. This lets us 'remember' stuff that we'll change later.
 *
 * @author DanJ
 */
public final class OriginalChunkInfo implements MapFileMap.Storable {

    public final ChunkPosition position;
    public final int highestBlockY;

    public OriginalChunkInfo(Chunk chunk) {
        this.position = ChunkPosition.of(chunk);
        int centerX = position.x * 16 + 8;
        int centerZ = position.z * 16 + 8;

        highestBlockY = chunk.getWorld().getHighestBlockYAt(centerX, centerZ);
    }

    ////////////////////////////////////////////////////////////////
    // MapFileMap.Storage
    public OriginalChunkInfo(MapFileMap map) {
        this.position = map.getValue("position", ChunkPosition.class);
        this.highestBlockY = map.getInteger("highestBlockY");
    }

    @Override
    public Map<?, ?> toMap() {
        MapFileMap map = new MapFileMap();
        map.put("position", position);
        map.put("highestBlockY", highestBlockY);
        return map;
    }
}
