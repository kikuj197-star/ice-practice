package local.icepractice;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class IceUnionClient {
    private record Section(ClientLevel world,int chunkX,int baseY,int chunkZ,LevelChunkSection blocks) {}
    private static final ArrayDeque<Section> queue=new ArrayDeque<>();
    private static IceUnionStore store; private static boolean failed; private static long drops; private static int ticks;
    private static Method isSkyblock,getLocation;
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(ClientCommands.literal("icecache").then(ClientCommands.literal("status").executes(c->status())).then(ClientCommands.literal("save").executes(c->{flush();return status();}))));
        ClientTickEvents.END_CLIENT_TICK.register(IceUnionClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{while(!queue.isEmpty())scanOne();flush();});
    }
    private static boolean allowed() {
        Minecraft c=Minecraft.getInstance();if(failed||c.level==null||c.player==null||c.getSingleplayerServer()!=null||!c.isSameThread())return false;
        try{return !IceGameContext.pickobulus().isEmpty()&&IceGameContext.dwarven();}
        catch(RuntimeException e){return false;}
    }
    private static boolean open() {
        if(failed)return false;if(store!=null)return true;
        Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("config/icepractice/glacite-ice-union.txt");
        try{store=new IceUnionStore(path);return true;}catch(IOException e){failed=true;System.err.println("[IceCache] Unable to load ice cache; collection disabled: "+e);message("Cache load failed; no changes made. See log.");return false;}
    }
    public static void serverChunk(int x,int z) {
        if(!allowed()||!open())return;ClientLevel world=Minecraft.getInstance().level;
        LevelChunk chunk=world.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);if(chunk==null)return;
        LevelChunkSection[] sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){
            LevelChunkSection s=sections[i];if(s==null || !s.maybeHas(b->b.is(Blocks.PACKED_ICE)))continue;
            if(queue.size()>=512){drops++;continue;}
            queue.addLast(new Section(world,x,chunk.getMinY()+16*i,z,s.copy()));
        }
    }
    public static void serverBlock(BlockPos pos,BlockState state){if(state.is(Blocks.PACKED_ICE)&&allowed()&&open())store.add(pos.getX(),pos.getY(),pos.getZ());}
    private static void tick(Minecraft c){
        for(int n=0;n<2&&!queue.isEmpty();n++)scanOne();
        if(++ticks%200==0)flush();
    }
    private static void scanOne(){Section s=queue.removeFirst();for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)if(s.blocks().getBlockState(x,y,z).is(Blocks.PACKED_ICE))store.add(s.chunkX()*16+x,s.baseY()+y,s.chunkZ()*16+z);}
    private static void flush(){if(store==null)return;try{store.flush();}catch(IOException e){System.err.println("[IceCache] Save failed; pending positions retained for retry: "+e);message("Save failed; pending ice kept in memory, retry with /icecache save.");}}
    private static void message(String s){Minecraft c=Minecraft.getInstance();if(c.player!=null)c.player.sendSystemMessage(Component.literal("[IceCache] "+s));}
    private static int status(){if(!open())return 0;message("Known ice: "+store.size()+"; pending save: "+store.pending()+"; queued sections: "+queue.size()+"; dropped sections: "+drops+"; "+(allowed()?"collecting":"waiting for Dwarven Mines"));return 1;}
}
