package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Breaks the looked-at block when a brown mushroom turns into a red one.
 * The click is sent once, 2 to 5 ticks (random) after the change was seen.
 * Only a left click is sent: no rotation, no movement, no slot change.
 */
final class MushroomBreaker {
    private static final int MIN_DELAY_TICKS = 2;
    private static final int MAX_DELAY_TICKS = 5; // inclusive

    private static BlockPos lastPos = null;
    private static boolean lastWasBrown = false;

    private static BlockPos pendingPos = null; // block waiting for its delayed click
    private static int ticksLeft = 0;

    private MushroomBreaker() {}

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            lastPos = null;
            lastWasBrown = false;
            pendingPos = null; // looked away: cancel the click
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // A click is waiting: count down, then click once
        if (pendingPos != null) {
            if (!pos.equals(pendingPos) || !state.is(Blocks.RED_MUSHROOM)) {
                pendingPos = null; // looked at another block or the block changed: cancel
            } else {
                if (--ticksLeft <= 0) {
                    mc.gameMode.startDestroyBlock(pos, hit.getDirection());
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    pendingPos = null;
                }
                lastPos = pos;
                lastWasBrown = false;
                return;
            }
        }

        // Brown mushroom turned red at the same block: schedule the click
        if (pos.equals(lastPos) && lastWasBrown && state.is(Blocks.RED_MUSHROOM)) {
            pendingPos = pos;
            ticksLeft = ThreadLocalRandom.current().nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS + 1);
            lastWasBrown = false;
        } else {
            lastWasBrown = state.is(Blocks.BROWN_MUSHROOM);
        }
        lastPos = pos;
    }
}
