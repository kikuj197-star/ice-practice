package local.icepractice;

/** Pure integer-space preview model adapted from Skyblocker PickobulusHelper. */
public final class IceBlastModel {
    public interface World {
        boolean isIce(int x, int y, int z);
        boolean isAir(int x, int y, int z);
        void breakIce(int x, int y, int z);
    }
    private static final int[][] NEIGHBOURS = {{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    public static int apply(World world, int cx, int cy, int cz) {
        int count=0;
        for (int x=-3;x<=2;x++) for (int y=-3;y<=2;y++) for (int z=-3;z<=2;z++) {
            int px=cx+x, py=cy+y, pz=cz+z;
            if (!world.isIce(px,py,pz)) continue;
            boolean exposed=false;
            for (int[] n:NEIGHBOURS) if (world.isAir(px+n[0],py+n[1],pz+n[2])) { exposed=true; break; }
            if (exposed) { world.breakIce(px,py,pz); count++; }
        }
        return count;
    }
    private IceBlastModel() {}
}
