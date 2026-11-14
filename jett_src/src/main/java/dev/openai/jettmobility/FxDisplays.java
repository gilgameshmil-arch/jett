package dev.openai.jettmobility;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Entidades item_display do vanilla usadas como "objetos 3D" visíveis a todos (multiplayer): a esfera de fumaça e o anel de facas.
 * São criadas/animadas com comandos do próprio jogo (summon / data merge / tp), porque a transformação e a interpolação
 * dessas entidades são controladas por NBT. O modelo vem do componente item_model (assets/jettmobility/items/fx/*.json).
 */
public final class FxDisplays {
    private FxDisplays() {}

    private static final Logger LOG = LoggerFactory.getLogger("jettmobility");
    private static final String TAG = "jettmobility_fx";
    private static final Set<UUID> LIVE = new HashSet<>();

    public static void init() {
        // Se o servidor caiu no meio de um efeito, entidades antigas ficam salvas no mundo: removemos ao carregar.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getTags().contains(TAG) && !LIVE.contains(entity.getUUID())) {
                world.getServer().execute(entity::discard);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID id : new HashSet<>(LIVE)) {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                }
            }
            LIVE.clear();
        });
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String ff(double v) {
        return f(v) + "f";
    }

    private static boolean run(MinecraftServer server, ServerLevel level, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        try {
            server.getCommands().getDispatcher().execute(command, source);
            return true;
        } catch (CommandSyntaxException e) {
            LOG.warn("[jettmobility] comando de efeito falhou: {} -> {}", command, e.getMessage());
            return false;
        }
    }

    /** Cria um item_display com o modelo indicado (ex.: "jettmobility:fx/smoke_core"). Devolve o UUID. */
    public static UUID spawn(MinecraftServer server, ServerLevel level, Vec3 pos, String itemModel, double scale) {
        UUID id = UUID.randomUUID();
        LIVE.add(id); // registrar ANTES: o evento de carga não pode tratar esta entidade como "órfã"
        long msb = id.getMostSignificantBits();
        long lsb = id.getLeastSignificantBits();
        String s = ff(scale);
        String command = "summon minecraft:item_display " + f(pos.x) + " " + f(pos.y) + " " + f(pos.z)
                + " {UUID:[I;" + (int) (msb >> 32) + "," + (int) msb + "," + (int) (lsb >> 32) + "," + (int) lsb + "]"
                + ",Tags:[\"" + TAG + "\"]"
                + ",billboard:\"fixed\",view_range:2.0f,teleport_duration:3,interpolation_duration:4,start_interpolation:-1"
                + ",item_display:\"none\",brightness:{sky:15,block:15}"
                + ",item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:item_model\":\"" + itemModel + "\"}}"
                + ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:["
                + s + "," + s + "," + s + "]}}";
        if (!run(server, level, command)) {
            LIVE.remove(id);
            return null;
        }
        return id;
    }

    /** Anima a entidade até a nova rotação (eixo + ângulo em radianos) e escala ao longo de 'ticks' ticks. */
    public static void animate(MinecraftServer server, ServerLevel level, UUID id,
                               double ax, double ay, double az, double angle, double scale, int ticks) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6) { ax = 0; ay = 1; az = 0; len = 1; }
        double sin = Math.sin(angle / 2) / len;
        double cos = Math.cos(angle / 2);
        String s = ff(scale);
        run(server, level, "data merge entity " + id + " {start_interpolation:0,interpolation_duration:" + ticks
                + ",transformation:{left_rotation:[" + ff(ax * sin) + "," + ff(ay * sin) + "," + ff(az * sin) + "," + ff(cos)
                + "],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + s + "," + s + "," + s + "]}}");
    }

    public static void teleport(MinecraftServer server, ServerLevel level, UUID id, double x, double y, double z) {
        run(server, level, "tp " + id + " " + f(x) + " " + f(y) + " " + f(z));
    }

    public static void kill(ServerLevel level, UUID id) {
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e != null) e.discard();
        LIVE.remove(id);
    }
}
