package local.icepractice;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class IceProbeClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int RADIUS=12, DURATION=120, ENTITY_RADIUS=32, MAX_ENTITIES=64;
    private static boolean enabled=false;
    private static Observation active;
    private static Path logPath;
    private static int completed;
    private static final class Observation {
        final ClientLevel level; final JsonObject record=new JsonObject();
        final Map<BlockPos,Boolean> initialIce=new LinkedHashMap<>();
        final Set<Integer> existingEntities=new HashSet<>();
        final Map<Integer,JsonObject> entities=new LinkedHashMap<>();
        final Set<BlockPos> removed=new LinkedHashSet<>();
        final Set<BlockPos> capturedBlocks=new HashSet<>();
        final Set<Integer> packetEntities=new HashSet<>();
        final JsonArray events=new JsonArray(), blocks=new JsonArray(), iceChanges=new JsonArray();
        final long startedNanos=System.nanoTime();
        Vec3 origin;
        int droppedEvents=0, droppedBlocks=0;
        int ticks=0;
        Observation(ClientLevel level) { this.level=level; }
    }
    @Override public void onInitializeClient() {
        IceGameContext.initialize();
        IceWaypoints.initialize();
        IceWorldRenderer.initialize();
        IceWaypointScreen.register();
        IceUnionClient.initialize();
        IceAngleClient.initialize();
        ClientCommandRegistrationCallback.EVENT.register((d,r) -> d.register(ClientCommands.literal("iceprobe")
            .then(ClientCommands.literal("start").executes(c -> start()))
            .then(ClientCommands.literal("stop").executes(c -> stop()))
            .then(ClientCommands.literal("status").executes(c -> status()))));
        UseBlockCallback.EVENT.register((p,w,h,hit) -> { click(p,h); return InteractionResult.PASS; });
        UseItemCallback.EVENT.register((p,w,h) -> { click(p,h); return InteractionResult.PASS; });
        ClientTickEvents.END_CLIENT_TICK.register(IceProbeClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { if(active!=null) finish("disconnected"); enabled=false; });
    }
    private static void msg(String s) { Minecraft c=Minecraft.getInstance();if(c.player!=null)c.player.sendSystemMessage(Component.literal("[IceProbe] "+s)); }
    private static int start() {
        Minecraft c=Minecraft.getInstance();
        if(c.player==null || c.level==null || c.getSingleplayerServer()!=null) {msg("Join a multiplayer server before recording.");return 0;}
        logPath=c.gameDirectory.toPath().resolve("config").resolve("icepractice").resolve("pickobulus-observations.jsonl");
        enabled=true;msg("Recording ON. Right-click manually to record a 6-second observation.");msg("Log: config/icepractice/pickobulus-observations.jsonl");return 1;
    }
    private static int stop(){enabled=false;if(active!=null)finish("stopped");msg("Recording OFF. Saved observations this session: "+completed);return 1;}
    private static int status(){msg("Recording "+(enabled?"ON":"OFF")+"; "+(active==null?"ready":"observing")+"; saved: "+completed);return 1;}
    private static JsonArray vec(Vec3 v){JsonArray a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static void click(Player player, InteractionHand hand) {
        Minecraft c=Minecraft.getInstance();
        if(!enabled||active!=null||hand!=InteractionHand.MAIN_HAND||c.player!=player||c.level==null||c.getSingleplayerServer()!=null)return;
        Observation o=new Observation(c.level);active=o;
        Vec3 eye=player.getEyePosition(), direction=player.getViewVector(1.0f); o.origin=eye;
        BlockHitResult hit=c.level.clip(new ClipContext(eye,eye.add(direction.scale(60)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        JsonObject j=o.record;j.addProperty("schema",2);j.addProperty("started_utc",Instant.now().toString());j.add("player_position",vec(player.position()));j.add("eye_position",vec(eye));j.add("view_vector",vec(direction));j.addProperty("yaw",player.getYRot());j.addProperty("pitch",player.getXRot());j.addProperty("sneaking",player.isShiftKeyDown());j.addProperty("held_item_type",BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());j.addProperty("manual_right_click",true);j.addProperty("ability_activation_verified",false);
        j.addProperty("raycast_hit",hit.getType()==HitResult.Type.BLOCK);
        if(hit.getType()==HitResult.Type.BLOCK){BlockPos center=hit.getBlockPos();j.add("raycast_block",pos(center));j.add("raycast_hit_position",vec(hit.getLocation()));j.addProperty("scan_radius_blocks",RADIUS);
            for(int x=-RADIUS;x<=RADIUS;x++)for(int y=-RADIUS;y<=RADIUS;y++)for(int z=-RADIUS;z<=RADIUS;z++){
                BlockPos b=center.offset(x,y,z);
                if(c.level.hasChunkAt(b)&&c.level.getBlockState(b).is(Blocks.PACKED_ICE))o.initialIce.put(b.immutable(),true);
            }
        }
        j.addProperty("initial_packed_ice_in_scan",o.initialIce.size());
        for(double t=0;t<=60;t+=0.5) captureBlocks(o,eye.add(direction.scale(t)),2);
        for(Entity e:c.level.entitiesForRendering()) if(!(e instanceof Player))o.existingEntities.add(e.getId());
        msg("Observation started; wait 6 seconds. Only existing packed ice near the aimed block is counted.");
    }
    private static void tick(Minecraft c) {
        Observation o=active;if(o==null)return;
        if(c.level!=o.level||c.player==null){finish("world_changed");return;}
        o.ticks++;
        for(BlockPos b:o.initialIce.keySet())if(c.level.hasChunkAt(b)&&!c.level.getBlockState(b).is(Blocks.PACKED_ICE)&&o.removed.add(b)){JsonObject change=new JsonObject();change.add("pos",pos(b));change.addProperty("tick",o.ticks);change.addProperty("elapsed_ms",(System.nanoTime()-o.startedNanos)/1_000_000.0);change.addProperty("state",c.level.getBlockState(b).toString());o.iceChanges.add(change);}
        Vec3 origin=c.player.position();
        for(Entity e:c.level.entitiesForRendering()) {
            if(e instanceof Player || o.existingEntities.contains(e.getId()) || e.position().distanceToSqr(origin)>ENTITY_RADIUS*ENTITY_RADIUS)continue;
            JsonObject tracked=o.entities.get(e.getId());
            if(tracked==null){if(o.entities.size()>=MAX_ENTITIES)continue;tracked=new JsonObject();tracked.addProperty("local_entity_id",e.getId());tracked.addProperty("entity_type",BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());tracked.add("samples",new JsonArray());o.entities.put(e.getId(),tracked);}
            JsonObject sample=new JsonObject();sample.addProperty("tick",o.ticks);sample.add("position",vec(e.position()));sample.add("velocity",vec(e.getDeltaMovement()));tracked.getAsJsonArray("samples").add(sample);
            captureBlocks(o,e.position(),1);
        }
        if(o.ticks>=DURATION)finish("complete");
    }
    private static void finish(String reason) {
        Observation o=active;if(o==null)return;active=null;
        o.record.addProperty("duration_client_ticks",o.ticks);o.record.addProperty("end_reason",reason);o.record.addProperty("observed_packed_ice_changed",o.removed.size());
        o.record.addProperty("interpretation","Client-observed changes near aim, not verified ability activation; may include other players or server updates; entity samples are candidates, not confirmed projectile.");
        JsonArray changed=new JsonArray();for(BlockPos p:o.removed)changed.add(pos(p));o.record.add("changed_ice_positions",changed);
        JsonArray entities=new JsonArray();o.entities.values().forEach(entities::add);o.record.add("new_nonplayer_entities",entities);
        o.record.add("packet_events",o.events);o.record.add("block_snapshots",o.blocks);o.record.add("ice_change_events",o.iceChanges);o.record.addProperty("dropped_packet_events",o.droppedEvents);o.record.addProperty("dropped_block_snapshots",o.droppedBlocks);
        try {Files.createDirectories(logPath.getParent());Files.writeString(logPath,GSON.toJson(o.record)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);completed++;msg("Saved: "+o.removed.size()+" ice changes; "+o.entities.size()+" candidate entities.");}
        catch(IOException e){msg("Could not save observation: "+e.getClass().getSimpleName());}
    }
    private static void captureBlocks(Observation o,Vec3 point,int radius) {
        BlockPos center=BlockPos.containing(point);
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){
            BlockPos p=center.offset(x,y,z);if(!o.level.hasChunkAt(p)||o.capturedBlocks.contains(p))continue;
            if(o.blocks.size()>=20000){o.droppedBlocks++;continue;}
            o.capturedBlocks.add(p.immutable());JsonObject b=new JsonObject();b.add("pos",pos(p));b.addProperty("tick",o.ticks);b.addProperty("state",o.level.getBlockState(p).toString());o.blocks.add(b);
        }
    }
    public static boolean listening(){return active!=null && Minecraft.getInstance().isSameThread();}
    public static boolean nearby(double x,double y,double z){return listening() && active.origin.distanceToSqr(x,y,z)<=64*64;}
    public static void addPacketEntity(int id){if(listening())active.packetEntities.add(id);}
    public static boolean packetEntity(int id){return listening() && active.packetEntities.contains(id);}
    public static void event(JsonObject e){if(!listening())return;Observation o=active;if(o.events.size()>=4096){o.droppedEvents++;return;}e.addProperty("tick",o.ticks);e.addProperty("elapsed_ms",(System.nanoTime()-o.startedNanos)/1_000_000.0);o.events.add(e);}
    public static JsonArray point(double x,double y,double z){JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
}
