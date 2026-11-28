package local.icepractice;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class IceGameContext {
 private static volatile Boolean apiDwarven; private static volatile boolean apiSkyblock,apiMining;
 private static volatile boolean showWaypoints;private static int ticks;
 public static void initialize(){registerOptionalHypixelLocation();ClientPlayConnectionEvents.JOIN.register((h,s,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());ClientTickEvents.END_CLIENT_TICK.register(c->{if(++ticks%10==0)refresh(c);});}
 private static void reset(){apiDwarven=null;apiSkyblock=false;apiMining=false;showWaypoints=false;ticks=0;}
 private static void refresh(Minecraft c){if(c.player==null||c.level==null){showWaypoints=false;return;}boolean single=c.getSingleplayerServer()!=null;boolean fallback=!single&&apiDwarven==null&&IceLocationRules.fallback(locationLines(c));showWaypoints=IceLocationRules.display(single,apiDwarven,fallback);}
 public static boolean showWaypoints(){return showWaypoints;}
 public static boolean verifiedHypixelDwarven(){return Boolean.TRUE.equals(apiDwarven);}
 public static boolean verifiedHypixelSkyblock(){return apiSkyblock;}
 public static boolean verifiedHypixelMining(){return apiMining;}
 private static List<String> locationLines(Minecraft c){ArrayList<String> lines=new ArrayList<>(tabLines());Scoreboard board=c.level.getScoreboard();Objective o=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(o!=null){lines.add(o.getDisplayName().getString());for(PlayerScoreEntry e:board.listPlayerScores(o)){PlayerTeam t=board.getPlayersTeam(e.owner());lines.add(t==null?e.owner():t.getPlayerPrefix().getString()+e.owner()+t.getPlayerSuffix().getString());}}return lines;}
 private static void registerOptionalHypixelLocation(){try{
  Class<?> apiClass=Class.forName("net.hypixel.modapi.HypixelModAPI");Class<?> packet=Class.forName("net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket");Class<?> handlerType=Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler");Object api=apiClass.getMethod("getInstance").invoke(null);
  Object handler=java.lang.reflect.Proxy.newProxyInstance(handlerType.getClassLoader(),new Class<?>[]{handlerType},(proxy,method,args)->{if(method.getName().equals("handle")&&args!=null&&args.length==1){Object p=args[0];Object t=((Optional<?>)packet.getMethod("getServerType").invoke(p)).orElse(null);Object m=((Optional<?>)packet.getMethod("getMode").invoke(p)).orElse(null);apiSkyblock=t!=null&&"SKYBLOCK".equalsIgnoreCase(t.toString());apiMining=t!=null&&m!=null&&IceLocationRules.isMiningPacket(t.toString(),m.toString());apiDwarven=(t==null||m==null)?null:IceLocationRules.isDwarvenPacket(t.toString(),m.toString());if(Boolean.FALSE.equals(apiDwarven)&&Minecraft.getInstance().getSingleplayerServer()==null)showWaypoints=false;}else if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);else if(method.getName().equals("equals"))return proxy==args[0];else if(method.getName().equals("toString"))return "IcePracticeLocationHandler";return null;});
  apiClass.getMethod("createHandler",Class.class,handlerType).invoke(api,packet,handler);apiClass.getMethod("subscribeToEventPacket",Class.class).invoke(api,packet);
 }catch(ClassNotFoundException e){System.out.println("[IcePractice] Hypixel Mod API not installed; using sidebar location fallback.");}catch(ReflectiveOperationException|RuntimeException|LinkageError e){System.err.println("[IcePractice] Location API unavailable; using sidebar fallback: "+e);}}
 public static List<String> tabLines(){var c=Minecraft.getInstance().getConnection();if(c==null)return List.of();ArrayList<String> out=new ArrayList<>();for(PlayerInfo p:c.getOnlinePlayers())if(p.getTabListDisplayName()!=null)out.add(p.getTabListDisplayName().getString().strip());return out;}
 public static String pickobulus(){for(String s:tabLines())if(s.startsWith("Pickobulus: "))return s;return "";}
 public static boolean dwarven(){Minecraft c=Minecraft.getInstance();if(c.level==null)return false;if(apiDwarven!=null)return apiDwarven;for(String line:locationLines(c)){String s=line.toLowerCase(Locale.ROOT);if(s.contains("dwarven mines")||s.contains("glacite tunnels"))return true;}return false;}
 private IceGameContext(){}
}
