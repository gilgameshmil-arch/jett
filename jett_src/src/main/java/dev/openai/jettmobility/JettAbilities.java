package dev.openai.jettmobility;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de servidor (autoritativa) das habilidades: desbloqueio permanente, cooldowns e efeitos.
 * Partículas e sons são enviados pelo servidor, então todos os jogadores próximos veem/ouvem (multiplayer).
 * As habilidades são puramente de mobilidade/visual: nenhuma causa dano.
 */
public final class JettAbilities {
    private JettAbilities() {}

    /** Máscara de bits dos desbloqueios. Persistente no save do jogador e mantida após a morte. */
    public static final AttachmentType<Integer> UNLOCKS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "unlocks"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    // Sons vanilla referenciados pelo id (evita depender dos nomes das constantes em SoundEvents).
    private static final SoundEvent SND_WIND_BURST = sound("entity.wind_charge.wind_burst");
    private static final SoundEvent SND_BREEZE_JUMP = sound("entity.breeze.jump");
    private static final SoundEvent SND_BREEZE_IDLE = sound("entity.breeze.idle_air");
    private static final SoundEvent SND_BREEZE_SLIDE = sound("entity.breeze.slide");
    private static final SoundEvent SND_BREEZE_LAND = sound("entity.breeze.land");
    private static final SoundEvent SND_FLAP = sound("entity.ender_dragon.flap");
    private static final SoundEvent SND_BEACON = sound("block.beacon.activate");

    private static final Map<UUID, long[]> READY_AT = new HashMap<>();
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static long now = 0;

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", path));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            now++;
            EFFECTS.removeIf(effect -> !effect.tick(server));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EFFECTS.clear();
            READY_AT.clear();
            now = 0;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY_AT.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
    }

    // ------------------------------------------------------------------ desbloqueio

    public static int mask(ServerPlayer player) {
        return player.getAttachedOrElse(UNLOCKS, 0);
    }

    public static boolean isUnlocked(ServerPlayer player, Ability ability) {
        return (mask(player) & ability.bit()) != 0;
    }

    public static void unlock(ServerPlayer player, Ability ability) {
        player.setAttached(UNLOCKS, mask(player) | ability.bit());
        sync(player);
    }

    // ------------------------------------------------------------------ ativação

    public static void tryActivate(ServerPlayer player, int abilityId) {
        Ability ability = Ability.byId(abilityId);
        if (ability == null || player.isSpectator() || !player.isAlive()) return;
        if (!isUnlocked(player, ability)) {
            sync(player);
            return;
        }
        long[] ready = READY_AT.computeIfAbsent(player.getUUID(), id -> new long[Ability.VALUES.length]);
        if (ready[ability.ordinal()] > now) return; // ainda em cooldown (o servidor decide, nunca o cliente)
        ready[ability.ordinal()] = now + ability.cooldownTicks;

        ServerLevel level = (ServerLevel) player.level();
        switch (ability) {
            case UPDRAFT -> updraft(player, level);
            case CLOUDBURST -> cloudburst(player, level);
            case TAILWIND -> tailwind(player, level);
            case ULTIMATE -> ultimate(player, level);
        }
        sync(player);
    }

    private static void updraft(ServerPlayer p, ServerLevel level) {
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 1.0, v.z);
        p.hurtMarked = true; // força o envio da nova velocidade ao cliente do jogador
        p.fallDistance = 0;
        particles(level, ParticleTypes.GUST, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
        particles(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.1, 0.4, 0.15);
        play(level, p.getX(), p.getY(), p.getZ(), SND_WIND_BURST, 1.0f, 1.1f);
        play(level, p.getX(), p.getY(), p.getZ(), SND_BREEZE_JUMP, 0.8f, 1.2f);
        EFFECTS.add(new TrailEffect(p.getUUID(), 14));
    }

    private static void tailwind(ServerPlayer p, ServerLevel level) {
        // Dash horizontal na direção em que o jogador olha (yaw), mantendo a altura atual.
        double yaw = Math.toRadians(p.getYRot());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dx * 1.6, Math.max(v.y, p.onGround() ? 0.12 : v.y), dz * 1.6);
        p.hurtMarked = true;
        p.fallDistance = 0;
        particles(level, ParticleTypes.GUST, p.getX(), p.getY() + 0.8, p.getZ(), 1, 0, 0, 0, 0);
        particles(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8, p.getZ(), 24, 0.3, 0.4, 0.3, 0.1);
        play(level, p.getX(), p.getY(), p.getZ(), SND_FLAP, 1.0f, 1.7f);
        play(level, p.getX(), p.getY(), p.getZ(), SND_BREEZE_SLIDE, 0.9f, 1.3f);
        EFFECTS.add(new TrailEffect(p.getUUID(), 8));
    }

    private static void cloudburst(ServerPlayer p, ServerLevel level) {
        // Nuvem de vento onde o jogador está olhando (até 12 blocos). Efeito apenas visual e sonoro.
        HitResult hit = p.pick(12.0, 1.0f, false);
        Vec3 at = hit.getLocation().add(0, 1.0, 0);
        play(level, at.x, at.y, at.z, SND_WIND_BURST, 1.2f, 0.8f);
        particles(level, ParticleTypes.POOF, at.x, at.y, at.z, 30, 0.9, 0.7, 0.9, 0.05);
        EFFECTS.add(new CloudEffect(level, at.x, at.y, at.z));
    }

    private static void ultimate(ServerPlayer p, ServerLevel level) {
        play(level, p.getX(), p.getY(), p.getZ(), SND_WIND_BURST, 1.3f, 0.7f);
        play(level, p.getX(), p.getY(), p.getZ(), SND_BEACON, 0.9f, 1.4f);
        particles(level, ParticleTypes.POOF, p.getX(), p.getY() + 0.2, p.getZ(), 40, 1.2, 0.2, 1.2, 0.08);
        EFFECTS.add(new StormEffect(p.getUUID()));
    }

    // ------------------------------------------------------------------ sincronização com o HUD

    public static void sync(ServerPlayer p) {
        if (!ServerPlayNetworking.canSend(p, JettNetworking.StateS2C.TYPE)) return;
        long[] ready = READY_AT.get(p.getUUID());
        int[] cd = new int[4];
        for (int i = 0; i < cd.length; i++) {
            long remaining = ready == null ? 0 : ready[i] - now;
            cd[i] = (int) Math.max(0, Math.min(remaining, Integer.MAX_VALUE));
        }
        ServerPlayNetworking.send(p, new JettNetworking.StateS2C(mask(p), cd[0], cd[1], cd[2], cd[3]));
    }

    // ------------------------------------------------------------------ utilitários

    private static void play(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }

    // ------------------------------------------------------------------ efeitos com duração

    private interface Effect {
        /** @return false quando o efeito terminou e deve ser removido. */
        boolean tick(MinecraftServer server);
    }

    /** Nuvem de vento parada no mundo por 8 segundos. */
    private static final class CloudEffect implements Effect {
        private static final int DURATION = 160;
        private final ServerLevel level;
        private final double x, y, z;
        private int age = 0;

        CloudEffect(ServerLevel level, double x, double y, double z) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean tick(MinecraftServer server) {
            if (age >= DURATION) {
                play(level, x, y, z, SND_BREEZE_LAND, 0.8f, 0.9f);
                return false;
            }
            if (age % 25 == 0) play(level, x, y, z, SND_BREEZE_IDLE, 1.0f, 0.8f);
            particles(level, ParticleTypes.CLOUD, x, y, z, 14, 1.3, 0.9, 1.3, 0.005);
            if (age % 4 == 0) particles(level, ParticleTypes.POOF, x, y, z, 3, 1.0, 0.7, 1.0, 0.01);
            age++;
            return true;
        }
    }

    /** Rastro de vento que acompanha o jogador por alguns ticks (Updraft e Tailwind). */
    private static final class TrailEffect implements Effect {
        private final UUID player;
        private int left;

        TrailEffect(UUID player, int ticks) {
            this.player = player;
            this.left = ticks;
        }

        @Override
        public boolean tick(MinecraftServer server) {
            ServerPlayer p = server.getPlayerList().getPlayer(player);
            if (p == null || !p.isAlive() || left <= 0) return false;
            left--;
            ServerLevel level = (ServerLevel) p.level();
            particles(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.4, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            return true;
        }
    }

    /** Ultimate visual: anel de "lâminas" de vento girando em volta do jogador por 7 segundos. Sem dano. */
    private static final class StormEffect implements Effect {
        private static final int DURATION = 140;
        private static final int BLADES = 5;
        private final UUID player;
        private int age = 0;

        StormEffect(UUID player) {
            this.player = player;
        }

        @Override
        public boolean tick(MinecraftServer server) {
            ServerPlayer p = server.getPlayerList().getPlayer(player);
            if (p == null || !p.isAlive() || age >= DURATION) return false;
            ServerLevel level = (ServerLevel) p.level();
            double radius = 2.0 + 0.3 * Math.sin(age * 0.2);
            for (int i = 0; i < BLADES; i++) {
                double angle = age * 0.45 + i * (Math.PI * 2 / BLADES);
                double height = 0.2 + ((age * 0.05 + i * 0.4) % 2.0);
                double px = p.getX() + Math.cos(angle) * radius;
                double pz = p.getZ() + Math.sin(angle) * radius;
                particles(level, ParticleTypes.END_ROD, px, p.getY() + height, pz, 1, 0, 0, 0, 0);
                double trail = angle - 0.3;
                particles(level, ParticleTypes.CLOUD,
                        p.getX() + Math.cos(trail) * radius, p.getY() + height, p.getZ() + Math.sin(trail) * radius,
                        1, 0.05, 0.05, 0.05, 0.0);
            }
            if (age % 12 == 0) play(level, p.getX(), p.getY(), p.getZ(), SND_BREEZE_IDLE, 1.0f, 1.3f);
            age++;
            return true;
        }
    }
}
