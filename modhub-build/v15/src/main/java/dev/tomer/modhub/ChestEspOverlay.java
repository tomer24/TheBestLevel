package dev.tomer.modhub;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2611;
import net.minecraft.class_2627;
import net.minecraft.class_2338;
import net.minecraft.class_746;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Discovers client-known blocks in loaded chunks and REMEMBERS discoveries.
 * Persistent finds are private to a server + dimension. Remembered positions
 * may be stale if a block is later broken. This intentionally does not discover
 * hidden or unloaded chunks that the server hasn't sent to the client.
 */
public final class ChestEspOverlay {
    private static final int CHEST=0, ENDER=1, SHULKER=2, TORCH=3;
    private static final int MAX_SAVED=25000;
    private final TomerModHub hub;
    private final HashMap<Long,GlowBridge.GlowPoint> saved=new HashMap<>();
    private final ArrayList<GlowBridge.GlowPoint> nearby=new ArrayList<>();
    private boolean scanReady, dirty;
    private int originX,originY,originZ,scanIndex,volume,span,vertical;
    private int tick, lastSaveTick;
    private String worldKey="";
    private Path worldFile;

    public ChestEspOverlay(TomerModHub hub){this.hub=hub;}
    public int visibleCount(){return nearby.size();}
    public int savedCount(){return saved.size();}
    public void resetTorchScan(){scanReady=false;scanIndex=0;}

    private static String fingerprint(String key) {
        try {
            byte[] dig=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();
            for(int i=0;i<12;i++)s.append(String.format("%02x", dig[i]&255));
            return s.toString();
        } catch(Exception e) {return Integer.toHexString(key.hashCode());}
    }
    private String currentWorld(class_310 mc){
        String server=mc.method_1558()==null?"singleplayer":mc.method_1558().field_3761;
        String dimension=mc.field_1687.method_27983().method_29177().toString();
        return server+"|"+dimension;
    }
    private void switchWorld(class_310 mc) {
        String key=currentWorld(mc);
        if(key.equals(worldKey))return;
        save();
        worldKey=key; saved.clear();nearby.clear(); scanReady=false;scanIndex=0;
        worldFile=FabricLoader.getInstance().getConfigDir().resolve("tomer-modhub-finds").resolve(fingerprint(key)+".csv");
        try {
            if(Files.isRegularFile(worldFile)) {
                try(BufferedReader reader=Files.newBufferedReader(worldFile,StandardCharsets.UTF_8)){
                    String row;
                    while((row=reader.readLine())!=null && saved.size()<MAX_SAVED){
                        String[] p=row.split(",");
                        if(p.length!=4)continue;
                        try {
                            int x=Integer.parseInt(p[0]),y=Integer.parseInt(p[1]),z=Integer.parseInt(p[2]),type=Integer.parseInt(p[3]);
                            if(type>=0&&type<=3 && y>=-64&&y<=319){
                                class_2338 pos=new class_2338(x,y,z);
                                saved.put(pos.method_10063(),new GlowBridge.GlowPoint(x,y,z,type));
                            }
                        } catch(NumberFormatException ignored) { }
                    }
                }
            }
        } catch(IOException e){System.err.println("[Tomer Mod Hub] Failed to load discoveries: "+e);}
        dirty=false;
    }
    private void save() {
        if(!dirty || worldFile==null)return;
        try {
            Files.createDirectories(worldFile.getParent());
            Path tmp=worldFile.resolveSibling(worldFile.getFileName()+".tmp");
            try(BufferedWriter out=Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)){
                for(GlowBridge.GlowPoint p:saved.values()){
                    out.write(p.x()+","+p.y()+","+p.z()+","+p.type());
                    out.newLine();
                }
            }
            try {Files.move(tmp,worldFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,worldFile,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;
        } catch(IOException e){System.err.println("[Tomer Mod Hub] Failed to save discoveries: "+e);}
    }
    public void clearFound() {
        saved.clear();nearby.clear();scanReady=false;scanIndex=0;dirty=true;save();
        GlowBridge.points=List.of(); GlowBridge.savedCount=0;
    }
    public List<String> nearestFound() {
        ArrayList<String> list=new ArrayList<>();
        for(int i=0;i<Math.min(5,nearby.size());i++){
            GlowBridge.GlowPoint p=nearby.get(i);
            list.add(label(p.type())+" "+p.x()+" "+p.y()+" "+p.z());
        }
        return list;
    }
    private static String label(int type){return switch(type){case ENDER->"ENDER";case SHULKER->"SHULKER";case TORCH->"TORCH";default->"CHEST";};}
    private String block(class_310 mc,class_2338 pos){
        return mc.field_1687.method_8320(pos).method_26204().toString().toLowerCase(Locale.ROOT);
    }
    private int type(String id){
        if(id.contains("ender_chest"))return ENDER;
        if(id.contains("shulker_box"))return SHULKER;
        if(id.contains("trapped_chest")||id.contains("minecraft:chest"))return CHEST;
        if(id.contains("torch"))return TORCH;
        return -1;
    }
    private boolean enabled(int t){return switch(t){
        case CHEST->true;
        case ENDER->hub.showEnderChests();
        case SHULKER->hub.showShulkers();
        case TORCH->hub.showTorches();
        default->false;};
    }
    private void add(class_2338 pos,int t){
        if(t<0||!enabled(t)||saved.size()>=MAX_SAVED)return;
        long key=pos.method_10063();
        GlowBridge.GlowPoint p=new GlowBridge.GlowPoint(pos.method_10263(),pos.method_10264(),pos.method_10260(),t);
        GlowBridge.GlowPoint previous=saved.put(key,p);
        if(previous==null || previous.type()!=t)dirty=true;
    }
    private void scan(class_310 mc) {
        if(!scanReady)return;
        class_2338.class_2339 mutable=new class_2338.class_2339();
        int budget=4000, radius=hub.getChestEspRange();
        while(budget-->0&&scanIndex<volume){
            int i=scanIndex++;
            int dx=i%span-span/2, dz=(i/span)%span-span/2, dy=i/(span*span)-vertical;
            int x=originX+dx, y=originY+dy, z=originZ+dz;
            if(y<-64 || y>319 || dx*dx+dz*dz+dy*dy>radius*radius)continue;
            mutable.method_10103(x,y,z);
            int t=type(block(mc,mutable));
            if(t>=0)add(mutable,t);
        }
        if(scanIndex>=volume)scanReady=false;
    }
    public void update(class_310 mc){
        if(mc==null||mc.field_1687==null||mc.field_1724==null){
            save();worldKey="";worldFile=null;saved.clear();nearby.clear();scanReady=false;scanIndex=0;
            GlowBridge.points=List.of();GlowBridge.savedCount=0;GlowBridge.enabled=false;
            return;
        }
        switchWorld(mc);
        class_746 p=mc.field_1724;
        int x=(int)Math.floor(p.method_23317()),y=(int)Math.floor(p.method_23318()),z=(int)Math.floor(p.method_23321());
        if((!scanReady&&(scanIndex==0 || tick%360==0)) ||
             Math.abs(x-originX)>=12 || Math.abs(y-originY)>=12 || Math.abs(z-originZ)>=12){
            originX=x;originY=y;originZ=z;
            int radius=Math.min(56,hub.getChestEspRange());
            span=radius*2+1;vertical=Math.min(radius,64);
            volume=span*span*(vertical*2+1);
            scanIndex=0;scanReady=true;
        }
        scan(mc);
        if(++tick%8==0){
            for(class_2586 be:mc.field_1687.method_72019()){
                int t=-1;
                if(be instanceof class_2595)t=CHEST;
                else if(be instanceof class_2611)t=ENDER;
                else if(be instanceof class_2627)t=SHULKER;
                if(t>=0)add(be.method_11016(),t);
            }
            double r2=(double)hub.getChestEspRange()*hub.getChestEspRange();
            nearby.clear();
            for(GlowBridge.GlowPoint item:saved.values()){
                if(!enabled(item.type()))continue;
                if(distanceSquared(item,p)<=r2)nearby.add(item);
            }
            nearby.sort(Comparator.comparingDouble(a->distanceSquared(a,p)));
            if(nearby.size()>180)nearby.subList(180,nearby.size()).clear();
            GlowBridge.enabled=hub.isChestEspEnabled();
            GlowBridge.range=hub.getChestEspRange();
            GlowBridge.savedCount=saved.size();
            GlowBridge.points=List.copyOf(nearby);
        }
        if(dirty && tick-lastSaveTick>=100){save();lastSaveTick=tick;}
    }
    private static double distanceSquared(GlowBridge.GlowPoint a,class_746 p){
        double dx=a.x()+.5-p.method_23317(),dy=a.y()+.5-p.method_23318(),dz=a.z()+.5-p.method_23321();
        return dx*dx+dy*dy+dz*dz;
    }
    /** HUD only lists persistent discoveries; the ACTUAL blocks glow in world space. */
    public void render(class_332 g){
        if(!hub.isChestEspEnabled())return;
        class_310 mc=class_310.method_1551();
        if(mc==null || mc.field_1724==null || mc.field_1755!=null)return;
        int w=g.method_51421(),h=g.method_51443();
        if(w<230||h<150)return;
        int x0=w-208;
        g.method_25294(x0,8,w-5,67,0xA7131A22);
        g.method_25294(x0,8,x0+3,67,0xFF50EDB2);
        g.method_25303(mc.field_1772,"TOMER GLOW ESP",x0+8,13,0xFFB3FFD9);
        g.method_25303(mc.field_1772,"SAVED: "+saved.size()+"   NEAR: "+nearby.size(),x0+8,27,0xFFFFFFFF);
        String progress=scanReady?"Scanning "+(volume==0?0:(100L*scanIndex/volume))+"%":"Scan complete";
        g.method_25303(mc.field_1772,progress,x0+8,42,0xFFA3B6C7);
    }
}

