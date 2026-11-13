package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class AutoFirework {
    private static final int PENDING_TICKS = 40;

    private static long lastUse = Long.MIN_VALUE / 2;
    private static long lastPlain = Long.MIN_VALUE / 2;
    private static long boostUntil = Long.MIN_VALUE / 2;
    private static int pending;

    private AutoFirework() {}

    /**
     * Called when the right-click key is processed. Returns true when the click is consumed by the mod:
     *  - you are inside a cobweb / planks / logs (any crosshair target), or the crosshair is on a placed trigger block;
     *  - Stage 1 is ON, you are gliding and hold a rocket (plain rocket click, the mod fires it and runs Stage 1).
     * Everything else stays vanilla.
     */
    public static boolean tryUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null || mc.interactionManager == null) return false;
        if (!cfg.enabled) return false;
        if (p.isSpectator() || p.hasVehicle()) return false;

        long now = mc.world.getTime();
        boolean gliding = p.isFallFlying();
        Hand hand = findHand(p);

        // 1) trigger: inside cobweb/planks/logs, OR looking at a placed trigger block
        boolean inside = (cfg.launchInsideCobweb && ElytraUtil.inCobweb(p))
                || (cfg.launchInsidePlanks && ElytraUtil.insidePlanks(p));
        boolean onTriggerBlock = mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && cfg.isTriggerBlock(mc.world.getBlockState(hit.getBlockPos()));
        boolean trigger = cfg.autoFirework && !p.isSneaking() && (inside || onTriggerBlock);

        if (!trigger) {
            // plain rocket click while gliding: only Stage 1 takes it over
            if (gliding && hand != null && Stage1.isEnabled()) {
                if (now - lastPlain < 4) return true; // like vanilla's use delay
                lastPlain = now;
                fire(mc, p, hand, now);
                Stage1.trigger(mc, p, hand);
                return true;
            }
            return false;
        }

        // 2) holding a block => the player wants to place it, stay vanilla
        if (!cfg.triggerWithBlockInHand && p.getMainHandStack().getItem() instanceof BlockItem) return false;

        if (!ElytraUtil.hasUsableElytra(p)) { dbg(p, "no usable elytra"); return false; }
        // 3) a rocket MUST be in the main hand or off hand (nothing is taken from the hotbar)
        if (hand == null) { dbg(p, "no rocket in hand"); return false; }

        boolean cooling = now - lastUse < cfg.cooldownTicks;

        // already gliding: fire at once
        if (gliding) {
            boolean boosting = cfg.skipIfBoosting && now < boostUntil;
            if (!cooling && !boosting) fire(mc, p, hand, now);
            else dbg(p, cooling ? "cooldown" : "rocket still boosting");
            Stage1.trigger(mc, p, hand);
            return true;
        }

        if (cooling) return true;
        if (p.isTouchingWater() || p.hasStatusEffect(StatusEffects.LEVITATION)) { dbg(p, "water / levitation"); return false; }

        // on the ground: jump, then open wings + fire when the server will accept it
        if (p.isOnGround()) {
            if (!cfg.launchFromGround) return false;
            double jump = 0.42;
            StatusEffectInstance boost = p.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (boost != null) jump += 0.1 * (boost.getAmplifier() + 1);
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x, Math.max(v.y, jump), v.z);
            pending = PENDING_TICKS;
            return true;
        }

        // in the air: open wings and fire in the same tick
        if (ElytraUtil.canStartNow(p)) {
            if (ElytraUtil.tryStartGliding(p)) fire(mc, p, hand, now);
            return true;
        }
        pending = PENDING_TICKS;
        return true;
    }

    /** Called every client tick; finishes a queued take-off. */
    public static void tick(MinecraftClient mc) {
        if (pending <= 0) return;
        if (!ElytraProConfig.get().enabled) { pending = 0; return; }
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { pending = 0; return; }
        pending--;
        if (p.hasVehicle() || p.isTouchingWater() || p.isSpectator()) { pending = 0; return; }

        Hand hand = findHand(p);
        if (hand == null) { pending = 0; return; }
        long now = mc.world.getTime();

        if (p.isFallFlying()) {
            if (now - lastUse >= ElytraProConfig.get().cooldownTicks) fire(mc, p, hand, now);
            pending = 0;
            return;
        }
        if (!ElytraUtil.canStartNow(p)) return;
        if (ElytraUtil.tryStartGliding(p)) fire(mc, p, hand, now);
        pending = 0;
    }

    /** The hand that holds a firework rocket (main hand first), or null. */
    private static Hand findHand(ClientPlayerEntity p) {
        if (p.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) return Hand.MAIN_HAND;
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) return Hand.OFF_HAND;
        return null;
    }

    private static void fire(MinecraftClient mc, ClientPlayerEntity p, Hand hand, long now) {
        ItemStack stack = p.getStackInHand(hand);
        FireworksComponent fw = stack.get(DataComponentTypes.FIREWORKS);
        int flight = fw != null ? fw.flightDuration() : 1;
        lastUse = now;
        boostUntil = now + 10L * Math.max(1, flight) + 6;
        ActionResult r = mc.interactionManager.interactItem(p, hand);
        if (r.shouldSwingHand()) p.swingHand(hand);
    }

    private static void dbg(ClientPlayerEntity p, String why) {
        if (ElytraProConfig.get().debug) p.sendMessage(Text.literal("[ElytraPro] " + why), true);
    }
}
