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

package com.intellectualcrafters.plot.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.flag.Flag;
import com.intellectualcrafters.plot.flag.FlagManager;
import com.intellectualcrafters.plot.object.BlockLoc;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotCluster;
import com.intellectualcrafters.plot.object.PlotClusterId;
import com.intellectualcrafters.plot.object.PlotComment;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotSettings;
import com.intellectualcrafters.plot.util.ClusterManager;
import com.intellectualcrafters.plot.util.TaskManager;

/**
 * @author Citymonstret
 */
public class SQLManager implements AbstractDB {

    // Public final
    public final String SET_OWNER;
    public final String GET_ALL_PLOTS;
    public final String CREATE_PLOTS;
    public final String CREATE_SETTINGS;
    public final String CREATE_HELPERS;
    public final String CREATE_PLOT;
    public final String CREATE_CLUSTER;
    // Private Final
    private Connection connection;
    private final String prefix;

    /**
     * Constructor
     *
     * @param c connection
     * @param p prefix
     */
    public SQLManager(final Connection c, final String p) {
        // Private final
        this.connection = c;
        this.prefix = p;
        // Set timout
        // setTimout();

        // Public final
        this.SET_OWNER = "UPDATE `" + this.prefix + "plot` SET `owner` = ? WHERE `plot_id_x` = ? AND `plot_id_z` = ?";
        this.GET_ALL_PLOTS = "SELECT `id`, `plot_id_x`, `plot_id_z`, `world` FROM `" + this.prefix + "plot`";
        this.CREATE_PLOTS = "INSERT INTO `" + this.prefix + "plot`(`plot_id_x`, `plot_id_z`, `owner`, `world`) values ";
        this.CREATE_SETTINGS = "INSERT INTO `" + this.prefix + "plot_settings` (`plot_plot_id`) values ";
        this.CREATE_HELPERS = "INSERT INTO `" + this.prefix + "plot_helpers` (`plot_plot_id`, `user_uuid`) values ";
        this.CREATE_PLOT = "INSERT INTO `" + this.prefix + "plot`(`plot_id_x`, `plot_id_z`, `owner`, `world`) VALUES(?, ?, ?, ?)";
        this.CREATE_CLUSTER = "INSERT INTO `" + this.prefix + "cluster`(`pos1_x`, `pos1_z`, `pos2_x`, `pos2_z`, `owner`, `world`) VALUES(?, ?, ?, ?, ?, ?)";

        // schedule reconnect
        if (PlotMain.getMySQL() != null) {
            Bukkit.getScheduler().runTaskTimer(PlotMain.getMain(), new Runnable() {
                @Override
                public void run() {
                    try {
                        SQLManager.this.connection = PlotMain.getMySQL().forceConnection();
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 11000, 11000);
        }

    }

    //
    // public void setTimout() {
    // TaskManager.runTask(new Runnable() {
    // @Override
    // public void run() {
    // try {
    // final PreparedStatement statement =
    // connection.prepareStatement("SET GLOBAL wait_timeout =28800;");
    // statement.executeQuery();
    // statement.close();
    // } catch (final SQLException e) {
    // e.printStackTrace();
    // PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Could not reset MySQL timout.");
    // }
    // }
    // });
    // }

    /**
     * Set Plot owner
     *
     * @param plot Plot Object
     * @param uuid Owner UUID
     */
    @Override
    public void setOwner(final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement(SQLManager.this.SET_OWNER);
                    statement.setString(1, uuid.toString());
                    statement.setInt(2, plot.id.x);
                    statement.setInt(3, plot.id.y);
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Could not set owner for plot " + plot.id);
                }
            }
        });
    }

    @Override
    public void createAllSettingsAndHelpers(final ArrayList<Plot> mylist) {
        int size = mylist.size();
        int packet;
        if (PlotMain.getMySQL() != null) {
            packet = Math.min(size, 50000);
        }
        else {
            packet = Math.min(size, 5000);
        }
        int amount = size/packet;
        for (int j = 0; j <= amount;j++) {
            List<Plot> plots = mylist.subList(j * packet, Math.min(size, (j + 1) * packet));
            final HashMap<String, HashMap<PlotId, Integer>> stored = new HashMap<>();
            final HashMap<Integer, ArrayList<UUID>> helpers = new HashMap<>();
            try {
                final PreparedStatement stmt = this.connection.prepareStatement(this.GET_ALL_PLOTS);
                final ResultSet result = stmt.executeQuery();
                while (result.next()) {
                    final int id = result.getInt("id");
                    final int idx = result.getInt("plot_id_x");
                    final int idz = result.getInt("plot_id_z");
                    final String world = result.getString("world");
    
                    if (!stored.containsKey(world)) {
                        stored.put(world, new HashMap<PlotId, Integer>());
                    }
                    stored.get(world).put(new PlotId(idx, idz), id);
                }
            } catch (final SQLException e) {
                e.printStackTrace();
            }
            for (final Plot plot : plots) {
                final String world = plot.world;
                if (stored.containsKey(world)) {
                    final Integer id = stored.get(world).get(plot.id);
                    if (id != null) {
                        helpers.put(id, plot.helpers);
                    }
                }
            }
            if (helpers.size() == 0) {
                return;
            }
            // add plot settings
            final Integer[] ids = helpers.keySet().toArray(new Integer[helpers.keySet().size()]);
            StringBuilder statement = new StringBuilder(this.CREATE_SETTINGS);
            for (int i = 0; i < (ids.length - 1); i++) {
                statement.append("(?),");
            }
            statement.append("(?)");
            PreparedStatement stmt = null;
            try {
                stmt = this.connection.prepareStatement(statement.toString());
                for (int i = 0; i < ids.length; i++) {
                    stmt.setInt(i + 1, ids[i]);
                }
                stmt.executeUpdate();
                stmt.close();
            } catch (final SQLException e) {
                for (int i = 0; i < ids.length; i++) {
                    createPlotSettings(ids[i], null);
                }
            }
            // add plot helpers
            String prefix = "";
            statement = new StringBuilder(this.CREATE_HELPERS);
            for (final Integer id : helpers.keySet()) {
                for (final UUID helper : helpers.get(id)) {
                    statement.append(prefix + "(?, ?)");
                    prefix = ",";
                }
            }
            if (prefix.equals("")) {
                return;
            }
            try {
                stmt = this.connection.prepareStatement(statement.toString());
                int counter = 0;
                for (final Integer id : helpers.keySet()) {
                    for (final UUID helper : helpers.get(id)) {
    
                        stmt.setInt((counter * 2) + 1, id);
                        stmt.setString((counter * 2) + 2, helper.toString());
                        counter++;
                    }
                }
                stmt.executeUpdate();
                stmt.close();
            } catch (final SQLException e) {
                try {
                    for (final Integer id : helpers.keySet()) {
                        for (final UUID helper : helpers.get(id)) {
                            setHelper(id, helper);
                        }
                    }
                }
                catch (Exception e2) {}
                PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set all helpers for plots");
            }
        }
    }

    /**
     * Create a plot
     *
     * @param plots
     */
    @Override
    public void createPlots(final ArrayList<Plot> mylist) {
        int size = mylist.size();
        int packet;
        if (PlotMain.getMySQL() != null) {
            packet = Math.min(size, 50000);
        }
        else {
            packet = Math.min(size, 5000);
        }
        int amount = size/packet;
        for (int j = 0; j <= amount;j++) {
            List<Plot> plots = mylist.subList(j * packet, Math.min(size, (j + 1) * packet));
            if (plots.size() == 0) {
                return;
            }
            final StringBuilder statement = new StringBuilder(this.CREATE_PLOTS);
    
            for (int i = 0; i < (plots.size() - 1); i++) {
                statement.append("(?,?,?,?),");
            }
            statement.append("(?,?,?,?)");
    
            PreparedStatement stmt = null;
            try {
                stmt = this.connection.prepareStatement(statement.toString());
                for (int i = 0; i < plots.size(); i++) {
                    final Plot plot = plots.get(i);
                    stmt.setInt((i * 4) + 1, plot.id.x);
                    stmt.setInt((i * 4) + 2, plot.id.y);
                    try {
                        stmt.setString((i * 4) + 3, plot.owner.toString());
                    }
                    catch (Exception e) {
                        stmt.setString((i * 4) + 3, DBFunc.everyone.toString());
                    }
                    stmt.setString((i * 4) + 4, plot.world);
                }
                stmt.executeUpdate();
                stmt.close();
            } catch (final Exception e) {
                e.printStackTrace();
                PlotMain.sendConsoleSenderMessage("&6[WARN] "+"Could not bulk save. Conversion may be slower...");
                try {
                    for (Plot plot : plots) {
                        try {
                            createPlot(plot);
                        }
                        catch (Exception e3) {
                            PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to save plot: "+plot.id);
                        }
                    }
                }
                catch (Exception e2) {
                    e2.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to save plots!");
                }
            }
        }
    }

    /**
     * Create a plot
     *
     * @param plot
     */
    @Override
    public void createPlot(final Plot plot) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement(SQLManager.this.CREATE_PLOT);
                    stmt.setInt(1, plot.id.x);
                    stmt.setInt(2, plot.id.y);
                    stmt.setString(3, plot.owner.toString());
                    stmt.setString(4, plot.world);
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to save plot " + plot.id);
                }
            }
        });
    }

    @Override
    public void createPlotAndSettings(final Plot plot) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement(SQLManager.this.CREATE_PLOT);
                    stmt.setInt(1, plot.id.x);
                    stmt.setInt(2, plot.id.y);
                    stmt.setString(3, plot.owner.toString());
                    stmt.setString(4, plot.world);
                    stmt.executeUpdate();
                    stmt.close();
                    
                    int id = getId(plot.world, plot.id);
                    stmt = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_settings`(`plot_plot_id`) VALUES(" + "?)");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to save plot " + plot.id);
                }
            }
        });
    }

    
    /**
     * Create tables
     *
     * @throws SQLException
     */
    @Override
    public void createTables(final String database, final boolean add_constraint) throws SQLException {

        final boolean mysql = database.equals("mysql");
        final Statement stmt = this.connection.createStatement();
        if (mysql) {
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot` (" + "`id` INT(11) NOT NULL AUTO_INCREMENT," + "`plot_id_x` INT(11) NOT NULL," + "`plot_id_z` INT(11) NOT NULL," + "`owner` VARCHAR(40) NOT NULL," + "`world` VARCHAR(45) NOT NULL," + "`timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," + "PRIMARY KEY (`id`)" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=0");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_denied` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_helpers` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_comments` (" + "`plot_plot_id` INT(11) NOT NULL," + "`comment` VARCHAR(40) NOT NULL," + "`tier` INT(11) NOT NULL," + "`sender` VARCHAR(40) NOT NULL" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_trusted` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_settings` (" + "  `plot_plot_id` INT(11) NOT NULL," + "  `biome` VARCHAR(45) DEFAULT 'FOREST'," + "  `rain` INT(1) DEFAULT 0," + "  `custom_time` TINYINT(1) DEFAULT '0'," + "  `time` INT(11) DEFAULT '8000'," + "  `deny_entry` TINYINT(1) DEFAULT '0'," + "  `alias` VARCHAR(50) DEFAULT NULL," + "  `flags` VARCHAR(512) DEFAULT NULL," + "  `merged` INT(11) DEFAULT NULL," + "  `position` VARCHAR(50) NOT NULL DEFAULT 'DEFAULT'," + "  PRIMARY KEY (`plot_plot_id`)," + "  UNIQUE KEY `unique_alias` (`alias`)" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_ratings` ( `plot_plot_id` INT(11) NOT NULL, `rating` INT(2) NOT NULL, `player` VARCHAR(40) NOT NULL, PRIMARY KEY(`plot_plot_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
            if (add_constraint) {
                stmt.addBatch("ALTER TABLE `" + this.prefix + "plot_settings` ADD CONSTRAINT `" + this.prefix + "plot_settings_ibfk_1` FOREIGN KEY (`plot_plot_id`) REFERENCES `" + this.prefix + "plot` (`id`) ON DELETE CASCADE");
            }
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster` (" + "`id` INT(11) NOT NULL AUTO_INCREMENT," + "`pos1_x` INT(11) NOT NULL," + "`pos1_z` INT(11) NOT NULL," + "`pos2_x` INT(11) NOT NULL," + "`pos2_z` INT(11) NOT NULL," + "`owner` VARCHAR(40) NOT NULL," + "`world` VARCHAR(45) NOT NULL," + "`timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," + "PRIMARY KEY (`id`)" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=0");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_helpers` (" + "`cluster_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_invited` (" + "`cluster_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL"  + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_settings` (" + "  `cluster_id` INT(11) NOT NULL," + "  `biome` VARCHAR(45) DEFAULT 'FOREST'," + "  `rain` INT(1) DEFAULT 0," + "  `custom_time` TINYINT(1) DEFAULT '0'," + "  `time` INT(11) DEFAULT '8000'," + "  `deny_entry` TINYINT(1) DEFAULT '0'," + "  `alias` VARCHAR(50) DEFAULT NULL," + "  `flags` VARCHAR(512) DEFAULT NULL," + "  `merged` INT(11) DEFAULT NULL," + "  `position` VARCHAR(50) NOT NULL DEFAULT 'DEFAULT'," + "  PRIMARY KEY (`cluster_id`)" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");

        } else {
        	stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot` (" + "`id` INTEGER PRIMARY KEY AUTOINCREMENT," + "`plot_id_x` INT(11) NOT NULL," + "`plot_id_z` INT(11) NOT NULL," + "`owner` VARCHAR(45) NOT NULL," + "`world` VARCHAR(45) NOT NULL," + "`timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_denied` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_helpers` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_trusted` (" + "`plot_plot_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_comments` (" + "`plot_plot_id` INT(11) NOT NULL," + "`comment` VARCHAR(40) NOT NULL," + "`tier` INT(11) NOT NULL," + "`sender` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_settings` (" + "  `plot_plot_id` INT(11) NOT NULL," + "  `biome` VARCHAR(45) DEFAULT 'FOREST'," + "  `rain` INT(1) DEFAULT 0," + "  `custom_time` TINYINT(1) DEFAULT '0'," + "  `time` INT(11) DEFAULT '8000'," + "  `deny_entry` TINYINT(1) DEFAULT '0'," + "  `alias` VARCHAR(50) DEFAULT NULL," + "  `flags` VARCHAR(512) DEFAULT NULL," + "  `merged` INT(11) DEFAULT NULL," + "  `position` VARCHAR(50) NOT NULL DEFAULT 'DEFAULT'," + "  PRIMARY KEY (`plot_plot_id`)" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "plot_ratings` (`plot_plot_id` INT(11) NOT NULL, `rating` INT(2) NOT NULL, `player` VARCHAR(40) NOT NULL, PRIMARY KEY(`plot_plot_id`))");
            
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster` (" + "`id` INTEGER PRIMARY KEY AUTOINCREMENT," + "`pos1_x` INT(11) NOT NULL," + "`pos1_z` INT(11) NOT NULL," + "`pos2_x` INT(11) NOT NULL," + "`pos2_z` INT(11) NOT NULL," + "`owner` VARCHAR(40) NOT NULL," + "`world` VARCHAR(45) NOT NULL," + "`timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_helpers` (" + "`cluster_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_invited` (" + "`cluster_id` INT(11) NOT NULL," + "`user_uuid` VARCHAR(40) NOT NULL" + ")");
            stmt.addBatch("CREATE TABLE IF NOT EXISTS `" + this.prefix + "cluster_settings` (" + "  `cluster_id` INT(11) NOT NULL," + "  `biome` VARCHAR(45) DEFAULT 'FOREST'," + "  `rain` INT(1) DEFAULT 0," + "  `custom_time` TINYINT(1) DEFAULT '0'," + "  `time` INT(11) DEFAULT '8000'," + "  `deny_entry` TINYINT(1) DEFAULT '0'," + "  `alias` VARCHAR(50) DEFAULT NULL," + "  `flags` VARCHAR(512) DEFAULT NULL," + "  `merged` INT(11) DEFAULT NULL," + "  `position` VARCHAR(50) NOT NULL DEFAULT 'DEFAULT'," + "  PRIMARY KEY (`cluster_id`)" + ")");
        }
        stmt.executeBatch();
        stmt.clearBatch();
        stmt.close();
    }

    /**
     * Delete a plot
     *
     * @param plot
     */
    @Override
    public void delete(final String world, final Plot plot) {
        PlotMain.removePlot(world, plot.id, false);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                final int id = getId(world, plot.id);
                try {
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_settings` WHERE `plot_plot_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_helpers` WHERE `plot_plot_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_trusted` WHERE `plot_plot_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_comments` WHERE `plot_plot_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot` WHERE `id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to delete plot " + plot.id);
                }
            }
        });
    }

    /**
     * Create plot settings
     *
     * @param id
     * @param plot
     */
    @Override
    public void createPlotSettings(final int id, final Plot plot) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_settings`(`plot_plot_id`) VALUES(" + "?)");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    @Override
    public int getId(final String world, final PlotId id2) {
        PreparedStatement stmt = null;
        try {
            stmt = this.connection.prepareStatement("SELECT `id` FROM `" + this.prefix + "plot` WHERE `plot_id_x` = ? AND `plot_id_z` = ? AND world = ? ORDER BY `timestamp` ASC");
            stmt.setInt(1, id2.x);
            stmt.setInt(2, id2.y);
            stmt.setString(3, world);
            final ResultSet r = stmt.executeQuery();
            int id = Integer.MAX_VALUE;
            while (r.next()) {
                id = r.getInt("id");
            }
            stmt.close();
            return id;
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return Integer.MAX_VALUE;
    }

    /**
     * @return
     */
    @Override
    public LinkedHashMap<String, HashMap<PlotId, Plot>> getPlots() {
        final LinkedHashMap<String, HashMap<PlotId, Plot>> newplots = new LinkedHashMap<>();
        try {
            final DatabaseMetaData data = this.connection.getMetaData();
            ResultSet rs = data.getColumns(null, null, this.prefix + "plot", "plot_id");
            final boolean execute = rs.next();
            if (execute) {
                final Statement statement = this.connection.createStatement();
                statement.addBatch("ALTER IGNORE TABLE `" + this.prefix + "plot` ADD `plot_id_x` int(11) DEFAULT 0");
                statement.addBatch("ALTER IGNORE TABLE `" + this.prefix + "plot` ADD `plot_id_z` int(11) DEFAULT 0");
                statement.addBatch("UPDATE `" + this.prefix + "plot` SET\n" + "    `plot_id_x` = IF(" + "        LOCATE(';', `plot_id`) > 0," + "        SUBSTRING(`plot_id`, 1, LOCATE(';', `plot_id`) - 1)," + "        `plot_id`" + "    )," + "    `plot_id_z` = IF(" + "        LOCATE(';', `plot_id`) > 0," + "        SUBSTRING(`plot_id`, LOCATE(';', `plot_id`) + 1)," + "        NULL" + "    )");
                statement.addBatch("ALTER TABLE `" + this.prefix + "plot` DROP `plot_id`");
                statement.addBatch("ALTER IGNORE TABLE `" + this.prefix + "plot_settings` ADD `flags` VARCHAR(512) DEFAULT NULL");
                statement.executeBatch();
                statement.close();
            }
            rs = data.getColumns(null, null, this.prefix + "plot_settings", "merged");
            if (!rs.next()) {
                final Statement statement = this.connection.createStatement();
                statement.addBatch("ALTER TABLE `" + this.prefix + "plot_settings` ADD `merged` int(11) DEFAULT NULL");
                statement.executeBatch();
                statement.close();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        final HashMap<Integer, Plot> plots = new HashMap<>();

        Statement stmt = null;
        try {

            Set<String> worlds = new HashSet<>();
            if (PlotMain.config.contains("worlds")) {
                worlds = PlotMain.config.getConfigurationSection("worlds").getKeys(false);
            }
            final HashMap<String, UUID> uuids = new HashMap<>();
            final HashMap<String, Integer> noExist = new HashMap<>();

            /*
             * Getting plots
             */
            stmt = this.connection.createStatement();
            ResultSet r = stmt.executeQuery("SELECT `id`, `plot_id_x`, `plot_id_z`, `owner`, `world` FROM `" + this.prefix + "plot`");
            PlotId plot_id;
            int id;
            Plot p;
            String o;
            UUID user;
            while (r.next()) {
                plot_id = new PlotId(r.getInt("plot_id_x"), r.getInt("plot_id_z"));
                id = r.getInt("id");
                final String worldname = r.getString("world");
                if (!worlds.contains(worldname)) {
                    if (noExist.containsKey(worldname)) {
                        noExist.put(worldname, noExist.get(worldname) + 1);
                    } else {
                        noExist.put(worldname, 1);
                    }
                }
                o = r.getString("owner");
                user = uuids.get(o);
                if (user == null) {
                    user = UUID.fromString(o);
                    uuids.put(o, user);
                }
                p = new Plot(plot_id, user, new ArrayList<UUID>(), new ArrayList<UUID>(), new ArrayList<UUID>(), "", null, null, worldname, new boolean[]{false, false, false, false});
                plots.put(id, p);
            }

            /*
             * Getting helpers
             */
            r = stmt.executeQuery("SELECT `user_uuid`, `plot_plot_id` FROM `" + this.prefix + "plot_helpers`");
            while (r.next()) {
                id = r.getInt("plot_plot_id");
                o = r.getString("user_uuid");
                user = uuids.get(o);
                if (user == null) {
                    user = UUID.fromString(o);
                    uuids.put(o, user);
                }
                final Plot plot = plots.get(id);
                if (plot != null) {
                    plot.addHelper(user);
                } else {
                    PlotMain.sendConsoleSenderMessage("&cPLOT " + id + " in plot_helpers does not exist. Please create the plot or remove this entry.");
                }
            }
            /*
             * Getting trusted
             */
            r = stmt.executeQuery("SELECT `user_uuid`, `plot_plot_id` FROM `" + this.prefix + "plot_trusted`");
            while (r.next()) {
                id = r.getInt("plot_plot_id");
                o = r.getString("user_uuid");
                user = uuids.get(o);
                if (user == null) {
                    user = UUID.fromString(o);
                    uuids.put(o, user);
                }
                final Plot plot = plots.get(id);
                if (plot != null) {
                    plot.addTrusted(user);
                } else {
                    PlotMain.sendConsoleSenderMessage("&cPLOT " + id + " in plot_trusted does not exist. Please create the plot or remove this entry.");
                }
            }
            /*
             * Getting denied
             */
            r = stmt.executeQuery("SELECT `user_uuid`, `plot_plot_id` FROM `" + this.prefix + "plot_denied`");
            while (r.next()) {
                id = r.getInt("plot_plot_id");
                o = r.getString("user_uuid");
                user = uuids.get(o);
                if (user == null) {
                    user = UUID.fromString(o);
                    uuids.put(o, user);
                }
                final Plot plot = plots.get(id);
                if (plot != null) {
                    plot.addDenied(user);
                } else {
                    PlotMain.sendConsoleSenderMessage("&cPLOT " + id + " in plot_denied does not exist. Please create the plot or remove this entry.");
                }
            }
            r = stmt.executeQuery("SELECT * FROM `" + this.prefix + "plot_settings`");
            while (r.next()) {
                id = r.getInt("plot_plot_id");
                final Plot plot = plots.get(id);
                if (plot != null) {

                    final String b = r.getString("biome");
                    if (b != null) {
                        for (final Biome mybiome : Biome.values()) {
                            if (mybiome.toString().equalsIgnoreCase(b)) {
                                break;
                            }
                        }
                    }

                    final String alias = r.getString("alias");
                    if (alias != null) {
                        plot.settings.setAlias(alias);
                    }

                    final String pos = r.getString("position");
                    
                    switch (pos.toLowerCase()) {
                        case "":
                        case "default":
                        case "0,0,0":
                        case "center":
                            break;
                        default:
                            try {
                                String[] split = pos.split(",");
                                BlockLoc loc = new BlockLoc(Integer.parseInt(split[0]),Integer.parseInt(split[1]),Integer.parseInt(split[2]));
                                plot.settings.setPosition(loc);
                            }
                            catch (Exception e) {}
                    }
                    final Integer m = r.getInt("merged");
                    if (m != null) {
                        final boolean[] merged = new boolean[4];
                        for (int i = 0; i < 4; i++) {
                            merged[3 - i] = ((m) & (1 << i)) != 0;
                        }
                        plot.settings.setMerged(merged);
                    } else {
                        plot.settings.setMerged(new boolean[]{false, false, false, false});
                    }

                    String[] flags_string;
                    final String myflags = r.getString("flags");
                    if (myflags == null) {
                        flags_string = new String[]{};
                    } else {
                        if (myflags.length() > 0) {
                            flags_string = myflags.split(",");
                        }
                        else {
                            flags_string = new String[]{};
                        }
                    }
                    final Set<Flag> flags = new HashSet<>();
                    boolean exception = false;
                    for (final String element : flags_string) {
                        if (element.contains(":")) {
                            final String[] split = element.split(":");
                            try {
                            	String flag_str = split[1].replaceAll("\u00AF", ":").replaceAll("\u00B4", ",");
                            	Flag flag = new Flag(FlagManager.getFlag(split[0], true), flag_str);
                                flags.add(flag);
                            } catch (final Exception e) {
                                e.printStackTrace();
                                exception = true;
                            }
                        } else {
                            flags.add(new Flag(FlagManager.getFlag(element, true), ""));
                        }
                    }
                    if (exception) {
                        PlotMain.sendConsoleSenderMessage("&cPlot " + id + " had an invalid flag. A fix has been attempted.");
                        setFlags(id, flags.toArray(new Flag[0]));
                    }
                    plot.settings.flags = flags;
                } else {
                    PlotMain.sendConsoleSenderMessage("&cPLOT " + id + " in plot_settings does not exist. Please create the plot or remove this entry.");
                }
            }
            stmt.close();
            for (final Plot plot : plots.values()) {
                final String world = plot.world;
                if (!newplots.containsKey(world)) {
                    newplots.put(world, new HashMap<PlotId, Plot>());
                }
                newplots.get(world).put(plot.id, plot);
            }
            boolean invalidPlot = false;
            for (final String worldname : noExist.keySet()) {
                invalidPlot = true;
                PlotMain.sendConsoleSenderMessage("&c[WARNING] Found " + noExist.get(worldname) + " plots in DB for non existant world; '" + worldname + "'.");
            }
            if (invalidPlot) {
                PlotMain.sendConsoleSenderMessage("&c[WARNING] - Please create the world/s or remove the plots using the purge command");
            }
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to load plots.");
            e.printStackTrace();
        }
        return newplots;
    }

    @Override
    public void setMerged(final String world, final Plot plot, final boolean[] merged) {
        plot.settings.setMerged(merged);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    int n = 0;
                    for (int i = 0; i < 4; ++i) {
                        n = (n << 1) + (merged[i] ? 1 : 0);
                    }
                    final PreparedStatement stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "plot_settings` SET `merged` = ? WHERE `plot_plot_id` = ?");
                    stmt.setInt(1, n);
                    stmt.setInt(2, getId(world, plot.id));
                    stmt.execute();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Could not set merged for plot " + plot.id);
                }
            }
        });
    }

    @Override
    public void setFlags(final String world, final Plot plot, final Set<Flag> flags) {
        final StringBuilder flag_string = new StringBuilder();
        int i = 0;
        for (final Flag flag : flags) {
            if (i != 0) {
                flag_string.append(",");
            }
            flag_string.append(flag.getKey() + ":" + flag.getValueString().replaceAll(":", "\u00AF").replaceAll(",", "\u00B4"));
            i++;
        }
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "plot_settings` SET `flags` = ? WHERE `plot_plot_id` = ?");
                    stmt.setString(1, flag_string.toString());
                    stmt.setInt(2, getId(world, plot.id));
                    stmt.execute();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Could not set flag for plot " + plot.id);
                }
            }
        });
    }

    public void setFlags(final int id, final Flag[] flags) {
        final ArrayList<Flag> newflags = new ArrayList<>();
        for (final Flag flag : flags) {
            if ((flag != null) && (flag.getKey() != null) && !flag.getKey().equals("")) {
                newflags.add(flag);
            }
        }
        final String flag_string = StringUtils.join(newflags, ",");
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "plot_settings` SET `flags` = ? WHERE `plot_plot_id` = ?");
                    stmt.setString(1, flag_string);
                    stmt.setInt(2, id);
                    stmt.execute();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Could not set flag for plot " + id);
                }
            }
        });
    }

    /**
     * @param plot
     * @param alias
     */
    @Override
    public void setAlias(final String world, final Plot plot, final String alias) {
        plot.settings.setAlias(alias);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "plot_settings` SET `alias` = ?  WHERE `plot_plot_id` = ?");
                    stmt.setString(1, alias);
                    stmt.setInt(2, getId(world, plot.id));
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set alias for plot " + plot.id);
                    e.printStackTrace();
                }

            }
        });
    }

    /**
     * Purge all plots with the following database IDs
     */
    public void purgeIds(final String world, final Set<Integer> uniqueIds) {
        if (uniqueIds.size() > 0) {
            try {

                String stmt_prefix = "";
                final StringBuilder idstr = new StringBuilder("");

                for (final Integer id : uniqueIds) {
                    idstr.append(stmt_prefix + id);
                    stmt_prefix = " OR `plot_plot_id` = ";
                }
                PreparedStatement stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + this.prefix + "plot_helpers` WHERE `plot_plot_id` = " + idstr + "");
                stmt.executeUpdate();
                stmt.close();

                stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + this.prefix + "plot_denied` WHERE `plot_plot_id` = " + idstr + "");
                stmt.executeUpdate();
                stmt.close();

                stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + this.prefix + "plot_settings` WHERE `plot_plot_id` = " + idstr + "");
                stmt.executeUpdate();
                stmt.close();

                stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + this.prefix + "plot_trusted` WHERE `plot_plot_id` = " + idstr + "");
                stmt.executeUpdate();
                stmt.close();

                stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + this.prefix + "plot` WHERE `world` = ?");
                stmt.setString(1, world);
                stmt.executeUpdate();
                stmt.close();
            } catch (final SQLException e) {
                e.printStackTrace();
                PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"FAILED TO PURGE WORLD '" + world + "'!");
                return;
            }
        }
        PlotMain.sendConsoleSenderMessage("&6[INFO] "+"SUCCESSFULLY PURGED WORLD '" + world + "'!");
    }
    @Override
    public void purge(final String world, Set<PlotId> plots) {
    	for (PlotId id : plots) {
    		PlotMain.removePlot(world, id, true);
    	}
        PreparedStatement stmt;
        try {
            stmt = SQLManager.this.connection.prepareStatement("SELECT `id`, `plot_id_x`, `plot_id_z` FROM `" + this.prefix + "plot` WHERE `world` = ?");
            stmt.setString(1, world);
            ResultSet r = stmt.executeQuery();
            PlotId plot_id;
            Set<Integer> ids = new HashSet<>();
            while (r.next()) {
                plot_id = new PlotId(r.getInt("plot_id_x"), r.getInt("plot_id_z"));
                if (plots.contains(plot_id)) {
                    ids.add(r.getInt("id"));
                }
            }
            purgeIds(world, ids);
        } catch (SQLException e) {
            e.printStackTrace();
            PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"FAILED TO PURGE WORLD '" + world + "'!");
        }
    }
    
    /**
     * @param plot
     * @param position
     */
    @Override
    public void setPosition(final String world, final Plot plot, final String position) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "plot_settings` SET `position` = ?  WHERE `plot_plot_id` = ?");
                    stmt.setString(1, position);
                    stmt.setInt(2, getId(world, plot.id));
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set position for plot " + plot.id);
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * @param id
     *
     * @return
     */
    @Override
    public HashMap<String, Object> getSettings(final int id) {
        final HashMap<String, Object> h = new HashMap<>();
        PreparedStatement stmt = null;
        try {
            stmt = this.connection.prepareStatement("SELECT * FROM `" + this.prefix + "plot_settings` WHERE `plot_plot_id` = ?");
            stmt.setInt(1, id);
            final ResultSet r = stmt.executeQuery();
            String var;
            Object val;
            while (r.next()) {
                var = "biome";
                val = r.getObject(var);
                h.put(var, val);
                var = "rain";
                val = r.getObject(var);
                h.put(var, val);
                var = "custom_time";
                val = r.getObject(var);
                h.put(var, val);
                var = "time";
                val = r.getObject(var);
                h.put(var, val);
                var = "deny_entry";
                val = r.getObject(var);
                h.put(var, (short) 0);
                var = "alias";
                val = r.getObject(var);
                h.put(var, val);
                var = "position";
                val = r.getObject(var);
                h.put(var, val);
                var = "flags";
                val = r.getObject(var);
                h.put(var, val);
                var = "merged";
                val = r.getObject(var);
                h.put(var, val);
            }
            stmt.close();
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to load settings for plot: " + id);
            e.printStackTrace();
        }
        return h;
    }

    @Override
    public void removeComment(final String world, final Plot plot, final PlotComment comment) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                	PreparedStatement statement;
                	if (plot != null) {
	                    statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_comments` WHERE `plot_plot_id` = ? AND `comment` = ? AND `tier` = ? AND `sender` = ?");
	                    statement.setInt(1, getId(world, plot.id));
	                    statement.setString(2, comment.comment);
	                    statement.setInt(3, comment.tier);
	                    statement.setString(4, comment.senderName);
                	}
                	else {
                		statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_comments` WHERE `comment` = ? AND `tier` = ? AND `sender` = ?");
	                    statement.setString(1, comment.comment);
	                    statement.setInt(2, comment.tier);
	                    statement.setString(3, comment.senderName);
                	}
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove helper for plot " + plot.id);
                }
            }
        });
    }

    @Override
    public ArrayList<PlotComment> getComments(final String world, final Plot plot, final int tier, boolean below) {
        final ArrayList<PlotComment> comments = new ArrayList<>();
        try {
            final PreparedStatement statement;
            String comparison = below ? ">=" : "=";
            if (plot != null) {
            	statement = this.connection.prepareStatement("SELECT * FROM `" + this.prefix + "plot_comments` WHERE `plot_plot_id` = ? AND `tier` " + comparison + " ?");
            	statement.setInt(1, getId(plot.getWorld().getName(), plot.id));
                statement.setInt(2, tier);
            }
            else {
            	statement = this.connection.prepareStatement("SELECT * FROM `" + this.prefix + "plot_comments` WHERE `tier` " + comparison + " ?");
                statement.setInt(1, tier);
            }
            final ResultSet set = statement.executeQuery();
            PlotComment comment;
            while (set.next()) {
                final String sender = set.getString("sender");
                final String msg = set.getString("comment");
                comment = new PlotComment(msg, sender, tier);
                comments.add(comment);
            }
            statement.close();
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to fetch comment");
            e.printStackTrace();
        }
        return comments;
    }

    @Override
    public void setComment(final String world, final Plot plot, final PlotComment comment) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_comments` (`plot_plot_id`, `comment`, `tier`, `sender`) VALUES(?,?,?,?)");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, comment.comment);
                    statement.setInt(3, comment.tier);
                    statement.setString(4, comment.senderName);
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set comment for plot " + plot.id);
                }
            }
        });

    }

    /**
     * @param plot
     * @param player
     */
    @Override
    public void removeHelper(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_helpers` WHERE `plot_plot_id` = ? AND `user_uuid` = ?");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove helper for plot " + plot.id);
                }
            }
        });
    }

    /**
     * @param plot
     * @param player
     */
    @Override
    public void removeTrusted(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_trusted` WHERE `plot_plot_id` = ? AND `user_uuid` = ?");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove trusted user for plot " + plot.id);
                }
            }
        });
    }

    /**
     * @param plot
     * @param player
     */
    @Override
    public void setHelper(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_helpers` (`plot_plot_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set helper for plot " + plot.id);
                    e.printStackTrace();
                }
            }
        });
    }
    
    public void setHelper(final int id, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_helpers` (`plot_plot_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, id);
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set helper for id " + id);
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * @param plot
     * @param player
     */
    @Override
    public void setTrusted(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_trusted` (`plot_plot_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set plot trusted for plot " + plot.id);
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * @param plot
     * @param player
     */
    @Override
    public void removeDenied(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "plot_denied` WHERE `plot_plot_id` = ? AND `user_uuid` = ?");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove denied for plot " + plot.id);
                }
            }
        });
    }

    /**
     * @param plot
     * @param player
     */
    @Override
    public void setDenied(final String world, final Plot plot, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "plot_denied` (`plot_plot_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, getId(world, plot.id));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set denied for plot " + plot.id);
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public double getRatings(final Plot plot) {
        try {
            final PreparedStatement statement = this.connection.prepareStatement("SELECT AVG(`rating`) AS `rating` FROM `" + this.prefix + "plot_ratings` WHERE `plot_plot_id` = ? ");
            statement.setInt(1, getId(plot.getWorld().getName(), plot.id));
            final ResultSet set = statement.executeQuery();
            double rating = 0;
            while (set.next()) {
                rating = set.getDouble("rating");
            }
            statement.close();
            return rating;
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to fetch rating for plot " + plot.getId().toString());
            e.printStackTrace();
        }
        return 0.0d;
    }

	@Override
	public void delete(final PlotCluster cluster) {
		ClusterManager.removeCluster(cluster);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                final int id = getClusterId(cluster.world, ClusterManager.getClusterId(cluster));
                try {
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster_settings` WHERE `cluster_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster_helpers` WHERE `cluster_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster_invited` WHERE `cluster_id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster` WHERE `id` = ?");
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to delete plot cluster: " + cluster.getP1() + ":" + cluster.getP2());
                }
            }
        });
	}

	@Override
	public int getClusterId(String world, PlotClusterId id) {
		PreparedStatement stmt = null;
        try {
            stmt = this.connection.prepareStatement("SELECT `id` FROM `" + this.prefix + "cluster` WHERE `pos1_x` = ? AND `pos1_z` = ? AND `pos2_x` = ? AND `pos2_z` = ? AND `world` = ? ORDER BY `timestamp` ASC");
            stmt.setInt(1, id.pos1.x);
            stmt.setInt(2, id.pos1.y);
            stmt.setInt(3, id.pos2.x);
            stmt.setInt(4, id.pos2.y);
            stmt.setString(5, world);
            final ResultSet r = stmt.executeQuery();
            int c_id = Integer.MAX_VALUE;
            while (r.next()) {
            	c_id = r.getInt("id");
            }
            stmt.close();
            return c_id;
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return Integer.MAX_VALUE;
	}

	@Override
	public HashMap<String, HashSet<PlotCluster>> getClusters() {
		 final LinkedHashMap<String, HashSet<PlotCluster>> newClusters = new LinkedHashMap<>();
        final HashMap<Integer, PlotCluster> clusters = new HashMap<>();
        Statement stmt = null;
        try {

            Set<String> worlds = new HashSet<>();
            if (PlotMain.config.contains("worlds")) {
                worlds = PlotMain.config.getConfigurationSection("worlds").getKeys(false);
            }
            final HashMap<String, UUID> uuids = new HashMap<>();
            final HashMap<String, Integer> noExist = new HashMap<>();

            /*
             * Getting clusters
             */
            stmt = this.connection.createStatement();
            ResultSet r = stmt.executeQuery("SELECT * FROM `" + this.prefix + "cluster`");
            PlotId pos1;
            PlotId pos2;
            PlotCluster cluster; 
            String owner;
            String worldname;
            UUID user;
            int id;
            while (r.next()) {
            	pos1 = new PlotId(r.getInt("pos1_x"), r.getInt("pos1_z"));
            	pos2 = new PlotId(r.getInt("pos2_x"), r.getInt("pos2_z"));
                id = r.getInt("id");
                worldname = r.getString("world");
                if (!worlds.contains(worldname)) {
                    if (noExist.containsKey(worldname)) {
                        noExist.put(worldname, noExist.get(worldname) + 1);
                    } else {
                        noExist.put(worldname, 1);
                    }
                }
                owner = r.getString("owner");
                user = uuids.get(owner);
                if (user == null) {
                    user = UUID.fromString(owner);
                    uuids.put(owner, user);
                }
                cluster = new PlotCluster(worldname, pos1, pos2, user);
                clusters.put(id, cluster);
            }

            /*
             * Getting helpers
             */
            r = stmt.executeQuery("SELECT `user_uuid`, `cluster_id` FROM `" + this.prefix + "cluster_helpers`");
            while (r.next()) {
                id = r.getInt("cluster_id");
                owner = r.getString("user_uuid");
                user = uuids.get(owner);
                if (user == null) {
                    user = UUID.fromString(owner);
                    uuids.put(owner, user);
                }
                cluster = clusters.get(id);
                if (cluster != null) {
                    cluster.helpers.add(user);
                } else {
                    PlotMain.sendConsoleSenderMessage("&cCluster " + id + " in cluster_helpers does not exist. Please create the cluster or remove this entry.");
                }
            }
            /*
             * Getting invited
             */
            r = stmt.executeQuery("SELECT `user_uuid`, `cluster_id` FROM `" + this.prefix + "cluster_invited`");
            while (r.next()) {
                id = r.getInt("cluster_id");
                owner = r.getString("user_uuid");
                user = uuids.get(owner);
                if (user == null) {
                    user = UUID.fromString(owner);
                    uuids.put(owner, user);
                }
                cluster = clusters.get(id);
                if (cluster != null) {
                    cluster.invited.add(user);
                } else {
                    PlotMain.sendConsoleSenderMessage("&cCluster " + id + " in cluster_invited does not exist. Please create the cluster or remove this entry.");
                }
            }
            r = stmt.executeQuery("SELECT * FROM `" + this.prefix + "cluster_settings`");
            while (r.next()) {
                id = r.getInt("cluster_id");
                cluster = clusters.get(id);
                if (cluster != null) {
                    final String b = r.getString("biome");
                    if (b != null) {
                        for (final Biome mybiome : Biome.values()) {
                            if (mybiome.toString().equalsIgnoreCase(b)) {
                                break;
                            }
                        }
                    }

                    final String alias = r.getString("alias");
                    if (alias != null) {
                        cluster.settings.setAlias(alias);
                    }

                    final String pos = r.getString("position");
                    
                    switch (pos.toLowerCase()) {
                        case "":
                        case "default":
                        case "0,0,0":
                        case "center":
                            break;
                        default:
                            try {
                                String[] split = pos.split(",");
                                BlockLoc loc = new BlockLoc(Integer.parseInt(split[0]),Integer.parseInt(split[1]),Integer.parseInt(split[2]));
                                cluster.settings.setPosition(loc);
                            }
                            catch (Exception e) {}
                    }
                    final Integer m = r.getInt("merged");
                    if (m != null) {
                        final boolean[] merged = new boolean[4];
                        for (int i = 0; i < 4; i++) {
                            merged[3 - i] = ((m) & (1 << i)) != 0;
                        }
                        cluster.settings.setMerged(merged);
                    } else {
                        cluster.settings.setMerged(new boolean[]{false, false, false, false});
                    }

                    String[] flags_string;
                    final String myflags = r.getString("flags");
                    if (myflags == null) {
                        flags_string = new String[]{};
                    } else {
                        if (myflags.length() > 0) {
                            flags_string = myflags.split(",");
                        }
                        else {
                            flags_string = new String[]{};
                        }
                    }
                    final Set<Flag> flags = new HashSet<>();
                    boolean exception = false;
                    for (final String element : flags_string) {
                        if (element.contains(":")) {
                            final String[] split = element.split(":");
                            try {
                            	String flag_str = split[1].replaceAll("\u00AF", ":").replaceAll("�", ",");
                            	Flag flag = new Flag(FlagManager.getFlag(split[0], true), flag_str);
                                flags.add(flag);
                            } catch (final Exception e) {
                                e.printStackTrace();
                                exception = true;
                            }
                        } else {
                            flags.add(new Flag(FlagManager.getFlag(element, true), ""));
                        }
                    }
                    if (exception) {
                        PlotMain.sendConsoleSenderMessage("&cPlot " + id + " had an invalid flag. A fix has been attempted.");
                        setFlags(id, flags.toArray(new Flag[0]));
                    }
                    cluster.settings.flags = flags;
                } else {
                    PlotMain.sendConsoleSenderMessage("&cCluster " + id + " in cluster_settings does not exist. Please create the cluster or remove this entry.");
                }
            }
            stmt.close();
            for (final PlotCluster c : clusters.values()) {
                final String world = c.world;
                if (!newClusters.containsKey(world)) {
                	newClusters.put(world, new HashSet<PlotCluster>());
                }
                newClusters.get(world).add(c);
            }
            boolean invalidPlot = false;
            for (final String w : noExist.keySet()) {
                invalidPlot = true;
                PlotMain.sendConsoleSenderMessage("&c[WARNING] Found " + noExist.get(w) + " clusters in DB for non existant world; '" + w + "'.");
            }
            if (invalidPlot) {
                PlotMain.sendConsoleSenderMessage("&c[WARNING] - Please create the world/s or remove the clusters using the purge command");
            }
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to load clusters.");
            e.printStackTrace();
        }
        return newClusters;
	}

	@Override
	public void setFlags(final PlotCluster cluster, Set<Flag> flags) {
		final StringBuilder flag_string = new StringBuilder();
        int i = 0;
        for (final Flag flag : flags) {
            if (i != 0) {
                flag_string.append(",");
            }
            flag_string.append(flag.getKey() + ":" + flag.getValueString().replaceAll(":", "\u00AF").replaceAll(",", "\u00B4"));
            i++;
        }
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "cluster_settings` SET `flags` = ? WHERE `cluster_id` = ?");
                    stmt.setString(1, flag_string.toString());
                    stmt.setInt(2, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    stmt.execute();
                    stmt.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Could not set flag for plot " + cluster);
                }
            }
        });
		
	}

	@Override
	public void setClusterName(final PlotCluster cluster, final String name) {
		cluster.settings.setAlias(name);
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "cluster_settings` SET `alias` = ?  WHERE `cluster_id` = ?");
                    stmt.setString(1, name);
                    stmt.setInt(2, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set alias for cluster " + cluster);
                    e.printStackTrace();
                }

            }
        });
	}

	@Override
	public void removeHelper(final PlotCluster cluster, final UUID uuid) {
		TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster_helpers` WHERE `cluster_id` = ? AND `user_uuid` = ?");
                    statement.setInt(1, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove helper for cluster " + cluster);
                }
            }
        });
	}

	@Override
	public void setHelper(final PlotCluster cluster, final UUID uuid) {
		TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "cluster_helpers` (`cluster_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set helper for cluster " + cluster);
                    e.printStackTrace();
                }
            }
        });
	}

	@Override
	public void createCluster(final PlotCluster cluster) {
		TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement(SQLManager.this.CREATE_CLUSTER);
                    stmt.setInt(1, cluster.getP1().x);
                    stmt.setInt(2, cluster.getP1().y);
                    stmt.setInt(3, cluster.getP2().x);
                    stmt.setInt(4, cluster.getP2().y);
                    stmt.setString(5, cluster.owner.toString());
                    stmt.setString(6, cluster.world);
                    stmt.executeUpdate();
                    stmt.close();
                    
                    int id = getClusterId(cluster.world, ClusterManager.getClusterId(cluster));
                    stmt = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "cluster_settings`(`cluster_id`, `alias`) VALUES(?, ?" + ")");
                    stmt.setInt(1, id);
                    stmt.setString(2, cluster.settings.getAlias());
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&c[ERROR] "+"Failed to save cluster " + cluster);
                }
            }
        });
	}

	@Override
	public void resizeCluster(final PlotCluster current, final PlotClusterId resize) {
		final PlotId pos1 = new PlotId(current.getP1().x, current.getP1().y);
		final PlotId pos2 = new PlotId(current.getP2().x, current.getP2().y);
		current.setP1(resize.pos1);
		current.setP2(resize.pos2);
		TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "cluster` SET `pos1_x` = ?, `pos1_z` = ?, `pos2_x` = ?, `pos2_z` = ?  WHERE `id` = ?");
                    stmt.setInt(1, pos1.x);
                    stmt.setInt(2, pos1.y);
                    stmt.setInt(3, pos2.x);
                    stmt.setInt(4, pos2.y);
                    stmt.setInt(5, getClusterId(current.world, ClusterManager.getClusterId(current)));
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to rezize cluster " + current);
                    e.printStackTrace();
                }
            }
        });		
	}

	@Override
	public void setPosition(final PlotCluster cluster, final String position) {
		TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                PreparedStatement stmt = null;
                try {
                    stmt = SQLManager.this.connection.prepareStatement("UPDATE `" + SQLManager.this.prefix + "cluster_settings` SET `position` = ?  WHERE `cluster_id` = ?");
                    stmt.setString(1, position);
                    stmt.setInt(2, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    stmt.executeUpdate();
                    stmt.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set position for cluster " + cluster);
                    e.printStackTrace();
                }
            }
        });
	}

	@Override
	public HashMap<String, Object> getClusterSettings(int id) {
		final HashMap<String, Object> h = new HashMap<>();
        PreparedStatement stmt = null;
        try {
            stmt = this.connection.prepareStatement("SELECT * FROM `" + this.prefix + "cluster_settings` WHERE `cluster_id` = ?");
            stmt.setInt(1, id);
            final ResultSet r = stmt.executeQuery();
            String var;
            Object val;
            while (r.next()) {
                var = "biome";
                val = r.getObject(var);
                h.put(var, val);
                var = "rain";
                val = r.getObject(var);
                h.put(var, val);
                var = "custom_time";
                val = r.getObject(var);
                h.put(var, val);
                var = "time";
                val = r.getObject(var);
                h.put(var, val);
                var = "deny_entry";
                val = r.getObject(var);
                h.put(var, (short) 0);
                var = "alias";
                val = r.getObject(var);
                h.put(var, val);
                var = "position";
                val = r.getObject(var);
                h.put(var, val);
                var = "flags";
                val = r.getObject(var);
                h.put(var, val);
                var = "merged";
                val = r.getObject(var);
                h.put(var, val);
            }
            stmt.close();
        } catch (final SQLException e) {
            PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to load settings for cluster: " + id);
            e.printStackTrace();
        }
        return h;
	}

    @Override
    public void removeInvited(final PlotCluster cluster, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("DELETE FROM `" + SQLManager.this.prefix + "cluster_invited` WHERE `cluster_id` = ? AND `user_uuid` = ?");
                    statement.setInt(1, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    e.printStackTrace();
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to remove invited for cluster " + cluster);
                }
            }
        });
        
    }

    @Override
    public void setInvited(String world, final PlotCluster cluster, final UUID uuid) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                try {
                    final PreparedStatement statement = SQLManager.this.connection.prepareStatement("INSERT INTO `" + SQLManager.this.prefix + "cluster_invited` (`cluster_id`, `user_uuid`) VALUES(?,?)");
                    statement.setInt(1, getClusterId(cluster.world, ClusterManager.getClusterId(cluster)));
                    statement.setString(2, uuid.toString());
                    statement.executeUpdate();
                    statement.close();
                } catch (final SQLException e) {
                    PlotMain.sendConsoleSenderMessage("&7[WARN] "+"Failed to set helper for cluster " + cluster);
                    e.printStackTrace();
                }
            }
        });
    }
}
