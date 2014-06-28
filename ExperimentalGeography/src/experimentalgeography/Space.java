/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import com.google.common.collect.Sets;
import java.util.*;
import javax.print.attribute.standard.Destination;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 *
 * @author DanJ
 */
public abstract class Space {

    public static Space linear(Location start, Location end, int size) {
        return new LinearSpace(start, end, size);
    }

    public Space within(final ChunkPosition chunk) {
        final Space innerSpace = this;
        final World chunkWorld = chunk.getWorld();

        return new Space() {
            @Override
            public void forEachBlock(final BlockAction action) {
                innerSpace.forEachBlock(new BlockAction() {
                    @Override
                    public void appy(int x, int y, int z, World world) {
                        if (chunk.contains(x, z) && chunkWorld == world) {
                            action.appy(x, y, z, world);
                        }
                    }
                });
            }
        };
    }

    public final void fill(Material material) {
        fill(material, Collections.<Material>emptySet());
    }

    public final void fill(final Material material, Material... toSpare) {
        fill(material, Arrays.asList(toSpare));
    }

    public final void fill(final Material material, final Collection<Material> toSpare) {
        forEachBlock(new BlockAction() {
            @Override
            public void appy(int x, int y, int z, World world) {
                Block block = world.getBlockAt(x, y, z);

                if (!toSpare.contains(block.getType())) {
                    block.setType(material);
                }
            }
        });
    }

    public final Set<Block> getBlocks() {
        Set<Block> blocks = Sets.newTreeSet(BLOCK_COMPARATOR);
        addBlocksTo(blocks);
        return blocks;
    }

    public final void addBlocksTo(final Collection<Block> action) {
        forEachBlock(new BlockAction() {
            @Override
            public void appy(int x, int y, int z, World world) {
                Block block = world.getBlockAt(x, y, z);
                action.add(block);
            }
        });
    }

    public abstract void forEachBlock(BlockAction action);

    private static final class LinearSpace extends Space {

        private final Location start, end;
        private final int size;

        public LinearSpace(Location start, Location end, int size) {
            this.start = start.clone();
            this.end = end.clone();
            this.size = size;
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

                for (int dx = 0; dx < size; ++dx) {
                    for (int dy = 0; dy < size; ++dy) {
                        for (int dz = 0; dz < size; ++dz) {
                            int bx = x + dx - (size / 2);
                            int by = y + dy;
                            int bz = z + dz - (size / 2);

                            action.appy(bx, by, bz, world);
                        }
                    }
                }
            }
        }
    }
    public static final Comparator<Block> BLOCK_COMPARATOR = new Comparator<Block>() {
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

    public interface BlockAction {

        void appy(int x, int y, int z, World world);
    }
}
