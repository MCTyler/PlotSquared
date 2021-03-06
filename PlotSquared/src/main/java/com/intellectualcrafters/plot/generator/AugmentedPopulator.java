package com.intellectualcrafters.plot.generator;

import java.util.HashSet;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.object.BlockWrapper;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotGenerator;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotManager;
import com.intellectualcrafters.plot.object.PlotCluster;
import com.intellectualcrafters.plot.object.PlotWorld;
import com.intellectualcrafters.plot.util.PlotHelper;
import com.intellectualcrafters.plot.util.TaskManager;

public class AugmentedPopulator extends BlockPopulator {

	public final PlotWorld plotworld;
	public final PlotManager manager;
	public final PlotGenerator generator;
	public final PlotCluster cluster;
	
	private final int bx;
	private final int bz;
	private final int tx;
	private final int tz;
	
	public BlockWrapper getBlock(int X, int Z, int i, int j, short[][] r, boolean c) {
		int y = (i << 4) + (j >> 8);
		int a = (j - ((y & 0xF) << 8));
		int z = (a >> 4);
		int x = a - (z << 4);
		if (r[i] == null) {
		    return (c && (Z + z < bz || Z + z > tz || X + x < bx || X + x > tx)) ? null : new BlockWrapper(x, y, z, (short) 0, (byte) 0);
		}
		else {
		    return (c && (Z + z < bz || Z + z > tz || X + x < bx || X + x > tx)) ? null : new BlockWrapper(x, y, z, r[i][j], (byte) 0);
		}
	}
	
	public AugmentedPopulator(String world, PlotGenerator generator, PlotCluster cluster) {
	    this.cluster = cluster;
		this.generator = generator;
		this.plotworld = PlotMain.getWorldSettings(world);
		this.manager = generator.getPlotManager();

		World bukkitWorld = Bukkit.getWorld(world);
		
		Location b = manager.getPlotBottomLocAbs(plotworld, cluster.getP1());
		Location t = manager.getPlotTopLocAbs(plotworld, cluster.getP2()).add(1,0,1);
		
		this.bx = b.getBlockX();
		this.bz = b.getBlockZ();
		
		this.tx = t.getBlockX();
		this.tz = t.getBlockZ();
		
		// Add the populator
		if (this.plotworld.CLUSTER_ORE) {
		    bukkitWorld.getPopulators().add(0, this);
		}
		else {
		    bukkitWorld.getPopulators().add(this);
		}
	}
	
	@Override
	public void populate(final World world, final Random rand, Chunk chunk) {
		final int X = chunk.getX();
		final int Z = chunk.getZ();
		final int x = X << 4;
		final int z = Z << 4;
		int x2 = x + 15;
		int z2 = z + 15;
		
		boolean inX1 = (x >= bx && x <= tx);
		boolean inX2 = (x2 >= bx && x2 <= tx);
		boolean inZ1 = (z >= bz && z <= tz);
		boolean inZ2 = (z2 >= bz && z2 <= tz);
		
		boolean inX = inX1 || inX2;
		boolean inZ = inZ1 || inZ2;

		if (!inX || !inZ) {
			return;
		}
		
		final boolean check;
		if (!inX1 || !inX2 || !inZ1 || !inZ2) {
			check = true;
		}
		else {
			check = false;
		}
		if (this.plotworld.CLUSTER_ORE) {
		    populateBlocks(world, rand, X, Z, x, z, check);
		    TaskManager.runTaskLater(new Runnable() {
                @Override
                public void run() {
                    populateBiome(world, x, z);
                }
            }, 20);
        }
		else {
		    TaskManager.runTaskLater(new Runnable() {
                @Override
                public void run() {
                    populateBiome(world, x, z);
                }
            }, 20 + rand.nextInt(10));
            TaskManager.runTaskLater(new Runnable() {
                @Override
                public void run() {
                    populateBlocks(world, rand, X, Z, x, z, check);
                }
            }, 40 + rand.nextInt(10));
		}
		
	}
	
	private void populateBiome(World world, int x, int z) {
	    for (int i = 0; i < 16; i++) {
	        for (int j = 0; j < 16; j++) {
	            world.setBiome(x + i, z + j, plotworld.PLOT_BIOME);
	        }
	    }
	}
	
	private void populateBlocks(World world, Random rand, int X, int Z, int x, int z, boolean check) {
        short[][] result = generator.generateExtBlockSections(world, rand, X, Z, null);
        int length = result[0].length;
        for(int i = 0; i < result.length; i++) {
            for(int j = 0; j < length; j++) {
                BlockWrapper blockInfo = getBlock(x, z, i, j, result, check);
                if (blockInfo == null) {
                    continue;
                }
                PlotBlock plotblock = new PlotBlock((short) blockInfo.id, (byte) 0 );
                Block block = world.getBlockAt(x + blockInfo.x, blockInfo.y, z + blockInfo.z);
                PlotHelper.setBlock(block, plotblock);
            }
        }
	}
}