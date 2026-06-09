package net.shasankp000.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.shasankp000.Entity.BotSkinHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    private static final Identifier AI_SKIN_ID = Identifier.fromNamespaceAndPath("ai-player", "entity/ai_player");
    private static final Identifier AI_SKIN_TEXTURE = Identifier.fromNamespaceAndPath("ai-player", "textures/entity/ai_player.png");
    private static final PlayerSkin AI_SKIN = PlayerSkin.insecure(
            new ClientAsset.ResourceTexture(AI_SKIN_ID, AI_SKIN_TEXTURE),
            null,
            null,
            PlayerModelType.WIDE
    );

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void useAiPlayerSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (BotSkinHelper.isRegisteredBot(player.getGameProfile().name())) {
            cir.setReturnValue(AI_SKIN);
        }
    }
}
