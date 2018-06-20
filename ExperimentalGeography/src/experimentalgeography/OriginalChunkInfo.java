/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package experimentalgeography;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Biome;

/**
 * This class holds onto data about a chunk that is captured when the chunk is loaded. This lets us 'remember' stuff that we'll
 * change later.
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
        switch (chunk.getBlock(8, 8, 8).getBiome()) {
            case OCEAN://0
                 nodeY = 12;
                break;
          case PLAINS://1
                 nodeY = 28;
                break;
          case DESERT://2
               nodeY = 27;
                break;
            case EXTREME_HILLS://3
               nodeY = 26;
                break;
            case FOREST://4
               nodeY = 25;
                break;
            case TAIGA://5
                 nodeY = 24;
                break;
          case SWAMPLAND://6
               nodeY = 23;
                break;
            case RIVER://7
               nodeY = 22;
                break;
            case HELL://8
               nodeY = 50;
                break;
            case SKY://9
               nodeY = 75;
                break;
            case FROZEN_OCEAN://10
            case FROZEN_RIVER://11
            case ICE_FLATS://12
            case ICE_MOUNTAINS://13
                nodeY = 21;
                break;
           case MUSHROOM_ISLAND://14
            case MUSHROOM_ISLAND_SHORE://15
            case BEACHES://16
                 nodeY = 20;
                break;
          case DESERT_HILLS://17
            case FOREST_HILLS://18
            case TAIGA_HILLS://19
            case SMALLER_EXTREME_HILLS://20
                nodeY = 19;
                break;
           case JUNGLE://21
                 nodeY = 18;
                break;
          case JUNGLE_HILLS://22
            case JUNGLE_EDGE://23
               nodeY = 17;
                break;
            case DEEP_OCEAN://24
                nodeY = 8;
                break;
           case STONE_BEACH://25
            case COLD_BEACH://26
               nodeY = 16;
                break;
            case BIRCH_FOREST://27
            case BIRCH_FOREST_HILLS://28
                 nodeY = 15;
                break;
          case ROOFED_FOREST://29
               nodeY = 14;
                break;
            case TAIGA_COLD://30
            case TAIGA_COLD_HILLS://31
                nodeY = 13;
                break;
           case REDWOOD_TAIGA://32
            case REDWOOD_TAIGA_HILLS://33
            case EXTREME_HILLS_WITH_TREES://34
               nodeY = 12;
                break;
            case SAVANNA://35
            case SAVANNA_ROCK://36
               nodeY = 11;
                break;
            case MESA://37
            case MESA_ROCK://38
            case MESA_CLEAR_ROCK://39
               nodeY = 10;
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
            case MUTATED_DESERT://130
            case MUTATED_EXTREME_HILLS://131
                nodeY = 9;
                break;
           case MUTATED_FOREST://132
            case MUTATED_TAIGA://133
            case MUTATED_SWAMPLAND://134
               nodeY = 8;
                break;
            case MUTATED_ICE_FLATS://140
            case MUTATED_JUNGLE://149
            case MUTATED_JUNGLE_EDGE://151
                 nodeY = 7;
                break;
          case MUTATED_BIRCH_FOREST://155
            case MUTATED_BIRCH_FOREST_HILLS://156
            case MUTATED_ROOFED_FOREST://157
                nodeY = 6;
                break;
           case MUTATED_TAIGA_COLD://158
            case MUTATED_REDWOOD_TAIGA://160
            case MUTATED_REDWOOD_TAIGA_HILLS://161
            case MUTATED_EXTREME_HILLS_WITH_TREES://162
            case MUTATED_SAVANNA://163
            case MUTATED_SAVANNA_ROCK://164
                nodeY = 5;
                break;
           case MUTATED_MESA://165
            case MUTATED_MESA_ROCK://166
            case MUTATED_MESA_CLEAR_ROCK://167
                nodeY = 4;
                break;
           default:
                nodeY = 3;
                break;
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
