package dev.elytrapro.mixin;

import dev.elytrapro.client.AutoFirework;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void elytrapro$autoFirework(CallbackInfo ci) {
        if (AutoFirework.tryUse((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
