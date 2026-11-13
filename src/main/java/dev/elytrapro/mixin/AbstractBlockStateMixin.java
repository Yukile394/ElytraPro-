package dev.elytrapro.mixin;

import dev.elytrapro.client.Phase;
import dev.elytrapro.config.ElytraProConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void elytrapro$phase(BlockView world, BlockPos pos, ShapeContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (Phase.active && ElytraProConfig.get().isPhaseBlock((BlockState) (Object) this)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
