package dev.openai.jettmobility;

/** As quatro habilidades da Jett. A ordem (ordinal) define o id enviado pela rede e o bit de desbloqueio. */
public enum Ability {
    UPDRAFT("updraft", "updraft_unlock", 160),          // Q
    CLOUDBURST("cloudburst", "cloudburst_unlock", 300), // E
    TAILWIND("tailwind", "tailwind_unlock", 120),       // C
    ULTIMATE("ultimate", "wind_mastery_unlock", 1200);  // X

    public static final Ability[] VALUES = values();

    public final String key;
    public final String unlockItem;
    public final int cooldownTicks;

    Ability(String key, String unlockItem, int cooldownTicks) {
        this.key = key;
        this.unlockItem = unlockItem;
        this.cooldownTicks = cooldownTicks;
    }

    public int id() { return ordinal(); }

    public int bit() { return 1 << ordinal(); }

    public String nameKey() { return "ability." + JettMobility.MOD_ID + "." + key; }

    public static Ability byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
