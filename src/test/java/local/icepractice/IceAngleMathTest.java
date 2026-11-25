package local.icepractice;
import java.util.*;
public final class IceAngleMathTest {
 public static void main(String[] args){
  close(IceAngleMath.wrap(496.05),136.05);close(IceAngleMath.wrap(180),-180);close(IceAngleMath.wrap(-540),-180);
  double[] a=IceAngleMath.direction(0,0);close(a[0],0);close(a[1],0);close(a[2],1);
  a=IceAngleMath.direction(-90,0);close(a[0],1);close(a[1],0);close(a[2],0);
  a=IceAngleMath.direction(0,90);close(a[1],-1);
  if(IceAngleMath.stepIndex(1.70)!=3 || IceAngleMath.stepIndex(1.69)!=2)throw new AssertionError("impact step");
  boolean[] g=new boolean[25];g[12]=g[11]=g[13]=g[7]=g[17]=true; if(!Arrays.equals(IceAngleMath.crossSection(g,5,2,2),new int[]{-1,1,-1,1}))throw new AssertionError("slice");
  g[12]=false;if(IceAngleMath.crossSection(g,5,2,2)!=null)throw new AssertionError("disconnected reference");
  int[] best={10,12,12};int[] samples={10,9,9,9,12,11,11,11,12,12,12,11};
  if(IceAngleMath.bestFace(best,samples,4,1)!=1 || IceAngleMath.bestFace(best,samples,4,0)!=2)throw new AssertionError("face max/tie");
  if(IceAngleMath.bestFace(new int[]{0,-1},new int[8],4,0)!=-1)throw new AssertionError("no valid face");
  System.out.println("IceAngleMath checks passed");
 }
 static void close(double a,double b){if(Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);}
}
