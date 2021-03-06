////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////

package com.intellectualcrafters.plot.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.listeners.PlotListener;
import com.intellectualcrafters.plot.object.BlockLoc;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotManager;
import com.intellectualcrafters.plot.object.PlotWorld;

/**
 * plot functions
 *
 * @author Citymonstret
 */
@SuppressWarnings({"unused", "javadoc", "deprecation"}) public class PlotHelper {
    public final static HashMap<Plot, Integer> runners = new HashMap<>();
    public static boolean canSetFast = false;
    public static boolean canSendChunk = false;
    public static ArrayList<String> runners_p = new ArrayList<>();
    static long state = 1;
    public static HashMap<String, PlotId> lastPlot = new HashMap<>();
    public static HashMap<String, Integer> worldBorder = new HashMap<>();

    public static int getBorder(String worldname) {
    	if (worldBorder.containsKey(worldname)) {
    		PlotWorld plotworld = PlotMain.getWorldSettings(worldname);
    		return worldBorder.get(worldname) + 16;
    	}
    	return Integer.MAX_VALUE;
    }
    
    public static void setupBorder(String world) {
    	PlotWorld plotworld = PlotMain.getWorldSettings(world);
    	if (!plotworld.WORLD_BORDER) {
    		return;
    	}
    	if (!worldBorder.containsKey(world)) {
    		worldBorder.put(world,0);
    	}
    	for (Plot plot : PlotMain.getPlots(world).values()) {
    		updateWorldBorder(plot);
    	}
    }
    
    public static PlotId parseId(String arg) {
        try {
            String[] split = arg.split(";");
            return new PlotId(Integer.parseInt(split[0]), Integer.parseInt(split[1])) ;
        }
        catch (Exception e) {
            return null;
        }
    }
    
    /**
     * direction 0 = north, 1 = south, etc:
     *
     * @param id
     * @param direction
     *
     * @return
     */
    public static PlotId getPlotIdRelative(final PlotId id, final int direction) {
        switch (direction) {
            case 0:
                return new PlotId(id.x, id.y - 1);
            case 1:
                return new PlotId(id.x + 1, id.y);
            case 2:
                return new PlotId(id.x, id.y + 1);
            case 3:
                return new PlotId(id.x - 1, id.y);
        }
        return id;
    }

    /**
     * Merges all plots in the arraylist (with cost)
     *
     * @param plr
     * @param world
     * @param plotIds
     *
     * @return
     */
    public static boolean mergePlots(final Player plr, final World world, final ArrayList<PlotId> plotIds) {
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        if (PlotMain.useEconomy && plotworld.USE_ECONOMY) {
            final double cost = plotIds.size() * plotworld.MERGE_PRICE;
            if (cost > 0d) {
                final Economy economy = PlotMain.economy;
                if (economy.getBalance(plr) < cost) {
                    PlayerFunctions.sendMessage(plr, C.CANNOT_AFFORD_MERGE, "" + cost);
                    return false;
                }
                economy.withdrawPlayer(plr, cost);
                PlayerFunctions.sendMessage(plr, C.REMOVED_BALANCE, cost + "");
            }
        }
        return mergePlots(world, plotIds);
    }

    /**
     * Completely merges a set of plots<br> <b>(There are no checks to make sure you supply the correct
     * arguments)</b><br> - Misuse of this method can result in unusable plots<br> - the set of plots must belong to one
     * owner and be rectangular<br> - the plot array must be sorted in ascending order<br> - Road will be removed where
     * required<br> - changes will be saved to DB<br>
     *
     * @param world
     * @param plotIds
     *
     * @return boolean (success)
     */
    public static boolean mergePlots(final World world, final ArrayList<PlotId> plotIds) {
        if (plotIds.size() < 2) {
            return false;
        }
        final PlotId pos1 = plotIds.get(0);
        final PlotId pos2 = plotIds.get(plotIds.size() - 1);

        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);

        manager.startPlotMerge(world, plotworld, plotIds);

        boolean result = false;

        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {

                boolean changed = false;

                final boolean lx = x < pos2.x;
                final boolean ly = y < pos2.y;

                final PlotId id = new PlotId(x, y);

                final Plot plot = PlotMain.getPlots(world).get(id);

                Plot plot2 = null;

                removeSign(world, plot);

                if (lx) {
                    if (ly) {
                        if (!plot.settings.getMerged(1) || !plot.settings.getMerged(2)) {
                            changed = true;
                            manager.removeRoadSouthEast(plotworld, plot);
                        }
                    }
                    if (!plot.settings.getMerged(1)) {
                        changed = true;
                        plot2 = PlotMain.getPlots(world).get(new PlotId(x + 1, y));
                        mergePlot(world, plot, plot2);
                        plot.settings.setMerged(1, true);
                        plot2.settings.setMerged(3, true);
                    }
                }
                if (ly) {
                    if (!plot.settings.getMerged(2)) {
                        changed = true;
                        plot2 = PlotMain.getPlots(world).get(new PlotId(x, y + 1));
                        mergePlot(world, plot, plot2);
                        plot.settings.setMerged(2, true);
                        plot2.settings.setMerged(0, true);
                    }
                }
            }
        }
        
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final PlotId id = new PlotId(x, y);
                final Plot plot = PlotMain.getPlots(world).get(id);
                DBFunc.setMerged(world.getName(), plot, plot.settings.getMerged());
            }
        }


        manager.finishPlotMerge(world, plotworld, plotIds);

        return result;
    }

    /**
     * Merges 2 plots Removes the road inbetween <br> - Assumes the first plot parameter is lower <br> - Assumes neither
     * are a Mega-plot <br> - Assumes plots are directly next to each other <br> - Saves to DB
     *
     * @param world
     * @param lesserPlot
     * @param greaterPlot
     */
    public static void mergePlot(final World world, final Plot lesserPlot, final Plot greaterPlot) {
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);

        if (lesserPlot.id.x.equals(greaterPlot.id.x)) {
            if (!lesserPlot.settings.getMerged(2)) {
                lesserPlot.settings.setMerged(2, true);
                greaterPlot.settings.setMerged(0, true);
                manager.removeRoadSouth(plotworld, lesserPlot);
            }
        } else {
            if (!lesserPlot.settings.getMerged(1)) {
                lesserPlot.settings.setMerged(1, true);
                greaterPlot.settings.setMerged(3, true);
                manager.removeRoadEast(plotworld, lesserPlot);
            }
        }
    }

    /*
     * Random number gen section
     */
    public static long nextLong() {
        final long a = state;
        state = xorShift64(a);
        return a;
    }

    /*
     * End of random number gen section
     */

    public static long xorShift64(long a) {
        a ^= (a << 21);
        a ^= (a >>> 35);
        a ^= (a << 4);
        return a;
    }

    public static int random(final int n) {
        if (n == 1) {
            return 0;
        }
        final long r = ((nextLong() >>> 32) * n) >> 32;
        return (int) r;
    }

    public static void removeSign(final World world, final Plot p) {
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final Location loc = manager.getSignLoc(world, plotworld, p);
        final Block bs = loc.getBlock();
        if (bs.getType() == Material.WALL_SIGN) {
            bs.setType(Material.AIR);
        }
    }

    public static void setSign(final Player player, final Plot p) {
        setSign(Bukkit.getWorld(p.world), player.getName(), p);
    }

    @SuppressWarnings("deprecation")
    public static void setSign(final World world, String name, final Plot p) {
        if (name == null) {
            name = "unknown";
        }
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final Location loc = manager.getSignLoc(world, plotworld, p);
        final Block bs = loc.getBlock();
        bs.setType(Material.AIR);
        bs.setTypeIdAndData(Material.WALL_SIGN.getId(), (byte) 2, false);
        final String id = p.id.x + ";" + p.id.y;
        final Sign sign = (Sign) bs.getState();
        sign.setLine(0, C.OWNER_SIGN_LINE_1.translated().replaceAll("%id%", id));
        sign.setLine(1, C.OWNER_SIGN_LINE_2.translated().replaceAll("%id%", id).replaceAll("%plr%", name));
        sign.setLine(2, C.OWNER_SIGN_LINE_3.translated().replaceAll("%id%", id).replaceAll("%plr%", name));
        sign.setLine(3, C.OWNER_SIGN_LINE_4.translated().replaceAll("%id%", id).replaceAll("%plr%", name));
        sign.update(true);
    }

    public static String getPlayerName(final UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }
        final OfflinePlayer plr = UUIDHandler.uuidWrapper.getOfflinePlayer(uuid);
        if (!plr.hasPlayedBefore()) {
            return "unknown";
        }
        return plr.getName();
    }

    public static String getStringSized(final int max, final String string) {
        if (string.length() > max) {
            return string.substring(0, max);
        }
        return string;
    }

    /**
     * Set a block quickly, attempts to use NMS if possible
     *
     * @param block
     * @param plotblock
     */
    public static boolean setBlock(final Block block, final PlotBlock plotblock) {

        if (canSetFast) {
            if ((block.getTypeId() != plotblock.id) || (plotblock.data != block.getData())) {
                try {
                    SetBlockFast.set(block.getWorld(), block.getX(), block.getY(), block.getZ(), plotblock.id, plotblock.data);
                    return true;
                } catch (final Throwable e) {
                    canSetFast = false;
                }
            }
            return false;
        }
        if (block.getData() == plotblock.data) {
            if (block.getTypeId() != plotblock.id) {
                block.setTypeId(plotblock.id);
            }
        } else {
            if (block.getTypeId() == plotblock.id) {
                block.setData(plotblock.data);
            } else {
                block.setTypeIdAndData(plotblock.id, plotblock.data, false);
            }
        }
        return false;
    }

    /**
     * Adjusts a plot wall
     *
     * @param player
     * @param plot
     * @param block
     */
    public static void adjustWall(final Player player, final Plot plot, final PlotBlock block) {
        final World world = player.getWorld();
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);

        manager.setWall(world, plotworld, plot.id, block);
        if (canSetFast) {
            SetBlockFast.update(player);
        }
    }

    public static void autoMerge(final World world, final Plot plot, final Player player) {
        if (plot == null) {
            return;
        }
        if (plot.owner == null) {
            return;
        }
        if (!plot.owner.equals(UUIDHandler.getUUID(player))) {
            return;
        }

        ArrayList<PlotId> plots;
        boolean merge = true;
        int count = 0;
        while (merge) {
            if (count > 16) {
                break;
            }
            count++;
            final PlotId bot = PlayerFunctions.getBottomPlot(world, plot).id;
            final PlotId top = PlayerFunctions.getTopPlot(world, plot).id;
            plots = PlayerFunctions.getPlotSelectionIds(new PlotId(bot.x, bot.y - 1), new PlotId(top.x, top.y));
            if (ownsPlots(world, plots, player, 0)) {
                final boolean result = mergePlots(world, plots);
                if (result) {
                    merge = true;
                    continue;
                }
            }
            plots = PlayerFunctions.getPlotSelectionIds(new PlotId(bot.x, bot.y), new PlotId(top.x + 1, top.y));
            if (ownsPlots(world, plots, player, 1)) {
                final boolean result = mergePlots(world, plots);
                if (result) {
                    merge = true;
                    continue;
                }
            }
            plots = PlayerFunctions.getPlotSelectionIds(new PlotId(bot.x, bot.y), new PlotId(top.x, top.y + 1));
            if (ownsPlots(world, plots, player, 2)) {
                final boolean result = mergePlots(world, plots);
                if (result) {
                    merge = true;
                    continue;
                }
            }
            plots = PlayerFunctions.getPlotSelectionIds(new PlotId(bot.x - 1, bot.y), new PlotId(top.x, top.y));
            if (ownsPlots(world, plots, player, 3)) {
                final boolean result = mergePlots(world, plots);
                if (result) {
                    merge = true;
                    continue;
                }
            }
            merge = false;
        }
        if (canSetFast) {
            SetBlockFast.update(player);
        }
    }

    private static boolean ownsPlots(final World world, final ArrayList<PlotId> plots, final Player player, final int dir) {
        final PlotId id_min = plots.get(0);
        final PlotId id_max = plots.get(plots.size() - 1);
        for (final PlotId myid : plots) {
            final Plot myplot = PlotMain.getPlots(world).get(myid);
            if ((myplot == null) || !myplot.hasOwner() || !(myplot.getOwner().equals(UUIDHandler.getUUID(player)))) {
                return false;
            }
            final PlotId top = PlayerFunctions.getTopPlot(world, myplot).id;
            if (((top.x > id_max.x) && (dir != 1)) || ((top.y > id_max.y) && (dir != 2))) {
                return false;
            }
            final PlotId bot = PlayerFunctions.getBottomPlot(world, myplot).id;
            if (((bot.x < id_min.x) && (dir != 3)) || ((bot.y < id_min.y) && (dir != 0))) {
                return false;
            }
        }
        return true;
    }
    
    public static void updateWorldBorder(Plot plot) {
    	if (!worldBorder.containsKey(plot.world)) {
    		return;
    	}
    	String world = plot.world;
    	PlotManager manager = PlotMain.getPlotManager(world);
    	PlotWorld plotworld = PlotMain.getWorldSettings(world);
    	Location bot = manager.getPlotBottomLocAbs(plotworld, plot.id);
		Location top = manager.getPlotTopLocAbs(plotworld, plot.id);
		int border = worldBorder.get(plot.world);
		int botmax = Math.max(Math.abs(bot.getBlockX()), Math.abs(bot.getBlockZ()));
		int topmax = Math.max(Math.abs(top.getBlockX()), Math.abs(top.getBlockZ()));
		int max = Math.max(botmax, topmax);
		if (max > border ) {
			worldBorder.put(plot.world, max);
		}
    }

    public static boolean createPlot(final Player player, final Plot plot) {
    	if (PlotHelper.worldBorder.containsKey(plot.world)) {
    		updateWorldBorder(plot);
    	}
        final World w = plot.getWorld();
        final Plot p = new Plot(plot.id, UUIDHandler.getUUID(player), plot.settings.getBiome(), new ArrayList<UUID>(), new ArrayList<UUID>(), w.getName());
        PlotMain.updatePlot(p);
        DBFunc.createPlotAndSettings(p);
        final PlotWorld plotworld = PlotMain.getWorldSettings(w);
        if (plotworld.AUTO_MERGE) {
            autoMerge(w, p, player);
        }

        return true;
    }

    public static int getLoadedChunks(final World world) {
        return world.getLoadedChunks().length;
    }

    public static int getEntities(final World world) {
        return world.getEntities().size();
    }

    // public static void adjustLinkedPlots(String id) {
    // World world = Bukkit.getWorld(Settings.PLOT_WORLD);
    // int x = getIdX(id);
    // int z = getIdZ(id);
    //
    // plot p11 = getPlot(id);
    // if (p11 != null) {
    // plot p01, p10, p12, p21, p00, p02, p20, p22;
    // p01 = getPlot(x - 1, z);
    // p10 = getPlot(x, z - 1);
    // p12 = getPlot(x, z + 1);
    // p21 = getPlot(x + 1, z);
    // p00 = getPlot(x - 1, z - 1);
    // p02 = getPlot(x - 1, z + 1);
    // p20 = getPlot(x + 1, z - 1);
    // p22 = getPlot(x + 1, z + 1);
    // if (p01 != null && p01.owner.equals(p11.owner)) {
    // fillroad(p01, p11, world);
    // }
    //
    // if (p10 != null && p10.owner.equals(p11.owner)) {
    // fillroad(p10, p11, world);
    // }
    //
    // if (p12 != null && p12.owner.equals(p11.owner)) {
    // fillroad(p12, p11, world);
    // }
    //
    // if (p21 != null && p21.owner.equals(p11.owner)) {
    // fillroad(p21, p11, world);
    // }
    //
    // if (p00 != null && p10 != null && p01 != null
    // && p00.owner.equals(p11.owner)
    // && p11.owner.equals(p10.owner)
    // && p10.owner.equals(p01.owner)) {
    // fillmiddleroad(p00, p11, world);
    // }
    //
    // if (p10 != null && p20 != null && p21 != null
    // && p10.owner.equals(p11.owner)
    // && p11.owner.equals(p20.owner)
    // && p20.owner.equals(p21.owner)) {
    // fillmiddleroad(p20, p11, world);
    // }
    //
    // if (p01 != null && p02 != null && p12 != null
    // && p01.owner.equals(p11.owner)
    // && p11.owner.equals(p02.owner)
    // && p02.owner.equals(p12.owner)) {
    // fillmiddleroad(p02, p11, world);
    // }
    //
    // if (p12 != null && p21 != null && p22 != null
    // && p12.owner.equals(p11.owner)
    // && p11.owner.equals(p21.owner)
    // && p21.owner.equals(p22.owner)) {
    // fillmiddleroad(p22, p11, world);
    // }
    // }
    // }
    //
    // public static void fillroad(plot plot1, plot plot2, World w) {
    // Location bottomPlot1, topPlot1, bottomPlot2, topPlot2;
    // bottomPlot1 = getPlotBottomLoc(w, plot1.id);
    // topPlot1 = getPlotTopLoc(w, plot1.id);
    // bottomPlot2 = getPlotBottomLoc(w, plot2.id);
    // topPlot2 = getPlotTopLoc(w, plot2.id);
    //
    // int minX, maxX, minZ, maxZ;
    //
    // boolean isWallX;
    //
    // int h = Settings.ROAD_HEIGHT;
    // int wallId = Settings.WALL_BLOCK;
    // int fillId = Settings.TOP_BLOCK;
    //
    // if(bottomPlot1.getBlockX() == bottomPlot2.getBlockX()) {
    // minX = bottomPlot1.getBlockX();
    // maxX = topPlot1.getBlockX();
    //
    // minZ = Math.min(bottomPlot1.getBlockZ(), bottomPlot2.getBlockZ()) +
    // Settings.PLOT_WIDTH;
    // maxZ = Math.min(topPlot1.getBlockZ(), topPlot2.getBlockZ()) -
    // Settings.PLOT_WIDTH;
    // } else {
    // minZ = bottomPlot1.getBlockZ();
    // maxZ = topPlot1.getBlockZ();
    //
    // minX = Math.min(bottomPlot1.getBlockX(), bottomPlot2.getBlockX()) +
    // Settings.PLOT_WIDTH;
    // maxX = Math.max(topPlot1.getBlockX(), topPlot2.getBlockX()) -
    // Settings.PLOT_WIDTH;
    // }
    //
    // isWallX = (maxX - minX) > (maxZ - minZ);
    //
    // if(isWallX) {
    // minX--;
    // maxX++;
    // } else {
    // minZ--;
    // maxZ++;
    // }
    //
    // for(int x = minX; x <= maxX; x++) {
    // for(int z = minZ; x <= maxZ; z++) {
    // for(int y = h; y < h + 3; y++) {
    // if(y >= (h + 2)) {
    // w.getBlockAt(x,y,z).setType(Material.AIR);
    // } else if(y == (h + 1)) {
    // if(isWallX && (x == minX || x == maxX)) {
    // w.getBlockAt(x,y,z).setTypeIdAndData(wallId, (byte) 0, true);
    // } else if(!isWallX && (z == minZ || z == maxZ)) {
    // w.getBlockAt(x,y,z).setTypeIdAndData(wallId, (byte) 0, true);
    // } else {
    // w.getBlockAt(x,y,z).setType(Material.AIR);
    // }
    // } else {
    // w.getBlockAt(x,y,z).setTypeIdAndData(fillId, (byte) 0, true);
    // }
    // }
    // }
    // }
    // }
    //
    // public static void fillmiddleroad(plot p1, plot p2, World w) {
    // Location b1 = getPlotBottomLoc(w, p1.id);
    // Location t1 = getPlotTopLoc(w, p1.id);
    // Location b2 = getPlotBottomLoc(w, p2.id);
    // Location t2 = getPlotTopLoc(w, p2.id);
    //
    // int minX, maxX, minZ, maxZ;
    //
    // int h = Settings.ROAD_HEIGHT;
    // int fillID = Settings.TOP_BLOCK;
    //
    // minX = Math.min(t1.getBlockX(), t2.getBlockX());
    // maxX = Math.max(b1.getBlockX(), b2.getBlockX());
    //
    // minZ = Math.min(t1.getBlockZ(), t2.getBlockZ());
    // maxZ = Math.max(b1.getBlockZ(), b2.getBlockZ());
    //
    // for(int x = minX; x <= maxX; x++) {
    // for(int z = minZ; z <= maxZ; z++) {
    // for(int y = h; y < h + 3; y++) {
    // if(y >= (h + 1)) {
    // w.getBlockAt(x,y,z).setType(Material.AIR);
    // } else {
    // w.getBlockAt(x,y,z).setTypeId(fillID);
    // }
    // }
    // }
    // }
    // }

    public static int getTileEntities(final World world) {
        PlotMain.getWorldSettings(world);
        int x = 0;
        for (final Chunk chunk : world.getLoadedChunks()) {
            x += chunk.getTileEntities().length;
        }
        return x;
    }

    public static double getWorldFolderSize(final World world) {
        // long size = FileUtil.sizeOfDirectory(world.getWorldFolder());
        final File folder = world.getWorldFolder();
        final long size = folder.length();
        return (((size) / 1024) / 1024);
    }

    public static String createId(final int x, final int z) {
        return x + ";" + z;
    }

    public static void adjustWallFilling(final Player requester, final Plot plot, final PlotBlock block) {
        if (runners.containsKey(plot)) {
            PlayerFunctions.sendMessage(requester, C.WAIT_FOR_TIMER);
            return;
        }
        final World world = requester.getWorld();
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        manager.setWallFilling(world, plotworld, plot.id, block);
        PlayerFunctions.sendMessage(requester, C.SET_BLOCK_ACTION_FINISHED);
        if (canSetFast) {
            SetBlockFast.update(requester);
        }
    }

    public static void setFloor(final Player requester, final Plot plot, final PlotBlock[] blocks) {
        if (runners.containsKey(plot)) {
            PlayerFunctions.sendMessage(requester, C.WAIT_FOR_TIMER);
            return;
        }

        final World world = requester.getWorld();
        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        PlayerFunctions.sendMessage(requester, C.SET_BLOCK_ACTION_FINISHED);
        manager.setFloor(world, plotworld, plot.id, blocks);
        if (canSetFast) {
            SetBlockFast.update(requester);
        }
    }

    public static int square(final int x) {
        return x * x;
    }

    public static short[] getBlock(final String block) {
        if (block.contains(":")) {
            final String[] split = block.split(":");
            return new short[]{Short.parseShort(split[0]), Short.parseShort(split[1])};
        }
        return new short[]{Short.parseShort(block), 0};
    }

    public static void clearAllEntities(final World world, final Plot plot, final boolean tile) {

        final List<Entity> entities = world.getEntities();
        for (final Entity entity : entities) {
            final PlotId id = PlayerFunctions.getPlot(entity.getLocation());
            if (plot.id.equals(id)) {
                if (entity instanceof Player) {
                    final Player player = (Player) entity;
                    PlotMain.teleportPlayer(player, entity.getLocation(), plot);
                    PlotListener.plotExit(player, plot);
                } else {
                    entity.remove();
                }
            }
        }
    }

    public static void clear(final World world, final Plot plot, final boolean isDelete) {
        if (runners.containsKey(plot)) {
            PlayerFunctions.sendMessage(null, C.WAIT_FOR_TIMER);
            return;
        }
        final PlotManager manager = PlotMain.getPlotManager(world);

        final Location pos1 = PlotHelper.getPlotBottomLoc(world, plot.id).add(1, 0, 1);

        final int prime = 31;
        int h = 1;
        h = (prime * h) + pos1.getBlockX();
        h = (prime * h) + pos1.getBlockZ();
        state = h;

        manager.clearPlot(world, plot, isDelete);
        
        if (canSetFast) {
            final Plugin plugin = PlotMain.getMain();
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    PlotHelper.setBiome(world, plot, Biome.FOREST);
                    refreshPlotChunks(world, plot);
                }
            }, 90L);
        }
    }

    /**
     * Clear a plot
     *
     * @param requester
     * @param plot
     */
    public static void clear(final Player requester, final Plot plot, final boolean isDelete) {
        if (requester == null) {
            clearAllEntities(plot.getWorld(), plot, false);
            clear(plot.getWorld(), plot, isDelete);
            removeSign(plot.getWorld(), plot);
            return;
        }
        if (runners.containsKey(plot)) {
            PlayerFunctions.sendMessage(requester, C.WAIT_FOR_TIMER);
            return;
        }

        PlayerFunctions.sendMessage(requester, C.CLEARING_PLOT);

        final long start = System.currentTimeMillis();

        final World world;
        world = requester.getWorld();

        clearAllEntities(world, plot, false);
        clear(world, plot, isDelete);
        removeSign(world, plot);
        final Plugin plugin = PlotMain.getMain();
        PlayerFunctions.sendMessage(requester, C.CLEARING_DONE.s().replaceAll("%time%", "" + ((System.currentTimeMillis() - start))));
    }

    public static void setCuboid(final World world, final Location pos1, final Location pos2, final PlotBlock newblock) {
        if (!canSetFast) {
            for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                    for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                        final Block block = world.getBlockAt(x, y, z);
                        if (!((block.getTypeId() == newblock.id) && (block.getData() == newblock.data))) {
                            block.setTypeIdAndData(newblock.id, newblock.data, false);
                        }
                    }
                }
            }
        } else {
            try {
                for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                    for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                        for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                            final Block block = world.getBlockAt(x, y, z);
                            if (!((block.getTypeId() == newblock.id) && (block.getData() == newblock.data))) {
                                SetBlockFast.set(world, x, y, z, newblock.id, newblock.data);
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                //
            }
        }
    }

    public static void setCuboid(final World world, final Location pos1, final Location pos2, final PlotBlock[] blocks) {
        if (blocks.length == 1) {
            setCuboid(world, pos1, pos2, blocks[0]);
            return;
        }
        if (!canSetFast) {
            for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                    for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                        final int i = random(blocks.length);
                        final PlotBlock newblock = blocks[i];
                        final Block block = world.getBlockAt(x, y, z);
                        if (!((block.getTypeId() == newblock.id) && (block.getData() == newblock.data))) {
                            block.setTypeIdAndData(newblock.id, newblock.data, false);
                        }
                    }
                }
            }
        } else {
            try {
                for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                    for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                        for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                            final int i = random(blocks.length);
                            final PlotBlock newblock = blocks[i];
                            final Block block = world.getBlockAt(x, y, z);
                            if (!((block.getTypeId() == newblock.id) && (block.getData() == newblock.data))) {
                                SetBlockFast.set(world, x, y, z, newblock.id, newblock.data);
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                //
            }
        }
    }

    public static void setSimpleCuboid(final World world, final Location pos1, final Location pos2, final PlotBlock newblock) {
        if (!canSetFast) {
            for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                    for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                        final Block block = world.getBlockAt(x, y, z);
                        if (!((block.getTypeId() == newblock.id))) {
                            block.setTypeId(newblock.id, false);
                        }
                    }
                }
            }
        } else {
            try {
                for (int y = pos1.getBlockY(); y < pos2.getBlockY(); y++) {
                    for (int x = pos1.getBlockX(); x < pos2.getBlockX(); x++) {
                        for (int z = pos1.getBlockZ(); z < pos2.getBlockZ(); z++) {
                            final Block block = world.getBlockAt(x, y, z);
                            if (!((block.getTypeId() == newblock.id))) {
                                SetBlockFast.set(world, x, y, z, newblock.id, (byte) 0);
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                //
            }
        }
    }

    public static void setBiome(final World world, final Plot plot, final Biome b) {
        final int bottomX = getPlotBottomLoc(world, plot.id).getBlockX();
        final int topX = getPlotTopLoc(world, plot.id).getBlockX() + 1;
        final int bottomZ = getPlotBottomLoc(world, plot.id).getBlockZ();
        final int topZ = getPlotTopLoc(world, plot.id).getBlockZ() + 1;

        final Block block = world.getBlockAt(getPlotBottomLoc(world, plot.id).add(1, 1, 1));
        final Biome biome = block.getBiome();

        if (biome.equals(b)) {
            return;
        }

        for (int x = bottomX; x <= topX; x++) {
            for (int z = bottomZ; z <= topZ; z++) {
                final Block blk = world.getBlockAt(x, 0, z);
                final Biome c = blk.getBiome();
                if (c.equals(b)) {
                    x += 15;
                    continue;
                }
                blk.setBiome(b);
            }
        }
    }

    public static int getHeighestBlock(final World world, final int x, final int z) {
        boolean safe = false;
        for (int i = 1; i < world.getMaxHeight(); i++) {
            final int id = world.getBlockAt(x, i, z).getTypeId();
            if (id == 0) {
                if (safe) {
                    return i - 1;
                }
                safe = true;
            }
        }
        return 64;
    }

    /**
     * Get plot home
     *
     * @param w      World in which the plot is located
     * @param plotid Plot ID
     *
     * @return Home Location
     */
    public static Location getPlotHome(final World w, final PlotId plotid) {
        Plot plot = getPlot(w, plotid);
        BlockLoc home = plot.settings.getPosition();
        final Location bot = getPlotBottomLoc(w, plotid);
    	PlotManager manager = PlotMain.getPlotManager(w);
        if (home == null || (home.x == 0 && home.z == 0)) {
            final Location top = getPlotTopLoc(w, plotid);
            final int x = top.getBlockX() - bot.getBlockX();
            final int z = top.getBlockZ() - bot.getBlockZ();
            final int y = Math.max(getHeighestBlock(w, x, z), manager.getSignLoc(w, PlotMain.getWorldSettings(w), plot).getBlockY());
            return new Location(w, bot.getBlockX() + (x / 2), y, bot.getBlockZ() + (z / 2));
        }
        else {
        	final int y = Math.max(getHeighestBlock(w, home.x, home.z), home.y);
            return bot.add(home.x, y, home.z);
        }
    }

    /**
     * Retrieve the location of the default plot home position
     *
     * @param plot Plot
     *
     * @return the location
     */
    public static Location getPlotHomeDefault(final Plot plot) {
        final Location l = getPlotBottomLoc(plot.getWorld(), plot.getId()).subtract(0, 0, 0);
        l.setY(getHeighestBlock(plot.getWorld(), l.getBlockX(), l.getBlockZ()));
        return l;
    }

    /**
     * Get the plot home
     *
     * @param w    World
     * @param plot Plot Object
     *
     * @return Plot Home Location
     *
     * @see #getPlotHome(org.bukkit.World, com.intellectualcrafters.plot.object.PlotId)
     */
    public static Location getPlotHome(final World w, final Plot plot) {
        return getPlotHome(w, plot.id);
    }

    /**
     * Refresh the plot chunks
     *
     * @param world World in which the plot is located
     * @param plot  Plot Object
     */
    public static void refreshPlotChunks(final World world, final Plot plot) {
        final int bottomX = getPlotBottomLoc(world, plot.id).getBlockX();
        final int topX = getPlotTopLoc(world, plot.id).getBlockX();
        final int bottomZ = getPlotBottomLoc(world, plot.id).getBlockZ();
        final int topZ = getPlotTopLoc(world, plot.id).getBlockZ();

        final int minChunkX = (int) Math.floor((double) bottomX / 16);
        final int maxChunkX = (int) Math.floor((double) topX / 16);
        final int minChunkZ = (int) Math.floor((double) bottomZ / 16);
        final int maxChunkZ = (int) Math.floor((double) topZ / 16);

        final ArrayList<Chunk> chunks = new ArrayList<>();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (canSendChunk) {
                    final Chunk chunk = world.getChunkAt(x, z);
                    chunks.add(chunk);
                } else {
                    world.refreshChunk(x, z);
                }
            }
        }
        try {
            SendChunk.sendChunk(chunks);
        } catch (final Throwable e) {
            canSendChunk = false;
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    world.refreshChunk(x, z);
                }
            }
        }
    }

    /**
     * Gets the top plot location of a plot (all plots are treated as small plots) - To get the top loc of a mega plot
     * use getPlotTopLoc(...)
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static Location getPlotTopLocAbs(final World world, final PlotId id) {
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final PlotManager manager = PlotMain.getPlotManager(world);
        return manager.getPlotTopLocAbs(plotworld, id);
    }

    /**
     * Gets the bottom plot location of a plot (all plots are treated as small plots) - To get the top loc of a mega
     * plot use getPlotBottomLoc(...)
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static Location getPlotBottomLocAbs(final World world, final PlotId id) {
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final PlotManager manager = PlotMain.getPlotManager(world);
        return manager.getPlotBottomLocAbs(plotworld, id);
    }

    /**
     * Obtains the width of a plot (x width)
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static int getPlotWidth(final World world, final PlotId id) {
        return getPlotTopLoc(world, id).getBlockX() - getPlotBottomLoc(world, id).getBlockX();
    }

    /**
     * Gets the top loc of a plot (if mega, returns top loc of that mega plot) - If you would like each plot treated as
     * a small plot use getPlotTopLocAbs(...)
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static Location getPlotTopLoc(final World world, PlotId id) {
        final Plot plot = PlotMain.getPlots(world).get(id);
        if (plot != null) {
            id = PlayerFunctions.getTopPlot(world, plot).id;
        }
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final PlotManager manager = PlotMain.getPlotManager(world);
        return manager.getPlotTopLocAbs(plotworld, id);
    }

    /**
     * Gets the bottom loc of a plot (if mega, returns bottom loc of that mega plot) - If you would like each plot
     * treated as a small plot use getPlotBottomLocAbs(...)
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static Location getPlotBottomLoc(final World world, PlotId id) {
        final Plot plot = PlotMain.getPlots(world).get(id);
        if (plot != null) {
            id = PlayerFunctions.getBottomPlot(world, plot).id;
        }
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);
        final PlotManager manager = PlotMain.getPlotManager(world);
        return manager.getPlotBottomLocAbs(plotworld, id);
    }
    
    /**
     * Fetches the plot from the main class
     *
     * @param world
     * @param id
     *
     * @return
     */
    public static Plot getPlot(final World world, final PlotId id) {
        if (id == null) {
            return null;
        }
        if (PlotMain.getPlots(world).containsKey(id)) {
            return PlotMain.getPlots(world).get(id);
        }
        return new Plot(id, null, Biome.FOREST, new ArrayList<UUID>(), new ArrayList<UUID>(), world.getName());
    }

    /**
     * Returns the plot at a given location
     *
     * @param loc
     *
     * @return
     */
    public static Plot getCurrentPlot(final Location loc) {
        final PlotId id = PlayerFunctions.getPlot(loc);
        if (id == null) {
            return null;
        }
        if (PlotMain.getPlots(loc.getWorld()).containsKey(id)) {
            return PlotMain.getPlots(loc.getWorld()).get(id);
        }
        return new Plot(id, null, Biome.FOREST, new ArrayList<UUID>(), new ArrayList<UUID>(), loc.getWorld().getName());
    }
}
