/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.EntityType;

/**
 * This class contains the logic to populate a specific chunk with our content; there's a 'start' location somewhere in the chunk,
 * and then we also get the start locations for adjacent chunks; these are the 'ends', and we connect to 'start' to each 'end'.
 *
 * @author DanJ
 */
public final class Connector {

    private final Chunk target;
    private final World world;
    private final Biome biome;
    private final int keyBiome;
    private final Location start;
    private final Location[] ends;
    private final Location[] surrounding;
    private final Random itemPickRandom;
    private Material cornerBlocks;
    private byte cornerData;
    private Material edgeBlocks;
    private byte edgeData;
    private Material wallBlocks;
    private byte wallData;
    private Material floorBlocks;
    private byte floorData;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
    private EnumSet wallSpare;
    private EnumSet floorSpare;
    private EnumSet hollowSpare;

    public Connector(Chunk target, Location start, Location[] ends, Location[] surrounding) {
        this.target = Preconditions.checkNotNull(target);
        this.world = target.getWorld();
        this.biome = start.getBlock().getBiome();
        this.keyBiome = (int) Math.cbrt(this.biome.ordinal()); //for the purposes of doctoring internal generator settings
        this.start = Preconditions.checkNotNull(start);
        this.ends = Preconditions.checkNotNull(ends);
        this.surrounding = Preconditions.checkNotNull(surrounding);
        this.itemPickRandom = new Random();
        this.cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
        this.cornerData = 1;
        this.edgeBlocks = Material.SMOOTH_BRICK;
        this.edgeData = 1;
        this.wallBlocks = Material.SMOOTH_BRICK;
        this.wallData = 0;
        this.floorBlocks = Material.SMOOTH_BRICK;
        this.floorData = 2;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
        this.wallSpare = EnumSet.of(Material.AIR, Material.GLOWSTONE, Material.GOLD_ORE, Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);
        this.floorSpare = EnumSet.of(Material.GLOWSTONE, Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);
        this.hollowSpare = EnumSet.of(Material.AIR, Material.GLOWSTONE, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);


    }

    /**
     * This method actually connects the start location to the ends, but updates only blocks in the target chunk. Here is where we
     * will grind out the tunnel variations.
     */
    public void connect() {
        Space space = getConnectedSpace();

        Space inTarget = space.withinChunk(target);
        //what is left when replacing blocks
        assignWallSurfaces(target.getBlock(8, 8, 8).getBiome());
        //for this tunnel, what materials are being used
        for (Block block : inTarget.getBlocks()) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            if (space.contains(x, y, z, world)) {
                //we are in the area being turned to tunnels

                int interiorNeighborCount =
                        (space.contains(x, y - 1, z, world) ? 1 : 0)
                        + (space.contains(x, y + 1, z, world) ? 1 : 0)
                        + (space.contains(x + 1, y, z, world) ? 1 : 0)
                        + (space.contains(x - 1, y, z, world) ? 1 : 0)
                        + (space.contains(x, y, z + 1, world) ? 1 : 0)
                        + (space.contains(x, y, z - 1, world) ? 1 : 0);
                //we have four basic conditions represented by number of neighbor also-tunnel blocks
                if (interiorNeighborCount == 3) {
                    //we are a corner somewhere, cracked like the edges
                    block.setType(cornerBlocks);
                    block.setData(cornerData);
                }
                if (interiorNeighborCount == 4) {
                    //we are a horizontal edge of some kind
                    block.setType(edgeBlocks);
                    block.setData(edgeData);
                }
                if (interiorNeighborCount == 5) {
                    if (space.contains(x, y - 1, z, world)) {
                        //we are not a floor section
                        if (!wallSpare.contains(block.getType())) {
                            //contains air. We will not make unnecessary wall sections or wipe out good ores
                            block.setType(wallBlocks);
                            block.setData(wallData);
                        }
                    } else {
                        //there isn't a block under us, we're the floor
                        //stone brick cracked from foot traffic
                        if (!floorSpare.contains(block.getType())) {
                            block.setType(floorBlocks);
                            block.setData(floorData);
                        }
                    }
                }
                if (interiorNeighborCount > 5) {
                    //air inside the catacombs and tunnels is surrounded by other tunnel blocks
                    if (!hollowSpare.contains(block.getType())) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * This method returns the space occupied by the corridors; it is the union of all spaces connecting the start to the ends,
     * and the ends to each other in a loop.
     *
     * @return The space to contain our corridors.
     */
    private Space getConnectedSpace() {
        Space space = Space.empty();
        double tunnelWidth = 1.4 + keyBiome; //finding somewhere to stick the size-modifier

        for (Location end : ends) {
            space = space.union(getConnectingSpace(start, end, tunnelWidth));
        }

        for (int i = 0; i < surrounding.length; ++i) {
            int nextIndex = (i + 1) % surrounding.length;
            space = space.union(getConnectingSpace(surrounding[i], surrounding[nextIndex], tunnelWidth));
        }
        return space;
    }

    /**
     * This method returns the space that connects two points; if the distance between them is too great, this may simply return
     * an empty space to indicate that there's no connection.
     *
     * @param start The starting point of the space.
     * @param end The ending point of the space.
     * @return The space that connects these points, or an empty space.
     */
    private static Space getConnectingSpace(Location start, Location end, double width) {
        double dist = start.distance(end);

        if (dist > 0.0) {
            int size = (int) (Math.cbrt(Math.max(0, 32 - dist)) * width);

            if (size > 1) {
                return Space.linear(start, end, size, size);
            }
        }

        return Space.empty();
    }

    public void decorate() {
        final int darkness = (int) (this.biome.ordinal() + 30);
        double dist = 0.0;
        //distance between nodes has to be smaller than this to place features
        Material ceilingLight = Material.GLOWSTONE;
        Material floorFeature = Material.MOB_SPAWNER;
        EntityType mobType = EntityType.ZOMBIE;
        //these get overridden randomly. It's independent of the tunnel biome override as they are not just
        //assigning stuff according to biome, it's increasing the pool of possible outcomes according to the biome #

        int randomIndex = itemPickRandom.nextInt(Math.max(1, this.biome.ordinal()) + 25);
        //from 7 swampland to 67 Mega Spruce Taiga Hills
        //our overrides are a big switch with the normal ones low and the crazy stuff high
        switch (randomIndex) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 20:
            case 30:
            case 40:
            case 50:
            case 11:
            case 21:
            case 31:
            case 41:
            case 51:
                floorFeature = Material.WATER;
                break;
            case 12:
            case 22:
            case 32:
            case 42:
            case 52:
            case 13:
            case 23:
            case 33:
            case 43:
            case 53:
                floorFeature = Material.LAVA;
                break;
            case 14:
            case 24:
            case 34:
            case 15:
            case 25:
            case 35:
            case 44:
            case 54:
            case 45:
            case 55:
                floorFeature = Material.IRON_BLOCK;
                break;
            case 16:
            case 26:
            case 17:
            case 27:
                floorFeature = Material.COAL_BLOCK;
                break;
            case 36:
            case 37:
            case 46:
            case 47:
                floorFeature = Material.GOLD_BLOCK;
                break;
            case 56:
            case 57:
            case 75:
            case 76:
            case 77:
            case 78:
            case 79:
                floorFeature = Material.DIAMOND_BLOCK;
                //fight past all the monstrosity and there can be rewards
                break;

            case 18:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.ZOMBIE;
                break;
            case 19:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.CREEPER;
                break;
            case 28:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.SKELETON;
                break;
            case 29:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.SPIDER;
                break;
            case 38:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.WITCH;
                break;
            case 39:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.ENDERMAN;
                break;
            case 48:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.CAVE_SPIDER;
                break;
            case 49:
            case 58:
            case 59:
            case 60:
            case 61:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.BLAZE;
                break;
            case 62:
            case 63:
            case 64:
            case 65:
            case 66:
            case 67:
            case 68:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.GHAST;
                break;
            case 80:
            case 81:
            case 82:
            case 83:
            case 84:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
            case 90:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.WITHER;
                //may be unreachable, but yowza!
                break;
        }



        for (Location end : ends) {
            dist = Math.max(dist, start.distance(end));
        }
        if (dist < darkness) {
            //here we tack on the glowstone ceiling lights, and/or call the spawner/loot math
            int x = (int) start.getX();
            int y = (int) (start.getY() + keyBiome);
            int z = (int) start.getZ();
            Block block;
            if (ChunkPosition.of(target).contains(x, z)) {
                block = world.getBlockAt(x, y, z);
                while ((block.getType() == Material.AIR) && block.getY() < 255) {
                    block = block.getRelative(BlockFace.UP);
                }
                block.setType(ceilingLight);
                block = world.getBlockAt(x, y, z);
                while ((block.getType() == Material.AIR) && block.getY() > 0) {
                    block = block.getRelative(BlockFace.DOWN);
                }
                block.setType(floorFeature);
                if (floorFeature == Material.MOB_SPAWNER) {
                    BlockState blockState = block.getState();
                    CreatureSpawner spawner = ((CreatureSpawner) blockState);
                    spawner.setSpawnedType(mobType);
                    blockState.update();
                }
            }
        }
    }

    /**
     * This method assigns the wall surfaces.
     */
    private void assignWallSurfaces(Biome biome) {

        switch (biome) {

            case HELL:
                cornerBlocks = Material.NETHER_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.NETHER_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;

                break;
            case SKY:
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 8;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled

                break;

            case EXTREME_HILLS:
            case EXTREME_HILLS_PLUS:
            case EXTREME_HILLS_MOUNTAINS:
            case EXTREME_HILLS_PLUS_MOUNTAINS:
                cornerBlocks = Material.AIR;
                cornerData = 0;
                edgeBlocks = Material.AIR;
                edgeData = 0;
                wallBlocks = Material.AIR;
                wallData = 0;
                floorBlocks = Material.AIR;
                floorData = 0;
                //Extreme hills gives just caves, linked. expands spaces, breaks up other biome tunnels.
                //Weird versions give HUGE bare caves.
                break;

            case FOREST:
            case FOREST_HILLS:
            case PLAINS:
                cornerBlocks = Material.COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                //forest, plains have cobble




                break;
            case JUNGLE:
            case JUNGLE_HILLS:
            case JUNGLE_EDGE:
            case JUNGLE_MOUNTAINS:
            case JUNGLE_EDGE_MOUNTAINS:
                cornerBlocks = Material.COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.COBBLESTONE;
                floorData = 0;
                //jungle is cobble
                break;

            case ROOFED_FOREST:
            case SWAMPLAND:
            case SWAMPLAND_MOUNTAINS:
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.MOSSY_COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.MOSSY_COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.MOSSY_COBBLESTONE;
                floorData = 0;
                //under swamp is mossy cobble
                break;

            case DESERT:
            case DESERT_HILLS:
                cornerBlocks = Material.SANDSTONE;
                cornerData = 1;
                edgeBlocks = Material.SANDSTONE;
                edgeData = 2;
                wallBlocks = Material.SANDSTONE;
                wallData = 0;
                floorBlocks = Material.SANDSTONE;
                floorData = 2;
                //deserts are made of sandstone
                break;

            case FROZEN_OCEAN:
            case FROZEN_RIVER:
            case ICE_PLAINS:
            case ICE_MOUNTAINS:
            case COLD_BEACH:
            case COLD_TAIGA:
            case COLD_TAIGA_HILLS:
            case ICE_PLAINS_SPIKES:
            case COLD_TAIGA_MOUNTAINS:
                cornerBlocks = Material.PACKED_ICE;
                cornerData = 0;
                edgeBlocks = Material.PACKED_ICE;
                edgeData = 0;
                wallBlocks = Material.ICE;
                wallData = 0;
                floorBlocks = Material.PACKED_ICE;
                floorData = 0;
                //cold places are ice caves
                break;

            case MUSHROOM_ISLAND:
            case MUSHROOM_SHORE:
                cornerBlocks = Material.HUGE_MUSHROOM_2;
                cornerData = 14;
                edgeBlocks = Material.HUGE_MUSHROOM_2;
                edgeData = 14;
                wallBlocks = Material.HUGE_MUSHROOM_2;
                wallData = 15;
                floorBlocks = Material.HUGE_MUSHROOM_2;
                floorData = 5;
                //mushroom islands and roofed forest are made of mushroom
                break;


            case BEACH:
            case OCEAN:
            case RIVER:
                cornerBlocks = Material.SMOOTH_BRICK;
                cornerData = 1;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 1;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 1;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
                //under water the floor is mossy because water. Beach is near water.
                break;

            case DEEP_OCEAN:
                cornerBlocks = Material.SANDSTONE;
                cornerData = 1;
                edgeBlocks = Material.SANDSTONE;
                edgeData = 2;
                wallBlocks = Material.STAINED_GLASS;
                wallData = 15;
                floorBlocks = Material.SANDSTONE;
                floorData = 2;
                //under deep ocean we have black glass ceilings and sandstone
                break;

            case MESA_BRYCE:
            case MESA_PLATEAU_FOREST_MOUNTAINS:
            case MESA_PLATEAU_MOUNTAINS:
            case BIRCH_FOREST_MOUNTAINS:
            case BIRCH_FOREST_HILLS_MOUNTAINS:
            case ROOFED_FOREST_MOUNTAINS:
            case MEGA_SPRUCE_TAIGA:
            case MEGA_SPRUCE_TAIGA_HILLS:
                cornerBlocks = Material.QUARTZ_BLOCK;
                cornerData = 1;
                edgeBlocks = Material.QUARTZ_BLOCK;
                edgeData = 2;
                wallBlocks = Material.STAINED_GLASS;
                wallData = 15;
                floorBlocks = Material.NETHERRACK;
                floorData = 0;
                //insanely huge places with no other ID get a crazy looking treatment
                //everything from Bryce up, except Extreme Hills are bare/empty and thus not here
                break;

            default:
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 1;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 1;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 2;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
            //our base design, stonebrick with the edges mossy and the floor cracked

        }

    }
}
