/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class PacketMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between mining blocks in ticks.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("completion-progress")
        .description("Mining progress required before sending the finishing packet.")
        .defaultValue(1.0)
        .range(0.1, 1)
        .sliderRange(0.1, 1)
        .build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Queues up to two blocks and mines them in rapid succession.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> multitask = sgGeneral.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Allows Packet Mine to finish blocks while using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Logs Packet Mine timing data for protocol testing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends rotation packets to the server when mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switches to the best tool when the block is ready to be mined instantly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("not-on-use")
        .description("Won't auto switch if you're using an item.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Whether or not to render the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> readySideColor = sgRender.add(new ColorSetting.Builder()
        .name("ready-side-color")
        .description("The color of the sides of the blocks that can be broken.")
        .defaultValue(new SettingColor(0, 204, 0, 10))
        .build()
    );

    private final Setting<SettingColor> readyLineColor = sgRender.add(new ColorSetting.Builder()
        .name("ready-line-color")
        .description("The color of the lines of the blocks that can be broken.")
        .defaultValue(new SettingColor(0, 204, 0, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    private final Pool<MyBlock> blockPool = new Pool<>(MyBlock::new);
    public final List<MyBlock> blocks = new ArrayList<>();

    private boolean swapped, shouldUpdateSlot;

    public PacketMine() {
        super(Categories.World, "packet-mine", "Sends packets to mine blocks without the mining animation.");
    }

    @Override
    public void onActivate() {
        swapped = false;
        shouldUpdateSlot = false;
    }

    @Override
    public void onDeactivate() {
        for (MyBlock block : blocks) blockPool.free(block);
        blocks.clear();
        if (shouldUpdateSlot) {
            mc.player.networkHandler.send(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            shouldUpdateSlot = false;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!BlockUtils.canBreak(event.blockPos)) return;

        event.cancel();

        if (!isMiningBlock(event.blockPos)) {
            int maxBlocks = doubleBreak.get() ? 2 : 1;
            while (blocks.size() >= maxBlocks) {
                MyBlock removed = blocks.removeLast();
                removed.abort();
                blockPool.free(removed);
            }

            blocks.addLast(blockPool.get().set(event));
        }
    }

    public boolean isMiningBlock(BlockPos pos) {
        for (MyBlock block : blocks) {
            if (block.blockPos.equals(pos)) return true;
        }

        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        blocks.removeIf(block -> {
            if (!block.shouldRemove()) return false;

            blockPool.free(block);
            return true;
        });

        if (shouldUpdateSlot) {
            mc.player.networkHandler.send(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            shouldUpdateSlot = false;
        }

        if (!blocks.isEmpty()) blocks.getFirst().mine();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!debug.get() || !(event.packet instanceof BlockUpdateS2CPacket packet)) return;

        for (MyBlock block : blocks) {
            if (!block.blockPos.equals(packet.getPos())) continue;

            long elapsed = block.attemptedBreakAt == 0 ? -1 : System.currentTimeMillis() - block.attemptedBreakAt;
            MeteorClient.LOG.info("[PacketMine Test] UPDATE {} {} {}ms", block.blockPos.toShortString(), packet.getState().isAir() ? "AIR" : "SOLID", elapsed);
            info("Block update for %s: %s, %d ms after STOP.", block.blockPos.toShortString(), packet.getState().isAir() ? "AIR" : "SOLID", elapsed);
            break;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            for (MyBlock block : blocks) {
                if (Modules.get().get(BreakIndicators.class).isActive() && Modules.get().get(BreakIndicators.class).packetMine.get() && block.mining) {
                    continue;
                } else block.render(event);
            }
        }
    }

    public class MyBlock {
        public BlockPos blockPos;
        public BlockState blockState;
        public Block block;

        public Direction direction;

        public int timer;
        public boolean mining, finished;
        private long attemptedBreakAt;
        public double progress, prevProgress;

        public MyBlock set(StartBreakingBlockEvent event) {
            this.blockPos = event.blockPos;
            this.direction = event.direction;
            this.blockState = mc.world.getBlockState(blockPos);
            this.block = blockState.getBlock();
            this.timer = delay.get();
            this.mining = false;
            this.finished = false;
            this.progress = 0;
            this.prevProgress = 0;
            this.attemptedBreakAt = 0;

            return this;
        }

        public boolean shouldRemove() {
            boolean remove = mc.world.getBlockState(blockPos).getBlock() != block || Utils.distance(mc.player.getX() - 0.5, mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ() - 0.5, blockPos.getX() + direction.getOffsetX(), blockPos.getY() + direction.getOffsetY(), blockPos.getZ() + direction.getOffsetZ()) > mc.player.getBlockInteractionRange();

            if (remove) {
                mc.getNetworkHandler().send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
                mc.getNetworkHandler().send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            return remove;
        }

        public boolean isReady() {
            return progress >= speed.get();
        }

        public void mine() {
            if (finished) {
                if (!mc.world.getBlockState(blockPos).isAir() && System.currentTimeMillis() - attemptedBreakAt >= 500) {
                    abort();
                    mining = false;
                    finished = false;
                    progress = 0;
                    prevProgress = 0;
                    timer = delay.get();
                }
                return;
            }

            if (timer > 0) {
                timer--;
                return;
            }

            if (!mining) {
                sendStartPacket();
            }

            int miningSlot = mc.player.getInventory().selectedSlot;
            if (autoSwitch.get() && (!mc.player.isUsingItem() || !notOnUse.get())) {
                FindItemResult tool = InvUtils.findFastestTool(blockState);
                if (tool.found() && tool.slot() < 9) miningSlot = tool.slot();
            }

            prevProgress = progress;
            progress += BlockUtils.getBreakDelta(miningSlot, blockState);
            if (isReady() && (multitask.get() || !mc.player.isUsingItem())) {
                if (rotate.get()) Rotations.rotate(Rotations.getYaw(blockPos), Rotations.getPitch(blockPos));
                finishMining(miningSlot);
            }
        }

        private void sendStartPacket() {
            if (mining) return;

            mc.getNetworkHandler().send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, direction));
            mc.getNetworkHandler().send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mining = true;
            if (debug.get()) {
                MeteorClient.LOG.info("[PacketMine Test] START {} slot={}", blockPos.toShortString(), mc.player.getInventory().selectedSlot);
                info("START %s using slot %d.", blockPos.toShortString(), mc.player.getInventory().selectedSlot);
            }
        }

        private void finishMining(int miningSlot) {
            if (finished || !mining) return;

            if (autoSwitch.get() && (!mc.player.isUsingItem() || !notOnUse.get()) && mc.player.getInventory().selectedSlot != miningSlot) {
                mc.player.networkHandler.send(new UpdateSelectedSlotC2SPacket(miningSlot));
                swapped = true;
                shouldUpdateSlot = true;
            }

            mc.getNetworkHandler().send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction));
            mc.getNetworkHandler().send(new HandSwingC2SPacket(Hand.MAIN_HAND));
            finished = true;
            attemptedBreakAt = System.currentTimeMillis();
            if (debug.get()) {
                MeteorClient.LOG.info("[PacketMine Test] STOP {} progress={} slot={}", blockPos.toShortString(), progress, miningSlot);
                info("STOP %s at %.3f progress using slot %d.", blockPos.toShortString(), progress, miningSlot);
            }
        }

        private void abort() {
            if (!mining || mc.getNetworkHandler() == null) return;
            mc.getNetworkHandler().send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
        }

        public void render(Render3DEvent event) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);

            double x1 = blockPos.getX();
            double y1 = blockPos.getY();
            double z1 = blockPos.getZ();
            double x2 = blockPos.getX() + 1;
            double y2 = blockPos.getY() + 1;
            double z2 = blockPos.getZ() + 1;

            if (!shape.isEmpty()) {
                x1 = blockPos.getX() + shape.getMin(Direction.Axis.X);
                y1 = blockPos.getY() + shape.getMin(Direction.Axis.Y);
                z1 = blockPos.getZ() + shape.getMin(Direction.Axis.Z);
                x2 = blockPos.getX() + shape.getMax(Direction.Axis.X);
                y2 = blockPos.getY() + shape.getMax(Direction.Axis.Y);
                z2 = blockPos.getZ() + shape.getMax(Direction.Axis.Z);
            }

            double renderProgress = MathHelper.clamp(MathHelper.lerp(event.tickDelta, prevProgress, progress) / speed.get(), 0, 1);
            double centerX = (x1 + x2) * 0.5;
            double centerY = (y1 + y2) * 0.5;
            double centerZ = (z1 + z2) * 0.5;
            double halfX = (x2 - x1) * renderProgress * 0.5;
            double halfY = (y2 - y1) * renderProgress * 0.5;
            double halfZ = (z2 - z1) * renderProgress * 0.5;

            Color renderSideColor = lerpColor(sideColor.get(), readySideColor.get(), renderProgress);
            Color renderLineColor = lerpColor(lineColor.get(), readyLineColor.get(), renderProgress);
            event.renderer.box(centerX - halfX, centerY - halfY, centerZ - halfZ, centerX + halfX, centerY + halfY, centerZ + halfZ, renderSideColor, renderLineColor, shapeMode.get(), 0);
        }

        private Color lerpColor(Color from, Color to, double delta) {
            return new Color(
                (int) MathHelper.lerp(delta, from.r, to.r),
                (int) MathHelper.lerp(delta, from.g, to.g),
                (int) MathHelper.lerp(delta, from.b, to.b),
                (int) MathHelper.lerp(delta, from.a, to.a)
            );
        }
    }
}
