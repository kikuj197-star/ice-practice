package local.icepractice;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class IceWaypointsTest {
 public static void main(String[] a)throws Exception{
  try(var in=IceWaypointsTest.class.getResourceAsStream("/assets/icepractice/default-waypoints.json")){if(in==null)throw new AssertionError("missing defaults");IceWaypoints.config=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),IceWaypoints.Config.class);}
  var points=IceWaypoints.config.waypoints;
  if(points.size()!=90||points.stream().anyMatch(p->p.name.contains(" START ")))throw new AssertionError("default import");
  var stand=points.stream().filter(p->p.name.startsWith("08 189 STAND")).findFirst().orElseThrow();if(stand.x!=101||stand.y!=135||stand.z!=318||stand.type!=IceWaypoints.Type.STAND)throw new AssertionError("stand block below feet");
  var aim=points.stream().filter(p->p.name.startsWith("08 189 AIM")).findFirst().orElseThrow();if(aim.y!=137||!IceWaypoints.label(aim).equals("08 189 瞄点"))throw new AssertionError("aim / simple label");
  IceWaypoints.config.simpleLabels=false;if(!IceWaypoints.label(aim).equals(aim.name))throw new AssertionError("detailed label");
  if(IceWaypoints.showClear(24,true,false)||!IceWaypoints.showClear(26,true,false)||!IceWaypoints.showClear(24,true,true)||!IceWaypoints.showClear(24,false,false))throw new AssertionError("clear visibility");
  if(Math.abs(IceWaypoints.labelWorldScale(100,1)/IceWaypoints.labelWorldScale(10,1)-10)>1e-5||Math.abs(IceWaypoints.labelWorldScale(100,2)/IceWaypoints.labelWorldScale(100,1)-2)>1e-5)throw new AssertionError("fixed screen size and user scale");
  System.out.println("IceWaypoints checks passed");
 }
}
