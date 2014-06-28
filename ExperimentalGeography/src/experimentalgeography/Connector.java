/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 * This class contains the logic to populate a specific chunk with our content;
 * there's a 'start' location somewhere in the chunk, and then we also get the
 * start locations for adjacent chunks; these are the 'ends', and we connect to
 * 'start' to each 'end'.
 *
 * @author DanJ
 */
public final class Connector {

    private final Chunk target;
    private final World world;
    private final Location start;
    private final Location[] ends;

    public Connector(Chunk target, Location start, Location[] ends) {
        this.target = Preconditions.checkNotNull(target);
        this.world = target.getWorld();
        this.start = start;
        this.ends = ends;
    }

    /**
     * this method actually connects the start location to the ends, but updates
     * only blocks in the target chunk.
     */
    public void connect() {
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

        fillWithFloor(space, Material.AIR, Material.SMOOTH_BRICK,
                EnumSet.of(Material.DIAMOND_ORE));
    }

    public void decorate() {
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

            if (ChunkPosition.of(target).contains(x, z)) {
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

    /**
     * This method fills the space given with the 'air' matierial, except that
     * the bottommost blocks get the 'floor' material, and any blocks that have
     * the 'toSpare' materials are not altered.
     *
     * @param space The space ot fill.
     * @param air The fill material.
     * @param floor The fill material used at the bottom of the space.
     * @param toSpare These materials are not replaced, if fcund.
     */
    private void fillWithFloor(Space space, final Material air, final Material floor, Set<Material> toSpare) {
        Space inTarget = space.withinChunk(target);

        for (Block block : inTarget.getBlocks()) {
            if (!toSpare.contains(block.getType())) {
                if (isFloorBlock(space, block.getX(), block.getY(), block.getZ(), block.getWorld())) {
                    block.setType(floor);
                } else {
                    block.setType(air);
                }
            }
        }
    }

    /**
     * This method decides if the block indicates is a 'floor block' for the
     * space given.
     *
     * @param space The space being filled.
     * @param x The x coordinate of the block being tested.
     * @param y The y coordinate of the block being tested.
     * @param z The z coordinate of the block being tested.
     * @param world The world being updated.
     * @return True if the block should use the floor material.
     */
    private static boolean isFloorBlock(Space space, int x, int y, int z, World world) {
        if (!space.contains(x, y, z, world)) {
            return false;
        }

        if (!space.contains(x, y - 1, z, world)) {
            return true;
        }

        if (space.contains(x, y - 2, z, world)) {
            return false;
        }

        boolean interior = space.contains(x + 1, y, z, world)
                && space.contains(x - 1, y, z, world)
                && space.contains(x, y, z + 1, world)
                && space.contains(x, y, z - 1, world);

        if (interior) {
            return false;
        } else if (isFloorBlock(space, x, y - 1, z, world)) {
            return false;
        } else {
            return true;
        }
    }
}
