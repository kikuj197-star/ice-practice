package local.icepractice;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Bounded local snapshots. Only explicitly confirmed snapshots are written to disk. */
public final class IceAngleCache {
    static final int TEMP_LIMIT=64, SAVED_LIMIT=128;
    public static final class Entry {
        String world, initialFace, face;
        int x,y,z,best;
        double eyeX,eyeY,eyeZ,yaw,pitch;
        boolean sneaking;
        long terrain;
        long[] dependencies;
        double[] slices;
        float[] vertices;
    }
    private static final Gson GSON=new Gson();
    private final LinkedHashMap<String,Entry> recent=new LinkedHashMap<>(16,.75f,true);
    private final LinkedHashMap<String,Entry> saved=new LinkedHashMap<>(16,.75f,true);
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"IceAngle-cache-writer");t.setDaemon(true);return t;});
    private final Path path;
    public IceAngleCache(Path path){this.path=path;load();}
    private static String key(Entry e){return e.world+"|"+e.x+","+e.y+","+e.z+"|"+e.eyeX+","+e.eyeY+","+e.eyeZ+"|"+e.sneaking;}
    private static boolean valid(Entry e){return e!=null&&e.world!=null&&e.initialFace!=null&&e.face!=null&&e.dependencies!=null&&e.dependencies.length>0&&e.dependencies.length<=20000&&e.slices!=null&&e.slices.length==4&&e.vertices!=null&&e.vertices.length>0&&e.vertices.length<=20000&&e.vertices.length%12==0&&e.best>0;}
    private static void putBounded(LinkedHashMap<String,Entry> map,Entry e,int limit){map.entrySet().removeIf(a->match(a.getValue(),e.world,e.x,e.y,e.z,e.eyeX,e.eyeY,e.eyeZ,e.sneaking));map.put(key(e),e);while(map.size()>limit)map.remove(map.keySet().iterator().next());}
    public void remember(Entry e){if(valid(e))putBounded(recent,e,TEMP_LIMIT);}
    public void confirm(Entry e){confirm(e,success->{});}
    public void confirm(Entry e,Consumer<Boolean> onComplete){if(!valid(e)){onComplete.accept(false);return;}putBounded(saved,e,SAVED_LIMIT);List<Entry> copy=new ArrayList<>(saved.values());writer.execute(()->onComplete.accept(write(copy)));}
    public Entry promoteLegacy(Entry source,String world){if(source.world.equals(world))return source;boolean wasSaved=saved.containsValue(source);Entry e=new Entry();e.world=world;e.initialFace=source.initialFace;e.face=source.face;e.x=source.x;e.y=source.y;e.z=source.z;e.best=source.best;e.eyeX=source.eyeX;e.eyeY=source.eyeY;e.eyeZ=source.eyeZ;e.yaw=source.yaw;e.pitch=source.pitch;e.sneaking=source.sneaking;e.terrain=source.terrain;e.dependencies=source.dependencies.clone();e.slices=source.slices.clone();e.vertices=source.vertices.clone();recent.entrySet().removeIf(a->a.getValue()==source);saved.entrySet().removeIf(a->a.getValue()==source);remember(e);if(wasSaved)confirm(e);return e;}
    public Entry find(String world,int x,int y,int z,double ex,double ey,double ez,boolean sneak){Entry e=findIn(recent,world,x,y,z,ex,ey,ez,sneak);return e!=null?e:findIn(saved,world,x,y,z,ex,ey,ez,sneak);}
    private static boolean match(Entry e,String world,int x,int y,int z,double ex,double ey,double ez,boolean sneak){double dx=ex-e.eyeX,dy=ey-e.eyeY,dz=ez-e.eyeZ;return e.world.equals(world)&&e.x==x&&e.y==y&&e.z==z&&e.sneaking==sneak&&dx*dx+dy*dy+dz*dz<=.04*.04;}
    private static Entry findIn(LinkedHashMap<String,Entry> map,String world,int x,int y,int z,double ex,double ey,double ez,boolean sneak){String found=null;for(var a:map.entrySet())if(match(a.getValue(),world,x,y,z,ex,ey,ez,sneak))found=a.getKey();return found==null?null:map.get(found);}
    public int recentCount(){return recent.size();} public int savedCount(){return saved.size();}
    void awaitWrites() throws Exception {writer.submit(()->{}).get(10,TimeUnit.SECONDS);}
    public void close(){writer.shutdown();try{if(!writer.awaitTermination(10,TimeUnit.SECONDS))System.err.println("[IceAngle] Timed out waiting for cache writes during shutdown.");}catch(InterruptedException e){Thread.currentThread().interrupt();System.err.println("[IceAngle] Interrupted while waiting for cache writes.");}}
    private void load(){if(!Files.isRegularFile(path))return;try(InputStream raw=Files.newInputStream(path);Reader in=new InputStreamReader(new GZIPInputStream(raw),StandardCharsets.UTF_8)){Entry[] data=GSON.fromJson(in,Entry[].class);if(data!=null)for(Entry e:data)if(valid(e))putBounded(saved,e,SAVED_LIMIT);}catch(Exception e){System.err.println("[IceAngle] Cache not loaded: "+e);}}
    private boolean write(List<Entry> data){Path tmp=path.resolveSibling(path.getFileName()+".tmp");try{Files.createDirectories(path.getParent());try(OutputStream out=new GZIPOutputStream(Files.newOutputStream(tmp));Writer w=new OutputStreamWriter(out,StandardCharsets.UTF_8)){GSON.toJson(data,w);}try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(Exception e){System.err.println("[IceAngle] Cache not saved: "+e);return false;}}
}
