package ac.cult.cultac.platform.bukkit.cloud;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.cloud.CloudCapture;
import ac.cult.cultac.cloud.CloudService;
import ac.cult.cultac.cloud.SegmentBinder;
import ac.cult.cultac.platform.bukkit.BukkitRuntimeRegistrations;
import ac.cult.cultac.platform.bukkit.player.BukkitPlatformPlayer;
import ac.cult.cultac.player.CultPlayer;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * The upload's server side while it is on: binds every capture segment to this server, and reads each player's facts
 * on the server thread a tick after they join, once join handlers have set them, and sends them to their capture.
 */
public final class BukkitCloudFacts implements Listener {
    private static @Nullable BukkitCloudFacts running;

    private final Plugin plugin;

    private BukkitCloudFacts(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The upload turned on: segments bind here, and the server's facts and every online player's are read. */
    public static synchronized void turnedOn(Plugin plugin) {
        if (running == null) {
            BukkitCloudFacts facts = new BukkitCloudFacts(plugin);
            running = facts;
            CloudCapture.segmentBinder((player, bind, playing) -> {
                String name = CloudService.ownServerName();
                if (name != null) bind.accept(name);
            });
            Bukkit.getGlobalRegionScheduler()
                    .execute(plugin, () -> BukkitRuntimeRegistrations.registerEvents(facts, plugin));
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            CultAPI.INSTANCE.getPlatformServer().cloudFacts();
            readOnline(plugin);
        });
    }

    /** The upload turned off: no segment binds and no join is read. */
    public static synchronized void turnedOff() {
        BukkitCloudFacts facts = running;
        if (facts == null) return;
        running = null;
        CloudCapture.segmentBinder(SegmentBinder.NONE);
        Bukkit.getGlobalRegionScheduler().execute(facts.plugin, () -> HandlerList.unregisterAll(facts));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!CloudService.enabled()) return;
        readOnOwnerThread(plugin, event.getPlayer(), 1);
    }

    private static void readOnline(Plugin plugin) {
        if (!CloudService.enabled()) return;
        for (var online : Bukkit.getOnlinePlayers()) readOnOwnerThread(plugin, online, 1);
    }

    /** The facts are entity state: on Folia only the region that owns the player may read them. */
    private static void readOnOwnerThread(Plugin plugin, Player online, long delay) {
        var id = online.getUniqueId();
        online.getScheduler().execute(plugin, () -> read(id), null, delay);
    }

    private static void read(UUID id) {
        CultPlayer player = CultAPI.INSTANCE.getPlayerDataManager().getPlayer(id);
        if (player == null) return;
        var platform = player.platformPlayer != null ? player.platformPlayer : player.user.getPlayer();
        if (platform instanceof BukkitPlatformPlayer bukkit && bukkit.readCloudFacts()) {
            CloudCapture.factsChanged(player);
        }
    }
}
