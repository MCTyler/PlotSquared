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

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.util.PlayerFunctions;
import com.intellectualcrafters.plot.util.PlotHelper;

public class Target extends SubCommand {

    public Target() {
        super(Command.TARGET, "Target a plot with your compass", "target <X;Z>", CommandCategory.ACTIONS, true);
    }

    @Override
    public boolean execute(final Player plr, final String... args) {
        if (!PlotMain.isPlotWorld(plr.getWorld())) {
            PlayerFunctions.sendMessage(plr, C.NOT_IN_PLOT_WORLD);
            return false;
        }
        if (args.length == 1) {
            PlotId id = PlotHelper.parseId(args[1]);
            if (id == null) {
                PlayerFunctions.sendMessage(plr, C.NOT_VALID_PLOT_ID);
                return false;
            }
            Location loc = PlotHelper.getPlotHome(plr.getWorld(), id);
            plr.setCompassTarget(loc);
            PlayerFunctions.sendMessage(plr, C.COMPASS_TARGET);
            return true;
        }
        PlayerFunctions.sendMessage(plr, C.COMMAND_SYNTAX, "/plot target <X;Z>");
        return false;
    }
}
