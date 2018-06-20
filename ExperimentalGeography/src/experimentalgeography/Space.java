/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 * Space represents some finite set of blocks that can be manipulated; you can
 * convert a space into a set of Block objects.
 *
 * Spaces are defined procedurally, by overriding the forEachBlock method,
 *
 * @author DanJ
 */
public abstract class Space {

    /**
     * Returns a space that contains no blocks at all.
     *
     * @return The empty space singleton.
     */
    public static Space empty() {
        return EmptySpace.INSTANCE;
    }

    /**
     * Returns a space that includes blocks running from 'start' to 'end', which
     * is as tall and wide as specified.
     *
     * @param start The starting point of the line.
     * @param end The ending point of the line.
     * @param width The width, in blocks, of the line.
     * @param height The height, in blocks, of the line.
     * @return The space defined by this line.
     */
    public static Space linear(Location start, Location end, int width, int height) {
        return new LinearSpace(start.clone(), end.clone(), width, height);
    }

    /**
     * Returns an new space including all blocks in this space as well as any
     * blocks in 'other'.
     *
     * @param other The space to combine with this one.
     * @return The space including all the blocks.
     */
    public Space union(Space other) {
        if (other == this || other instanceof EmptySpace) {
            return this;
        }

        return new UnionedSpace(this, other);
    }

    /**
     * Returns a space including all the blocks of this space that are inside
     * the chunk indicated, but no others.
     *
     * @param chunk The chunk that the new space is limited to.
     * @return The new space that is limited to the chunk.
     */
    public final Space withinChunk(Chunk chunk) {
        int minX = chunk.getX() * 16;
        int minZ = chunk.getZ() * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        return within(minX, maxX, minZ, maxZ, chunk.getWorld());
    }

    /**
     * Returns a space including all the blocks of this space that in side the
     * co-ordinates indicated by the parameter, and are in the specified world.
     *
     * @param minX The minimum x coordinate allowed.
     * @param maxX The maximum x coordinate allowed.
     * @param minZ The minimum z coordinate allowed.
     * @param maxZ The maximum z coordinate allowed.
     * @param world The world whose blocks are accepted.
     * @return A new space adjusted to contain only the blocks indicated.
     */
    public Space within(int minX, int maxX, int minZ, int maxZ, World world) {
        return new LimitedSpace(this, minX, maxX, minZ, maxZ, world);
    }

    /**
     * Returns a space that is the same as this one, but shifted through space.
     *
     * @param dx The delta to apply in the x direction.
     * @param dy The delta to apply in the y direction.
     * @param dz The delta to apply in the z direction.
     * @return The new, adjusted space.
     */
    public Space offset(final int dx, final int dy, final int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return this;
        }

        final Space innerSpace = this;

        return new Space() {
            @Override
            public void forEachBlock(final BlockAction action) {
                innerSpace.forEachBlock(new BlockAction() {
                    @Override
                    public void apply(int x, int y, int z, World world) {
                        action.apply(x + dx, y + dy, z + dz, world);
                    }
                });
            }
        };
    }
    ////////////////////////////////
    // Block Access
    //
    private Set<Block> lazyBlocks;

    /**
     * Returns an immutable set of all the blocks in the space. This set is
     * cached for performance, since this is used to implement contains() by
     * default.
     *
     * Unlike forEachBlock(), this method does not return duplicate blocks.
     *
     * @return
     */
    public final Set<Block> getBlocks() {
        if (lazyBlocks == null) {
            BlockCollectorSet blocks = new BlockCollectorSet();
            forEachBlock(blocks);
            lazyBlocks = Collections.unmodifiableSet(blocks);
        }

        return lazyBlocks;
    }

    /**
     * Returns true if the indicated block is included in this space. By default
     * this is implemented by getBlocks(), and it should always agree with that
     * method's set.
     *
     * @param x The x coordinate of the block to test.
     * @param y The y coordinate of the block to test.
     * @param z The z coordinate of the block to test.
     * @param world The world containing the block to test.
     * @return True if the block is inside the space.
     */
    public boolean contains(int x, int y, int z, World world) {
        Block block = world.getBlockAt(x, y, z);
        return getBlocks().contains(block);
    }

    ////////////////////////////////
    // Block Enumeration
    //
    /**
     * Gives you each block in this space in turn, by calling action.apply() for
     * each one.
     *
     * This is the most primitive method, which each Space subclass must
     * provide. This method may provide blocks in any order, and it can supply
     * the same block more than once; use getBlocks() to get a de-duplicated set
     * instead.
     *
     * @param action The apply method of this object is called for each block
     * (maybe more than once!)
     */
    public abstract void forEachBlock(BlockAction action);

    /**
     * This interface is for objects that receive the blocks from
     * forEachBlock(); by taking the co-ordinates as separate parameters, we can
     * often avoid allocating a Block object.
     */
    public interface BlockAction {

        void apply(int x, int y, int z, World world);
    }

    /**
     * This class is a TreeSet that collects blocks from forEachBlock(),
     * de-duping as it goes.
     */
    private static final class BlockCollectorSet extends TreeSet<Block> implements BlockAction {

        public BlockCollectorSet() {
            super(BLOCK_COMPARATOR);
        }

        @Override
        public void apply(int x, int y, int z, World world) {
            add(world.getBlockAt(x, y, z));
        }
    }

    ////////////////////////////////
    // Block Subclasses
    //
    /**
     * This class is a space with no blocks in it.
     */
    private static final class EmptySpace extends Space {

        public static final EmptySpace INSTANCE = new EmptySpace();

        @Override
        public Space union(Space other) {
            return other;
        }

        @Override
        public Space within(int minX, int maxX, int minZ, int maxZ, World world) {
            return this;
        }

        @Override
        public Space offset(int dx, int dy, int dz) {
            return this;
        }

        @Override
        public boolean contains(int x, int y, int z, World world) {
            return false;
        }

        @Override
        public void forEachBlock(BlockAction action) {
            // an empty space has no blocks!
        }
    }

    /**
     * This class represents a space defined by a line from a start to an end.
     * This is used to implement linear().
     */
    private static final class LinearSpace extends Space {

        private final Location start, end;
        private final int width, height;

        public LinearSpace(Location start, Location end, int width, int height) {
            this.start = start;
            this.end = end;
            this.width = width;
            this.height = height;
        }

        @Override
        public Space offset(int dx, int dy, int dz) {
            return new LinearSpace(
                    start.clone().add(dx, dy, dz),
                    end.clone().add(dx, dy, dz),
                    width, height);
        }

        @Override
        public void forEachBlock(BlockAction action) {
            World world = start.getWorld();
            double dist = start.distance(end);

            for (double i = dist; i >= 0; --i) {
                double s = i / dist;
                double e = 1.0 - s;

                int x = (int) (start.getX() * s + end.getX() * e);
                int y = (int) (start.getY() * s + end.getY() * e);
                int z = (int) (start.getZ() * s + end.getZ() * e);

                for (int dx = 0; dx < width; ++dx) {
                    for (int dy = 0; dy < height; ++dy) {
                        for (int dz = 0; dz < width; ++dz) {
                            int bx = x + dx - (width / 2);
                            int by = y + dy;
                            int bz = z + dz - (width / 2);

                            action.apply(bx, by, bz, world);
                        }
                    }
                }
            }
        }
    }

    /**
     * This class wraps another space, and blocks any blocks from the wrong
     * chunk (as it were). Used to implement withinChunk().
     */
    private static final class LimitedSpace extends Space {

        private final Space inner;
        private final int minX, maxX, minZ, maxZ;
        private final World world;

        public LimitedSpace(Space inner, int minX, int maxX, int minZ, int maxZ, World world) {
            this.inner = inner;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.world = world;
        }

        private boolean isInLimit(int x, int z, World checkWorld) {
            return x >= minX && x <= maxX
                    && z >= minZ && z <= maxZ
                    && checkWorld == world;
        }

        @Override
        public Space within(int minX, int maxX, int minZ, int maxZ, World world) {
            if (this.world == world) {
                int newMinX = Math.max(this.minX, minX);
                int newMaxX = Math.min(this.maxX, maxX);
                int newMinZ = Math.max(this.minZ, minZ);
                int newMaxZ = Math.min(this.maxZ, maxZ);

                if (newMinX <= newMaxX && newMinZ <= newMaxZ) {
                    return new LimitedSpace(inner, newMinX, newMaxX, newMinZ, newMaxZ, world);
                }
            }

            return empty();
        }

        @Override
        public Space offset(int dx, int dy, int dz) {
            return new LimitedSpace(
                    inner.offset(dx, dy, dz),
                    minX + dx, maxX + dx,
                    minZ + dz, maxZ + dz,
                    world);
        }

        @Override
        public boolean contains(int x, int y, int z, World world) {
            return isInLimit(x, z, world) && inner.contains(x, y, z, world);
        }

        @Override
        public void forEachBlock(final BlockAction action) {
            inner.forEachBlock(new BlockAction() {
                @Override
                public void apply(int x, int y, int z, World world) {
                    if (isInLimit(x, z, world)) {
                        action.apply(x, y, z, world);
                    }
                }
            });
        }
    }

    /**
     * This class contains a list of component spaces, and provides every block
     * for the lot of them.
     */
    private static class UnionedSpace extends Space {

        private final Space[] components;

        public UnionedSpace(Space... components) {
            this.components = components;
        }

        @Override
        public Space union(Space other) {
            List<Space> combined = Lists.newArrayList();
            Collections.addAll(combined, components);
            if (other instanceof UnionedSpace) {
                Collections.addAll(combined, ((UnionedSpace) other).components);
            } else {
                combined.add(other);
            }

            return new UnionedSpace(combined.toArray(new Space[combined.size()]));
        }

        @Override
        public Space within(int minX, int maxX, int minZ, int maxZ, World world) {
            Space[] limited = new Space[components.length];

            for (int i = 0; i < limited.length; ++i) {
                limited[i] = components[i].within(minX, maxX, minZ, maxZ, world);
            }

            return new UnionedSpace(limited);
        }

        @Override
        public boolean contains(int x, int y, int z, World world) {
            for (Space s : components) {
                if (s.contains(x, y, z, world)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void forEachBlock(BlockAction action) {
            for (Space s : components) {
                s.forEachBlock(action);
            }
        }
    }
    ////////////////////////////////
    // Block Comparison
    //
    private static final java.util.Comparator<Block> BLOCK_COMPARATOR = new java.util.Comparator<Block>() {
        @Override
        public int compare(Block left, Block right) {
            if (left == right) {
                return 0;
            } else if (left == null) {
                return -1;
            } else if (right == null) {
                return 1;
            }

            int cmp = compareInts(left.getX(), right.getX());
            if (cmp != 0) {
                return cmp;
            }

            cmp = compareInts(left.getZ(), right.getZ());
            if (cmp != 0) {
                return cmp;
            }

            return compareInts(left.getY(), right.getY());
        }
    };

    private static int compareInts(int left, int right) {
        if (left == right) {
            return 0;
        } else if (left < right) {
            return -1;
        } else {
            return 1;
        }
    }
}