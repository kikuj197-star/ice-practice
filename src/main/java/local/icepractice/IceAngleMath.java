package local.icepractice;
import java.util.Arrays;

/** Math shared by the local-only aim preview and its tests. */
public final class IceAngleMath {
    public static double wrap(double yaw){double v=(yaw+180.0)%360.0;if(v<0)v+=360.0;return v-180.0;}
    public static double[] direction(double yaw,double pitch){double y=Math.toRadians(yaw),p=Math.toRadians(pitch);return new double[]{-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p)};}
    public static int stepIndex(double distance){return (int)Math.ceil((distance+1e-8)/0.85);}
    /** Returns one reachable face with the greatest preview; ties prefer the currently aimed face. */
    public static int bestFace(int[] faceBest,int[] scores,int faceSize,int preferred){
        int best=Arrays.stream(faceBest).max().orElse(-1);if(best<=0)return -1;
        if(preferred>=0&&preferred<faceBest.length&&faceBest[preferred]==best)return preferred;
        int selected=-1,most=-1;for(int i=0;i<faceBest.length;i++)if(faceBest[i]==best){int n=0;for(int j=0;j<faceSize;j++)if(scores[i*faceSize+j]==best)n++;if(n>most){most=n;selected=i;}}
        return selected;
    }
    public static int[] crossSection(boolean[] good,int n,int cx,int cy){
        if(!good[cy*n+cx])return null;int l=cx,r=cx,u=cy,d=cy;
        while(l>0&&good[cy*n+l-1])l--;while(r+1<n&&good[cy*n+r+1])r++;
        while(u>0&&good[(u-1)*n+cx])u--;while(d+1<n&&good[(d+1)*n+cx])d++;
        return new int[]{l-cx,r-cx,u-cy,d-cy};
    }
    private IceAngleMath(){}
}
