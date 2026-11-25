package local.icepractice;
import java.util.*;
public final class IceBlastModelTest {
 record P(int x,int y,int z) {}
 static final class Grid implements IceBlastModel.World {
  Set<P> ice=new HashSet<>(), stone=new HashSet<>();
  public boolean isIce(int x,int y,int z){return ice.contains(new P(x,y,z));}
  public boolean isAir(int x,int y,int z){P p=new P(x,y,z);return !ice.contains(p)&&!stone.contains(p);}
  public void breakIce(int x,int y,int z){if(!ice.remove(new P(x,y,z)))throw new AssertionError("not ice");}
 }
 public static void main(String[] args) {
  Grid g=new Grid(); g.ice.add(new P(0,0,0)); g.ice.add(new P(2,0,0)); g.ice.add(new P(3,0,0)); g.stone.add(new P(1,0,0));
  if(IceBlastModel.apply(g,0,0,0)!=2 || !g.ice.contains(new P(3,0,0)) || !g.stone.contains(new P(1,0,0)))throw new AssertionError("range or non-ice safety");
  Grid h=new Grid(); h.ice.add(new P(0,0,0)); for(int[] d:new int[][]{{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}})h.stone.add(new P(d[0],d[1],d[2]));
  if(IceBlastModel.apply(h,0,0,0)!=0)throw new AssertionError("enclosed ice");
  Grid q=new Grid();q.ice.add(new P(-3,0,0));q.ice.add(new P(-4,0,0));
  if(IceBlastModel.apply(q,0,0,0)!=1 || !q.ice.contains(new P(-4,0,0)))throw new AssertionError("negative boundary");
  System.out.println("IceBlastModel checks passed");
 }
}
