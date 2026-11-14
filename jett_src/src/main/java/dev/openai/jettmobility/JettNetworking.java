package dev.openai.jettmobility;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class JettNetworking {
    private JettNetworking() {}

    /** Cliente -> servidor: "apertei a tecla da habilidade N". O servidor valida tudo. */
    public record AbilityC2S(int ability) implements CustomPacketPayload {
        public static final Type<AbilityC2S> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "ability"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityC2S> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, AbilityC2S::ability, AbilityC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Servidor -> cliente dono: máscara de desbloqueio + cooldown restante (ticks) de cada habilidade, para o HUD. */
    public record StateS2C(int mask, int cd0, int cd1, int cd2, int cd3) implements CustomPacketPayload {
        public static final Type<StateS2C> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StateS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StateS2C::mask,
                ByteBufCodecs.VAR_INT, StateS2C::cd0,
                ByteBufCodecs.VAR_INT, StateS2C::cd1,
                ByteBufCodecs.VAR_INT, StateS2C::cd2,
                ByteBufCodecs.VAR_INT, StateS2C::cd3,
                StateS2C::new);

        public int cooldown(int index) {
            return switch (index) { case 0 -> cd0; case 1 -> cd1; case 2 -> cd2; default -> cd3; };
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(AbilityC2S.TYPE, AbilityC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(StateS2C.TYPE, StateS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AbilityC2S.TYPE,
                (payload, context) -> JettAbilities.tryActivate(context.player(), payload.ability()));
    }
}
