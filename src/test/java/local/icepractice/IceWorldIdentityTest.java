package local.icepractice;
public final class IceWorldIdentityTest {
 public static void main(String[] args){String dim="overworld";String a=IceWorldIdentity.multiplayer("proxy-a",dim,true),b=IceWorldIdentity.multiplayer("proxy-b",dim,true);
  if(!a.equals(b))throw new AssertionError("verified Hypixel aliases should share cache");
  if(IceWorldIdentity.multiplayer("proxy-a",dim,false).equals(IceWorldIdentity.multiplayer("proxy-b",dim,false)))throw new AssertionError("unverified servers must stay separate");
  if(a.equals(IceWorldIdentity.multiplayer("proxy-a","another-dimension",true))||a.equals(IceWorldIdentity.singleplayer("world",dim)))throw new AssertionError("worlds and dimensions must remain separate");
  System.out.println("IceWorldIdentity checks passed");
 }
}
