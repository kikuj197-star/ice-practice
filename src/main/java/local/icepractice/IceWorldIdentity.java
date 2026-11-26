package local.icepractice;

/** Separate a verified Hypixel island from the address of the proxy used to reach it. */
public final class IceWorldIdentity {
 public static String multiplayer(String serverAddress,String dimension,boolean verifiedDwarven){return (verifiedDwarven?"hypixel:skyblock:mining_3":"server:"+serverAddress)+"|"+dimension;}
 public static String singleplayer(String worldPath,String dimension){return "single:"+worldPath+"|"+dimension;}
 private IceWorldIdentity(){}
}
