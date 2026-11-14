package dev.openai.jettmobility;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class JettMobility implements ModInitializer {
    public static final String MOD_ID = "jettmobility";

    public static final Item UPDRAFT_UNLOCK = register(Ability.UPDRAFT);
    public static final Item CLOUDBURST_UNLOCK = register(Ability.CLOUDBURST);
    public static final Item TAILWIND_UNLOCK = register(Ability.TAILWIND);
    public static final Item ULTIMATE_UNLOCK = register(Ability.ULTIMATE);

    /** Faca de vento: item de coleção com modelo 3D próprio (mão e inventário). Não causa dano. */
    public static final Item WIND_KNIFE = registerPlain("wind_knife");

    private static Item register(Ability ability) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, ability.unlockItem));
        // Desde 1.21.2 o id do item precisa estar nas Properties antes de criar o Item.
        Item item = new UnlockItem(new Item.Properties().setId(key).stacksTo(16), ability);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerPlain(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
    }

    @Override
    public void onInitialize() {
        JettParticles.init();
        JettNetworking.register();
        JettAbilities.init();
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(UPDRAFT_UNLOCK);
            entries.accept(CLOUDBURST_UNLOCK);
            entries.accept(TAILWIND_UNLOCK);
            entries.accept(ULTIMATE_UNLOCK);
            entries.accept(WIND_KNIFE);
        });
    }
}
