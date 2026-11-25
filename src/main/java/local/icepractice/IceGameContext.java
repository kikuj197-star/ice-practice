package local.icepractice;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.*;

public final class IceGameContext {
 public static List<String> tabLines(){var c=Minecraft.getInstance().getConnection();if(c==null)return List.of();ArrayList<String> out=new ArrayList<>();for(PlayerInfo p:c.getOnlinePlayers())if(p.getTabListDisplayName()!=null)out.add(p.getTabListDisplayName().getString().strip());return out;}
 public static String pickobulus(){for(String s:tabLines())if(s.startsWith("Pickobulus: "))return s;return "";}
 public static boolean dwarven(){Minecraft c=Minecraft.getInstance();if(c.level==null)return false;ArrayList<String> lines=new ArrayList<>(tabLines());Scoreboard board=c.level.getScoreboard();Objective o=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(o!=null){lines.add(o.getDisplayName().getString());for(PlayerScoreEntry e:board.listPlayerScores(o)){PlayerTeam t=board.getPlayersTeam(e.owner());lines.add(t==null?e.owner():t.getPlayerPrefix().getString()+e.owner()+t.getPlayerSuffix().getString());}}for(String line:lines){String s=line.toLowerCase(Locale.ROOT);if(s.contains("dwarven mines")||s.contains("glacite tunnels"))return true;}return false;}
 private IceGameContext(){}
}
