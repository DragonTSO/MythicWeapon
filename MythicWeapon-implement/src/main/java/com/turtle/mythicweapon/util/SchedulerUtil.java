package com.turtle.mythicweapon.util;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduler utility that abstracts between Folia and Spigot/Paper schedulers.
 *
 * On Folia: uses Entity/Region schedulers (entity-bound tasks stay on the correct thread).
 * On Spigot/Paper: uses classic BukkitRunnable.
 */
public class SchedulerUtil {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Run a repeating task tied to an entity.
     * On Folia: entity region thread. On Spigot/Paper: main thread.
     */
    public static void runEntityTimer(JavaPlugin plugin, Entity entity, CancellableTask task,
                                      long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            runEntityTimerFolia(plugin, entity, task, Math.max(1, delayTicks), periodTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) {
                        cancel();
                        return;
                    }
                    task.run();
                    if (task.isCancelled()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    /**
     * Run a delayed task tied to an entity.
     */
    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            runEntityDelayedFolia(plugin, entity, task, Math.max(1, delayTicks));
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    /**
     * Run a global delayed task (not entity-bound).
     */
    public static void runGlobalDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            runGlobalDelayedFolia(plugin, task, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a task bound to a world region/chunk.
     * On Folia: runs on RegionScheduler for the provided coordinates.
     * On Spigot/Paper: runs on main thread.
     */
    public static void runRegionTask(JavaPlugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        if (IS_FOLIA) {
            runRegionTaskFolia(plugin, world, chunkX, chunkZ, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a repeating task bound to a specific world location.
     * On Folia: uses RegionScheduler (self-rescheduling pattern) so the task always
     * runs on the region thread that owns the given location.
     * On Spigot/Paper: uses classic BukkitRunnable on the main thread.
     *
     * <p>Use this instead of {@link #runEntityTimer} when the task accesses entities/blocks
     * at a <b>fixed location</b> (e.g., fire zones, domes, arrow rain) rather than
     * following the player.</p>
     */
    public static void runRegionTimer(JavaPlugin plugin, Location location, CancellableTask task,
                                      long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            runRegionTimerFolia(plugin, location, task, Math.max(1, delayTicks), periodTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) {
                        cancel();
                        return;
                    }
                    task.run();
                    if (task.isCancelled()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    /**
     * Run a delayed task bound to a specific world location.
     * On Folia: uses RegionScheduler so the task runs on the correct region thread.
     * On Spigot/Paper: uses classic BukkitRunnable on the main thread.
     */
    public static void runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            runRegionDelayedFolia(plugin, location, task, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a repeating global task (not entity-bound).
     * On Folia: uses GlobalRegionScheduler. On Spigot/Paper: uses BukkitRunnable.
     */
    public static void runGlobalTimer(JavaPlugin plugin, CancellableTask task,
                                      long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            runGlobalTimerFolia(plugin, task, Math.max(1, delayTicks), periodTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) {
                        cancel();
                        return;
                    }
                    task.run();
                    if (task.isCancelled()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    // ====== Folia via reflection ======


    private static void runEntityTimerFolia(JavaPlugin plugin, Entity entity, CancellableTask task,
                                            long delayTicks, long periodTicks) {
        try {
            Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method runAtFixedRate = findMethod(entityScheduler.getClass(), "runAtFixedRate");

            java.util.function.Consumer<Object> consumer = scheduledTask -> {
                if (task.isCancelled()) {
                    cancelScheduledTask(scheduledTask);
                    return;
                }
                task.run();
                if (task.isCancelled()) {
                    cancelScheduledTask(scheduledTask);
                }
            };

            runAtFixedRate.invoke(entityScheduler, plugin, consumer, null, delayTicks, periodTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity timer fallback: " + e.getMessage());
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) { cancel(); return; }
                    task.run();
                    if (task.isCancelled()) cancel();
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    private static void runEntityDelayedFolia(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        try {
            Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method runDelayed = findMethod(entityScheduler.getClass(), "runDelayed");

            java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
            runDelayed.invoke(entityScheduler, plugin, consumer, null, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia entity delay fallback: " + e.getMessage());
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private static void runGlobalDelayedFolia(JavaPlugin plugin, Runnable task, long delayTicks) {
        try {
            Object globalScheduler = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = findMethod(globalScheduler.getClass(), "runDelayed");

            java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
            runDelayed.invoke(globalScheduler, plugin, consumer, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia global delay fallback: " + e.getMessage());
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private static void runGlobalTimerFolia(JavaPlugin plugin, CancellableTask task,
                                            long delayTicks, long periodTicks) {
        try {
            Object globalScheduler = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = findMethod(globalScheduler.getClass(), "runAtFixedRate");

            java.util.function.Consumer<Object> consumer = scheduledTask -> {
                if (task.isCancelled()) {
                    cancelScheduledTask(scheduledTask);
                    return;
                }
                task.run();
                if (task.isCancelled()) {
                    cancelScheduledTask(scheduledTask);
                }
            };

            runAtFixedRate.invoke(globalScheduler, plugin, consumer, delayTicks, periodTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia global timer fallback: " + e.getMessage());
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) { cancel(); return; }
                    task.run();
                    if (task.isCancelled()) cancel();
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    private static void runRegionTaskFolia(JavaPlugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        try {
            Object regionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method run = regionScheduler.getClass().getMethod(
                    "run", Plugin.class, World.class, int.class, int.class, java.util.function.Consumer.class
            );

            int blockX = chunkX << 4;
            int blockZ = chunkZ << 4;
            java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
            run.invoke(regionScheduler, plugin, world, blockX, blockZ, consumer);
        } catch (Exception e) {
            // Never fallback to Bukkit scheduler on Folia for world access.
            // Running world/chunk reads there can trigger async thread violations.
            plugin.getLogger().severe("Folia region task scheduling failed (chunk "
                    + chunkX + "," + chunkZ + " in world " + world.getName() + "): " + e.getMessage());
        }
    }

    /**
     * Folia: repeating task at a fixed location using RegionScheduler.
     * Uses self-rescheduling via runDelayed since RegionScheduler has no runAtFixedRate.
     */
    private static void runRegionTimerFolia(JavaPlugin plugin, Location location,
                                            CancellableTask task, long delayTicks, long periodTicks) {
        try {
            Object regionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = findMethod(regionScheduler.getClass(), "runDelayed");

            scheduleNextRegionTick(regionScheduler, runDelayed, plugin, location, task,
                    delayTicks, periodTicks);
        } catch (Exception e) {
            plugin.getLogger().severe("Folia region timer scheduling failed at "
                    + location + ": " + e.getMessage());
        }
    }

    /**
     * Self-rescheduling helper for region-based repeating tasks on Folia.
     */
    private static void scheduleNextRegionTick(Object regionScheduler, Method runDelayed,
                                                JavaPlugin plugin, Location location,
                                                CancellableTask task, long delay, long period) {
        try {
            java.util.function.Consumer<Object> consumer = scheduledTask -> {
                if (task.isCancelled()) return;
                task.run();
                if (!task.isCancelled()) {
                    // Reschedule for next period
                    scheduleNextRegionTick(regionScheduler, runDelayed, plugin, location,
                            task, period, period);
                }
            };

            runDelayed.invoke(regionScheduler, plugin, location.getWorld(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4,
                    consumer, delay);
        } catch (Exception e) {
            plugin.getLogger().severe("Folia region tick reschedule failed: " + e.getMessage());
        }
    }

    /**
     * Folia: delayed task at a fixed location using RegionScheduler.
     */
    private static void runRegionDelayedFolia(JavaPlugin plugin, Location location,
                                              Runnable task, long delayTicks) {
        try {
            Object regionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = findMethod(regionScheduler.getClass(), "runDelayed");

            java.util.function.Consumer<Object> consumer = scheduledTask -> task.run();
            runDelayed.invoke(regionScheduler, plugin, location.getWorld(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4,
                    consumer, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().severe("Folia region delayed scheduling failed at "
                    + location + ": " + e.getMessage());
        }
    }

    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new RuntimeException("Method " + name + " not found on " + clazz.getName());
    }

    private static void cancelScheduledTask(Object scheduledTask) {
        try {
            scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
        } catch (Exception ignored) {
        }
    }

    /**
     * Teleport an entity safely on both Folia and Spigot/Paper.
     * On Folia: uses entity.teleportAsync(location) via reflection.
     * On Spigot/Paper: uses entity.teleport(location).
     */
    public static void teleportSafe(Entity entity, Location location) {
        if (IS_FOLIA) {
            try {
                Method teleportAsync = entity.getClass().getMethod("teleportAsync", Location.class);
                teleportAsync.invoke(entity, location);
            } catch (Exception e) {
                // Fallback — should not happen but just in case
                entity.teleport(location);
            }
        } else {
            entity.teleport(location);
        }
    }

    /**
     * A cancellable task for use with repeating scheduler calls.
     */
    public static class CancellableTask implements Runnable {
        private boolean cancelled = false;
        private Runnable action;

        public CancellableTask() {
        }

        public CancellableTask(Runnable action) {
            this.action = action;
        }

        public void setAction(Runnable action) {
            this.action = action;
        }

        @Override
        public void run() {
            if (!cancelled && action != null) action.run();
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
