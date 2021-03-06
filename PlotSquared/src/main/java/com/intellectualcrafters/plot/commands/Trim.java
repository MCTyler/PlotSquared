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

package com.intellectualcrafters.plot.commands;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.config.Settings;
import com.intellectualcrafters.plot.generator.HybridPlotManager;
import com.intellectualcrafters.plot.generator.HybridPlotWorld;
import com.intellectualcrafters.plot.object.ChunkLoc;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.util.ChunkManager;
import com.intellectualcrafters.plot.util.ExpireManager;
import com.intellectualcrafters.plot.util.PlayerFunctions;
import com.intellectualcrafters.plot.util.TaskManager;
import com.intellectualcrafters.plot.util.UUIDHandler;

public class Trim extends SubCommand {

    public static boolean TASK = false;
    private static int TASK_ID = 0;
    
    public Trim() {
        super("trim", "plots.admin", "Delete unmodified portions of your plotworld", "trim", "", CommandCategory.DEBUG, false);
    }

    public PlotId getId(String id) {
        try {
            String[] split = id.split(";");
            return new PlotId(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
        }
        catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public boolean execute(final Player plr, final String... args) {
        if (plr != null) {
            PlayerFunctions.sendMessage(plr, (C.NOT_CONSOLE));
            return false;
        }
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            PlotId id = getId(arg);
            if (id != null) {
                PlayerFunctions.sendMessage(plr, "/plot trim x;z &l<world>");
                return false;
            }
            if (arg.equals("all")) {
                PlayerFunctions.sendMessage(plr, "/plot trim all &l<world>");
                return false;
            }
            PlayerFunctions.sendMessage(plr, C.TRIM_SYNTAX);
            return false;
        }
        if (args.length != 2) {
            PlayerFunctions.sendMessage(plr, C.TRIM_SYNTAX);
            return false;
        }
        String arg = args[0].toLowerCase();
        if (!arg.equals("all")) {
            PlayerFunctions.sendMessage(plr, C.TRIM_SYNTAX);
            return false;
        }
        final World world = Bukkit.getWorld(args[1]);
        if (world == null || PlotMain.getWorldSettings(world) == null) {
            PlayerFunctions.sendMessage(plr, C.NOT_VALID_WORLD);
            return false;
        }
        if (runTrimTask(world)) {
            sendMessage(C.TRIM_START.s());
            return true;
        }
        sendMessage(C.TRIM_IN_PROGRESS.s());
        return false;
    }
    
    public boolean runTrimTask(final World world) {
        if (Trim.TASK) {
            return false;
        }
        Trim.TASK = true;
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                final HybridPlotManager manager = (HybridPlotManager) PlotMain.getPlotManager(world);
                final HybridPlotWorld plotworld = (HybridPlotWorld) PlotMain.getWorldSettings(world);
                final String worldname = world.getName();
                String directory = new File(".").getAbsolutePath() + File.separator + world.getName() + File.separator + "region";
                File folder = new File(directory);
                File[] regionFiles = folder.listFiles();
                ArrayList<ChunkLoc> chunkChunks = new ArrayList<>();
                for (File file : regionFiles) {
                    String name = file.getName();
                    if (name.endsWith("mca")) {
                        if (file.getTotalSpace() <= 8192) {
                            file.delete();
                        }
                        else {
                            boolean delete = false;
                            Path path = Paths.get(file.getPath());
                            try {
                                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                                long creation = attr.creationTime().toMillis();
                                long modification = file.lastModified();
                                long diff = Math.abs(creation - modification);
                                if (diff < 10000) {
                                    PlotMain.sendConsoleSenderMessage("&6 - Deleted region "+name+" (max 256 chunks)");
                                    file.delete();
                                    delete = true;
                                }
                            } catch (Exception e) {
                                
                            }
                            if (!delete) {
                                String[] split = name.split("\\.");
                                try {
                                    int x = Integer.parseInt(split[1]);
                                    int z = Integer.parseInt(split[2]);
                                    ChunkLoc loc = new ChunkLoc(x, z);
                                    chunkChunks.add(loc);
                                }
                                catch (Exception e) {  }
                            }
                        }
                    }
                }
                final Set<Plot> plots = ExpireManager.getOldPlots(world.getName()).keySet();        
                Trim.TASK_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PlotMain.getMain(), new Runnable() {
                    @Override
                    public void run() {
                        if (manager != null && plots.size() > 0) {
                            Plot plot = plots.iterator().next();
                            if (plot.hasOwner()) {
                                HybridPlotManager.checkModified(plot, 0);
                            }
                            if (plot.owner == null || !HybridPlotManager.checkModified(plot, plotworld.REQUIRED_CHANGES)) {
                                PlotMain.removePlot(worldname, plot.id, true);
                            }
                            plots.remove(0);
                        }
                        else {
                            trimPlots(world);
                            Trim.TASK = false;
                            sendMessage("Done!");
                            Bukkit.getScheduler().cancelTask(Trim.TASK_ID);
                            return;
                        }
                    }
                }, 1, 1);
            }
        });
        return true;
    }
    
    private void trimPlots(World world) {
        String worldname = world.getName();
        ArrayList<ChunkLoc> chunks = ChunkManager.getChunkChunks(world);
        for (ChunkLoc loc : chunks) {
            int sx = loc.x << 4;
            int sz = loc.z << 4;
            
            boolean delete = true;
            
            loop:
            for (int x = sx; x < sx + 16; x++) {
                for (int z = sz; z < sz + 16; z++) {
                    Chunk chunk = world.getChunkAt(x, z);
                    if (ChunkManager.hasPlot(world, chunk)) {
                        delete = false;
                        break loop;
                    }
                }
            }
            if (delete) {
                ChunkManager.deleteRegionFile(worldname, loc);
            }
        }
    }
    
    private void sendMessage(final String message) {
        PlotMain.sendConsoleSenderMessage("&3PlotSquared -> World trim&8: " + message);
    }
    
}
