package dev.openai.jettmobility;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item craftável que, ao ser usado UMA vez (botão direito), desbloqueia a habilidade de forma permanente.
 * Depois do desbloqueio, a habilidade é usada só pela tecla: não é preciso segurar nada.
 */
public class UnlockItem extends Item {
    private final Ability ability;

    public UnlockItem(Properties properties, Ability ability) {
        super(properties);
        this.ability = ability;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (JettAbilities.isUnlocked(serverPlayer, ability)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.jettmobility.already_unlocked", Component.translatable(ability.nameKey())), true);
            return InteractionResult.FAIL;
        }
        JettAbilities.unlock(serverPlayer, ability);
        stack.consume(1, player);
        serverPlayer.displayClientMessage(
                Component.translatable("message.jettmobility.unlocked", Component.translatable(ability.nameKey())), true);
        return InteractionResult.SUCCESS;
    }
}
