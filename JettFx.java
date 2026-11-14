package dev.openai.jettmobility;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efeitos visuais do cliente. O servidor manda "toque o efeito X"; cada cliente próximo gera as partículas
 * em padrões calculados (esfera giratória, espirais, funil) com texturas e tamanhos próprios.
 */
public final class JettFx {
    private JettFx() {}

    private interface ClientEffect {
        /** @return false quando terminou. */
        boolean tick(Minecraft mc);
    }

    private static final List<ClientEffect> ACTIVE = new ArrayList<>();
    private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;

    public static void handle(JettNetworking.FxS2C fx) {
        if (ACTIVE.size() > 24) return;
        switch (fx.kind()) {
            case JettNetworking.FX_UPDRAFT -> ACTIVE.add(new UpdraftFx(fx.entityId(), fx.duration()));
            case JettNetworking.FX_DASH -> ACTIVE.add(new DashFx(fx.entityId(), fx.x(), fx.z(), fx.duration()));
            case JettNetworking.FX_SPHERE -> ACTIVE.add(new SphereFx(new Vec3(fx.x(), fx.y(), fx.z()), fx.duration()));
            case JettNetworking.FX_STORM -> ACTIVE.add(new StormFx(fx.entityId(), fx.duration()));
            default -> { }
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) return;
        ACTIVE.removeIf(effect -> !effect.tick(mc));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ utilitários

    private static double rnd() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Cria uma partícula, aumenta (as de nuvem do vanilla são minúsculas), define vida e tinge. */
    private static void puff(Minecraft mc, ParticleOptions type, double x, double y, double z,
                             double vx, double vy, double vz, float scale, int life, float r, float g, float b) {
        if (mc.player != null && mc.player.distanceToSqr(x, y, z) > MAX_DISTANCE_SQ) return;
        Particle particle = mc.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        if (particle == null) return;
        particle.scale(scale);
        particle.setLifetime(life);
        // Particle#setColor não existe mais em 1.21.11 (o tom já vem da textura), então não tingimos aqui.
    }

    private static void smoke(Minecraft mc, double x, double y, double z, double vx, double vy, double vz, float scale, int life) {
        puff(mc, JettParticles.SMOKE_PUFF, x, y, z, vx, vy, vz, scale, life,
                (float) (0.88 + 0.12 * rnd()), (float) (0.94 + 0.06 * rnd()), 1.0f);
    }

    private static void wisp(Minecraft mc, double x, double y, double z, double vx, double vy, double vz, float scale, int life) {
        puff(mc, JettParticles.WIND_WISP, x, y, z, vx, vy, vz, scale, life, 1.0f, 1.0f, 1.0f);
    }

    private static Entity find(Minecraft mc, int id) {
        return mc.level == null ? null : mc.level.getEntity(id);
    }

    // ------------------------------------------------------------------ Cloudburst: esfera de fumaça

    /** Casca esférica densa, com a fumaça girando em torno do eixo vertical, miolo fofo e poeira no chão. */
    private static final class SphereFx implements ClientEffect {
        private final Vec3 c;
        private final int duration;
        private int age = 0;

        SphereFx(Vec3 center, int duration) {
            this.c = center;
            this.duration = duration;
        }

        @Override
        public boolean tick(Minecraft mc) {
            if (age >= duration) return false;
            double grow = smooth(age / 10.0);
            double fade = smooth((duration - age) / 50.0);
            double r = JettTuning.SPHERE_RADIUS * grow * (0.6 + 0.4 * fade);
            if (r < 0.2) {
                age++;
                return true;
            }

            // explosão inicial: a bola "abre" a partir do ponto de impacto
            if (age < 4) {
                for (int i = 0; i < 18; i++) {
                    double[] d = randomDir();
                    smoke(mc, c.x + d[0] * r * 0.35, c.y + d[1] * r * 0.35, c.z + d[2] * r * 0.35,
                            d[0] * 0.3, d[1] * 0.3, d[2] * 0.3, (float) (7 + 3 * rnd()), 12 + (int) (6 * rnd()));
                }
            }

            // casca: partículas na superfície com velocidade tangencial (giro em torno do eixo Y)
            int shell = (int) Math.ceil(26 * fade);
            for (int i = 0; i < shell; i++) {
                double[] d = randomDir();
                double rr = r * (0.97 + 0.06 * rnd());
                double swirl = 0.065;
                smoke(mc, c.x + d[0] * rr, c.y + d[1] * rr, c.z + d[2] * rr,
                        -d[2] * swirl, 0.008 * (rnd() - 0.5), d[0] * swirl,
                        (float) (6.5 + 3 * rnd()), 16 + (int) (8 * rnd()));
            }
            // miolo: volume preenchido, giro mais lento
            int core = (int) Math.ceil(9 * fade);
            for (int i = 0; i < core; i++) {
                double[] d = randomDir();
                double rr = r * 0.8 * Math.cbrt(rnd());
                smoke(mc, c.x + d[0] * rr, c.y + d[1] * rr, c.z + d[2] * rr,
                        -d[2] * 0.025, 0.0, d[0] * 0.025,
                        (float) (9 + 4 * rnd()), 20 + (int) (10 * rnd()));
            }
            // poeira de vento no chão, empurrada para fora
            if (age % 2 == 0 && fade > 0.2) {
                double groundY = c.y - JettTuning.SPHERE_RADIUS * 0.75 + 0.15;
                for (int i = 0; i < 3; i++) {
                    double a = rnd() * Math.PI * 2;
                    double rad = JettTuning.SPHERE_RADIUS * (0.5 + 0.5 * rnd()) * grow;
                    smoke(mc, c.x + Math.cos(a) * rad, groundY, c.z + Math.sin(a) * rad,
                            Math.cos(a) * 0.05, 0.01, Math.sin(a) * 0.05, 4.5f, 14);
                }
            }
            // faíscas de vento orbitando a superfície
            if (age % 3 == 0) {
                double a = age * 0.22;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    double h = Math.sin(age * 0.07 + k * 2) * r * 0.7;
                    double ring = Math.sqrt(Math.max(r * r - h * h, 0));
                    wisp(mc, c.x + Math.cos(ang) * ring, c.y + h, c.z + Math.sin(ang) * ring,
                            -Math.sin(ang) * 0.12, 0, Math.cos(ang) * 0.12, 3.0f, 12);
                }
            }
            age++;
            return true;
        }
    }

    private static double[] randomDir() {
        double u = rnd() * 2 - 1;
        double phi = rnd() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new double[] {s * Math.cos(phi), u, s * Math.sin(phi)};
    }

    // ------------------------------------------------------------------ Updraft: corrente de ar subindo em espiral

    private static final class UpdraftFx implements ClientEffect {
        private final int entityId;
        private final int duration;
        private int age = 0;

        UpdraftFx(int entityId, int duration) {
            this.entityId = entityId;
            this.duration = duration;
        }

        @Override
        public boolean tick(Minecraft mc) {
            Entity e = find(mc, entityId);
            if (e == null || age >= duration) return false;
            double ex = e.getX(), ey = e.getY(), ez = e.getZ();

            if (age == 0) { // anel de choque no chão
                for (int i = 0; i < 28; i++) {
                    double a = i * Math.PI * 2 / 28;
                    smoke(mc, ex + Math.cos(a) * 0.5, ey + 0.1, ez + Math.sin(a) * 0.5,
                            Math.cos(a) * 0.24, 0.02, Math.sin(a) * 0.24, 4.5f, 14);
                }
            }
            double life = 1.0 - (double) age / duration;
            for (int strand = 0; strand < 3; strand++) {
                for (int sub = 0; sub < 2; sub++) {
                    double ang = (age + sub * 0.5) * 0.85 + strand * Math.PI * 2 / 3;
                    double rad = (0.75 + 0.15 * Math.sin(age * 0.4)) * (0.7 + 0.3 * life);
                    double y = ey + 0.1 + rnd() * 1.7;
                    double px = ex + Math.cos(ang) * rad;
                    double pz = ez + Math.sin(ang) * rad;
                    double tx = -Math.sin(ang) * 0.07, tz = Math.cos(ang) * 0.07;
                    wisp(mc, px, y, pz, tx, 0.10 + 0.08 * rnd(), tz, 3.2f, 11);
                    if (sub == 0) smoke(mc, px, y, pz, tx, 0.07 + 0.05 * rnd(), tz, 3.6f, 14);
                }
            }
            age++;
            return true;
        }
    }

    // ------------------------------------------------------------------ Tailwind: dupla hélice de vento atrás do jogador

    private static final class DashFx implements ClientEffect {
        private final int entityId;
        private final int duration;
        private final Vec3 dir;
        private Vec3 last = null;
        private int age = 0;

        DashFx(int entityId, double dx, double dz, int duration) {
            this.entityId = entityId;
            this.duration = duration;
            double len = Math.sqrt(dx * dx + dz * dz);
            this.dir = len < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(dx / len, 0, dz / len);
        }

        @Override
        public boolean tick(Minecraft mc) {
            Entity e = find(mc, entityId);
            if (e == null || age >= duration) return false;
            Vec3 cur = new Vec3(e.getX(), e.getY() + 0.9, e.getZ());
            if (last == null) last = cur.subtract(dir.scale(0.4));
            Vec3 right = new Vec3(-dir.z, 0, dir.x);
            Vec3 up = new Vec3(0, 1, 0);
            double life = Math.max(0.25, 1.0 - (double) age / duration);

            if (age == 0) { // estouro em cone atrás do jogador
                for (int i = 0; i < 26; i++) {
                    Vec3 p = cur.subtract(dir.scale(rnd() * 1.4));
                    smoke(mc, p.x + (rnd() - 0.5) * 0.8, p.y + (rnd() - 0.5) * 0.9, p.z + (rnd() - 0.5) * 0.8,
                            -dir.x * (0.2 + rnd() * 0.25), (rnd() - 0.5) * 0.06, -dir.z * (0.2 + rnd() * 0.25),
                            (float) (4.5 + 2 * rnd()), 12);
                }
            }
            // hélice: 4 sub-passos por tick ao longo do trajeto => espiral contínua mesmo a 2 blocos/tick
            for (int sub = 1; sub <= 4; sub++) {
                double t = sub / 4.0;
                Vec3 pos = last.lerp(cur, t);
                double phase = (age + t) * 1.5;
                for (int strand = 0; strand < 2; strand++) {
                    double ang = phase + strand * Math.PI;
                    double rad = 0.78 * life;
                    Vec3 off = right.scale(Math.cos(ang) * rad).add(up.scale(Math.sin(ang) * rad));
                    Vec3 tangent = right.scale(-Math.sin(ang)).add(up.scale(Math.cos(ang))).scale(0.05);
                    wisp(mc, pos.x + off.x, pos.y + off.y, pos.z + off.z,
                            tangent.x - dir.x * 0.05, tangent.y, tangent.z - dir.z * 0.05, 3.2f, 11);
                    if ((sub & 1) == 0) {
                        smoke(mc, pos.x + off.x * 1.1, pos.y + off.y * 1.1, pos.z + off.z * 1.1,
                                off.x * 0.03, off.y * 0.03, off.z * 0.03, 3.8f, 14);
                    }
                }
                // faixa central de velocidade
                wisp(mc, pos.x, pos.y, pos.z, -dir.x * 0.2, 0, -dir.z * 0.2, 2.6f, 6);
            }
            last = cur;
            age++;
            return true;
        }
    }

    // ------------------------------------------------------------------ Ultimate: funil de vento em volta do jogador

    private static final class StormFx implements ClientEffect {
        private final int entityId;
        private final int duration;
        private int age = 0;

        StormFx(int entityId, int duration) {
            this.entityId = entityId;
            this.duration = duration;
        }

        @Override
        public boolean tick(Minecraft mc) {
            Entity e = find(mc, entityId);
            if (e == null || age >= duration) return false;
            double ex = e.getX(), ey = e.getY(), ez = e.getZ();
            double fade = smooth((duration - age) / 25.0);
            int strands = Math.max(1, (int) Math.round(4 * fade));

            for (int s = 0; s < strands; s++) {
                for (int k = 0; k < 2; k++) {
                    double height = ((age * 0.06 + s * 0.55 + k * 1.1) % 2.3);
                    double rad = 1.15 + height * 0.35;
                    double ang = age * 0.4 + s * Math.PI * 2 / strands + height * 1.5;
                    double px = ex + Math.cos(ang) * rad;
                    double pz = ez + Math.sin(ang) * rad;
                    double tx = -Math.sin(ang) * 0.06, tz = Math.cos(ang) * 0.06;
                    wisp(mc, px, ey + height, pz, tx, 0.02, tz, 3.0f, 10);
                    if (k == 0) smoke(mc, px, ey + height, pz, tx, 0.02, tz, 3.4f, 13);
                }
            }
            if (age % 20 == 0 && fade > 0.3) { // pulso de poeira no chão a cada segundo
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    smoke(mc, ex + Math.cos(a) * 0.8, ey + 0.1, ez + Math.sin(a) * 0.8,
                            Math.cos(a) * 0.16, 0.01, Math.sin(a) * 0.16, 4.0f, 12);
                }
            }
            age++;
            return true;
        }
    }
}
