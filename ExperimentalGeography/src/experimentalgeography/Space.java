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
 *
 * @author DanJ
 */
public abstract class Space {

    public static Space empty() {
        return EmptySpace.INSTANCE;
    }

    public static Space linear(Location start, Location end, int width, int height) {
        return new LinearSpace(start.clone(), end.clone(), width, height);
    }

    public Space union(Space other) {
        if (other == this || other instanceof EmptySpace) {
            return this;
        }

        Space[] components = {this, other};
        return new UnionedSpace(components);
    }

    public Space within(ChunkPosition chunk) {
        return new ChunkLimitedSpace(this, chunk);
    }

    public Space offset(final int dx, final int dy, final int dz) {
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

    public final Set<Block> getBlocks() {
        if (lazyBlocks == null) {
            Set<Block> blocks = Sets.newTreeSet(BLOCK_COMPARATOR);
            addBlocksTo(blocks);
            lazyBlocks = Collections.unmodifiableSet(blocks);
        }

        return lazyBlocks;
    }

    public boolean contains(int x, int y, int z, World world) {
        Block block = world.getBlockAt(x, y, z);
        return getBlocks().contains(block);
    }

    ////////////////////////////////
    // Block Changes
    //
    public final void fill(final Material material, Material... toSpare) {
        fill(material, Arrays.asList(toSpare));
    }

    public final void fill(final Material material, final Collection<Material> toSpare) {
        forEachBlock(new BlockAction() {
            @Override
            public void apply(int x, int y, int z, World world) {
                Block block = world.getBlockAt(x, y, z);

                if (!toSpare.contains(block.getType())) {
                    block.setType(material);
                }
            }
        });
    }

    public final void fillWithFloor(final Material air, final Material floor, final Material... toSpare) {
        fillWithFloor(air, floor, Arrays.asList(toSpare));
    }

    public final void fillWithFloor(final Material air, final Material floor, final Collection<Material> toSpare) {
        forEachBlock(new BlockAction() {
            @Override
            public void apply(int x, int y, int z, World world) {
                Block block = world.getBlockAt(x, y, z);

                if (!toSpare.contains(block.getType())) {
                    if (contains(x, y - 1, z, world)) {
                        block.setType(air);
                    } else {
                        block.setType(floor);
                    }
                }
            }
        });
    }

    ////////////////////////////////
    // Block Enumeration
    //
    public interface BlockAction {

        void apply(int x, int y, int z, World world);
    }

    public final void addBlocksTo(final Collection<Block> action) {
        forEachBlock(new BlockAction() {
            @Override
            public void apply(int x, int y, int z, World world) {
                Block block = world.getBlockAt(x, y, z);
                action.add(block);
            }
        });
    }

    public abstract void forEachBlock(BlockAction action);

    ////////////////////////////////
    // Block Subclasses
    //
    private static final class EmptySpace extends Space {

        public static final EmptySpace INSTANCE = new EmptySpace();

        @Override
        public Space union(Space other) {
            return other;
        }

        @Override
        public Space within(ChunkPosition chunk) {
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
                    end.clone().add(dz, dy, dz),
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

    private static final class ChunkLimitedSpace extends Space {

        private final Space inner;
        private final ChunkPosition chunk;
        private final World chunkWorld;

        public ChunkLimitedSpace(Space inner, ChunkPosition chunk) {
            this.inner = inner;
            this.chunk = chunk;
            this.chunkWorld = chunk.getWorld();
        }

        @Override
        public Space within(ChunkPosition chunk) {
            if (this.chunk.equals(chunk)) {
                return this;
            } else {
                return empty();
            }
        }

        @Override
        public Space offset(int dx, int dy, int dz) {
            if (dx == 0 && dz == 0) {
                return new ChunkLimitedSpace(inner.offset(dx, dy, dz), chunk);
            } else {
                return super.offset(dx, dy, dz);
            }
        }

        @Override
        public boolean contains(int x, int y, int z, World world) {
            return chunk.contains(x, z)
                    && chunkWorld == world
                    && inner.contains(x, y, z, world);
        }

        @Override
        public void forEachBlock(final BlockAction action) {
            inner.forEachBlock(new BlockAction() {
                @Override
                public void apply(int x, int y, int z, World world) {
                    if (chunk.contains(x, z) && chunkWorld == world) {
                        action.apply(x, y, z, world);
                    }
                }
            });
        }
    }

    private static class UnionedSpace extends Space {

        private Space[] components;

        public UnionedSpace(Space[] components) {
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
        public Space within(ChunkPosition chunk) {
            Space[] limited = new Space[components.length];

            for (int i = 0; i < limited.length; ++i) {
                limited[i] = components[i].within(chunk);
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
    private static final Comparator<Block> BLOCK_COMPARATOR = new Comparator<Block>() {
        @Override
        public int compare(Block left, Block right) {
            if (left == right) {
                return 0;
            } else if (left == null) {
                return -1;
            } else if (right == null) {
                return 1;
            }

            int cmp = left.getX() - right.getX();
            if (cmp != 0) {
                return cmp;
            }

            cmp = left.getZ() - right.getZ();
            if (cmp != 0) {
                return cmp;
            }

            return left.getY() - right.getY();
        }
    };
}
