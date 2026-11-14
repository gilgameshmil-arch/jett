package dev.openai.jettmobility;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Tipos de partícula próprios (texturas em assets/jettmobility/textures/particle e descrições em particles/). */
public final class JettParticles {
    private JettParticles() {}

    public static final SimpleParticleType SMOKE_PUFF = register("smoke_puff");
    public static final SimpleParticleType WIND_WISP = register("wind_wisp");

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(JettMobility.MOD_ID, name), FabricParticleTypes.simple());
    }

    public static void init() {
        // força o carregamento da classe (e o registro) durante a inicialização
    }
}
