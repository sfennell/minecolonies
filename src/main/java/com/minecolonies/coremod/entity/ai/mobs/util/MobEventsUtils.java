package com.minecolonies.coremod.entity.ai.mobs.util;

import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.LanguageHandler;
import com.minecolonies.api.util.Log;
import com.minecolonies.coremod.colony.CitizenData;
import com.minecolonies.coremod.colony.Colony;
import com.minecolonies.coremod.entity.ai.citizen.fisherman.Pond;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.minecolonies.api.util.constant.ColonyConstants.*;

/**
 * Utils for Colony mob events
 */
public final class MobEventsUtils
{
    /**
     * Spawn modifier to decrease the spawn-rate.
     */
    private static final int SPAWN_MODIFIER         = 3;

    /**
     * Pirate ship water pool size.
     */
    private static final int PIRATE_SHIP_WATER_SIZE = 15;

    /**
     * Private constructor to hide the implicit public one.
     */
    private MobEventsUtils()
    {
    }

    public static void raiderEvent(final World world, final Colony colony)
    {
        if (world == null || !colony.isCanHaveBarbEvents())
        {
            return;
        }

        final Horde horde = numberOfSpawns(colony);
        final int hordeSize = horde.hordeSize;
        if(hordeSize == 0)
        {
            return;
        }

        BlockPos targetSpawnPoint = calculateSpawnLocation(world, colony);
        Log.getLogger().info("[BarbarianEvent]: Spawning: " + targetSpawnPoint.getX() + " " + targetSpawnPoint.getZ());
        if (targetSpawnPoint.equals(colony.getCenter()))
        {
            return;
        }

        if (Configurations.gameplay.enableInDevelopmentFeatures)
        {
            LanguageHandler.sendPlayersMessage(
              colony.getMessageEntityPlayers(),
              "Horde Spawn Point: " + targetSpawnPoint);
        }
        colony.getRaiderManager().addRaiderSpawnPoint(targetSpawnPoint);
        colony.markDirty();

        int raidNumber = HUGE_HORDE_MESSAGE_ID;
        String shipSize = BIG_PIRATE_SHIP;
        if(hordeSize < SMALL_HORDE_SIZE)
        {
            raidNumber = SMALL_HORDE_MESSAGE_ID;
            shipSize = SMALL_PIRATE_SHIP;
        }
        else if(hordeSize < MEDIUM_HORDE_SIZE)
        {
            raidNumber = MEDIUM_HORDE_MESSAGE_ID;
            shipSize = MEDIUM_PIRATE_SHIP;
        }
        else if(hordeSize < BIG_HORDE_SIZE)
        {
            raidNumber = BIG_HORDE_MESSAGE_ID;
            shipSize = MEDIUM_PIRATE_SHIP;
        }

        colony.setNightsSinceLastRaid(0);

        if ((world.getBlockState(targetSpawnPoint).getBlock() == Blocks.WATER && Pond.checkWater(world, targetSpawnPoint, PIRATE_SHIP_WATER_SIZE, PIRATE_SHIP_WATER_SIZE))
              || (world.getBlockState(targetSpawnPoint.down()).getBlock() == Blocks.WATER && Pond.checkWater(world, targetSpawnPoint.down(), PIRATE_SHIP_WATER_SIZE, PIRATE_SHIP_WATER_SIZE))
        )
        {
            if (world.getBlockState(targetSpawnPoint).getBlock() != Blocks.WATER)
            {
                targetSpawnPoint = targetSpawnPoint.down();
            }
            PirateEventUtils.pirateEvent(targetSpawnPoint, world, colony, shipSize, raidNumber);
            return;
        }

        BarbarianEventUtils.barbarianEvent(world, colony, targetSpawnPoint, raidNumber, horde);
    }

    /**
     * Sets the number of spawns for each barbarian type
     *
     * @param colony The colony to get the RaidLevel from
     * @return the total horde strength.
     */
    private static Horde numberOfSpawns(final Colony colony)
    {
        if (colony.getCitizenManager().getCitizens().size() < MIN_CITIZENS_FOR_RAID)
        {
            return new Horde(0, 0, 0, 0);
        }

        final int raidLevel = Math.min(Configurations.gameplay.maxBarbarianSize,(int) ((getColonyRaidLevel(colony) / SPAWN_MODIFIER) * ((double) Configurations.gameplay.spawnBarbarianSize * 0.1)));
        final int numberOfChiefs = Math.max(1, (int) (raidLevel * CHIEF_BARBARIANS_MULTIPLIER));
        final int numberOfArchers = Math.max(1,(int) (raidLevel * ARCHER_BARBARIANS_MULTIPLIER));
        final int numberOfBarbarians = raidLevel - numberOfChiefs - numberOfArchers;

        return new Horde(raidLevel, numberOfBarbarians, numberOfArchers, numberOfChiefs);
    }

    /**
     * Calculate a random spawn point along the colony's border
     *
     * @param world  in the world.
     * @param colony the Colony to spawn the barbarians near.
     * @return Returns the random blockPos
     */
    private static BlockPos calculateSpawnLocation(final World world, @NotNull final Colony colony)
    {
        final Random random = new Random();
        final BlockPos pos = colony.getRaiderManager().getRandomOutsiderInDirection(
          random.nextInt(2) < 1 ? EnumFacing.EAST : EnumFacing.WEST,
          random.nextInt(2) < 1 ? EnumFacing.NORTH : EnumFacing.SOUTH);

        if (pos.equals(colony.getCenter()))
        {
            Log.getLogger().info("Spawning at colony center: " + colony.getCenter().getX() + " " + colony.getCenter().getZ());
            return colony.getCenter();
        }

        return BlockPosUtil.findLand(pos, world);
    }

    /**
     * Takes a colony and spits out that colony's RaidLevel.
     *
     * @param colony The colony to use
     * @return an int describing the raid level
     */
    public static int getColonyRaidLevel(final Colony colony)
    {
        int levels = 0;
        @NotNull final List<CitizenData> citizensList = new ArrayList<>(colony.getCitizenManager().getCitizens());
        for (@NotNull final CitizenData citizen : citizensList)
        {
            levels += citizen.getLevel();
        }

        return levels;
    }

    public static boolean isItTimeToRaid(final World world, final Colony colony)
    {
        if (colony.getCitizenManager().getCitizens().size() < NUMBER_OF_CITIZENS_NEEDED)
        {
            return false;
        }

        if (world.isDaytime() && !colony.isHasRaidBeenCalculated())
        {
            colony.getRaiderManager().setHasRaidBeenCalculated(true);
            if (!colony.hasWillRaidTonight())
            {
                final boolean raid = raidThisNight(world, colony);
                if (Configurations.gameplay.enableInDevelopmentFeatures)
                {
                    LanguageHandler.sendPlayersMessage(
                      colony.getMessageEntityPlayers(),
                      "Will raid tonight: " + raid);
                }
                colony.getRaiderManager().setWillRaidTonight(raid);
            }
            return false;
        }
        else if (colony.hasWillRaidTonight() && !world.isDaytime() && colony.isHasRaidBeenCalculated())
        {
            colony.getRaiderManager().setHasRaidBeenCalculated(false);
            colony.getRaiderManager().setWillRaidTonight(false);
            if (Configurations.gameplay.enableInDevelopmentFeatures)
            {
                LanguageHandler.sendPlayersMessage(
                  colony.getMessageEntityPlayers(),
                  "Night reached: raiding");
            }
            return true;
        }
        else if (!world.isDaytime() && colony.isHasRaidBeenCalculated())
        {
            colony.getRaiderManager().setHasRaidBeenCalculated(false);
        }

        return false;
    }

    /**
     * Returns whether a raid should happen depending on the Config
     *
     * @param world The world in which the raid is possibly happening (Used to get a random number easily)
     * @return Boolean value on whether to act this night
     */
    private static boolean raidThisNight(final World world, final Colony colony)
    {
        return colony.getNightsSinceLastRaid() > Configurations.gameplay.minimumNumberOfNightsBetweenRaids
                && world.rand.nextDouble() < 1.0 / Configurations.gameplay.averageNumberOfNightsBetweenRaids;
    }

    /**
     * Class representing a horde attack.
     */
    protected static class Horde
    {
        protected final int numberOfRaiders;
        protected final int numberOfArchers;
        protected final int numberOfBosses;
        protected final int hordeSize;

        /**
         * Create a new horde.
         * @param hordeSize the size.
         * @param numberOfRaiders the number of raiders.
         * @param numberOfArchers the number of archers.
         * @param numberOfBosses the number of bosses.
         */
        Horde(final int hordeSize, final int numberOfRaiders, final int numberOfArchers, final int numberOfBosses)
        {
            this.hordeSize = hordeSize;
            this.numberOfRaiders = numberOfRaiders;
            this.numberOfArchers = numberOfArchers;
            this.numberOfBosses = numberOfBosses;
        }
    }
}
