package local.icepractice;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class IcePractice implements ModInitializer {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final class Pending {
        int count, lastTick, x, y, z;
        Pending(int count, int tick, BlockPos p) { this.count=count;lastTick=tick;x=p.getX();y=p.getY();z=p.getZ(); }
    }
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID,Pending>> it=PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID,Pending> e=it.next(); Pending p=e.getValue();
                if (server.getTickCount()-p.lastTick < 4) continue;
                ServerPlayer player=server.getPlayerList().getPlayer(e.getKey());
                if (player!=null) player.sendSystemMessage(Component.literal("Ice Practice (estimate): target "+p.x+", "+p.y+", "+p.z+"; packed ice broken: "+p.count));
                it.remove();
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> use(player, level, hand));
        UseItemCallback.EVENT.register((player, level, hand) -> use(player, level, hand));
    }
    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("icepractice")
            .then(Commands.literal("on").executes(c -> toggle(c.getSource().getPlayerOrException(), true)))
            .then(Commands.literal("off").executes(c -> toggle(c.getSource().getPlayerOrException(), false)))
            .then(Commands.literal("wand").executes(c -> giveWand(c.getSource().getPlayerOrException()))));
    }
    private static int toggle(ServerPlayer player, boolean enabled) {
        if (player.level().getServer().isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("Ice Practice works only in an integrated single-player world.")); return 0;
        }
        if (enabled) ACTIVE.add(player.getUUID()); else ACTIVE.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(enabled ? "Ice Practice ON: right-click with a pickaxe; packed ice only. /icepractice off to disable." : "Ice Practice OFF."));
        return 1;
    }
    private static final String WAND_NAME = "Ice Practice AOTV";
    private static int giveWand(ServerPlayer player) {
        if (player.level().getServer().isDedicatedServer()) {
            player.sendSystemMessage(Component.literal("Ice Practice works only in an integrated single-player world.")); return 0;
        }
        ItemStack wand = new ItemStack(Items.DIAMOND_SHOVEL);
        wand.set(DataComponents.CUSTOM_NAME, Component.literal(WAND_NAME));
        if (!player.getInventory().add(wand)) {
            player.sendSystemMessage(Component.literal("Inventory full: make room before using /icepractice wand.")); return 0;
        }
        player.sendSystemMessage(Component.literal("Ice Practice AOTV: right click = short warp; sneak + right click = Etherwarp (60 blocks)."));
        return 1;
    }
    private static InteractionResult teleport(Player player, Level level) {
        if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
        if (p.isShiftKeyDown()) {
            Vec3 eye=p.getEyePosition();
            BlockHitResult hit=level.clip(new ClipContext(eye, eye.add(p.getViewVector(1.0f).scale(60)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType()!=HitResult.Type.BLOCK) { p.sendSystemMessage(Component.literal("Etherwarp: no target within 60 blocks."));return InteractionResult.SUCCESS; }
            BlockPos target=hit.getBlockPos();
            if (!level.getBlockState(target.above()).isAir() || !level.getBlockState(target.above(2)).isAir()) {
                p.sendSystemMessage(Component.literal("Etherwarp: the two blocks above the target must be air."));return InteractionResult.SUCCESS;
            }
            p.teleportTo(target.getX()+0.5, target.getY()+1.0, target.getZ()+0.5);
            p.setDeltaMovement(0,0,0);
            p.resetFallDistance();
            return InteractionResult.SUCCESS;
        }
        Vec3 direction=p.getViewVector(1.0f), eye=p.getEyePosition();
        BlockHitResult hit=level.clip(new ClipContext(eye,eye.add(direction.scale(8)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double distance=hit.getType()==HitResult.Type.BLOCK ? Math.max(0,eye.distanceTo(hit.getLocation())-0.5) : 8.0;
        for (double dist=distance;dist>=0.5;dist-=0.25) {
            Vec3 offset=direction.scale(dist);
            if (level.noCollision(p,p.getBoundingBox().move(offset))) {
                Vec3 dest=p.position().add(offset);
                p.teleportTo(dest.x,dest.y,dest.z);p.setDeltaMovement(0,0,0);p.resetFallDistance();return InteractionResult.SUCCESS;
            }
        }
        p.sendSystemMessage(Component.literal("Warp: no safe space along the path."));return InteractionResult.SUCCESS;
    }
    private static InteractionResult use(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || level.getServer() == null || level.getServer().isDedicatedServer()) return InteractionResult.PASS;
        ItemStack held=player.getItemInHand(hand);
        if (held.is(Items.DIAMOND_SHOVEL) && held.has(DataComponents.CUSTOM_NAME) && WAND_NAME.equals(held.getHoverName().getString())) return teleport(player,level);
        if (!ACTIVE.contains(player.getUUID()) || !held.is(ItemTags.PICKAXES)) return InteractionResult.PASS;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(20.0));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) { player.sendSystemMessage(Component.literal("Ice Practice: no target within 20 blocks.")); return InteractionResult.SUCCESS; }
        BlockPos center = hit.getBlockPos();
        int count = IceBlastModel.apply(new IceBlastModel.World() {
            private BlockPos pos(int x, int y, int z) { return new BlockPos(x,y,z); }
            public boolean isIce(int x,int y,int z) { return level.getBlockState(pos(x,y,z)).is(Blocks.PACKED_ICE); }
            public boolean isAir(int x,int y,int z) { return level.getBlockState(pos(x,y,z)).isAir(); }
            public void breakIce(int x,int y,int z) { level.setBlockAndUpdate(pos(x,y,z), Blocks.AIR.defaultBlockState()); }
        }, center.getX(), center.getY(), center.getZ());
        int tick=level.getServer().getTickCount();
        Pending pending=PENDING.get(player.getUUID());
        if (pending!=null && tick-pending.lastTick < 4) { pending.count+=count; pending.lastTick=tick; }
        else { if (pending!=null) player.sendSystemMessage(Component.literal("Ice Practice (estimate): target "+pending.x+", "+pending.y+", "+pending.z+"; packed ice broken: "+pending.count)); PENDING.put(player.getUUID(),new Pending(count,tick,center)); }
        return InteractionResult.SUCCESS;
    }
}
