package local.icepractice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Own configuration; the Skyblocker file is never modified by the mod. */
public final class IceWaypoints {
 public enum Type { STAND, AIM, CLEAR }
 public static final class Point {public Type type=Type.STAND;public int x,y,z;public String name="New point",group="Custom";public boolean enabled=true; public BlockPos pos(){return new BlockPos(x,y,z);} }
 public static final class Config {public int version=1;public boolean enabled=true,simpleLabels=true;public float labelScale=1f;public String standColor="FF0000",aimColor="00FF00",clearColor="FF8C00";public List<Point> waypoints=new ArrayList<>();}
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
 public static Config config;private static Path file;
 public static void initialize(){file=Minecraft.getInstance().gameDirectory.toPath().resolve("config/icepractice/waypoints.json");try{if(Files.exists(file)){try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){config=GSON.fromJson(r,Config.class);}if(config==null||config.waypoints==null)throw new IOException("Invalid waypoint file");}else{try(InputStream in=IceWaypoints.class.getResourceAsStream("/assets/icepractice/default-waypoints.json")){if(in==null)throw new IOException("Default waypoints unavailable");try(Reader r=new InputStreamReader(in,StandardCharsets.UTF_8)){config=GSON.fromJson(r,Config.class);}}save();}}catch(Exception e){System.err.println("[IceWaypoints] Could not load configuration; original file retained: "+e);config=new Config();}}
 public static void save(){if(file==null)return;try{Files.createDirectories(file.getParent());Path tmp=file.resolveSibling("waypoints.json.tmp");Files.writeString(tmp,GSON.toJson(config)+"\n",StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}catch(IOException e){System.err.println("[IceWaypoints] Save failed: "+e);}}
 public static int color(Type t){String hex=switch(t){case STAND->config.standColor;case AIM->config.aimColor;case CLEAR->config.clearColor;};try{return Integer.parseInt(hex,16)&0xffffff;}catch(Exception e){return switch(t){case STAND->0xff0000;case AIM->0x00ff00;case CLEAR->0xff8c00;};}}
 public static String label(Point p){if(!config.simpleLabels)return p.name;String[] words=p.name.strip().split("\\s+");String result=words.length>0?words[0]:"";if(words.length>1&&words[1].matches("\\d+"))result+=" "+words[1];return result+" "+(p.type==Type.STAND?"站点":"瞄点");}
 public static float labelWorldScale(double distance,float scale){return (float)(.0025*Math.max(.01,distance)*Math.clamp(scale,.5f,3f));}
 public static boolean showClear(double distanceSquared,boolean loaded,boolean packedIce){return distanceSquared>25||!loaded||packedIce;}
 public static boolean draw(Point p,Minecraft c){if(!config.enabled||!p.enabled||p.type==null||c.player==null||c.level==null)return false;if(p.type!=Type.CLEAR)return true;double distance=p.pos().distToCenterSqr(c.player.position());boolean loaded=c.level.hasChunkAt(p.pos());return showClear(distance,loaded,loaded&&c.level.getBlockState(p.pos()).is(net.minecraft.world.level.block.Blocks.PACKED_ICE));}
 private IceWaypoints(){}
}
