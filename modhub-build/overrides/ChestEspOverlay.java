package dev.tomer.modhub;

import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2611;
import net.minecraft.class_2627;
import net.minecraft.class_2338;
import net.minecraft.class_746;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ESP drawn in HUD space, on top of the entire world: boxes remain visible
 * through ground and stone when aimed at a target, and an on-screen list
 * always shows nearest coordinates even if you're looking away.
 * Server-hidden/unloaded chunks cannot be scanned.
 */
public final class ChestEspOverlay {
    private static final int GOLD = 0xFFFFC645;
    private static final int PURPLE = 0xFFC57BFF;
    private static final int CYAN = 0xFF47E5E1;
    private static final int RED = 0xFFFF7864;
    private final TomerModHub hub;
    private final List<Marker> markers = new ArrayList<>();
    private final Map<Long, Marker> torches = new HashMap<>();
    private int tick, scanIndex;
    private int originX, originY, originZ;
    private boolean scanReady;
    private static final int TORCH_HORIZONTAL = 26, TORCH_VERTICAL = 18;
    private static final int NX = TORCH_HORIZONTAL * 2 + 1;
    private static final int NY = TORCH_VERTICAL * 2 + 1;
    private static final int TORCH_SCAN_VOLUME = NX * NX * NY;
    private record Marker(double x,double y,double z,int type,int dist) { }
    public ChestEspOverlay(TomerModHub hub){this.hub=hub;}
    public int visibleCount(){return markers.size();}
    public void resetTorchScan(){scanReady=false; torches.clear();scanIndex=0;}

    public void update(class_310 mc) {
        if(mc.field_1687 == null || mc.field_1724 == null){markers.clear();resetTorchScan();return;}
        if(hub.showTorches()) scanTorches(mc);
        else if(!torches.isEmpty())resetTorchScan();
        if(++tick < 8)return;
        tick=0;
        markers.clear();
        class_746 p=mc.field_1724;
        double px=p.method_23317(), py=p.method_23318(), pz=p.method_23321();
        double r=(double)hub.getChestEspRange();
        for(class_2586 entity : mc.field_1687.method_72019()){
            int type=-1;
            if(entity instanceof class_2595)type=0;
            else if(entity instanceof class_2611 && hub.showEnderChests())type=1;
            else if(entity instanceof class_2627 && hub.showShulkers())type=2;
            if(type<0)continue;
            class_2338 pos=entity.method_11016();
            append(pos.method_10263()+.5,pos.method_10264()+.5,pos.method_10260()+.5,type,px,py,pz,r);
        }
        if(hub.showTorches())for(Marker marker:torches.values())
            append(marker.x,marker.y,marker.z,3,px,py,pz,r);
        markers.sort(Comparator.comparingInt(Marker::dist).thenComparingInt(Marker::type));
        if(markers.size()>240)markers.subList(240,markers.size()).clear();
    }
    private void append(double x,double y,double z,int type,double px,double py,double pz,double range){
        double d=Math.sqrt((x-px)*(x-px)+(y-py)*(y-py)+(z-pz)*(z-pz));
        if(d<=range)markers.add(new Marker(x,y,z,type,(int)Math.round(d)));
    }
    private void scanTorches(class_310 mc){
        class_746 p=mc.field_1724;
        int x=(int)Math.floor(p.method_23317()), y=(int)Math.floor(p.method_23318()), z=(int)Math.floor(p.method_23321());
        if(!scanReady || Math.abs(x-originX)>10 || Math.abs(y-originY)>8 || Math.abs(z-originZ)>10){
            originX=x;originY=y;originZ=z;
            scanIndex=0;torches.clear();scanReady=true;
        }
        int work=0;
        class_2338.class_2339 mutable=new class_2338.class_2339();
        while(work++<2600 && scanIndex<TORCH_SCAN_VOLUME){
            int index=scanIndex++;
            int localX=index%NX-TORCH_HORIZONTAL;
            int localZ=(index/NX)%NX-TORCH_HORIZONTAL;
            int localY=index/(NX*NX)-TORCH_VERTICAL;
            int xx=originX+localX, yy=originY+localY, zz=originZ+localZ;
            if(yy<-64 || yy>320)continue;
            mutable.method_10103(xx,yy,zz);
            String name=mc.field_1687.method_8320(mutable).method_26204().toString().toLowerCase();
            if(name.contains("torch")){
                long key=mutable.method_10063();
                torches.put(key,new Marker(xx+.5, yy+.6, zz+.5,3,0));
                if(torches.size()>1200)break;
            }
        }
        // Start a new full scan after a short pause. Keeps moving torches and
        // newly loaded chunks updated, without scanning millions of blocks/tick.
        if(scanIndex>=TORCH_SCAN_VOLUME)scanReady=false;
    }
    public void render(class_332 g){
        if(!hub.isChestEspEnabled())return;
        class_310 mc=class_310.method_1551();
        if(mc==null || mc.field_1724==null || mc.field_1687==null || mc.field_1755!=null)return;
        class_746 p=mc.field_1724;
        int w=mc.method_22683().method_4486(), h=mc.method_22683().method_4502();
        if(w<240||h<170)return;
        // Always-visible nearby list. Depth/stone does not hide these labels.
        g.method_25294(w-181,8,w-8,104,0xB0141920);
        g.method_25294(w-181,8,w-177,104,0xFF43C8AB);
        g.method_25303(mc.field_1772,"TOMER ESP  "+markers.size(),w-170,14,0xFFFFFFFF);
        int listIndex=0;
        for(Marker m:markers){
            if(listIndex>=6)break;
            int color=color(m.type);
            String level=m.y < p.method_23318()-3 ? " v" : (m.y>p.method_23318()+3 ? " ^" : "");
            String str=label(m.type)+" " +(int)m.x+","+(int)m.y+","+(int)m.z+" "+m.dist+"m"+level;
            g.method_25303(mc.field_1772,str,w-170,28+listIndex*12,color);
            listIndex++;
        }
        // Projection is computed from player yaw/pitch, completely ignoring
        // world depth-testing. Markers can be seen for underground targets.
        double yaw=Math.toRadians(p.method_36454()), pitch=Math.toRadians(p.method_36455());
        double sy=Math.sin(yaw), cy=Math.cos(yaw), sp=Math.sin(pitch), cp=Math.cos(pitch);
        double focal=w/(2.0*Math.tan(Math.toRadians(72)/2));
        int chests=0, torchesShown=0;
        for(Marker m:markers){
            if(m.type==3 && torchesShown>=20)continue;
            if(m.type!=3 && chests>=40)continue;
            double dx=m.x-p.method_23317(),dy=m.y-p.method_23318()-1.62,dz=m.z-p.method_23321();
            double f=-dx*sy+dz*cy, side=-dx*cy-dz*sy;
            double depth=f*cp-dy*sp, up=dy*cp+f*sp;
            if(depth<=.45)continue;
            int sx=(int)Math.round(w*.5+side*focal/depth);
            int syy=(int)Math.round(h*.5-up*focal/depth);
            if(sx<18||sx>w-18||syy<25||syy>h-30)continue;
            int size=Math.max(7,Math.min(31,(int)(21*focal/(depth+17))));
            int col=color(m.type);
            // Soft outer glow + crisp inner outline (both through walls).
            outline(g,sx-size-3,syy-size-3,sx+size+3,syy+size+3,(col&0x00FFFFFF)|0x55000000,3);
            outline(g,sx-size,syy-size,sx+size,syy+size,col,2);
            g.method_25300(mc.field_1772,label(m.type)+" "+m.dist+"m",sx,syy-size-14,col);
            if(m.type==3)torchesShown++;else chests++;
        }
    }
    private int color(int type){return switch(type){case 1->PURPLE;case 2->CYAN;case 3->RED;default->GOLD;};}
    private String label(int type){return switch(type){case 1->"ENDER";case 2->"SHULKER";case 3->"TORCH";default->"CHEST";};}
    private void outline(class_332 g,int x1,int y1,int x2,int y2,int c,int thick){
        g.method_25294(x1,y1,x2+1,y1+thick,c);
        g.method_25294(x1,y2-thick,x2+1,y2+1,c);
        g.method_25294(x1,y1,x1+thick,y2+1,c);
        g.method_25294(x2-thick,y1,x2+1,y2+1,c);
    }
}