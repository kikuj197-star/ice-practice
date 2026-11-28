package local.icepractice;
import java.util.*;

/** Separate display scope from the waypoints themselves and from the active ability. */
public final class IceLocationRules {
 public static boolean display(boolean singleplayer,Boolean hypixelDwarven,boolean fallback){return singleplayer||Boolean.TRUE.equals(hypixelDwarven)||(hypixelDwarven==null&&fallback);}
 public static boolean isDwarvenPacket(String serverType,String mode){return "SKYBLOCK".equalsIgnoreCase(serverType)&&"mining_3".equalsIgnoreCase(mode);}
 public static boolean isMiningPacket(String serverType,String mode){return "SKYBLOCK".equalsIgnoreCase(serverType)&&mode!=null&&Set.of("mining_1","mining_2","mining_3","crystal_hollows","mineshaft").contains(mode.toLowerCase(Locale.ROOT));}
 public static boolean fallback(List<String> lines){boolean hypixel=false,skyblock=false,dwarven=false;for(String line:lines){String s=line.toLowerCase(Locale.ROOT);if(s.contains("hypixel.net"))hypixel=true;if(s.contains("skyblock"))skyblock=true;if(s.contains("dwarven mines")||s.contains("glacite tunnels")||s.contains("great glacite lake")||s.contains("upper mines")||s.contains("royal mines")||s.contains("lava springs")||s.contains("cliffside veins"))dwarven=true;}return hypixel&&skyblock&&dwarven;}
 private IceLocationRules(){}
}
