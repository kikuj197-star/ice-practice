package local.icepractice;
import java.nio.file.*;
import java.io.IOException;
public final class IceUnionStoreTest {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("ice-union-test");Path file=dir.resolve("ice.txt");
  try{
   IceUnionStore s=new IceUnionStore(file);check(s.add(1,2,3));check(!s.add(1,2,3));check(s.add(-4,125,334));s.flush();
   IceUnionStore r=new IceUnionStore(file);check(r.size()==2);check(!r.add(1,2,3));check(r.add(9,9,9));r.flush();
   IceUnionStore again=new IceUnionStore(file);check(again.size()==3);check(!again.add(-4,125,334));
   Files.writeString(file,"bad record\n");try{new IceUnionStore(file);throw new AssertionError("corrupt cache accepted");}catch(IOException expected){}
   System.out.println("IceUnionStore checks passed");
  }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir.resolve("ice.txt.tmp"));Files.deleteIfExists(dir);}
 }
 static void check(boolean x){if(!x)throw new AssertionError();}
}
