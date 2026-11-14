package dev.openai.jettmobility;

/** Estado das habilidades no cliente, alimentado pelo servidor (pacote StateS2C). Usado só pelo HUD e pelas teclas. */
public final class JettClientState {
    private JettClientState() {}

    private static int mask = 0;
    private static final long[] COOLDOWN_END = new long[Ability.VALUES.length];
    private static long clientTicks = 0;

    public static void tick() { clientTicks++; }

    public static void apply(JettNetworking.StateS2C state) {
        mask = state.mask();
        for (int i = 0; i < COOLDOWN_END.length; i++) {
            COOLDOWN_END[i] = clientTicks + state.cooldown(i);
        }
    }

    public static void reset() {
        mask = 0;
        java.util.Arrays.fill(COOLDOWN_END, 0);
    }

    public static boolean anyUnlocked() { return mask != 0; }

    public static boolean isUnlocked(Ability ability) { return (mask & ability.bit()) != 0; }

    public static int remainingTicks(Ability ability) {
        return (int) Math.max(0, COOLDOWN_END[ability.ordinal()] - clientTicks);
    }

    public static boolean isReady(Ability ability) {
        return isUnlocked(ability) && remainingTicks(ability) == 0;
    }

    /** Cooldown "previsto" no cliente no instante da tecla; o servidor corrige no próximo StateS2C. */
    public static void startCooldown(Ability ability) {
        COOLDOWN_END[ability.ordinal()] = clientTicks + ability.cooldownTicks;
    }
}
