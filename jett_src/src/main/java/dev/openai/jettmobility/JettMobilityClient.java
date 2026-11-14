package dev.openai.jettmobility;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.PlayerCloudParticle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class JettMobilityClient implements ClientModInitializer {
    private static final KeyMapping[] KEYS = new KeyMapping[Ability.VALUES.length];

    // Previsão local do dash: o cliente já começa a se mover no mesmo tick da tecla (sem esperar a ida e volta ao servidor).
    private static int dashTicksLeft = 0;
    private static double dashDx = 0, dashDz = 0;

    public static KeyMapping keyFor(Ability ability) {
        return KEYS[ability.ordinal()];
    }

    @Override
    public void onInitializeClient() {
        // partículas próprias: textura/descrição em assets, comportamento de nuvem do vanilla (ajustado por JettFx)
        ParticleFactoryRegistry.getInstance().register(JettParticles.SMOKE_PUFF, PlayerCloudParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(JettParticles.WIND_WISP, PlayerCloudParticle.Provider::new);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "main"));
        KEYS[Ability.UPDRAFT.ordinal()] = key(Ability.UPDRAFT, InputConstants.KEY_Q, category);
        KEYS[Ability.CLOUDBURST.ordinal()] = key(Ability.CLOUDBURST, InputConstants.KEY_E, category);
        KEYS[Ability.TAILWIND.ordinal()] = key(Ability.TAILWIND, InputConstants.KEY_C, category);
        KEYS[Ability.ULTIMATE.ordinal()] = key(Ability.ULTIMATE, InputConstants.KEY_X, category);

        ClientPlayNetworking.registerGlobalReceiver(JettNetworking.StateS2C.TYPE,
                (payload, context) -> JettClientState.apply(payload));
        ClientPlayNetworking.registerGlobalReceiver(JettNetworking.FxS2C.TYPE,
                (payload, context) -> JettFx.handle(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            JettClientState.reset();
            JettFx.clear();
            dashTicksLeft = 0;
        });

        // START_CLIENT_TICK roda antes do tratamento de teclas do jogo: assim, quando uma habilidade
        // desbloqueada usa a mesma tecla de uma ação vanilla (E = inventário, Q = soltar item),
        // consumimos o clique do vanilla e só a habilidade é acionada.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!client.isPaused()) JettClientState.tick();
            applyDashPrediction(client);
            if (client.screen != null) return;
            for (Ability ability : Ability.VALUES) {
                KeyMapping key = KEYS[ability.ordinal()];
                while (key.consumeClick()) {
                    if (!JettClientState.isUnlocked(ability)) continue; // sem desbloqueio: a tecla segue vanilla
                    activate(client, ability);
                    swallowVanillaConflicts(client, key);
                }
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(JettFx::tick);

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "abilities"), new JettHud());
    }

    private static void activate(Minecraft client, Ability ability) {
        double[] dir = moveDirection(client);
        ClientPlayNetworking.send(new JettNetworking.AbilityC2S(ability.id(), (float) dir[0], (float) dir[1]));
        if (!JettClientState.isReady(ability) || client.player.isSpectator()) return;
        // previsão local (o servidor continua sendo a autoridade e corrige o estado)
        switch (ability) {
            case UPDRAFT -> {
                Vec3 v = client.player.getDeltaMovement();
                client.player.setDeltaMovement(v.x, JettTuning.UPDRAFT_VELOCITY, v.z);
                JettClientState.startCooldown(ability);
            }
            case TAILWIND -> {
                dashDx = dir[0];
                dashDz = dir[1];
                dashTicksLeft = JettTuning.DASH_TICKS;
                applyDashPrediction(client);
                JettClientState.startCooldown(ability);
            }
            default -> JettClientState.startCooldown(ability);
        }
    }

    private static void applyDashPrediction(Minecraft client) {
        if (dashTicksLeft <= 0 || client.player == null) return;
        double speed = dashTicksLeft > 1 ? JettTuning.DASH_SPEED : JettTuning.DASH_END_SPEED;
        client.player.setDeltaMovement(dashDx * speed, 0.0, dashDz * speed);
        dashTicksLeft--;
    }

    /** Direção horizontal desejada: teclas de movimento relativas ao olhar; sem teclas, para a frente. */
    private static double[] moveDirection(Minecraft client) {
        double yaw = Math.toRadians(client.player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        int forward = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
        int strafe = (client.options.keyLeft.isDown() ? 1 : 0) - (client.options.keyRight.isDown() ? 1 : 0);
        if (forward == 0 && strafe == 0) forward = 1;
        double dx = fx * forward + lx * strafe;
        double dz = fz * forward + lz * strafe;
        double len = Math.sqrt(dx * dx + dz * dz);
        return new double[] {dx / len, dz / len};
    }

    private static KeyMapping key(Ability ability, int code, KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + JettMobility.MOD_ID + "." + ability.key, InputConstants.Type.KEYSYM, code, category));
    }

    private static void swallowVanillaConflicts(Minecraft client, KeyMapping jett) {
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(jett);
        for (KeyMapping other : client.options.keyMappings) {
            if (other == jett || isJettKey(other)) continue;
            if (bound.equals(KeyBindingHelper.getBoundKeyOf(other))) {
                while (other.consumeClick()) {
                    // descarta o clique da ação vanilla que divide a tecla
                }
            }
        }
    }

    private static boolean isJettKey(KeyMapping candidate) {
        for (KeyMapping key : KEYS) {
            if (key == candidate) return true;
        }
        return false;
    }
}
