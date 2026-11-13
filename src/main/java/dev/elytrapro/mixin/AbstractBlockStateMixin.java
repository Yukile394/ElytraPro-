package dev.elytrapro.mixin;

import dev.elytrapro.client.Phase;
import dev.elytrapro.config.ElytraProConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    /** Planks / logs: no collision for the local player while Phase.active. */
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void elytrapro$phase(BlockView world, BlockPos pos, ShapeContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (Phase.active && ElytraProConfig.get().isPhaseBlock((BlockState) (Object) this)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }

    /** Cobweb: no slowdown for the local player while Phase.noWeb. */
    @Inject(method = "onEntityCollision(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void elytrapro$noWeb(World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (Phase.noWeb && entity instanceof ClientPlayerEntity
                && ((BlockState) (Object) this).isOf(Blocks.COBWEB)) {
            ci.cancel();
        }
    }
}
