package local.icepractice;
import java.nio.file.*;
public final class IceAngleCacheTest {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("ice-angle-cache-test"),file=dir.resolve("cache.gz");
  IceAngleCache c=new IceAngleCache(file);IceAngleCache.Entry e=entry(1);c.remember(e);
  if(c.find("world",1,2,3,1.02,2,3,false)!=e)throw new AssertionError("recent hit");
  if(c.find("world",1,2,3,1.05,2,3,false)!=null||c.find("other",1,2,3,1,2,3,false)!=null||c.find("world",2,2,3,1,2,3,false)!=null||c.find("world",1,2,3,1,2,3,true)!=null)throw new AssertionError("invalid reuse");
  if(Files.exists(file))throw new AssertionError("unconfirmed result persisted");
  c.confirm(e);c.awaitWrites();IceAngleCache reloaded=new IceAngleCache(file);
  if(reloaded.find("world",1,2,3,1,2,3,false)==null)throw new AssertionError("saved result not restored");
  IceAngleCache.Entry replacement=entry(1);replacement.initialFace="north";replacement.terrain=12345;reloaded.remember(replacement);
  if(reloaded.find("world",1,2,3,1,2,3,false)!=replacement)throw new AssertionError("new area should not depend on old face or terrain");
  reloaded.confirm(replacement);reloaded.awaitWrites();if(reloaded.savedCount()!=1)throw new AssertionError("same stand and target must not duplicate entries");
  IceAngleCache.Entry promoted=reloaded.promoteLegacy(replacement,"hypixel-world");reloaded.awaitWrites();if(reloaded.savedCount()!=1||reloaded.find("hypixel-world",1,2,3,1,2,3,false)!=promoted||new IceAngleCache(file).find("hypixel-world",1,2,3,1,2,3,false)==null)throw new AssertionError("saved legacy area promotion");
  IceAngleCache.Entry temporary=entry(22);reloaded.remember(temporary);reloaded.promoteLegacy(temporary,"hypixel-world");reloaded.awaitWrites();if(reloaded.savedCount()!=1||new IceAngleCache(file).find("hypixel-world",22,2,3,1,2,3,false)!=null)throw new AssertionError("temporary areas must not be persisted by promotion");
  for(int i=0;i<140;i++)reloaded.confirm(entry(i));reloaded.awaitWrites();
  if(reloaded.savedCount()!=128||new IceAngleCache(file).savedCount()!=128)throw new AssertionError("bounded persistence");
  Path closeFile=dir.resolve("close.gz");IceAngleCache closing=new IceAngleCache(closeFile);closing.confirm(entry(9));closing.close();if(new IceAngleCache(closeFile).find("world",9,2,3,1,2,3,false)==null)throw new AssertionError("shutdown must wait for pending writes");Files.delete(closeFile);
  Path crossFile=dir.resolve("cross.gz");IceAngleCache firstSession=new IceAngleCache(crossFile);IceAngleCache.Entry cross=entry(33);cross.world=IceWorldIdentity.multiplayer("proxy-a","overworld",true);firstSession.confirm(cross);firstSession.close();IceAngleCache secondSession=new IceAngleCache(crossFile);if(secondSession.find(IceWorldIdentity.multiplayer("proxy-b","overworld",true),33,2,3,1,2,3,false)==null)throw new AssertionError("saved Hypixel cache should survive restart and proxy change");if(secondSession.find(IceWorldIdentity.multiplayer("proxy-b","overworld",false),33,2,3,1,2,3,false)!=null)throw new AssertionError("unverified server must not reuse Hypixel cache");secondSession.close();Files.delete(crossFile);
  Files.delete(file);Files.delete(dir);System.out.println("IceAngleCache checks passed");
 }
 private static IceAngleCache.Entry entry(int x){var e=new IceAngleCache.Entry();e.world="world";e.x=x;e.y=2;e.z=3;e.initialFace="west";e.face="west";e.eyeX=1;e.eyeY=2;e.eyeZ=3;e.terrain=9;e.dependencies=new long[]{3,4};e.best=10;e.slices=new double[4];e.vertices=new float[12];return e;}
}
