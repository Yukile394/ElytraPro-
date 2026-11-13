package dev.elytrapro.mixin;

import dev.elytrapro.client.Phase;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void elytrapro$phaseOn(CallbackInfo ci) {
        Phase.begin((ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void elytrapro$phaseOff(CallbackInfo ci) {
        Phase.end();
    }
}
