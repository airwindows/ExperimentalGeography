/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import com.google.common.base.*;
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
    private final Location start;
    private final Location[] ends;
    private final Location[] surrounding;
    private final int surface;
    private Material cornerBlocks;
    private byte cornerData;
    private Material edgeBlocks;
    private byte edgeData;
    private Material wallBlocks;
    private byte wallData;
    private Material floorBlocks;
    private byte floorData;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled

    public Connector(Chunk target, Location start, Location[] ends, Location[] surrounding, int surface) {
        this.target = Preconditions.checkNotNull(target);
        this.world = target.getWorld();
        this.biome = start.getBlock().getBiome();
        this.start = Preconditions.checkNotNull(start);
        this.ends = Preconditions.checkNotNull(ends);
        this.surrounding = Preconditions.checkNotNull(surrounding);
        this.surface = surface;
        this.cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
        this.cornerData = 0;
        this.edgeBlocks = Material.SMOOTH_BRICK;
        this.edgeData = 0;
        this.wallBlocks = Material.SMOOTH_BRICK;
        this.wallData = 0;
        this.floorBlocks = Material.SMOOTH_BRICK;
        this.floorData = 0;  //stone brick data 0=plain 1=mossy 2=cracked 3=chiseled
    }

    /**
     * This method actually connects the start location to the ends, but updates only blocks in the pitBlock chunk. Here is where
     * we will grind out the tunnel variations.
     */
    public void connect() {
        Space space = getConnectedSpace();
        Random random = new Random();
        Space inTarget = space.withinChunk(target);
        //what is left when replacing blocks
        Block centerBlock = target.getBlock(8, 8, 8);
        assignWallSurfaces(centerBlock.getBiome());
        //for this tunnel, what materials are being used

        int spawnDistance = (int) Math.cbrt(centerBlock.getLocation().distance(this.world.getSpawnLocation()));
        int lightingFactor = (int) Math.cbrt((this.biome.ordinal()));
        int lootFactor = (int) Math.cbrt(this.biome.ordinal());
        int lootBoost = (int) Math.cbrt(this.biome.ordinal());
        //base commonness of lights and chests: higher is sparser. Down low, it's darker but there are still chests

        int chiaroscuro = (target.hashCode() % 10) - 5;
        //we have a plus-minus factor that's random-ish
        lightingFactor += chiaroscuro;
        lootFactor -= (chiaroscuro * 3);
        //These trend oppositely: to find more chests, go to where it's darker. Positive chiaroscuro means better loot, negative means safe and boring

        lightingFactor = Math.abs(lightingFactor) + spawnDistance;

        if (lootFactor < spawnDistance) {
            lootBoost -= (lootFactor - spawnDistance);
            //the more below 11 lootFactor goes, the more we increase lootBoost
        }
        lootFactor = Math.abs(lootFactor) + 11 + (int) Math.sqrt(spawnDistance);
        //We prevent the numbers from getting silly by limiting them here.

        for (Block block : inTarget.getBlocks()) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            if (space.contains(x, y, z, world)) {
                //we are in the area being turned to tunnels
                int interiorNeighborCount
                        = (space.contains(x, y - 1, z, world) ? 1 : 0)
                        + (space.contains(x, y + 1, z, world) ? 1 : 0)
                        + (space.contains(x + 1, y, z, world) ? 1 : 0)
                        + (space.contains(x - 1, y, z, world) ? 1 : 0)
                        + (space.contains(x, y, z + 1, world) ? 1 : 0)
                        + (space.contains(x, y, z - 1, world) ? 1 : 0);
                //we have four basic conditions represented by number of neighbor also-tunnel blocks
                switch (interiorNeighborCount) {
                    case 3:
                        if (cornerBlocks == Material.SMOOTH_BRICK) {
                            cornerData = (byte) random.nextInt(4);
                        }
                        block.setType(cornerBlocks);
                        block.setData(cornerData);
                        break;
                    case 4:
                        if (edgeBlocks == Material.SMOOTH_BRICK) {
                            edgeData = (byte) random.nextInt(3);
                        }
                        block.setType(edgeBlocks);
                        block.setData(edgeData);
                        break;
                    case 5:
                        if (space.contains(x, y - 1, z, world)) {
                            //we are not a floor section
                            if (block.getType() == Material.AIR) {
                                //contains air. We will not make unnecessary wall sections
                            } else {
                                if (wallBlocks == Material.SMOOTH_BRICK) {
                                    wallData = (byte) random.nextInt(3);
                                }
                                block.setType(wallBlocks);
                                block.setData(wallData);
                            }
                        } else {
                            if (floorBlocks == Material.SMOOTH_BRICK) {
                                floorData = (byte) random.nextInt(3);
                            }
                            if ((x % lightingFactor == 0) && (z % lightingFactor == 0)) {
                                if (this.biome == biome.OCEAN || this.biome == biome.DEEP_OCEAN) {
                                    block.setType(Material.SEA_LANTERN);
                                } else {
                                    block.setType(Material.REDSTONE_LAMP_ON);
                                    block.getRelative(BlockFace.DOWN).setType(Material.REDSTONE_BLOCK);
                                    block.setData((byte) 0);

                                }
                            } else {
                                block.setType(floorBlocks);
                                block.setData(floorData);
                            }
                            if ((x % lootFactor == 0) && (z % lootFactor == 0)) {
                                //here is where we make low value chests
                                block = block.getRelative(BlockFace.UP);
                                block.setType(Material.CHEST);
                                Chest chest = (Chest) block.getState();
                                Inventory inv = chest.getBlockInventory();

                                int loot = (block.getY() - chiaroscuro) - (11 + lootBoost);
                                while (loot < 0) {
                                    loot += 1;
                                    int typeID = random.nextInt(453);
                                    if (typeID != 137
                                            && typeID != 210
                                            && typeID != 211
                                            && typeID != 422
                                            && typeID != 166
                                            && typeID != 7
                                            && typeID != 217
                                            && typeID != 255
                                            && typeID != 383
                                            && typeID != 403
                                            && typeID != 52) {
                                        ItemStack itemstack = new ItemStack(typeID, 1);
                                        if (itemstack != null) {
                                            inv.setItem(random.nextInt(27), itemstack);
                                        } //place weird random things in there, which might be overwritten. Low value chest
                                    }
                                }

                                switch (loot) { //This intentionally falls through to lower value things, and they intentionally overwrite earlier entries.
                                    case 0:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.OBSIDIAN, 10)); //build wisely!
                                        if (chiaroscuro > random.nextInt(20)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 1:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND, random.nextInt(16) + 1));
                                    case 2:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.GOLD_INGOT, random.nextInt(32) + 1));
                                    case 3:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_INGOT, random.nextInt(32) + 1));
                                    case 4:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.FLINT_AND_STEEL, 1));
                                    case 5:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.FISHING_ROD, 1));
                                        if (chiaroscuro > random.nextInt(30)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 6:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_HELMET, 1));
                                    case 7:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
                                    case 8:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
                                    case 9:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_BOOTS, 1));
                                        if (chiaroscuro > random.nextInt(40)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 10:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_SWORD, 1));
                                    case 11:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_PICKAXE, 1));
                                    case 12:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_AXE, 1));
                                    case 13:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_SPADE, 1));
                                        if (chiaroscuro > random.nextInt(50)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 14:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_SWORD, 1));
                                    case 15:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_PICKAXE, 1));
                                    case 16:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_AXE, 1));
                                    case 17:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_SPADE, 1));
                                        if (chiaroscuro > random.nextInt(60)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 18:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_HELMET, 1));
                                    case 19:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_LEGGINGS, 1));
                                    case 20:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_CHESTPLATE, 1));
                                    case 21:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_BOOTS, 1));
                                        if (chiaroscuro > random.nextInt(70)) {
                                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                            break;
                                        }
                                    case 22:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.FURNACE, 1));
                                    case 23:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.WOOD, random.nextInt(64) + 1));
                                    case 24:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.STICK, random.nextInt(64) + 1));
                                    case 25:
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.COBBLESTONE, random.nextInt(64) + 1));
                                    case 26:
                                    default: //fall through
                                        inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                                } //this completes the low value chest
                            }
                        }
                        break;
                    case 6:
                    case 7:
                        if (block.getType() == Material.LOG
                                || block.getType() == Material.CHEST
                                || block.getType() == Material.GLOWSTONE
                                || block.getType() == Material.REDSTONE_LAMP_ON
                                || block.getType() == Material.SEA_LANTERN
                                || block.getType() == Material.IRON_BLOCK
                                || block.getType() == Material.LAPIS_BLOCK
                                || block.getType() == Material.DIAMOND_BLOCK
                                || block.getType() == Material.MOB_SPAWNER) {
                            //protected blocks
                        } else {
                            block.setType(Material.AIR);
                        }
                        break;
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
        for (Location end : ends) {
            space = space.union(getConnectingSpace(start, end, this.biome.ordinal()));
        }
        for (int i = 0; i < surrounding.length; ++i) {
            int nextIndex = (i + 1) % surrounding.length;
            space = space.union(getConnectingSpace(surrounding[i], surrounding[nextIndex], this.biome.ordinal()));
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
    private static Space getConnectingSpace(Location start, Location end, int biome) {
        if (start.distance(end) > (biome / 3) + 7) {
            return Space.linear(start, end, 4 + (biome / 23), 5 + (biome / 11)); //this is width and height
        }
        return Space.empty();
    }

    public void decorate() {
        Random random = new Random();
        Material ceilingLight = Material.GLOWSTONE;
        Material floorFeature = Material.GLOWSTONE;
        Material pillar = Material.LOG;
        EntityType mobType = EntityType.COW; //these get overridden 
        int x = (int) start.getX();
        int y = (int) (start.getY());
        int z = (int) start.getZ();
        Block block;
        if (ChunkPosition.of(target).contains(x, z) && world.getBlockAt(x, y, z).getType() == Material.AIR) {
            //double check: is the air block there, and has it already been filled by 'decorate()' from another direction
            block = world.getBlockAt(x, y, z);
            while ((block.getType() == Material.AIR) && block.getY() < 255) {
                block = block.getRelative(BlockFace.UP);
            } //get up to the ceiling
            if (block.getY() > 190) {
                block.getLocation().setY(64.0);
                //if we didn't even hit a roof skip right back down to 64
            }

            switch (biome) {
                case PLAINS://1
                case RIVER://7
                case FROZEN_RIVER://11
                case BEACHES://16
                case COLD_BEACH://26
                case STONE_BEACH://25
                case JUNGLE_EDGE://23
                    mobType = EntityType.COW;
                    pillar = Material.LOG; //see a treetrunk, know you can get food/supplies
                    break;
                case OCEAN://0
                case DEEP_OCEAN://24
                case FROZEN_OCEAN://10
                    mobType = EntityType.ZOMBIE;
                    pillar = Material.DIAMOND_BLOCK; //find the ocean and get diamonds
                    break;
                case DESERT://2
                case DESERT_HILLS://17
                case MESA://37
                case MESA_ROCK://38
                case MESA_CLEAR_ROCK://39
                    mobType = EntityType.HUSK;
                    pillar = Material.IRON_BLOCK;
                    break;
                case EXTREME_HILLS://3
                case FOREST://4
                case TAIGA://5
                case SWAMPLAND://6
                case ICE_FLATS://12
                case ICE_MOUNTAINS://13
                case FOREST_HILLS://18
                case TAIGA_HILLS://19
                case SMALLER_EXTREME_HILLS://20
                case JUNGLE://21
                case JUNGLE_HILLS://22
                case BIRCH_FOREST://27
                case BIRCH_FOREST_HILLS://28
                case ROOFED_FOREST://29
                case TAIGA_COLD://30
                case TAIGA_COLD_HILLS://31
                case REDWOOD_TAIGA://32
                case REDWOOD_TAIGA_HILLS://33
                case EXTREME_HILLS_WITH_TREES://34
                case SAVANNA://35
                case SAVANNA_ROCK://36
                    mobType = EntityType.ZOMBIE;
                    pillar = Material.IRON_BLOCK;
                    break;

                case HELL://8
                    mobType = EntityType.PIG_ZOMBIE;
                    pillar = Material.DIAMOND_BLOCK;
                    break;
                case SKY://9
                    mobType = EntityType.ENDERMAN;
                    pillar = Material.ENDER_STONE;
                    break;
                case MUSHROOM_ISLAND://14
                case MUSHROOM_ISLAND_SHORE://15
                    mobType = EntityType.MUSHROOM_COW;
                    pillar = Material.HUGE_MUSHROOM_1;
                    //mushroom islands and roofed forest are made of mushroom
                    break;

                /* These are planned for 1.13
            case SKY_ISLAND_LOW://40
            case SKY_ISLAND_MEDIUM://41
            case SKY_ISLAND_HIGH://42
            case SKY_ISLAND_BARREN://43
            case WARM_OCEAN://44
            case LUKEWARM_OCEAN://45
            case COLD_OCEAN://46
            case WARM_DEEP_OCEAN://47
            case LUKEWARM_DEEP_OCEAN://48
            case COLD_DEEP_OCEAN://49
            case FROZEN_DEEP_OCEAN://50
            case THE_VOID://127
                 */
                case MUTATED_EXTREME_HILLS://131
                case MUTATED_EXTREME_HILLS_WITH_TREES://162
                case MUTATED_DESERT://130
                case MUTATED_PLAINS://129
                case MUTATED_FOREST://132
                case MUTATED_TAIGA://133
                case MUTATED_SWAMPLAND://134
                case MUTATED_ICE_FLATS://140
                case MUTATED_JUNGLE://149
                case MUTATED_JUNGLE_EDGE://151
                case MUTATED_BIRCH_FOREST://155
                case MUTATED_BIRCH_FOREST_HILLS://156
                case MUTATED_ROOFED_FOREST://157
                case MUTATED_TAIGA_COLD://158
                case MUTATED_REDWOOD_TAIGA://160
                case MUTATED_REDWOOD_TAIGA_HILLS://161
                case MUTATED_SAVANNA://163
                case MUTATED_SAVANNA_ROCK://164
                case MUTATED_MESA://165
                case MUTATED_MESA_ROCK://166
                case MUTATED_MESA_CLEAR_ROCK://167
                    mobType = EntityType.SKELETON;
                    pillar = Material.DIAMOND_BLOCK; //tough places have skeleton armies, occasionally blazes
                default:
            } //mostly we do default glowstone, 50% of the time (half of them have chests)

            if (random.nextBoolean() == true) {
                ceilingLight = Material.MOB_SPAWNER;
                floorFeature = Material.MOB_SPAWNER;
                if (mobType == EntityType.ZOMBIE && random.nextBoolean() == true) {
                    if (random.nextBoolean() == true) {
                        mobType = EntityType.SKELETON;
                    } else {
                        if (random.nextBoolean() == true) {
                            mobType = EntityType.SPIDER;
                        } else {
                            if (random.nextBoolean() == true) {
                                mobType = EntityType.CREEPER;
                            } else {
                                mobType = EntityType.WITCH;
                            }
                        }
                    } //50% zombies, 25% skeles, 12.5% spiders, 6.25% creepers, 6.25% witches: generic low level mobfountain
                }
                if (mobType == EntityType.COW && random.nextBoolean() == true) {
                    if (random.nextBoolean() == true) {
                        mobType = EntityType.CHICKEN;
                    } else {
                        if (random.nextBoolean() == true) {
                            mobType = EntityType.HORSE;
                        } else {
                            if (random.nextBoolean() == true) {
                                mobType = EntityType.DONKEY;
                            } else {
                                mobType = EntityType.RABBIT;
                            }
                        }
                    } //50% cows, 25% chickens, 12.5% horses, 6.25% donkeys, 6.25 bunnies: generic helpful mobfountain on grass
                }
            } //50% of the time we're either mob spawners
            if (random.nextBoolean() == true) {
                ceilingLight = pillar;
                floorFeature = pillar;
            } //or pillars of resources/indications of what the place is about

            //here we tack on the glowstone ceiling lights, and/or call the spawner/loot math
            block.setType(ceilingLight);

            if (ceilingLight != Material.GLOWSTONE) {
                //everything that is not glowstone is some sort of pillar: carte blanche in choice of pillar blocks. Spawn pillar comes with specified target
                block = block.getRelative(BlockFace.DOWN);
                while ((block.getType() == Material.AIR) && block.getY() > 4) {
                    block.setType(ceilingLight);
                    if (ceilingLight == Material.MOB_SPAWNER) {
                        BlockState blockState = block.getState();
                        CreatureSpawner spawner = ((CreatureSpawner) blockState);
                        if (random.nextInt(block.getY()) == 1
                                && (mobType == EntityType.SKELETON
                                || mobType == EntityType.ZOMBIE
                                || mobType == EntityType.WITCH
                                || mobType == EntityType.CREEPER
                                || mobType == EntityType.SPIDER)) {
                            spawner.setSpawnedType(EntityType.BLAZE); //the lower you go, the more likely there will be blaze spawners mixed with the hostile army
                        } else {
                            spawner.setSpawnedType(mobType);
                        }
                        blockState.update();
                    }
                    block = block.getRelative(BlockFace.DOWN);
                }
                //make a pillar of these special materials to indicate what's there 
            } else {
                block = world.getBlockAt(x, y, z);
                while ((block.getType() == Material.AIR) && block.getY() > 4) {
                    block = block.getRelative(BlockFace.DOWN);
                }
                //step down more quickly to the floor without placing anything
            }
            if (block.getType() != Material.MOB_SPAWNER) {
                block.setType(floorFeature); //we won't make it mob spawner, since we are not updating the state properly
            }

            if (ceilingLight == Material.MOB_SPAWNER) {
                if (mobType == EntityType.SKELETON
                        || mobType == EntityType.ZOMBIE
                        || mobType == EntityType.WITCH
                        || mobType == EntityType.CREEPER
                        || mobType == EntityType.SPIDER
                        || mobType == EntityType.BLAZE) {
                    //if we have a tower, put an awesome loot chest at the base.
                    block.setType(Material.CHEST);
                    BlockState bs = block.getState();
                    Chest chest = (Chest) bs;
                    Inventory inv = chest.getBlockInventory();
                    for (int chestSlot = 0; chestSlot < 27; ++chestSlot) {
                        int typeID = random.nextInt(453);
                        if (typeID != 137
                                && typeID != 210
                                && typeID != 211
                                && typeID != 422
                                && typeID != 166
                                && typeID != 7
                                && typeID != 217
                                && typeID != 255
                                && typeID != 383
                                && typeID != 403
                                && typeID != 52) {
                            ItemStack itemstack = new ItemStack(typeID, 1);
                            if (itemstack != null) {
                                inv.setItem(chestSlot, itemstack);
                            } //place weird random things in there, which might be overwritten. Low value chest
                        }
                    }
                    switch (random.nextInt(14)) { //This intentionally falls through to lower value things, and they intentionally overwrite earlier entries.
                        case 0:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.OBSIDIAN, 10)); //build wisely!
                        case 1:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND, random.nextInt(64) + 1));
                        case 2:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.GOLD_INGOT, random.nextInt(64) + 1));
                        case 3:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_INGOT, random.nextInt(64) + 1));
                        case 4:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.FLINT_AND_STEEL, 1));
                        case 5:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.FISHING_ROD, 1));
                        case 6:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_HELMET, 1));
                        case 7:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_LEGGINGS, 1));
                        case 8:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_CHESTPLATE, 1));
                        case 9:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_BOOTS, 1));
                        case 10:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_SWORD, 1));
                        case 11:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_PICKAXE, 1));
                        case 12:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_AXE, 1));
                        case 13:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND_SPADE, 1));
                    } //this completes the high value chest
                } else {
                    //if we have food mobs, put a ordinary loot chest at the base.
                    block.setType(Material.CHEST);
                    BlockState bs = block.getState();
                    Chest chest = (Chest) bs;
                    Inventory inv = chest.getBlockInventory();
                    int typeID = random.nextInt(453);
                    if (typeID != 137
                            && typeID != 210
                            && typeID != 211
                            && typeID != 422
                            && typeID != 166
                            && typeID != 7
                            && typeID != 217
                            && typeID != 255
                            && typeID != 383
                            && typeID != 403
                            && typeID != 52) {
                        ItemStack itemstack = new ItemStack(typeID, 1);
                        if (itemstack != null) {
                            inv.setItem(random.nextInt(27), itemstack);
                        } //place weird random things in there, which might be overwritten. Low value chest
                    }

                    switch (random.nextInt(27)) { //This intentionally falls through to lower value things, and they intentionally overwrite earlier entries.
                        case 0:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.OBSIDIAN, 10)); //build wisely!
                        case 1:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.DIAMOND, random.nextInt(16) + 1));
                        case 2:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.GOLD_INGOT, random.nextInt(32) + 1));
                        case 3:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_INGOT, random.nextInt(32) + 1));
                        case 4:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.FLINT_AND_STEEL, 1));
                        case 5:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.FISHING_ROD, 1));
                        case 6:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_HELMET, 1));
                        case 7:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
                        case 8:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
                        case 9:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.CHAINMAIL_BOOTS, 1));
                        case 10:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_SWORD, 1));
                        case 11:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_PICKAXE, 1));
                        case 12:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_AXE, 1));
                        case 13:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.IRON_SPADE, 1));
                        case 14:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.WOOD_SWORD, 1));
                        case 15:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.WOOD_PICKAXE, 1));
                        case 16:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_AXE, 1));
                        case 17:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.STONE_SPADE, 1));
                        case 18:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_HELMET, 1));
                        case 19:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_LEGGINGS, 1));
                        case 20:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_CHESTPLATE, 1));
                        case 21:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.LEATHER_BOOTS, 1));
                        case 22:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.FURNACE, 1));
                        case 23:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.WOOD, random.nextInt(64) + 1));
                        case 24:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.STICK, random.nextInt(64) + 1));
                        case 25:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.COBBLESTONE, random.nextInt(64) + 1));
                        case 26:
                        case 27:
                        case 28:
                        case 29:
                        case 30:
                            inv.setItem(random.nextInt(27), new ItemStack(Material.TORCH, random.nextInt(64) + 1));
                        default: //fall through
                    } //this completes the low value chest for the food mob spawners
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
            case OCEAN://0
                cornerBlocks = Material.PRISMARINE;
                cornerData = 0;
                edgeBlocks = Material.PRISMARINE;
                edgeData = 0;
                wallBlocks = Material.PRISMARINE;
                wallData = 0;
                floorBlocks = Material.PRISMARINE;
                floorData = 0;
                break;
            case PLAINS://1
                cornerBlocks = Material.STONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.GRASS;
                floorData = 0;
                break;
            case DESERT://2
                cornerBlocks = Material.STONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case EXTREME_HILLS://3
            case FOREST://4
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.GRASS;
                floorData = 0;
                break;
            case TAIGA://5
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
                break;
            case SWAMPLAND://6
            case RIVER://7
                cornerBlocks = Material.CLAY; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.CLAY;
                edgeData = 0;
                wallBlocks = Material.CLAY;
                wallData = 0;
                floorBlocks = Material.GRASS;
                floorData = 0;
                break;
            case HELL://8
                cornerBlocks = Material.QUARTZ_BLOCK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.QUARTZ_BLOCK;
                edgeData = 0;
                wallBlocks = Material.QUARTZ_BLOCK;
                wallData = 0;
                floorBlocks = Material.QUARTZ_BLOCK;
                floorData = 0;
                break;
            case SKY://9
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 3;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 3;
                wallBlocks = Material.STAINED_GLASS;
                wallData = 15;
                floorBlocks = Material.ENDER_STONE;
                floorData = 0;
                break;
            case FROZEN_OCEAN://10
            case FROZEN_RIVER://11
            case ICE_FLATS://12
            case ICE_MOUNTAINS://13
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
            case MUSHROOM_ISLAND://14
            case MUSHROOM_ISLAND_SHORE://15
                cornerBlocks = Material.HUGE_MUSHROOM_2;
                cornerData = 14;
                edgeBlocks = Material.HUGE_MUSHROOM_2;
                edgeData = 14;
                wallBlocks = Material.MYCEL;
                wallData = 15;
                floorBlocks = Material.MYCEL;
                floorData = 0;
                //mushroom islands and roofed forest are made of mushroom
                break;
            case BEACHES://16
                cornerBlocks = Material.STONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case DESERT_HILLS://17
            case FOREST_HILLS://18
            case TAIGA_HILLS://19
            case SMALLER_EXTREME_HILLS://20
                cornerBlocks = Material.SMOOTH_BRICK; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
                break;
            case JUNGLE://21
            case JUNGLE_HILLS://22
            case JUNGLE_EDGE://23
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.GRASS;
                floorData = 0;
                //under swamp is mossy cobble
                break;
            case DEEP_OCEAN://24
                cornerBlocks = Material.PRISMARINE;
                cornerData = 15;
                edgeBlocks = Material.PRISMARINE;
                edgeData = 15;
                wallBlocks = Material.PRISMARINE;
                wallData = 15;
                floorBlocks = Material.PRISMARINE;
                floorData = 0;
                break;
            case STONE_BEACH://25
            case COLD_BEACH://26
                cornerBlocks = Material.STONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case BIRCH_FOREST://27
            case BIRCH_FOREST_HILLS://28
            case ROOFED_FOREST://29
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.GRASS;
                floorData = 0;
                break;
            case TAIGA_COLD://30
            case TAIGA_COLD_HILLS://31
            case REDWOOD_TAIGA://32
            case REDWOOD_TAIGA_HILLS://33
            case EXTREME_HILLS_WITH_TREES://34
            case SAVANNA://35
            case SAVANNA_ROCK://36
                cornerBlocks = Material.SMOOTH_BRICK;
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
                break;
            case MESA://37
            case MESA_ROCK://38
            case MESA_CLEAR_ROCK://39
                cornerBlocks = Material.SANDSTONE; //default cases for stuff
                cornerData = 3;
                edgeBlocks = Material.SANDSTONE;
                edgeData = 3;
                wallBlocks = Material.SANDSTONE;
                wallData = 3;
                floorBlocks = Material.SANDSTONE;
                floorData = 3;
                break;

            /* These are planned for 1.13
            case SKY_ISLAND_LOW://40
            case SKY_ISLAND_MEDIUM://41
            case SKY_ISLAND_HIGH://42
            case SKY_ISLAND_BARREN://43
            case WARM_OCEAN://44
            case LUKEWARM_OCEAN://45
            case COLD_OCEAN://46
            case WARM_DEEP_OCEAN://47
            case LUKEWARM_DEEP_OCEAN://48
            case COLD_DEEP_OCEAN://49
            case FROZEN_DEEP_OCEAN://50
            case THE_VOID://127
             */
            case MUTATED_PLAINS://129
                cornerBlocks = Material.STONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case MUTATED_DESERT://130
                cornerBlocks = Material.SANDSTONE; //default cases for stuff
                cornerData = 3;
                edgeBlocks = Material.SANDSTONE;
                edgeData = 3;
                wallBlocks = Material.SANDSTONE;
                wallData = 3;
                floorBlocks = Material.SANDSTONE;
                floorData = 3;
                break;
            case MUTATED_EXTREME_HILLS://131
                cornerBlocks = Material.TNT; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case MUTATED_FOREST://132
                cornerBlocks = Material.MOSSY_COBBLESTONE; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.MOSSY_COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.MOSSY_COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.MOSSY_COBBLESTONE;
                floorData = 0;
                break;
            case MUTATED_TAIGA://133
            case MUTATED_SWAMPLAND://134
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.MOSSY_COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.MOSSY_COBBLESTONE;
                wallData = 0;
                floorBlocks = Material.MOSSY_COBBLESTONE;
                floorData = 0;
                break;
            case MUTATED_ICE_FLATS://140
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
            case MUTATED_JUNGLE://149
            case MUTATED_JUNGLE_EDGE://151
                cornerBlocks = Material.MOSSY_COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
            case MUTATED_BIRCH_FOREST://155
            case MUTATED_BIRCH_FOREST_HILLS://156
                cornerBlocks = Material.COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.DIRT;
                wallData = 0;
                floorBlocks = Material.COBBLESTONE;
                floorData = 0;
                break;
            case MUTATED_ROOFED_FOREST://157
                cornerBlocks = Material.DIRT;
                cornerData = 0;
                edgeBlocks = Material.DIRT;
                edgeData = 0;
                wallBlocks = Material.DIRT;
                wallData = 0;
                floorBlocks = Material.COBBLESTONE;
                floorData = 0;
                break;
            case MUTATED_TAIGA_COLD://158
            case MUTATED_REDWOOD_TAIGA://160
            case MUTATED_REDWOOD_TAIGA_HILLS://161
                cornerBlocks = Material.SMOOTH_BRICK;
                cornerData = 0;
                edgeBlocks = Material.SMOOTH_BRICK;
                edgeData = 0;
                wallBlocks = Material.SMOOTH_BRICK;
                wallData = 0;
                floorBlocks = Material.SMOOTH_BRICK;
                floorData = 0;
                break;
            case MUTATED_EXTREME_HILLS_WITH_TREES://162
                cornerBlocks = Material.TNT; //default cases for stuff
                cornerData = 0;
                edgeBlocks = Material.STONE;
                edgeData = 0;
                wallBlocks = Material.STONE;
                wallData = 0;
                floorBlocks = Material.STONE;
                floorData = 0;
                break;
            case MUTATED_SAVANNA://163
            case MUTATED_SAVANNA_ROCK://164
                cornerBlocks = Material.COBBLESTONE;
                cornerData = 0;
                edgeBlocks = Material.COBBLESTONE;
                edgeData = 0;
                wallBlocks = Material.SANDSTONE;
                wallData = 0;
                floorBlocks = Material.SANDSTONE;
                floorData = 0;
                break;
            case MUTATED_MESA://165
            case MUTATED_MESA_ROCK://166
            case MUTATED_MESA_CLEAR_ROCK://167
                cornerBlocks = Material.HARD_CLAY;
                cornerData = 0;
                edgeBlocks = Material.HARD_CLAY;
                edgeData = 0;
                wallBlocks = Material.HARD_CLAY;
                wallData = 0;
                floorBlocks = Material.HARD_CLAY;
                floorData = 0;
            default:
        }

    }
}
