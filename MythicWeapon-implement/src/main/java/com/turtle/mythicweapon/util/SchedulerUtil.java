package com.turtle.mythicweapon.util;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
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
