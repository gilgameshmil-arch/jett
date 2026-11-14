package dev.openai.jettmobility;

/** Constantes de jogabilidade compartilhadas entre servidor e cliente (a previsão do cliente usa os mesmos valores). */
public final class JettTuning {
    private JettTuning() {}

    /** Velocidade vertical do Updraft (blocos/tick). */
    public static final double UPDRAFT_VELOCITY = 1.05;

    /** Tailwind: velocidade constante por alguns ticks (2.4 blocos/tick x 5 ticks ~ 12 blocos), depois freada suave. */
    public static final int DASH_TICKS = 5;
    public static final double DASH_SPEED = 2.4;
    public static final double DASH_END_SPEED = 0.35;

    /** Raio da esfera do Cloudburst (blocos). O modelo do núcleo é escalado para bater com este valor. */
    public static final double SPHERE_RADIUS = 3.2;
    public static final int SPHERE_TICKS = 180;

    public static final int STORM_TICKS = 140;
}
