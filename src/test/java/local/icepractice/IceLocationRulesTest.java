package local.icepractice;
import java.util.List;
public final class IceLocationRulesTest {
 public static void main(String[] args){
  if(!IceLocationRules.isDwarvenPacket("SKYBLOCK","mining_3")||IceLocationRules.isDwarvenPacket("SKYBLOCK","crystal_hollows")||IceLocationRules.isDwarvenPacket("BEDWARS","mining_3"))throw new AssertionError("official location");
  for(String mode:List.of("mining_1","mining_2","mining_3","crystal_hollows","mineshaft"))if(!IceLocationRules.isMiningPacket("SKYBLOCK",mode))throw new AssertionError("Mining location "+mode);
  if(IceLocationRules.isMiningPacket("SKYBLOCK","hub")||IceLocationRules.isMiningPacket("BEDWARS","mining_3")||IceLocationRules.isMiningPacket("SKYBLOCK",null))throw new AssertionError("non-mining location");
  if(!IceLocationRules.display(true,false,false)||!IceLocationRules.display(false,true,false)||IceLocationRules.display(false,false,true)||IceLocationRules.display(false,null,false)||!IceLocationRules.display(false,null,true))throw new AssertionError("world gating");
  if(!IceLocationRules.fallback(List.of("SKYBLOCK","Glacite Tunnels","www.hypixel.net"))||IceLocationRules.fallback(List.of("SKYBLOCK","Glacite Tunnels"))||IceLocationRules.fallback(List.of("SKYBLOCK","Hub","www.hypixel.net")))throw new AssertionError("sidebar fallback");
  System.out.println("IceLocationRules checks passed");
 }
}
