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

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.events.PlayerPlotDeniedEvent;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.util.PlayerFunctions;
import com.intellectualcrafters.plot.util.UUIDHandler;

@SuppressWarnings("deprecation") public class Denied extends SubCommand {

    public Denied() {
        super(Command.DENIED, "Manage plot helpers", "denied {add|remove} {player}", CommandCategory.ACTIONS, true);
    }

    @Override
    public boolean execute(final Player plr, final String... args) {
        if (args.length < 2) {
            PlayerFunctions.sendMessage(plr, C.DENIED_NEED_ARGUMENT);
            return true;
        }
        if (!PlayerFunctions.isInPlot(plr)) {
            PlayerFunctions.sendMessage(plr, C.NOT_IN_PLOT);
            return true;
        }
        final Plot plot = PlayerFunctions.getCurrentPlot(plr);
        if ((plot == null) || !plot.hasOwner()) {
            PlayerFunctions.sendMessage(plr, C.PLOT_UNOWNED);
            return false;
        }
        if (!plot.getOwner().equals(UUIDHandler.getUUID(plr)) && !PlotMain.hasPermission(plr, "plots.admin.command.denied")) {
            PlayerFunctions.sendMessage(plr, C.NO_PLOT_PERMS);
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            UUID uuid;
            if (args[1].equalsIgnoreCase("*")) {
                uuid = DBFunc.everyone;

            } else {
                uuid = UUIDHandler.getUUID(args[1]);
            }
            if (uuid == null) {
                PlayerFunctions.sendMessage(plr, C.INVALID_PLAYER, args[1]);
                return false;
            }
            if (!plot.denied.contains(uuid)) {
                if (plot.owner.equals(uuid)) {
                    PlayerFunctions.sendMessage(plr, C.ALREADY_OWNER);
                    return false;
                }
                if (plot.trusted.contains(uuid)) {
                    plot.trusted.remove(uuid);
                    DBFunc.removeTrusted(plr.getWorld().getName(), plot, uuid);
                }
                if (plot.helpers.contains(uuid)) {
                    plot.helpers.remove(uuid);
                    DBFunc.removeHelper(plr.getWorld().getName(), plot, uuid);
                }
                plot.addDenied(uuid);
                DBFunc.setDenied(plr.getWorld().getName(), plot, uuid);
                final PlayerPlotDeniedEvent event = new PlayerPlotDeniedEvent(plr, plot, uuid, true);
                Bukkit.getPluginManager().callEvent(event);
            } else {
                PlayerFunctions.sendMessage(plr, C.ALREADY_ADDED);
                return false;
            }
            final Player player = UUIDHandler.uuidWrapper.getPlayer(uuid);
            if (!uuid.equals(DBFunc.everyone) && (player != null) && player.isOnline()) {
                final Plot pl = PlayerFunctions.getCurrentPlot(player);
                if ((pl != null) && pl.id.equals(plot.id)) {
                    PlayerFunctions.sendMessage(player, C.YOU_BE_DENIED);
                    player.teleport(player.getWorld().getSpawnLocation());
                }
            }
            PlayerFunctions.sendMessage(plr, C.DENIED_ADDED);
            return true;
        } else if (args[0].equalsIgnoreCase("remove")) {
            if (args[1].equalsIgnoreCase("*")) {
                final UUID uuid = DBFunc.everyone;
                if (!plot.denied.contains(uuid)) {
                    PlayerFunctions.sendMessage(plr, C.WAS_NOT_ADDED);
                    return true;
                }
                plot.removeDenied(uuid);
                DBFunc.removeDenied(plr.getWorld().getName(), plot, uuid);
                PlayerFunctions.sendMessage(plr, C.DENIED_REMOVED);
                return true;
            }
            final UUID uuid = UUIDHandler.getUUID(args[1]);
            plot.removeDenied(uuid);
            DBFunc.removeDenied(plr.getWorld().getName(), plot, uuid);
            final PlayerPlotDeniedEvent event = new PlayerPlotDeniedEvent(plr, plot, uuid, false);
            Bukkit.getPluginManager().callEvent(event);
            PlayerFunctions.sendMessage(plr, C.DENIED_REMOVED);
        } else {
            PlayerFunctions.sendMessage(plr, C.DENIED_NEED_ARGUMENT);
            return true;
        }
        return true;
    }
}
