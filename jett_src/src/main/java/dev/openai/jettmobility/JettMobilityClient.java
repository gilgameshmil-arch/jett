package dev.openai.jettmobility;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class JettMobilityClient implements ClientModInitializer {
    private static final KeyMapping[] KEYS = new KeyMapping[Ability.VALUES.length];

    public static KeyMapping keyFor(Ability ability) {
        return KEYS[ability.ordinal()];
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "main"));
        KEYS[Ability.UPDRAFT.ordinal()] = key(Ability.UPDRAFT, InputConstants.KEY_Q, category);
        KEYS[Ability.CLOUDBURST.ordinal()] = key(Ability.CLOUDBURST, InputConstants.KEY_E, category);
        KEYS[Ability.TAILWIND.ordinal()] = key(Ability.TAILWIND, InputConstants.KEY_C, category);
        KEYS[Ability.ULTIMATE.ordinal()] = key(Ability.ULTIMATE, InputConstants.KEY_X, category);

        ClientPlayNetworking.registerGlobalReceiver(JettNetworking.StateS2C.TYPE,
                (payload, context) -> JettClientState.apply(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> JettClientState.reset());

        // START_CLIENT_TICK roda antes do tratamento de teclas do jogo: assim, quando uma habilidade
        // desbloqueada usa a mesma tecla de uma ação vanilla (E = inventário, Q = soltar item),
        // consumimos o clique do vanilla e só a habilidade é acionada.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!client.isPaused()) JettClientState.tick();
            if (client.screen != null) return;
            for (Ability ability : Ability.VALUES) {
                KeyMapping key = KEYS[ability.ordinal()];
                while (key.consumeClick()) {
                    if (!JettClientState.isUnlocked(ability)) continue; // sem desbloqueio: a tecla segue vanilla
                    ClientPlayNetworking.send(new JettNetworking.AbilityC2S(ability.id()));
                    swallowVanillaConflicts(client, key);
                }
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "abilities"), new JettHud());
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
