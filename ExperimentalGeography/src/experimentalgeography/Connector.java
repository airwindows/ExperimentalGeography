/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 *
 * @author DanJ
 */
public class Connector {

    private final ChunkPosition target;
    private final World world;

    public Connector(ChunkPosition target) {
        this.target = Preconditions.checkNotNull(target);
        this.world = target.getWorld();
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
    public void connect(Location start, Location[] ends) {
        Space space = Space.empty();

        for (Location end : ends) {
            double dist = start.distance(end);

            if (dist > 0.0) {
                int size = (int) (Math.cbrt(Math.max(0, 32 - dist)) * 2.1);

                if (size > 1) {
                    space = space.union(Space.linear(start, end, size, size + 1));
                }
            }
        }

        space.within(target).
                fillWithFloor(Material.AIR, Material.SMOOTH_BRICK, Material.DIAMOND_ORE);

        decorate(start, ends);
    }

    protected void decorate(Location start, Location[] ends) {
        //we'll be passing in variations on this by biome
        final int darkness = 64;
        //distance between nodes has to be smaller than this to place a light
        //if it's a big distance it will be darker: large caves get lit
        //16 is fairly dark, 32 still has some darkness

        double dist = 0.0;

        for (Location end : ends) {
            dist = Math.max(dist, start.distance(end));
        }

        if (dist < darkness) {
            //here we tack on the glowstone ceiling lights, and/or call the spawner/loot math
            int x = (int) start.getX();
            int y = (int) start.getY();
            int z = (int) start.getZ();

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
