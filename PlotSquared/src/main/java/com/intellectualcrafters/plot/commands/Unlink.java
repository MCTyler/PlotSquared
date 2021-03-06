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

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.events.PlotUnlinkEvent;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotManager;
import com.intellectualcrafters.plot.object.PlotWorld;
import com.intellectualcrafters.plot.util.PlayerFunctions;
import com.intellectualcrafters.plot.util.PlotHelper;
import com.intellectualcrafters.plot.util.SetBlockFast;
import com.intellectualcrafters.plot.util.UUIDHandler;

/**
 * Created 2014-08-01 for PlotSquared
 *
 * @author Citymonstret
 */
public class Unlink extends SubCommand {

    public Unlink() {
        super(Command.UNLINK, "Unlink a mega-plot", "unlink", CommandCategory.ACTIONS, true);
    }

    @Override
    public boolean execute(final Player plr, final String... args) {
        if (!PlayerFunctions.isInPlot(plr)) {
            return sendMessage(plr, C.NOT_IN_PLOT);
        }
        final Plot plot = PlayerFunctions.getCurrentPlot(plr);
        if (((plot == null) || !plot.hasOwner() || !plot.getOwner().equals(UUIDHandler.getUUID(plr))) && !PlotMain.hasPermission(plr, "plots.admin.command.unlink")) {
            return sendMessage(plr, C.NO_PLOT_PERMS);
        }
        if (PlayerFunctions.getTopPlot(plr.getWorld(), plot).equals(PlayerFunctions.getBottomPlot(plr.getWorld(), plot))) {
            return sendMessage(plr, C.UNLINK_IMPOSSIBLE);
        }

        final World world = plr.getWorld();
        final PlotId pos1 = PlayerFunctions.getBottomPlot(world, plot).id;
        final PlotId pos2 = PlayerFunctions.getTopPlot(world, plot).id;
        final ArrayList<PlotId> ids = PlayerFunctions.getPlotSelectionIds(pos1, pos2);

        final PlotUnlinkEvent event = new PlotUnlinkEvent(world, ids);

        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            event.setCancelled(true);
            PlayerFunctions.sendMessage(plr, "&cUnlink has been cancelled");
            return false;
        }

        final PlotManager manager = PlotMain.getPlotManager(world);
        final PlotWorld plotworld = PlotMain.getWorldSettings(world);

        manager.startPlotUnlink(world, plotworld, ids);

        for (final PlotId id : ids) {
            final Plot myplot = PlotMain.getPlots(world).get(id);

            if (plot == null) {
                continue;
            }

            if (plot.helpers != null) {
                myplot.helpers = plot.helpers;
            }
            if (plot.denied != null) {
                myplot.denied = plot.denied;
            }
            myplot.deny_entry = plot.deny_entry;
            myplot.settings.setMerged(new boolean[]{false, false, false, false});
            DBFunc.setMerged(world.getName(), myplot, myplot.settings.getMerged());
        }

        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final boolean lx = x < pos2.x;
                final boolean ly = y < pos2.y;

                final Plot p = PlotHelper.getPlot(world, new PlotId(x, y));

                if (lx) {
                    manager.createRoadEast(plotworld, p);
                    if (ly) {
                        manager.createRoadSouthEast(plotworld, p);
                    }

                }

                if (ly) {
                    manager.createRoadSouth(plotworld, p);
                }
                PlotHelper.setSign(plr.getWorld(), UUIDHandler.getName(plot.owner), plot);
            }
        }
        try {
            if (PlotHelper.canSetFast) {
                SetBlockFast.update(plr);
            }
        } catch (final Exception e) {
            // execute(final Player plr, final String... args) {
            try {
                PlotMain.sendConsoleSenderMessage("Error on: " + getClass().getMethod("execute", Player.class, String[].class).toGenericString() + ":119, when trying to use \"SetBlockFast#update\"");
            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }

        manager.finishPlotUnlink(world, plotworld, ids);

        PlayerFunctions.sendMessage(plr, "&6Plots unlinked successfully!");
        return true;
    }
}
