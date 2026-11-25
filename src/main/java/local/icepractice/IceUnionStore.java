package local.icepractice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Monotonic, crash-safe union of confirmed packed ice positions. */
public final class IceUnionStore {
    public record Pos(int x,int y,int z) {}
    private final Path path;
    private final Set<Pos> positions=new HashSet<>();
    private int pending;
    public IceUnionStore(Path path) throws IOException {
        this.path=path;
        if(Files.exists(path)) {
            int line=0;
            for(String s:Files.readAllLines(path,StandardCharsets.UTF_8)) {
                line++;if(s.isBlank()||s.startsWith("#"))continue;
                String[] v=s.trim().split("\\s+");
                try {if(v.length!=3)throw new IllegalArgumentException();positions.add(new Pos(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2])));}
                catch(IllegalArgumentException ex){throw new IOException("Invalid ice cache at line "+line,ex);}
            }
        }
    }
    public boolean add(int x,int y,int z) {boolean added=positions.add(new Pos(x,y,z));if(added)pending++;return added;}
    public int size(){return positions.size();}
    public int pending(){return pending;}
    public void flush() throws IOException {
        if(pending==0)return;
        Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        try {
            try(BufferedWriter out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)) {
                for(Pos p:positions){out.write(p.x()+" "+p.y()+" "+p.z());out.newLine();}
            }
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
            pending=0;
        } finally {Files.deleteIfExists(tmp);}
    }
}
