package local.icepractice;

import com.google.gson.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local forecast and passive face visualization: no player movement, aiming or clicks. */
public final class IceAngleClient {
    public record Quad(Vec3[] vertices){}
    private record Sample(Vec3 hit,BlockPos block,Direction face){}
    private static final int GRID=41, PER_TICK=192; private static final double RANGE=5.0;
    private static final Direction[] FACES=Direction.values();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Long,Integer> countCache=new HashMap<>();
    private static final Sample[] samples=new Sample[6*GRID*GRID];
    private static final int[] scores=new int[6*GRID*GRID],faceBest=new int[6];
    private static final ArrayList<Quad> workingQuads=new ArrayList<>();
    private static List<Quad> quads=List.of();
    private static boolean enabled=true,dirty=true,scanning=false,refining=false,ready=false,bridgeAvailable=false;
    private static ClientLevel sampledLevel;private static Vec3 anchor;private static boolean anchorSneaking;
    private static BlockPos targetBlock;private static Direction initialFace,selectedFace;
    private static int index=0,tileIndex=0,best=-1,current=-1,stable=0,ticks=0,color=0xff0000,opacity=40;
    private static double currentYawAtStart,currentPitchAtStart,referenceYaw,referencePitch;
    private static double[] slices;private static String message="Aim at one block";private static Path settingsPath;
    private static float hudX=.01f,hudY=.80f,hudScale=1f;
    private static IceAngleCache cache; private static IceAngleCache.Entry activeEntry;
    private static final Set<Long> dependencies=new HashSet<>();
    private static Pending pending; private static String tab=""; private static boolean forceRefresh;
    private static final class Pending {final IceAngleCache.Entry entry;final ClientLevel level;final long start;boolean cooled,changed;Pending(IceAngleCache.Entry e,ClientLevel l){entry=e;level=l;start=System.nanoTime();}}
    private static void chat(String s){Minecraft c=Minecraft.getInstance();if(c.player!=null)c.player.sendSystemMessage(Component.literal("[IceAngle] "+s));}
    public static void initialize(){
        Minecraft c=Minecraft.getInstance();settingsPath=c.gameDirectory.toPath().resolve("config/icepractice/ice-angle.json");loadSettings();cache=new IceAngleCache(c.gameDirectory.toPath().resolve("config/icepractice/ice-angle-cache-v2.json.gz"));
        bridgeAvailable=true;
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,Identifier.fromNamespaceAndPath("icepractice","angle_status"),(graphics,delta)->hud(graphics));
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(ClientCommands.literal("iceangle").executes(ctx->{Minecraft client=Minecraft.getInstance();client.execute(()->client.setScreen(new IceAngleConfigScreen(client.screen)));return 1;})
            .then(ClientCommands.literal("on").executes(ctx->{enabled=true;invalidate();chat("Preview ON; aim at packed ice within 5 blocks.");return 1;}))
            .then(ClientCommands.literal("off").executes(ctx->{enabled=false;targetBlock=null;invalidate();chat("Preview OFF.");return 1;}))
            .then(ClientCommands.literal("status").executes(ctx->status()))
            .then(ClientCommands.literal("refresh").executes(ctx->{invalidate();forceRefresh=true;chat("Recalculating when the target and blocks are stable.");return 1;}))
            .then(ClientCommands.literal("color").then(ClientCommands.argument("hex",StringArgumentType.word()).executes(ctx->setColor(StringArgumentType.getString(ctx,"hex")))))
            .then(ClientCommands.literal("opacity").then(ClientCommands.argument("percent",IntegerArgumentType.integer(5,100)).executes(ctx->{opacity=IntegerArgumentType.getInteger(ctx,"percent");saveSettings();chat("Opacity: "+opacity+"%.");return 1;})))));
        ClientTickEvents.END_CLIENT_TICK.register(IceAngleClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->cache.close());
        UseBlockCallback.EVENT.register((p,w,h,hit)->{onUse(p,h);return InteractionResult.PASS;});
        UseItemCallback.EVENT.register((p,w,h)->{onUse(p,h);return InteractionResult.PASS;});
        ClientPlayConnectionEvents.DISCONNECT.register((h,client)->{targetBlock=null;sampledLevel=null;pending=null;tab="";invalidate();});
    }
    private static void loadSettings(){try{if(Files.exists(settingsPath)){JsonObject j=JsonParser.parseString(Files.readString(settingsPath,StandardCharsets.UTF_8)).getAsJsonObject();if(j.has("color"))color=Integer.parseUnsignedInt(j.get("color").getAsString(),16)&0xffffff;if(j.has("opacity"))opacity=Math.clamp(j.get("opacity").getAsInt(),5,100);if(j.has("hudX"))hudX=Math.clamp(j.get("hudX").getAsFloat(),0,1);if(j.has("hudY"))hudY=Math.clamp(j.get("hudY").getAsFloat(),0,1);if(j.has("hudScale"))hudScale=Math.clamp(j.get("hudScale").getAsFloat(),.5f,2f);}}catch(Exception e){System.err.println("[IceAngle] Settings not loaded: "+e);}}
    private static void saveSettings(){try{Files.createDirectories(settingsPath.getParent());JsonObject j=new JsonObject();j.addProperty("color",String.format("%06X",color));j.addProperty("opacity",opacity);j.addProperty("hudX",hudX);j.addProperty("hudY",hudY);j.addProperty("hudScale",hudScale);Path tmp=settingsPath.resolveSibling("ice-angle.json.tmp");Files.writeString(tmp,GSON.toJson(j)+"\n",StandardCharsets.UTF_8);try{Files.move(tmp,settingsPath,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,settingsPath,StandardCopyOption.REPLACE_EXISTING);}}catch(IOException e){chat("Could not save color; it remains active this session.");}}
    private static int setColor(String hex){try{if(!hex.matches("(?i)[0-9a-f]{6}"))throw new IllegalArgumentException();color=Integer.parseInt(hex,16);saveSettings();chat("Color #"+hex.toUpperCase(Locale.ROOT)+"; opacity "+opacity+"%.");return 1;}catch(IllegalArgumentException e){chat("Use six hex digits, e.g. /iceangle color FF0000");return 0;}}
    private static int status(){if(!enabled){chat("OFF. Use /iceangle on.");return 1;}Minecraft c=Minecraft.getInstance();chat(message);chat("TAB: "+(tab.isEmpty()?"not found":tab)+"; cache: "+cache.recentCount()+" temporary, "+cache.savedCount()+" saved (max 128).");if(c.player!=null){var item=c.player.getMainHandItem();chat("Tool: "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem())+"; pickaxe tag: "+item.is(ItemTags.PICKAXES)+"; first-person: "+c.options.getCameraType().isFirstPerson()+"; renderer: "+bridgeAvailable+".");}if(ready&&selectedFace!=null){chat(String.format(Locale.ROOT,"Target %d %d %d; best face %s; predicted packed ice %d; face tiles %d.",targetBlock.getX(),targetBlock.getY(),targetBlock.getZ(),selectedFace.getName(),best,quads.size()));chat(String.format(Locale.ROOT,"Reference yaw %.2f pitch %.2f; %s",referenceYaw,referencePitch,sliceText()));}return 1;}
    private static void invalidate(){dirty=true;ready=false;scanning=false;refining=false;activeEntry=null;quads=List.of();workingQuads.clear();dependencies.clear();index=tileIndex=0;best=current=-1;selectedFace=null;slices=null;countCache.clear();Arrays.fill(samples,null);Arrays.fill(scores,-1);Arrays.fill(faceBest,-1);stable=0;}
    private static void changedPrediction(){countCache.clear();current=-1;}
    public static void chunkChanged(int chunkX,int chunkZ){Minecraft c=Minecraft.getInstance();if(!enabled||targetBlock==null||c.level==null||!c.isSameThread()||dependencies.isEmpty())return;boolean affected=false;for(long packed:dependencies){BlockPos p=BlockPos.of(packed);if((p.getX()>>4)==chunkX&&(p.getZ()>>4)==chunkZ){affected=true;break;}}if(!affected)return;if(ready)changedPrediction();else if(scanning||refining)invalidate();}
    public static void beforeBlockUpdate(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){Minecraft c=Minecraft.getInstance();if(!c.isSameThread()||c.level==null)return;var old=c.level.getBlockState(pos);MiningProfitClient.blockUpdate(c,pos,old,state);if(pending!=null&&pending.level==c.level&&old.is(Blocks.PACKED_ICE)&&!state.is(Blocks.PACKED_ICE)&&Math.abs(pos.getX()-pending.entry.x)<=8&&Math.abs(pos.getY()-pending.entry.y)<=8&&Math.abs(pos.getZ()-pending.entry.z)<=8)pending.changed=true;if(!enabled||targetBlock==null||!dependencies.contains(pos.asLong())||old.equals(state))return;boolean modelChange=old.is(Blocks.PACKED_ICE)!=state.is(Blocks.PACKED_ICE)||old.isAir()!=state.isAir();boolean shapeChange=!modelChange&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(old.getCollisionShape(c.level,pos),state.getCollisionShape(c.level,pos),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME);if(modelChange||shapeChange){if(ready)changedPrediction();else if(scanning||refining)invalidate();}}
    private static void tick(Minecraft c){
        ticks++;if(c.player==null||c.level==null)return;
        if(ticks%5==0){try{tab=IceGameContext.pickobulus();}catch(RuntimeException e){tab="";}}
        checkPending(c);
        if(!enabled)return;
        if(!bridgeAvailable){message="Surface renderer unavailable";return;}
        if(c.getSingleplayerServer()==null&&tab.isEmpty()){if(targetBlock!=null){targetBlock=null;invalidate();}message="Pickobulus not found in TAB (enable Pickaxe Ability widget)";return;}
        if(!c.options.getCameraType().isFirstPerson()){if(targetBlock!=null){targetBlock=null;invalidate();}message="Switch to first-person view";return;}
        Vec3 eye=c.player.getEyePosition();boolean sneak=c.player.isShiftKeyDown();Sample under=trace(c,eye,c.player.getYRot(),c.player.getXRot());
        if(under==null||!c.level.getBlockState(under.block).is(Blocks.PACKED_ICE)){if(targetBlock!=null){targetBlock=null;invalidate();}message="Aim at visible packed ice within 5 blocks";return;}
        if(sampledLevel!=c.level||targetBlock==null||!targetBlock.equals(under.block)){sampledLevel=c.level;targetBlock=under.block;initialFace=under.face;anchor=eye;anchorSneaking=sneak;invalidate();}
        if(anchor==null||anchor.distanceToSqr(eye)>.04*.04||sneak!=anchorSneaking){anchor=eye;anchorSneaking=sneak;invalidate();}
        if(dirty&&!scanning&&!refining){if(stable==0&&!forceRefresh&&restore(c))return;if(++stable<6){message="Waiting for a stable target";return;}forceRefresh=false;begin(c);}
        if(scanning){int limit=FACES.length*GRID*GRID;for(int k=0;k<PER_TICK&&index<limit;k++,index++){
            int f=index/(GRID*GRID),rem=index%(GRID*GRID),row=rem/GRID,col=rem%GRID;Sample s=faceSample(c,FACES[f],(col+.5)/GRID,(row+.5)/GRID);if(s==null)continue;
            int n=count(c,s);if(n<0)continue;samples[index]=s;scores[index]=n;if(n>faceBest[f])faceBest[f]=n;
        }message="Comparing faces "+(100*index/limit)+"%";if(index>=limit)chooseFace(c);}
        if(refining){int limit=(GRID-1)*(GRID-1);for(int k=0;k<PER_TICK&&tileIndex<limit;k++,tileIndex++)verifyTile(c,tileIndex/(GRID-1),tileIndex%(GRID-1));message="Checking face "+(100*tileIndex/limit)+"% (prediction "+best+")";if(tileIndex>=limit)finish(c);}
        if(ready&&ticks%5==0){Sample s=trace(c,anchor,c.player.getYRot(),c.player.getXRot());current=(s!=null&&s.block.equals(targetBlock))?count(c,s):-1;}
    }
    private static void begin(Minecraft c){Arrays.fill(samples,null);Arrays.fill(scores,-1);Arrays.fill(faceBest,-1);countCache.clear();quads=List.of();workingQuads.clear();dependencies.clear();trackRayBounds();slices=null;best=-1;index=0;dirty=false;ready=false;refining=false;scanning=true;currentYawAtStart=c.player.getYRot();currentPitchAtStart=c.player.getXRot();}
    private static void trackRayBounds(){BlockPos eye=BlockPos.containing(anchor);for(int x=Math.min(eye.getX(),targetBlock.getX())-1;x<=Math.max(eye.getX(),targetBlock.getX())+1;x++)for(int y=Math.min(eye.getY(),targetBlock.getY())-1;y<=Math.max(eye.getY(),targetBlock.getY())+1;y++)for(int z=Math.min(eye.getZ(),targetBlock.getZ())-1;z<=Math.max(eye.getZ(),targetBlock.getZ())+1;z++)dependencies.add(BlockPos.asLong(x,y,z));}
    private static Sample trace(Minecraft c,Vec3 eye,double yaw,double pitch){if(pitch < -90||pitch>90)return null;double[] d=IceAngleMath.direction(yaw,pitch);return traceDirection(c,eye,new Vec3(d[0],d[1],d[2]));}
    private static Sample traceDirection(Minecraft c,Vec3 eye,Vec3 direction){
        BlockHitResult h=c.level.clip(new ClipContext(eye,eye.add(direction.scale(RANGE)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));if(h.getType()!=HitResult.Type.BLOCK)return null;
        double distance=eye.distanceTo(h.getLocation());if(distance<1e-6||distance>RANGE)return null;
        for(double t=0;t<=distance;t+=4)if(!c.level.hasChunkAt(BlockPos.containing(eye.add(direction.scale(t)))))return null;
        if(!c.level.hasChunkAt(h.getBlockPos()))return null;return new Sample(h.getLocation(),h.getBlockPos().immutable(),h.getDirection());
    }
    private static Vec3 facePoint(Direction f,double u,double v){double x=targetBlock.getX(),y=targetBlock.getY(),z=targetBlock.getZ();return switch(f){case WEST->new Vec3(x,y+u,z+v);case EAST->new Vec3(x+1,y+u,z+v);case DOWN->new Vec3(x+u,y,z+v);case UP->new Vec3(x+u,y+1,z+v);case NORTH->new Vec3(x+u,y+v,z);case SOUTH->new Vec3(x+u,y+v,z+1);};}
    private static Sample faceSample(Minecraft c,Direction f,double u,double v){Vec3 delta=facePoint(f,u,v).subtract(anchor);if(delta.lengthSqr()<1e-10)return null;Sample s=traceDirection(c,anchor,delta.normalize());return s!=null&&s.block.equals(targetBlock)&&s.face==f?s:null;}
    private static int count(Minecraft c,Sample s){
        Vec3 delta=s.hit.subtract(anchor);double distance=delta.length();if(distance<1e-6)return -1;Vec3 direction=delta.scale(1.0/distance);double t=IceAngleMath.stepIndex(distance)*.85;BlockPos center=BlockPos.containing(anchor.add(direction.scale(t)));long key=center.asLong();Integer old=countCache.get(key);if(old!=null)return old;
        ClientLevel w=c.level;int minY=w.getMinY(),maxY=minY+w.getHeight();if(center.getY()-4<minY||center.getY()+3>=maxY)return -1;
        for(int x:new int[]{-4,3})for(int z:new int[]{-4,3})if(!w.hasChunkAt(center.offset(x,0,z)))return -1;
        Set<Long> removed=new HashSet<>();int n=IceBlastModel.apply(new IceBlastModel.World(){
            BlockPos b(int x,int y,int z){return new BlockPos(x,y,z);}
            public boolean isIce(int x,int y,int z){BlockPos q=b(x,y,z);dependencies.add(q.asLong());return !removed.contains(q.asLong())&&w.getBlockState(q).is(Blocks.PACKED_ICE);}
            public boolean isAir(int x,int y,int z){BlockPos q=b(x,y,z);dependencies.add(q.asLong());return removed.contains(q.asLong())||w.getBlockState(q).isAir();}
            public void breakIce(int x,int y,int z){removed.add(b(x,y,z).asLong());}
        },center.getX(),center.getY(),center.getZ());countCache.put(key,n);return n;
    }
    private static void chooseFace(Minecraft c){
        scanning=false;best=Arrays.stream(faceBest).max().orElse(-1);if(best<=0){ready=false;message="No positive ice forecast on this block";return;}
        int selected=IceAngleMath.bestFace(faceBest,scores,GRID*GRID,faceIndex(initialFace));if(selected<0){ready=false;message="No reachable best face";return;}
        selectedFace=FACES[selected];double nearest=Double.POSITIVE_INFINITY;
        for(int j=0;j<GRID*GRID;j++){int q=selected*GRID*GRID+j;if(scores[q]!=best)continue;Vec3 v=samples[q].hit.subtract(anchor).normalize();double yaw=Math.toDegrees(Math.atan2(-v.x,v.z)),pitch=-Math.toDegrees(Math.asin(Math.clamp(v.y,-1,1)));double deltaYaw=IceAngleMath.wrap(yaw-currentYawAtStart),deltaPitch=pitch-currentPitchAtStart;double dist=deltaYaw*deltaYaw+deltaPitch*deltaPitch;if(dist<nearest){nearest=dist;referenceYaw=yaw;referencePitch=pitch;}}
        Sample initial=trace(c,anchor,currentYawAtStart,currentPitchAtStart);if(initial!=null&&initial.block.equals(targetBlock)&&initial.face==selectedFace&&count(c,initial)==best){referenceYaw=IceAngleMath.wrap(currentYawAtStart);referencePitch=currentPitchAtStart;}
        refining=true;tileIndex=0;workingQuads.clear();
    }
    private static int faceIndex(Direction d){for(int i=0;i<FACES.length;i++)if(FACES[i]==d)return i;throw new IllegalStateException();}
    private static boolean sameFace(Sample a,Sample b){if(b==null||a.face!=b.face||!a.block.equals(b.block))return false;return switch(a.face.getAxis()){case X->Math.abs(a.hit.x-b.hit.x)<1e-5;case Y->Math.abs(a.hit.y-b.hit.y)<1e-5;case Z->Math.abs(a.hit.z-b.hit.z)<1e-5;};}
    private static void verifyTile(Minecraft c,int row,int col){
        int base=faceIndex(selectedFace)*GRID*GRID,a=base+row*GRID+col,b=a+1,d=a+GRID,cc=d+1;if(scores[a]!=best||scores[b]!=best||scores[cc]!=best||scores[d]!=best)return;
        Sample s=samples[a];if(!sameFace(s,samples[b])||!sameFace(s,samples[cc])||!sameFace(s,samples[d]))return;
        for(double[] offset:new double[][]{{.5,.5},{.5,0},{.5,1},{0,.5},{1,.5}}){Sample mid=faceSample(c,selectedFace,(col+.5+offset[0])/GRID,(row+.5+offset[1])/GRID);if(mid==null||!sameFace(s,mid)||count(c,mid)!=best)return;}
        Vec3 normal=Vec3.atLowerCornerOf(s.face.getUnitVec3i()).scale(.003);Vec3[] vertices={s.hit.add(normal),samples[b].hit.add(normal),samples[cc].hit.add(normal),samples[d].hit.add(normal)};
        Vec3 cross=vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0]));if(cross.dot(normal)<0){Vec3 tmp=vertices[1];vertices[1]=vertices[3];vertices[3]=tmp;}workingQuads.add(new Quad(vertices));
    }
    private static void finish(Minecraft c){
        refining=false;ready=true;boolean dots=workingQuads.isEmpty();
        if(dots){int f=faceIndex(selectedFace),added=0;for(int i=0;i<GRID*GRID&&added<128;i++){
            int idx=f*GRID*GRID+i;if(scores[idx]!=best||samples[idx]==null)continue;Sample s=samples[idx];Vec3 normal=Vec3.atLowerCornerOf(s.face.getUnitVec3i()).scale(.003),a,b;
            switch(s.face.getAxis()){case X->{a=new Vec3(0,.004,0);b=new Vec3(0,0,.004);}case Y->{a=new Vec3(.004,0,0);b=new Vec3(0,0,.004);}default->{a=new Vec3(.004,0,0);b=new Vec3(0,.004,0);}}
            Vec3 at=s.hit.add(normal);Vec3[] vertices={at.subtract(a).subtract(b),at.add(a).subtract(b),at.add(a).add(b),at.subtract(a).add(b)};if(vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0])).dot(normal)<0){Vec3 t=vertices[1];vertices[1]=vertices[3];vertices[3]=t;}workingQuads.add(new Quad(vertices));added++;
        }}
        quads=List.copyOf(workingQuads);slices=new double[4];for(int i=0;i<4;i++){int sign=(i%2==0)?-1:1;boolean yaw=i<2;double valid=0;for(int k=1;k<=180;k++){double delta=sign*k*.25,y=referenceYaw+(yaw?delta:0),p=referencePitch+(yaw?0:delta);Sample s=trace(c,anchor,y,p);if(s==null||!s.block.equals(targetBlock)||s.face!=selectedFace||count(c,s)!=best)break;valid=delta;}slices[i]=valid;}message="Block "+targetBlock.getX()+","+targetBlock.getY()+","+targetBlock.getZ()+" face "+selectedFace.getName()+" forecast "+best+(dots?" (sample dots)":"");
        long[] positions=dependencies.stream().mapToLong(Long::longValue).sorted().toArray();activeEntry=snapshot(c,0,positions);cache.remember(activeEntry);
    }
    private static String worldKey(Minecraft c){String dim=c.level.dimension().toString();return c.getCurrentServer()!=null?IceWorldIdentity.multiplayer(c.getCurrentServer().ip,dim,IceGameContext.verifiedHypixelDwarven()):IceWorldIdentity.singleplayer(c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString(),dim);}
    private static String legacyServerKey(Minecraft c){return IceWorldIdentity.multiplayer(c.getCurrentServer().ip,c.level.dimension().toString(),false);}
    private static IceAngleCache.Entry snapshot(Minecraft c,long terrain,long[] positions){IceAngleCache.Entry e=new IceAngleCache.Entry();e.world=worldKey(c);e.x=targetBlock.getX();e.y=targetBlock.getY();e.z=targetBlock.getZ();e.initialFace=initialFace.getName();e.face=selectedFace.getName();e.eyeX=anchor.x;e.eyeY=anchor.y;e.eyeZ=anchor.z;e.sneaking=anchorSneaking;e.terrain=terrain;e.dependencies=positions;e.best=best;e.yaw=referenceYaw;e.pitch=referencePitch;e.slices=slices.clone();e.vertices=new float[quads.size()*12];int i=0;for(Quad q:quads)for(Vec3 v:q.vertices()){e.vertices[i++]=(float)v.x;e.vertices[i++]=(float)v.y;e.vertices[i++]=(float)v.z;}return e;}
    private static boolean restore(Minecraft c){String world=worldKey(c);int x=targetBlock.getX(),y=targetBlock.getY(),z=targetBlock.getZ();IceAngleCache.Entry e=cache.find(world,x,y,z,anchor.x,anchor.y,anchor.z,anchorSneaking);if(e==null&&c.getCurrentServer()!=null&&IceGameContext.verifiedHypixelDwarven()){e=cache.find(legacyServerKey(c),x,y,z,anchor.x,anchor.y,anchor.z,anchorSneaking);if(e!=null)e=cache.promoteLegacy(e,world);}if(e==null)return false;Direction direction=Direction.byName(e.face);if(direction==null)return false;ArrayList<Quad> restored=new ArrayList<>();for(int i=0;i<e.vertices.length;i+=12){Vec3[] v=new Vec3[4];for(int j=0;j<4;j++)v[j]=new Vec3(e.vertices[i+3*j],e.vertices[i+3*j+1],e.vertices[i+3*j+2]);restored.add(new Quad(v));}quads=List.copyOf(restored);selectedFace=direction;best=e.best;referenceYaw=e.yaw;referencePitch=e.pitch;slices=e.slices.clone();anchor=new Vec3(e.eyeX,e.eyeY,e.eyeZ);dependencies.clear();for(long p:e.dependencies)dependencies.add(p);activeEntry=e;dirty=false;ready=true;message="Cached block "+x+","+y+","+z+" face "+e.face+" reference "+best;return true;}
    private static boolean onHighlightedFace(Sample s){if(s==null||s.face!=selectedFace||!s.block.equals(targetBlock))return false;for(Quad q:quads){double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;for(Vec3 v:q.vertices()){minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);minZ=Math.min(minZ,v.z);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);maxZ=Math.max(maxZ,v.z);}double eps=.006;if(s.hit.x>=minX-eps&&s.hit.x<=maxX+eps&&s.hit.y>=minY-eps&&s.hit.y<=maxY+eps&&s.hit.z>=minZ-eps&&s.hit.z<=maxZ+eps)return true;}return false;}
    private static void onUse(net.minecraft.world.entity.player.Player player,InteractionHand hand){Minecraft c=Minecraft.getInstance();if(!enabled||!ready||activeEntry==null||pending!=null||hand!=InteractionHand.MAIN_HAND||player!=c.player||c.level==null||!tab.equals("Pickobulus: Available"))return;Sample s=trace(c,c.player.getEyePosition(),c.player.getYRot(),c.player.getXRot());if(onHighlightedFace(s))pending=new Pending(activeEntry,c.level);}
    private static void checkPending(Minecraft c){if(pending==null)return;if(c.level!=pending.level||System.nanoTime()-pending.start>8_000_000_000L){pending=null;return;}if(!tab.isEmpty()&&!tab.equals("Pickobulus: Available"))pending.cooled=true;if(pending.cooled&&pending.changed){cache.confirm(pending.entry,success->c.execute(()->chat(success?"Successful blast observed; highlighted area saved.":"Cache write failed; see game log.")));pending=null;}}
    private static String sliceText(){if(slices==null)return "Angle slices unavailable.";return String.format(Locale.ROOT,"yaw %+.2f..%+.2f deg, pitch %+.2f..%+.2f deg (other axis fixed; sampled 0.25 deg; max +/-45).",slices[0],slices[1],slices[2],slices[3]);}
    private static void hud(GuiGraphicsExtractor g){Minecraft c=Minecraft.getInstance();if(!enabled||c.player==null||c.level==null||targetBlock==null||c.screen!=null)return;drawHud(g,"IceAngle "+message,ready?"Current forecast: "+(current<0?"unknown":current)+" | /iceangle status":"",current==best);}
    public static void drawHud(GuiGraphicsExtractor g,String title,String detail,boolean optimal){Minecraft c=Minecraft.getInstance();g.pose().pushMatrix();g.pose().translate(hudX*g.guiWidth(),hudY*g.guiHeight());g.pose().scale(hudScale,hudScale);g.fill(-3,-3,295,30,0x99000000);g.text(c.font,title,0,0,0xffffffff,true);if(!detail.isEmpty())g.text(c.font,detail,0,12,optimal?0xff55ff55:0xffffff55,true);g.pose().popMatrix();}
    public static float hudX(){return hudX;}public static float hudY(){return hudY;}public static float hudScale(){return hudScale;}public static String colorHex(){return String.format("%06X",color);}public static int opacity(){return opacity;}
    public static void setHud(float x,float y,float scale){hudX=Math.clamp(x,0,1);hudY=Math.clamp(y,0,1);hudScale=Math.clamp(scale,.5f,2f);}public static void saveHud(){saveSettings();}
    public static boolean configure(String hex,int alpha){if(!hex.matches("(?i)[0-9a-f]{6}")||alpha<5||alpha>100)return false;color=Integer.parseInt(hex,16);opacity=alpha;saveSettings();return true;}
    public static List<Quad> quads(){return enabled&&ready?quads:List.of();}
    public static float[] color(){return new float[]{((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f};}
    public static float alpha(){return opacity/100f;}
    private IceAngleClient(){}
}
