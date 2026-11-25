package local.icepractice.mixin;
import local.icepractice.IceProbeClient;
import local.icepractice.IceUnionClient;
import local.icepractice.IceAngleClient;

import com.google.gson.JsonObject;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class IceProbePacketMixin {
    private JsonObject event(String kind){JsonObject e=new JsonObject();e.addProperty("kind",kind);return e;}
    @Inject(method="handleLevelChunkWithLight",at=@At("TAIL"))
    private void iceChunk(ClientboundLevelChunkWithLightPacket p,CallbackInfo ci){IceAngleClient.chunkChanged(p.getX(),p.getZ());IceUnionClient.serverChunk(p.getX(),p.getZ());}
    @Inject(method="handleBlockUpdate",at=@At("TAIL"))
    private void iceBlock(ClientboundBlockUpdatePacket p,CallbackInfo ci){IceUnionClient.serverBlock(p.getPos(),p.getBlockState());}
    @Inject(method="handleBlockUpdate",at=@At("HEAD"))
    private void angleBeforeBlock(ClientboundBlockUpdatePacket p,CallbackInfo ci){IceAngleClient.beforeBlockUpdate(p.getPos(),p.getBlockState());}
    @Inject(method="handleChunkBlocksUpdate",at=@At("TAIL"))
    private void iceBlocks(ClientboundSectionBlocksUpdatePacket p,CallbackInfo ci){p.runUpdates(IceUnionClient::serverBlock);}
    @Inject(method="handleChunkBlocksUpdate",at=@At("HEAD"))
    private void angleBeforeBlocks(ClientboundSectionBlocksUpdatePacket p,CallbackInfo ci){if(Minecraft.getInstance().isSameThread())p.runUpdates(IceAngleClient::beforeBlockUpdate);}
    @Inject(method="handleParticleEvent",at=@At("HEAD"))
    private void particles(ClientboundLevelParticlesPacket p,CallbackInfo ci){
        if(!IceProbeClient.nearby(p.getX(),p.getY(),p.getZ()))return;
        JsonObject e=event("particle");e.add("position",IceProbeClient.point(p.getX(),p.getY(),p.getZ()));e.addProperty("type",BuiltInRegistries.PARTICLE_TYPE.getKey(p.getParticle().getType()).toString());e.add("spread",IceProbeClient.point(p.getXDist(),p.getYDist(),p.getZDist()));e.addProperty("count",p.getCount());e.addProperty("speed",p.getMaxSpeed());IceProbeClient.event(e);
    }
    @Inject(method="handleSoundEvent",at=@At("HEAD"))
    private void sound(ClientboundSoundPacket p,CallbackInfo ci){
        if(!IceProbeClient.nearby(p.getX(),p.getY(),p.getZ()))return;
        JsonObject e=event("sound");e.add("position",IceProbeClient.point(p.getX(),p.getY(),p.getZ()));e.addProperty("type",p.getSound().value().location().toString());e.addProperty("category",p.getSource().toString());e.addProperty("volume",p.getVolume());e.addProperty("pitch",p.getPitch());IceProbeClient.event(e);
    }
    @Inject(method="handleSoundEntityEvent",at=@At("HEAD"))
    private void entitySound(ClientboundSoundEntityPacket p,CallbackInfo ci){
        if(!IceProbeClient.listening() || Minecraft.getInstance().level==null)return;Entity entity=Minecraft.getInstance().level.getEntity(p.getId());if(entity==null || !IceProbeClient.nearby(entity.getX(),entity.getY(),entity.getZ()))return;
        JsonObject e=event("entity_sound");e.add("position",IceProbeClient.point(entity.getX(),entity.getY(),entity.getZ()));e.addProperty("entity_id",p.getId());e.addProperty("type",p.getSound().value().location().toString());e.addProperty("category",p.getSource().toString());e.addProperty("volume",p.getVolume());e.addProperty("pitch",p.getPitch());IceProbeClient.event(e);
    }
    @Inject(method="handleAddEntity",at=@At("HEAD"))
    private void add(ClientboundAddEntityPacket p,CallbackInfo ci){
        if(p.getType()==EntityType.PLAYER || !IceProbeClient.nearby(p.getX(),p.getY(),p.getZ()))return;
        IceProbeClient.addPacketEntity(p.getId());JsonObject e=event("entity_spawn");e.addProperty("entity_id",p.getId());e.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).toString());e.add("position",IceProbeClient.point(p.getX(),p.getY(),p.getZ()));e.add("velocity",IceProbeClient.point(p.getMovement().x,p.getMovement().y,p.getMovement().z));IceProbeClient.event(e);
    }
    @Inject(method="handleMoveEntity",at=@At("HEAD"))
    private void move(ClientboundMoveEntityPacket p,CallbackInfo ci){
        if(!IceProbeClient.listening() || Minecraft.getInstance().level==null)return;Entity entity=p.getEntity(Minecraft.getInstance().level);if(entity==null||!IceProbeClient.packetEntity(entity.getId()))return;
        JsonObject e=event("entity_move_delta");e.addProperty("entity_id",entity.getId());e.add("previous_client_position",IceProbeClient.point(entity.getX(),entity.getY(),entity.getZ()));e.add("delta_raw_4096",IceProbeClient.point(p.getXa(),p.getYa(),p.getZa()));e.addProperty("has_position",p.hasPosition());IceProbeClient.event(e);
    }
    @Inject(method="handleTeleportEntity",at=@At("HEAD"))
    private void teleport(ClientboundTeleportEntityPacket p,CallbackInfo ci){if(!IceProbeClient.packetEntity(p.id()))return;JsonObject e=event("entity_teleport");e.addProperty("entity_id",p.id());e.add("position_or_relative",IceProbeClient.point(p.change().position().x,p.change().position().y,p.change().position().z));e.addProperty("relative_flags",p.relatives().toString());IceProbeClient.event(e);}
    @Inject(method="handleEntityPositionSync",at=@At("HEAD"))
    private void sync(ClientboundEntityPositionSyncPacket p,CallbackInfo ci){if(!IceProbeClient.packetEntity(p.id()))return;JsonObject e=event("entity_position_sync");e.addProperty("entity_id",p.id());e.add("position",IceProbeClient.point(p.values().position().x,p.values().position().y,p.values().position().z));IceProbeClient.event(e);}
    @Inject(method="handleSetEntityMotion",at=@At("HEAD"))
    private void velocity(ClientboundSetEntityMotionPacket p,CallbackInfo ci){if(!IceProbeClient.packetEntity(p.id()))return;JsonObject e=event("entity_velocity");e.addProperty("entity_id",p.id());e.add("velocity",IceProbeClient.point(p.movement().x,p.movement().y,p.movement().z));IceProbeClient.event(e);}
    @Inject(method="handleRemoveEntities",at=@At("HEAD"))
    private void remove(ClientboundRemoveEntitiesPacket p,CallbackInfo ci){if(!IceProbeClient.listening())return;for(int id:p.getEntityIds()){if(!IceProbeClient.packetEntity(id))continue;JsonObject e=event("entity_remove");e.addProperty("entity_id",id);Entity entity=Minecraft.getInstance().level.getEntity(id);if(entity!=null)e.add("last_client_position",IceProbeClient.point(entity.getX(),entity.getY(),entity.getZ()));IceProbeClient.event(e);}}
}
