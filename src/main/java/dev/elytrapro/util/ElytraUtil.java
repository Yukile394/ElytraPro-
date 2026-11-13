package dev.elytrapro.util;

import dev.elytrapro.config.ElytraProConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.function.Predicate;

public final class ElytraUtil {
    private ElytraUtil() {}

    public static boolean hasUsableElytra(ClientPlayerEntity p) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && ElytraItem.isUsable(chest);
    }

    /** True if ANY part of the player's hitbox overlaps a cobweb (same rule vanilla uses to slow you down). */
    public static boolean inCobweb(ClientPlayerEntity p) {
        return anyCell(p, s -> s.isOf(Blocks.COBWEB));
    }

    /** True if the player's hitbox overlaps an "inside" block (planks / logs by default). */
    public static boolean insidePlanks(ClientPlayerEntity p) {
        return anyCell(p, s -> ElytraProConfig.get().isInsideBlock(s));
    }

    private static boolean anyCell(ClientPlayerEntity p, Predicate<BlockState> test) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        Box b = p.getBoundingBox().contract(1.0E-7, 1.0E-7, 1.0E-7);
        int x0 = MathHelper.floor(b.minX), x1 = MathHelper.floor(b.maxX);
        int y0 = MathHelper.floor(b.minY), y1 = MathHelper.floor(b.maxY);
        int z0 = MathHelper.floor(b.minZ), z1 = MathHelper.floor(b.maxZ);
        for (BlockPos pos : BlockPos.iterate(x0, y0, z0, x1, y1, z1)) {
            if (test.test(mc.world.getBlockState(pos))) return true;
        }
        return false;
    }

    /** Same conditions vanilla uses before it sends "start gliding". */
    public static boolean canStartNow(ClientPlayerEntity p) {
        if (p.isFallFlying() || p.isOnGround() || p.hasVehicle() || p.isClimbing()
                || p.getAbilities().flying || p.isSpectator() || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.LEVITATION) || !hasUsableElytra(p)) {
            return false;
        }
        if (ElytraProConfig.get().onlyWhenFalling && !inCobweb(p)) {
            return p.getVelocity().y < 0.0;
        }
        return true;
    }

    /** Starts gliding exactly like vanilla does (local state + server packet). */
    public static boolean tryStartGliding(ClientPlayerEntity p) {
        if (!canStartNow(p)) return false;
        if (p.checkFallFlying()) {
            p.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;
    }
}
