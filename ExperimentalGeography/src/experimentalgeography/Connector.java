/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
import static experimentalgeography.ExperimentalGeography.getChunkRandom;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
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
    private boolean flammable;
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
    private EnumSet solidColumn;

    public Connector(Chunk target, Location start, Location[] ends, Location[] surrounding) {
        this.target = Preconditions.checkNotNull(target);
        this.world = target.getWorld();
        this.biome = start.getBlock().getBiome();
        this.keyBiome = (int) Math.cbrt(this.biome.ordinal()); //for the purposes of doctoring internal generator settings
        this.start = Preconditions.checkNotNull(start);
        this.ends = Preconditions.checkNotNull(ends);
        this.surrounding = Preconditions.checkNotNull(surrounding);
        this.itemPickRandom = new Random();
        this.flammable = false;
        this.cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
        this.cornerData = 1;
        this.edgeBlocks = Material.SMOOTH_BRICK;
        this.edgeData = 1;
        this.wallBlocks = Material.SMOOTH_BRICK;
        this.wallData = 0;
        this.floorBlocks = Material.SMOOTH_BRICK;
        this.floorData = 2;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
        this.wallSpare = EnumSet.of(Material.AIR, Material.GLOWSTONE, Material.NETHER_BRICK, Material.GOLD_ORE, Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);
        this.floorSpare = EnumSet.of(Material.GLOWSTONE, Material.NETHER_BRICK, Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);
        this.hollowSpare = EnumSet.of(Material.AIR, Material.GLOWSTONE, Material.NETHER_BRICK, Material.MOB_SPAWNER, Material.ENDER_PORTAL_FRAME, Material.CHEST);
        this.solidColumn = EnumSet.of(Material.WEB, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.WOOD, Material.MONSTER_EGGS, Material.MOB_SPAWNER, Material.TNT);


    }

    /**
     * This method actually connects the start location to the ends, but updates only blocks in the pitBlock chunk. Here is where
     * we will grind out the tunnel variations.
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

            if (flammable == true) {
                if (block.getType() == Material.LAVA) {
                    block.setType(Material.GLOWSTONE);
                    //for flammable builds we are iterating through the entire chunk already:
                    //we will nuke all the lava source blocks while we're at it.
                }
            }

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
        double tunnelWidth = 1.32 + (keyBiome * 0.9); //finding somewhere to stick the size-modifier
        if (this.biome == Biome.HELL) {
            tunnelWidth = tunnelWidth * 1.3;
        }
        if (this.biome == Biome.SKY) {
            tunnelWidth = tunnelWidth * 0.9;
        }

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
        double bailout = 2.5;

        if (start.getBlock().getBiome().name().contains("MEGA")) {
            bailout = bailout - 0.5;
        }
        if (start.getBlock().getBiome().name().contains("S")) {
            bailout = bailout - 0.5;
        }
        if (start.getBlock().getBiome().name().contains("K")) {
            bailout = bailout - 0.5;
        }
        if (start.getBlock().getBiome().name().contains("Y")) {
            bailout = bailout - 0.4;
        }
        if (start.getBlock().getBiome().name().contains("HILLS")) {
            bailout = bailout + 1.1;
        }
        if (start.getBlock().getBiome().name().contains("MOUNTAINS")) {
            bailout = bailout + 1.4;
        }

        if (dist > 0.0) {
            int size = (int) (Math.cbrt(Math.max(0.1, 32 - dist)) * width);

            if (size > bailout) {
                return Space.linear(start, end, size, size);
            }
        }

        return Space.empty();
    }

    public void decorate() {
        Random random = new Random();

        if ((this.biome == biome.HELL) || (this.biome == biome.SKY) || this.biome.name().contains("ICE") || this.biome.name().contains("FROZEN")) {
            //bail without doing anything if we're in Nether or End or an Ice or Frozen biome
            return;
        }
        final int darkness = (int) ((this.biome.ordinal() * 0.26) + 30);
        double dist = 0.0;
        //distance between nodes has to be smaller than this to place features
        Material ceilingLight = Material.GLOWSTONE;
        Material floorFeature = Material.GLOWSTONE;
        //defaults for lighting
        if (this.biome.ordinal() < 25) {
            ceilingLight = Material.COAL_ORE;
            //noncrazy biomes don't get the glowstone ceiling lighting
        }
        if ((this.biome.ordinal() < 9) && (!this.biome.name().contains("OCEAN"))) {
            floorFeature = Material.NETHERRACK;
            //campfires in simple biomes, but not underwater which needs to be spookier
        }

        if ((this.biome.ordinal() > 20) && (random.nextInt(4) < 2)) {
            floorFeature = Material.IRON_BLOCK;
            //biomes that aren't real basic don't get the 'campfires' effect, but more iron loot
            //Stuff in between will get NO lighting beyond what you place, there's a 'dark zone'
            //This is not physical transition but defining a class of biome conditions some of which
            //may be used as border biomes
        }
        EntityType mobType = EntityType.CHICKEN;
        //these get overridden randomly. It's independent of the tunnel biome override as they are not just
        //assigning stuff according to biome, it's increasing the pool of possible outcomes according to the biome #

        int dangerZone = Math.max(1, this.biome.ordinal()) + 27;
        int randomIndex = itemPickRandom.nextInt(dangerZone);
        //from 7 swampland to 67 Mega Spruce Taiga Hills
        //our overrides are a big switch with the normal ones low and the crazy stuff high
        switch (randomIndex) {
            case 10:
                ceilingLight = Material.WATER;
                floorFeature = Material.AIR;
                //hole for a water stream to fall into
                break;
            case 24:
                ceilingLight = Material.LAVA;
                floorFeature = Material.AIR;
                break;
            case 7:
                floorFeature = Material.OBSIDIAN;
                //chest trigger
                break;
            case 34:
                ceilingLight = Material.OBSIDIAN;
                floorFeature = Material.TNT;
                //boobytrap chest
                break;

            case 9:
            case 27:
            case 32:
            case 35:
                floorFeature = Material.NETHERRACK;
                break;
            case 36:
            case 37:
                floorFeature = Material.COAL_BLOCK;
                break;
            case 8:
                floorFeature = Material.DIRT;
                //plant trees or try to
                break;

            case 25:
            case 26:
            case 44:
            case 45:
            case 46:
            case 47:
            case 54:
            case 55:
            case 56:
            case 57:
            case 80:
                if (itemPickRandom.nextInt(dangerZone) < 3) {
                    //bail-out on pillars too
                    switch ((int) (dangerZone / 7.5)) {
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                            ceilingLight = Material.WOOD;
                            floorFeature = Material.WOOD;
                            break;
                        case 4:
                        case 5:
                        case 6:
                            ceilingLight = Material.IRON_BLOCK;
                            floorFeature = Material.IRON_BLOCK;
                        case 7:
                            ceilingLight = Material.GOLD_BLOCK;
                            floorFeature = Material.GOLD_BLOCK;
                            break;
                        case 8:
                            ceilingLight = Material.EMERALD_BLOCK;
                            floorFeature = Material.EMERALD_BLOCK;
                            break;
                        case 9:
                            ceilingLight = Material.LAPIS_BLOCK;
                            floorFeature = Material.LAPIS_BLOCK;
                            break;
                        case 10:
                            ceilingLight = Material.TNT;
                            floorFeature = Material.TNT;
                            break;
                        case 11:
                        case 12:
                            ceilingLight = Material.DIAMOND_BLOCK;
                            floorFeature = Material.DIAMOND_BLOCK;
                            break;
                    }
                }
                //the pillars. They provide a guideline of what sort of hell awaits.
                break;

            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
                if (itemPickRandom.nextInt(dangerZone) < 3) {
                    ceilingLight = Material.MOB_SPAWNER;
                    floorFeature = Material.MOB_SPAWNER;
                    //the bail-out. Without it we have gradiated spawners approaching danger.
                    //With it, you can see the tower spawners.
                    //The 3 and under is less likely for high danger zones, more common for safer ones (smaller rnd pool)
                }

                switch ((int) (dangerZone / 7.5)) {
                    case 0:
                        mobType = EntityType.CHICKEN;
                        floorFeature = Material.MOB_SPAWNER;
                        break;
                    case 1:
                        mobType = EntityType.PIG;
                        floorFeature = Material.MOB_SPAWNER;
                        break;
                    case 2:
                        mobType = EntityType.COW;
                        floorFeature = Material.MOB_SPAWNER;
                        //these don't work without grass to spawn on: left as an exercise for the player
                        //grass blocks can be had from high level trapped chests if you're lucky, or make a trail
                        break;
                    case 3:
                        mobType = EntityType.ZOMBIE;
                        floorFeature = Material.MOB_SPAWNER;
                        break;
                    case 4:
                        mobType = EntityType.SKELETON;
                        floorFeature = Material.MOB_SPAWNER;
                        break;
                    case 5:
                        mobType = EntityType.CREEPER;
                        floorFeature = Material.MOB_SPAWNER;
                        break;
                    case 6:
                    case 7:
                        floorFeature = Material.MOB_SPAWNER;
                        mobType = EntityType.BLAZE;
                        break;
                    case 8:
                    case 9:
                        floorFeature = Material.MOB_SPAWNER;
                        mobType = EntityType.PIG_ZOMBIE;
                        break;
                    case 10:
                        floorFeature = Material.MOB_SPAWNER;
                        mobType = EntityType.GHAST;
                        //these you can see at a distance, somewhat
                        break;
                    case 11:
                    case 12:
                        mobType = EntityType.WITHER;
                        //not allowing witherspawns unless part of the cage match illuminated funbox
                        break;
                }
                //fountain of mobs! Can be nearly anything, so do NOT approach unwarily.
                //May need nerfing as this is a 'destroy EVERYTHING' trap.
                break;

            case 82:
            case 83:
            case 84:
            case 85:
            case 86:
            case 87:
            case 88:
            case 90:
                ceilingLight = Material.GLOWSTONE;
                floorFeature = Material.DIAMOND_BLOCK;
                //fight past all the monstrosity and there can be rewards
                //not lit, and beware the diamond block pillar, just saiyan
                break;
            case 28:
            case 29:
                floorFeature = Material.MOB_SPAWNER;
                int randomMobs = itemPickRandom.nextInt(4);
                switch (randomMobs) {
                    case 0:
                        mobType = EntityType.ZOMBIE;
                        break;
                    case 1:
                        mobType = EntityType.SKELETON;
                        break;
                    case 2:
                        mobType = EntityType.SPIDER;
                        break;
                    case 3:
                        mobType = EntityType.CREEPER;
                        break;
                }
                break;
            //our base random mob spawners
            case 38:
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.WITCH;
                break;
            case 39:
                ceilingLight = Material.ENDER_PORTAL;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.ENDERMAN;
                //the endermen come out of a portal to their world, scattered all over. Invisible but dangerous
                break;
            case 48:
                ceilingLight = Material.WEB;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.CAVE_SPIDER;
                //web column always means cavespider
                break;
            case 49:
                ceilingLight = Material.MONSTER_EGGS;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.SILVERFISH;
                break;
            case 60:
            case 61:
                ceilingLight = Material.SOUL_SAND;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.PIG_ZOMBIE;
                //all the nether guy spawners have little caches of netherwart on top
                break;
            case 68:
                ceilingLight = Material.SOUL_SAND;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.BLAZE;
                break;
            case 69:
            case 74:
                ceilingLight = Material.SOUL_SAND;
                floorFeature = Material.MOB_SPAWNER;
                mobType = EntityType.GHAST;
                break;
            case 81:
                floorFeature = Material.BEDROCK;
                //pit to void. Glowstone lights it. As common as wither
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
                while ((block.getType() == Material.AIR) && block.getY() < 200) {
                    block = block.getRelative(BlockFace.UP);
                }
                if (block.getY() > 190) {
                    block.getLocation().setY(60.0);
                    //if we didn't even hit a roof skip right back down to 60
                }
                block.setType(ceilingLight);
                if (ceilingLight == Material.SOUL_SAND) {
                    block = block.getRelative(BlockFace.UP);
                    block.setType(Material.NETHER_WARTS);
                    block = block.getRelative(BlockFace.DOWN);
                }

                if (solidColumn.contains(ceilingLight)) {
                    block = block.getRelative(BlockFace.DOWN);
                    while ((block.getType() == Material.AIR) && block.getY() > 0) {
                        block.setType(ceilingLight);
                        if (ceilingLight == Material.MOB_SPAWNER) {
                            BlockState blockState = block.getState();
                            CreatureSpawner spawner = ((CreatureSpawner) blockState);
                            spawner.setSpawnedType(mobType);
                            blockState.update();
                            if (dangerZone > 82.5) {
                                //we're doing a mob pillar and it is WITHER
                                //make a steel cage to look alarming
                                block.getRelative(1, 0, 0).setType(Material.IRON_FENCE);
                                block.getRelative(-1, 0, 0).setType(Material.IRON_FENCE);
                                block.getRelative(0, 0, 1).setType(Material.IRON_FENCE);
                                block.getRelative(0, 0, -1).setType(Material.IRON_FENCE);
                                block.getRelative(1, 0, 1).setType(Material.IRON_FENCE);
                                block.getRelative(1, 0, -1).setType(Material.IRON_FENCE);
                                block.getRelative(-1, 0, 1).setType(Material.IRON_FENCE);
                                block.getRelative(-1, 0, -1).setType(Material.IRON_FENCE);
                            }
                        }
                        block = block.getRelative(BlockFace.DOWN);
                    }
                    //make a pillar of these special materials to indicate what's there   
                    if ((ceilingLight == Material.MOB_SPAWNER) && (block.getY() > 3) && (dangerZone > 82.5)) {
                        //we just finished doing the wither spawner: make ultimate loot chest underneath
                        block.getRelative(0, 2, 0).setType(Material.TRAPPED_CHEST);
                        BlockState bs = block.getRelative(0, 2, 0).getState();
                        Chest chest = (Chest) bs;
                        for (int chestSlot = 0; chestSlot < 27; ++chestSlot) {
                            int typeID = random.nextInt((int) (Math.min(Math.pow(this.biome.ordinal(), 2), 166))) + 256;
                            ItemStack thisSlot = new ItemStack(typeID, 1);
                            int maxSize = thisSlot.getMaxStackSize();
                            if (maxSize == 0) {
                                maxSize = 1;
                            }
                            chest.getBlockInventory().setItem(chestSlot, new ItemStack(typeID, maxSize, (short) 0));
                        }
                        chest.update();
                        //finished the ultimate loot chest: maximum stacks of everything. Buried below the stack
                    }





                } else {
                    block = world.getBlockAt(x, y, z);
                    while ((block.getType() == Material.AIR) && block.getY() > 0) {
                        block = block.getRelative(BlockFace.DOWN);
                    }
                    //step down more quickly to the floor without placing anything
                }

                block.setType(floorFeature);
                if (floorFeature == Material.NETHERRACK) {
                    block.getRelative(0, 1, 0).setType(Material.FIRE);
                    //campfires
                }

                if (floorFeature == Material.DIRT) {
                    boolean treeSuccess;
                    if (this.biome.ordinal() < 30) {
                        treeSuccess = block.getWorld().generateTree(block.getRelative(0, 1, 0).getLocation(), TreeType.TREE);
                    } else {
                        treeSuccess = block.getWorld().generateTree(block.getRelative(0, 1, 0).getLocation(), TreeType.BIG_TREE);
                    }
                    if (treeSuccess) {
                        block.getRelative(1, 0, 0).setType(Material.GRASS);
                        block.getRelative(-1, 0, 0).setType(Material.GRASS);
                        block.getRelative(0, 0, 1).setType(Material.GRASS);
                        block.getRelative(0, 0, -1).setType(Material.GRASS);
                        block.getRelative(1, 0, 1).setType(Material.GRASS);
                        block.getRelative(1, 0, -1).setType(Material.GRASS);
                        block.getRelative(-1, 0, 1).setType(Material.GRASS);
                        block.getRelative(-1, 0, -1).setType(Material.GRASS);
                    }
                    //underground trees, little groves
                }


                if (floorFeature == Material.MOB_SPAWNER) {
                    if (ceilingLight == Material.MOB_SPAWNER) {
                        block.setType(Material.CHEST);
                        BlockState bs = block.getState();
                        Chest chest = (Chest) bs;
                        for (int chestSlot = 0; chestSlot < 27; ++chestSlot) {
                            int typeID = random.nextInt((int) (Math.min(Math.pow(this.biome.ordinal(), 2), 166))) + 256;
                            ItemStack thisSlot = new ItemStack(typeID, 1);
                            int maxSize = (int) Math.cbrt(thisSlot.getMaxStackSize());
                            maxSize = random.nextInt(maxSize + 1);
                            if (maxSize == 0) {
                                maxSize = 1;
                            }
                            //for the funbox chests, only the fancy stuff and more limited numbers
                            //this does omit command blocks
                            chest.getBlockInventory().setItem(chestSlot, new ItemStack(typeID, maxSize, (short) 0));
                        }
                        chest.update();
                        //if we have a tower, put a loot chest at the base
                    } else {
                        BlockState blockState = block.getState();
                        CreatureSpawner spawner = ((CreatureSpawner) blockState);
                        spawner.setSpawnedType(mobType);
                        blockState.update();
                        if (((mobType == EntityType.COW) || (mobType == EntityType.PIG) || (mobType == EntityType.CHICKEN)) && (this.biome.ordinal() < 30)) {
                            block.getRelative(1, 0, 0).setType(Material.GRASS);
                            block.getRelative(-1, 0, 0).setType(Material.GRASS);
                            block.getRelative(0, 0, 1).setType(Material.GRASS);
                            block.getRelative(0, 0, -1).setType(Material.GRASS);
                            block.getRelative(1, 0, 1).setType(Material.GRASS);
                            block.getRelative(1, 0, -1).setType(Material.GRASS);
                            block.getRelative(-1, 0, 1).setType(Material.GRASS);
                            block.getRelative(-1, 0, -1).setType(Material.GRASS);
                            //food mobs need to spawn on grass, but only simpler biomes will place it directly
                        }
                        if ((mobType == EntityType.CREEPER) && (this.biome.ordinal() > 40)) {
                            block.getRelative(1, 0, 0).setType(Material.TNT);
                            block.getRelative(-1, 0, 0).setType(Material.TNT);
                            block.getRelative(0, 0, 1).setType(Material.TNT);
                            block.getRelative(0, 0, -1).setType(Material.TNT);
                            block.getRelative(1, 0, 1).setType(Material.TNT);
                            block.getRelative(1, 0, -1).setType(Material.TNT);
                            block.getRelative(-1, 0, 1).setType(Material.TNT);
                            block.getRelative(-1, 0, -1).setType(Material.TNT);
                            //creepers spawn on TNT for added WTF in tougher biomes
                        }
                    }
                }
                if ((floorFeature == Material.TNT) && (ceilingLight == Material.OBSIDIAN)) {
                    block.getRelative(0, 1, 0).setType(Material.TRAPPED_CHEST);
                    BlockState bs = block.getRelative(0, 1, 0).getState();
                    Chest chest = (Chest) bs;
                    for (int chestSlot = 0; chestSlot < 27; ++chestSlot) {
                        int typeID = random.nextInt((int) (Math.min(Math.pow(this.biome.ordinal(), 2), 166))) + 256;
                        ItemStack thisSlot = new ItemStack(typeID, 1);
                        int maxSize = (int) Math.cbrt(thisSlot.getMaxStackSize());
                        maxSize = random.nextInt(maxSize + 1);
                        if (maxSize == 0) {
                            maxSize = 1;
                        }
                        //for the trapped chests, only the fancy stuff and more limited numbers
                        //this does omit command blocks
                        chest.getBlockInventory().setItem(chestSlot, new ItemStack(typeID, maxSize, (short) 0));
                    }
                    chest.update();
                    if (this.biome.ordinal() > 10) {
                        block.getRelative(0, -1, 0).setType(Material.TNT);
                    }
                    if (this.biome.ordinal() > 20) {
                        block.getRelative(0, -2, 0).setType(Material.TNT);
                    }
                    if (this.biome.ordinal() > 30) {
                        block.getRelative(0, -3, 0).setType(Material.TNT);
                    }
                    if (this.biome.ordinal() > 40) {
                        block.getRelative(0, -4, 0).setType(Material.TNT);
                        block.getRelative(1, -2, 0).setType(Material.TNT);
                        block.getRelative(-1, -2, 0).setType(Material.TNT);
                        block.getRelative(0, -2, 1).setType(Material.TNT);
                        block.getRelative(0, -2, -1).setType(Material.TNT);
                        block.getRelative(1, -2, 1).setType(Material.TNT);
                        block.getRelative(1, -2, -1).setType(Material.TNT);
                        block.getRelative(-1, -2, 1).setType(Material.TNT);
                        block.getRelative(-1, -2, -1).setType(Material.TNT);
                    }
                    if (this.biome.ordinal() > 50) {
                        block.getRelative(1, -3, 0).setType(Material.TNT);
                        block.getRelative(-1, -3, 0).setType(Material.TNT);
                        block.getRelative(0, -3, 1).setType(Material.TNT);
                        block.getRelative(0, -3, -1).setType(Material.TNT);
                        block.getRelative(1, -3, 1).setType(Material.TNT);
                        block.getRelative(1, -3, -1).setType(Material.TNT);
                        block.getRelative(-1, -3, 1).setType(Material.TNT);
                        block.getRelative(-1, -3, -1).setType(Material.TNT);
                        block.getRelative(1, -4, 0).setType(Material.TNT);
                        block.getRelative(-1, -4, 0).setType(Material.TNT);
                        block.getRelative(0, -4, 1).setType(Material.TNT);
                        block.getRelative(0, -4, -1).setType(Material.TNT);
                        block.getRelative(1, -4, 1).setType(Material.TNT);
                        block.getRelative(1, -4, -1).setType(Material.TNT);
                        block.getRelative(-1, -4, 1).setType(Material.TNT);
                        block.getRelative(-1, -4, -1).setType(Material.TNT);
                    }
                    //boom! that gets worse as you go to more dangerous areas.
                }
                if (floorFeature == Material.OBSIDIAN) {
                    block.getRelative(0, 1, 0).setType(Material.CHEST);
                    BlockState bs = block.getRelative(0, 1, 0).getState();
                    Chest chest = (Chest) bs;
                    for (int chestSlot = 0; chestSlot < 27; ++chestSlot) {
                        int typeID = random.nextInt((int) Math.max(Math.min(Math.pow(this.biome.ordinal(), 2), 422), 1));
                        //exclusive of the command block minecart this way
                        if (typeID == 0) {
                            typeID = 267;
                        }
                        if (typeID == 1) {
                            typeID = 4;
                        }
                        if (typeID == 2) {
                            typeID = 50;
                        }
                        if (typeID == 3) {
                            typeID = 364;
                        }
                        ItemStack thisSlot = new ItemStack(typeID, 1);
                        if (typeID != 137) {
                            //maybe if you tell it a bad ID it will be a null? I dunno.
                            int maxSize = 1;
                            //non-trapped chests have single items and a broader range of possible items
                            chest.getBlockInventory().setItem(chestSlot, new ItemStack(typeID, maxSize, (short) 0));
                        }
                    }
                    chest.update();
                }
                if (floorFeature == Material.BEDROCK) {
                    Location locationBuffer = block.getLocation();

                    Block pitBlock = locationBuffer.getBlock();
                    while (pitBlock.getLocation().getY() > 0) {
                        pitBlock.setType(Material.AIR);
                        pitBlock.getRelative(1, 0, 0).setType(Material.AIR);
                        pitBlock.getRelative(-1, 0, 0).setType(Material.AIR);
                        pitBlock.getRelative(0, 0, 1).setType(Material.AIR);
                        pitBlock.getRelative(0, 0, -1).setType(Material.AIR);
                        pitBlock.getRelative(1, 0, 1).setType(Material.AIR);
                        pitBlock.getRelative(1, 0, -1).setType(Material.AIR);
                        pitBlock.getRelative(-1, 0, 1).setType(Material.AIR);
                        pitBlock.getRelative(-1, 0, -1).setType(Material.AIR);
                        //the hole
                        pitBlock.getRelative(-2, 0, -1).setType(Material.BEDROCK);
                        pitBlock.getRelative(-2, 0, 0).setType(Material.BEDROCK);
                        pitBlock.getRelative(-2, 0, 1).setType(Material.BEDROCK);
                        pitBlock.getRelative(-1, 0, -2).setType(Material.BEDROCK);
                        pitBlock.getRelative(0, 0, -2).setType(Material.BEDROCK);
                        pitBlock.getRelative(1, 0, -2).setType(Material.BEDROCK);
                        pitBlock.getRelative(-1, 0, 2).setType(Material.BEDROCK);
                        pitBlock.getRelative(0, 0, 2).setType(Material.BEDROCK);
                        pitBlock.getRelative(1, 0, 2).setType(Material.BEDROCK);
                        pitBlock.getRelative(2, 0, -1).setType(Material.BEDROCK);
                        pitBlock.getRelative(2, 0, 0).setType(Material.BEDROCK);
                        pitBlock.getRelative(2, 0, 1).setType(Material.BEDROCK);
                        //the walls
                        pitBlock.getRelative(-2, 0, -2).setType(Material.GLOWSTONE);
                        pitBlock.getRelative(-2, 0, 2).setType(Material.GLOWSTONE);
                        pitBlock.getRelative(2, 0, -2).setType(Material.GLOWSTONE);
                        pitBlock.getRelative(2, 0, 2).setType(Material.GLOWSTONE);
                        //decoration makes this awful thing a prize
                        pitBlock = pitBlock.getRelative(BlockFace.DOWN);
                    }
                    pitBlock.setType(Material.AIR);
                    pitBlock.getRelative(1, 0, 0).setType(Material.AIR);
                    pitBlock.getRelative(-1, 0, 0).setType(Material.AIR);
                    pitBlock.getRelative(0, 0, 1).setType(Material.AIR);
                    pitBlock.getRelative(0, 0, -1).setType(Material.AIR);
                    pitBlock.getRelative(1, 0, 1).setType(Material.AIR);
                    pitBlock.getRelative(1, 0, -1).setType(Material.AIR);
                    pitBlock.getRelative(-1, 0, 1).setType(Material.AIR);
                    pitBlock.getRelative(-1, 0, -1).setType(Material.AIR);
                    pitBlock.getRelative(-2, 0, -2).setType(Material.DIAMOND_BLOCK);
                    pitBlock.getRelative(-2, 0, 2).setType(Material.DIAMOND_BLOCK);
                    pitBlock.getRelative(2, 0, -2).setType(Material.DIAMOND_BLOCK);
                    pitBlock.getRelative(2, 0, 2).setType(Material.DIAMOND_BLOCK);
                    //diamond down near void. Don't be greedy!
                }
            }
        }
    }

    /**
     * This method assigns the wall surfaces.
     */
    private void assignWallSurfaces(Biome biome) {
        Random random = new Random();
        switch (biome) {
            case HELL:
                cornerBlocks = Material.NETHER_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.NETHER_BRICK;
                edgeData = 0;
                wallBlocks = Material.NETHERRACK;
                wallData = 0;
                floorBlocks = Material.NETHERRACK;
                floorData = 0;
                //floors are destroyable by ghast, offering some danger
                break;
            case SKY:
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 3;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 3;
                wallBlocks = Material.STAINED_GLASS;
                wallData = 15;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
                break;

            case EXTREME_HILLS:
            case EXTREME_HILLS_MOUNTAINS:
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
            case EXTREME_HILLS_PLUS:
            case EXTREME_HILLS_PLUS_MOUNTAINS:
                cornerBlocks = Material.TNT;
                cornerData = 0;
                edgeBlocks = Material.STAINED_GLASS;
                edgeData = 15;
                wallBlocks = Material.STAINED_GLASS;
                wallData = 15;
                floorBlocks = Material.STAINED_GLASS;
                floorData = 0;
                if (random.nextInt(4) == 1) {
                    edgeBlocks = Material.TNT;
                    edgeData = 0;
                }
                flammable = true;
                //hehehehehehehehe
                break;

            case FLOWER_FOREST:
                cornerBlocks = Material.WOOL;
                cornerData = (byte) random.nextInt(16);
                edgeBlocks = Material.WOOL;
                edgeData = (byte) random.nextInt(16);
                wallBlocks = Material.WOOL;
                wallData = (byte) random.nextInt(16);
                floorBlocks = Material.WOOL;
                floorData = (byte) random.nextInt(16);
                flammable = true;
                //flammable
                break;

            //forest, plains get to be our default nice looking stonebrick

            case JUNGLE:
            case JUNGLE_HILLS:
            case JUNGLE_EDGE:
            case JUNGLE_MOUNTAINS:
            case JUNGLE_EDGE_MOUNTAINS:
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.MOSSY_COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.COBBLESTONE;
                floorData = 0;
                //jungle is cobble
                break;

            case ROOFED_FOREST:
            case ROOFED_FOREST_MOUNTAINS:
                cornerBlocks = Material.LOG_2;
                cornerData = 9;
                edgeBlocks = Material.LOG_2;
                edgeData = 9;
                wallBlocks = Material.LEAVES_2;
                wallData = 5;
                floorBlocks = Material.GRASS;
                floorData = 0;
                flammable = true;
                //under roofed forest is wooden caves
                break;
            case SWAMPLAND:
            case SWAMPLAND_MOUNTAINS:
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.MOSSY_COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.GRASS;
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

            case COLD_BEACH:
                cornerBlocks = Material.GLOWSTONE;
                cornerData = 0;
                edgeBlocks = Material.GLOWSTONE;
                edgeData = 0;
                wallBlocks = Material.PACKED_ICE;
                wallData = 0;
                floorBlocks = Material.PACKED_ICE;
                floorData = 0;
                //trapped in ice caves, glowstone marks the exits
                break;

            case FROZEN_OCEAN:
            case FROZEN_RIVER:
            case ICE_PLAINS:
            case ICE_MOUNTAINS:
            case COLD_TAIGA:
            case COLD_TAIGA_HILLS:
            case ICE_PLAINS_SPIKES:
            case COLD_TAIGA_MOUNTAINS:
                cornerBlocks = Material.PACKED_ICE;
                cornerData = 0;
                edgeBlocks = Material.PACKED_ICE;
                edgeData = 0;
                wallBlocks = Material.PACKED_ICE;
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
                floorBlocks = Material.GRASS;
                floorData = 0;
                //mushroom islands and roofed forest are made of mushroom
                break;


            case BEACH:
            case RIVER:
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = (byte) random.nextInt(2);
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = (byte) random.nextInt(2);
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 1;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
                //under water the bricks are mossy because water. Beach is near water.
                break;

            case OCEAN:
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
                cornerBlocks = Material.QUARTZ_BLOCK;
                cornerData = 1;
                edgeBlocks = Material.QUARTZ_BLOCK;
                edgeData = 2;
                wallBlocks = Material.QUARTZ_BLOCK;
                wallData = 0;
                floorBlocks = Material.QUARTZ_BLOCK;
                floorData = 3;
                break;
            //bryce counter to expectation is quartz halls

            case SAVANNA_PLATEAU:
            case MESA_PLATEAU_FOREST:
            case MESA_PLATEAU:
            case SAVANNA_PLATEAU_MOUNTAINS:
            case MESA_PLATEAU_FOREST_MOUNTAINS:
            case MESA_PLATEAU_MOUNTAINS:
                cornerBlocks = Material.COAL_BLOCK;
                cornerData = 0;
                edgeBlocks = Material.COAL_BLOCK;
                edgeData = 0;
                wallBlocks = Material.COAL_BLOCK;
                wallData = 0;
                floorBlocks = Material.COAL_BLOCK;
                floorData = 0;
                flammable = true;
                break;
            //plateaus are coal quarries          

            case BIRCH_FOREST_MOUNTAINS:
            case BIRCH_FOREST_HILLS_MOUNTAINS:
                cornerBlocks = Material.LOG;
                cornerData = 14;
                edgeBlocks = Material.LOG;
                edgeData = 14;
                wallBlocks = Material.LOG;
                wallData = 14;
                floorBlocks = Material.WOOD;
                floorData = 2;
                flammable = true;
                break;
            //birch forest is halls of birch wood

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
