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

    public static final int FX_UPDRAFT = 0;
    public static final int FX_DASH = 1;
    public static final int FX_SPHERE = 2;
    public static final int FX_STORM = 3;

    /** Cliente -> servidor: "apertei a tecla da habilidade N". (dirX, dirZ) = direção de movimento desejada (Tailwind). */
    public record AbilityC2S(int ability, float dirX, float dirZ) implements CustomPacketPayload {
        public static final Type<AbilityC2S> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "ability"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AbilityC2S::ability,
                ByteBufCodecs.FLOAT, AbilityC2S::dirX,
                ByteBufCodecs.FLOAT, AbilityC2S::dirZ,
                AbilityC2S::new);

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

    /**
     * Servidor -> todos os jogadores próximos: "toque o efeito visual X".
     * Para efeitos que acompanham alguém, entityId é o jogador e (x,y,z) carrega a direção; para a esfera, é a posição.
     */
    public record FxS2C(int kind, int entityId, double x, double y, double z, int duration) implements CustomPacketPayload {
        public static final Type<FxS2C> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, "fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FxS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FxS2C::kind,
                ByteBufCodecs.VAR_INT, FxS2C::entityId,
                ByteBufCodecs.DOUBLE, FxS2C::x,
                ByteBufCodecs.DOUBLE, FxS2C::y,
                ByteBufCodecs.DOUBLE, FxS2C::z,
                ByteBufCodecs.VAR_INT, FxS2C::duration,
                FxS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(AbilityC2S.TYPE, AbilityC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(StateS2C.TYPE, StateS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(FxS2C.TYPE, FxS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AbilityC2S.TYPE,
                (payload, context) -> JettAbilities.tryActivate(context.player(), payload.ability(), payload.dirX(), payload.dirZ()));
    }
}
