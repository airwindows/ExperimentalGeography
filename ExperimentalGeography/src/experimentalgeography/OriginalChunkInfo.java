/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import java.util.*;
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
    public final int nodeY;

    public OriginalChunkInfo(Chunk chunk) {
        this.position = ChunkPosition.of(chunk);
        Location center = ExperimentalGeography.perturbNode(chunk.getWorld(), position, 0);
        int centerX = (int) center.getX();
        int centerZ = (int) center.getZ();

        highestBlockY = chunk.getWorld().getHighestBlockYAt(centerX, centerZ);

        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL) {
            Random rnd = ExperimentalGeography.getChunkRandom(chunk.getWorld(), position);
            nodeY = rnd.nextInt(64) + 32;
            System.out.println(nodeY);
        } else {
            nodeY = highestBlockY / 2;
        }
    }

    ////////////////////////////////////////////////////////////////
    // MapFileMap.Storage
    public OriginalChunkInfo(MapFileMap map) {
        this.position = map.getValue("position", ChunkPosition.class);
        this.highestBlockY = map.getInteger("highestBlockY");
        this.nodeY = map.getInteger("nodeY");
    }

    @Override
    public Map<?, ?> toMap() {
        MapFileMap map = new MapFileMap();
        map.put("position", position);
        map.put("highestBlockY", highestBlockY);
        map.put("nodeY", nodeY);
        return map;
    }
}
