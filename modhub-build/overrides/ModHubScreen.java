package dev.tomer.modhub;

import net.minecraft.class_310;
import net.minecraft.class_2561;
import net.minecraft.class_437;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_4185;
import net.minecraft.class_11908;
import java.util.List;

/** Compact dashboard with Lunar-like layout and independent TOMER branding. */
public final class ModHubScreen extends class_437 {
    private static final int PANEL_W=724, PANEL_H=420;
    private static final int BG=0xF017191A, CONTENT=0xFF232526, SIDEBAR=0xFF1A1C1C;
    private static final int BORDER=0xFF424747, WHITE=0xFFEDEEEE, MUTED=0xFF9EA5A5;
    private static final int GREEN=0xFF23875E, RED=0xFF982E47, BLUE=0xFF4D82D3;
    private static final int ITEM=0xFF262929, ACTIVE=0xFF394039;
    private final TomerModHub hub;
    private int page; // 0 dashboard, 1 rtp, 2 navigator, 3 esp, 4 installed mods
    private int filter;
    private int left, top, width, height, side, contentX;
    private class_342 rangeField, xField, zField, espRangeField;
    private String rangeValue, xValue, zValue, espRangeValue;
    private int installedPage=0;

    public ModHubScreen(TomerModHub hub) {
        this(hub,0,0,Integer.toString(hub.getLimit()),Integer.toString(hub.getNavX()),
            Integer.toString(hub.getNavZ()),Integer.toString(hub.getChestEspRange()));
    }
    private ModHubScreen(TomerModHub hub,int page,int filter,String limit,String x,String z,String espRange) {
        super(class_2561.method_30163("Tomer Client"));
        this.hub=hub;this.page=page;this.filter=filter;
        rangeValue=limit;xValue=x;zValue=z;espRangeValue=espRange;
    }
    private void remember(){
        if(rangeField!=null)rangeValue=rangeField.method_1882();
        if(xField!=null)xValue=xField.method_1882();
        if(zField!=null)zValue=zField.method_1882();
        if(espRangeField!=null)espRangeValue=espRangeField.method_1882();
    }
    private void go(int page,int filter){
        remember();
        class_310.method_1551().method_1507(new ModHubScreen(hub,page,filter,rangeValue,xValue,zValue,espRangeValue));
    }
    private void closeHub(){class_310.method_1551().method_1507(null);}

    @Override protected void method_25426() {
        width=Math.min(PANEL_W,field_22789-16);
        height=Math.min(PANEL_H,field_22790-16);
        width=Math.max(410,width); height=Math.max(295,height);
        left=(field_22789-width)/2;top=(field_22790-height)/2;
        side=Math.max(115,Math.min(162,width/4));contentX=left+side+8;
        rangeField=null;xField=null;zField=null;espRangeField=null;

        button(left+width-34,top+8,23,23,"X",b->closeHub());
        button(left+12,top+83,side-23,22,"ALL MODS",b->go(0,0));
        button(left+12,top+110,side-23,22,"MOVEMENT",b->go(0,1));
        button(left+12,top+137,side-23,22,"UTILITY",b->go(0,2));
        button(left+12,top+height-36,side-23,22,"MY MODS",b->go(4,0));
        int contentW=left+width-contentX-12;
        if(page==0)setupDashboard(contentW);
        else if(page==1)setupRtp(contentW);
        else if(page==2)setupNavigator(contentW);
        else if(page==3)setupEsp(contentW);
        else setupInstalled(contentW);
    }
    private void button(int xx,int yy,int w,int h,String name,class_4185.class_4241 action){
        if(w<=0||h<=0)return;
        class_4185 b=class_4185.method_46430(class_2561.method_30163(name),action)
            .method_46434(xx,yy,w,h).method_46431();
        method_37063(b);
    }
    private int cw(){return left+width-contentX-13;}
    private void setupDashboard(int w){
        int gap=9, cardW=(w-gap*2)/3, y=top+112, h=Math.max(130,Math.min(170,height-172));
        if(filter!=2) {
            cardButtons(contentX,y,cardW,h,"RTP",hub.isRtpRunning(),()->hub.startRtp(),()->hub.stopRtp("Stopped"),()->go(1,filter));
            cardButtons(contentX+cardW+gap,y,cardW,h,"NAV",hub.isNavRunning(),()->go(2,filter),hub::stopNavigation,()->go(2,filter));
        }
        if(filter!=1) {
            int x=filter==2?contentX:contentX+(cardW+gap)*2;
            cardButtons(x,y,cardW,h,"ESP",hub.isChestEspEnabled(),hub::toggleChestEsp,hub::toggleChestEsp,()->go(3,filter));
            if(filter==2) button(contentX+cardW+gap,y+h-40,cardW,25,"MY MODS",b->go(4,0));
        }
        if(filter==0){
            button(contentX,top+height-48,Math.min(170,w/2),23,"SETTINGS: RTP",b->go(1,0));
            button(contentX+Math.min(178,w/2),top+height-48,Math.min(170,w/2),23,"SETTINGS: ESP",b->go(3,0));
        }
    }
    private void cardButtons(int x,int y,int w,int h,String tag,boolean enabled,Runnable start,Runnable stop,Runnable options){
        button(x+8,y+h-59,w-16,23,"OPTIONS",b->options.run());
        button(x+8,y+h-32,w-16,23,enabled?"ENABLED":"DISABLED",b->{if("NAV".equals(tag)&&!enabled){go(2,filter);return;}if(enabled)stop.run();else start.run();go(0,filter);});
    }
    private void fieldRange(String value,int y,int width){
        rangeField=new class_342(field_22793,contentX+13,y,Math.min(188,width-28),23,class_2561.method_30163("RTP range"));
        rangeField.method_1852(value);method_37063(rangeField);
    }
    private void setupRtp(int w){
        fieldRange(rangeValue,top+132,w);
        button(contentX+13,top+165,Math.min(w-26,235),25,"SAVE RANGE",b->{rangeValue=rangeField.method_1882();hub.setLimit(rangeValue);});
        button(contentX+13,top+206,Math.min(w-26,235),25,"REGION: "+hub.getRegion(),b->{hub.cycleRegion();go(1,filter);});
        button(contentX+13,top+250,Math.min(w-26,110),27,"START RTP",b->{rangeValue=rangeField.method_1882();hub.setLimit(rangeValue);if(hub.getLimit()==parse(rangeValue))hub.startRtp();});
        button(contentX+137,top+250,Math.min(w-150,110),27,"STOP",b->hub.stopRtp("Stopped"));
        button(contentX+13,top+height-38,Math.min(w-26,145),23,"< ALL MODS",b->go(0,0));
    }
    private int parse(String s){try{return Integer.parseInt(s.trim().replace(",",""));}catch(Exception ex){return -1;}}
    private void setupNavigator(int w){
        int fieldW=Math.max(70,Math.min(160,(w-35)/2));
        xField=new class_342(field_22793,contentX+13,top+135,fieldW,24,class_2561.method_30163("X"));
        zField=new class_342(field_22793,contentX+23+fieldW,top+135,fieldW,24,class_2561.method_30163("Z"));
        xField.method_1852(xValue);zField.method_1852(zValue);
        method_37063(xField);method_37063(zField);
        button(contentX+13,top+185,Math.min(170,w-30),27,"GO TO COORDS",b->{xValue=xField.method_1882();zValue=zField.method_1882();hub.startNavigation(xValue,zValue);});
        button(contentX+13,top+223,Math.min(170,w-30),27,"STOP WALKING",b->hub.stopNavigation());
        button(contentX+13,top+height-38,Math.min(w-26,145),23,"< ALL MODS",b->go(0,0));
    }
    private void setupEsp(int w){
        espRangeField=new class_342(field_22793,contentX+13,top+122,Math.min(168,w-30),22,class_2561.method_30163("ESP range"));
        espRangeField.method_1852(espRangeValue);method_37063(espRangeField);
        button(contentX+13,top+147,Math.min(168,w-30),23,"SAVE RANGE",b->{espRangeValue=espRangeField.method_1882();hub.setChestEspRange(espRangeValue);});
        button(contentX+13,top+185,Math.min(220,w-30),23,"CHEST ESP: "+(hub.isChestEspEnabled()?"ON":"OFF"),b->{hub.toggleChestEsp();go(3,filter);});
        button(contentX+13,top+214,Math.min(220,w-30),23,"ENDER CHESTS: "+(hub.showEnderChests()?"ON":"OFF"),b->{hub.toggleEnderChests();go(3,filter);});
        button(contentX+13,top+243,Math.min(220,w-30),23,"SHULKERS: "+(hub.showShulkers()?"ON":"OFF"),b->{hub.toggleShulkers();go(3,filter);});
        button(contentX+13,top+272,Math.min(220,w-30),23,"TORCHES: "+(hub.showTorches()?"ON":"OFF"),b->{hub.toggleTorches();go(3,filter);});
        button(contentX+13,top+height-38,Math.min(w-26,145),23,"< ALL MODS",b->go(0,0));
    }
    private void setupInstalled(int w){
        button(contentX+13,top+height-82,Math.max(100,(w-35)/2),25,"IMPORT JAR",b->hub.importModJar());
        button(contentX+23+Math.max(100,(w-35)/2),top+height-82,Math.max(95,(w-35)/2),25,"MODS FOLDER",b->hub.openModsFolder());
        button(contentX+13,top+height-45,Math.min(w-26,145),24,"< ALL MODS",b->go(0,0));
    }

    private void fill(class_332 g,int a,int b,int c,int d,int color){g.method_25294(a,b,c,d,color);}
    private void text(class_332 g,String s,int x,int y,int color){g.method_25303(field_22793,s,x,y,color);}
    private void center(class_332 g,String s,int x,int y,int color){g.method_25300(field_22793,s,x,y,color);}
    private String crop(String s,int n){return s.length()<=n?s:s.substring(0,Math.max(0,n-3))+"...";}
    @Override public void method_25394(class_332 g,int mouseX,int mouseY,float delta){
        fill(g,0,0,field_22789,field_22790,0x90000000);
        fill(g,left-2,top-2,left+width+2,top+height+2,BORDER);
        fill(g,left,top,left+width,top+height,BG);
        fill(g,left,top,left+side,top+height,SIDEBAR);
        fill(g,left,top+49,left+width,top+50,0xFF3C4040);
        fill(g,left+side,top+50,left+side+1,top+height,0xFF3A3E3D);
        fill(g,left+11,top+10,left+37,top+35,GREEN);
        center(g,"T",left+24,top+18,WHITE);
        text(g,"TOMER CLIENT",left+44,top+18,WHITE);
        text(g,"MODULE PROFILES",left+13,top+64,MUTED);
        text(g,"Fabric 1.21.10",left+13,top+height-62,MUTED);
        text(g,"ESC / R-SHIFT",left+13,top+height-51,0xFF78C9B6);
        text(g,"HOME",contentX+10,top+19,WHITE);
        text(g,"MODS",contentX+82,top+19,page==0?0xFF88C1FF:MUTED);
        text(g,"SETTINGS",contentX+141,top+19,page!=0?0xFF88C1FF:MUTED);
        fill(g,contentX+3,top+49,left+width-4,top+51,0xFF363C3A);
        if(page==0)drawDashboard(g);
        else if(page==1)drawRtp(g);
        else if(page==2)drawNavigator(g);
        else if(page==3)drawEsp(g);
        else drawInstalled(g);
        super.method_25394(g,mouseX,mouseY,delta);
    }
    private void drawDashboard(class_332 g){
        text(g,filter==0?"ALL MODULES":filter==1?"MOVEMENT":"UTILITY",contentX+7,top+62,WHITE);
        text(g,"MODS    /    ENABLE AND CONFIGURE",contentX+7,top+81,MUTED);
        int w=cw(),gap=9,cardW=(w-gap*2)/3,y=top+112,h=Math.max(130,Math.min(170,height-172));
        if(filter!=2){
            card(g,contentX,y,cardW,h,"RTP Bot","Random Teleport","+/- "+hub.getLimit(),hub.isRtpRunning(),BLUE);
            card(g,contentX+cardW+gap,y,cardW,h,"Navigator","Built-in walking","X / Z goal",hub.isNavRunning(),0xFF6797C8);
        }
        if(filter!=1)card(g,filter==2?contentX:contentX+(cardW+gap)*2,y,cardW,h,"Chest ESP","Loaded chests",hub.getChestEspRange()+" blocks",hub.isChestEspEnabled(),0xFFD0A347);
        text(g,"RTP: "+crop(hub.rtpStatus(),Math.max(25,w/8)),contentX+7,top+height-74,MUTED);
    }
    private void card(class_332 g,int x,int y,int w,int h,String name,String sub,String detail,boolean active,int c){
        fill(g,x,y,x+w,y+h,ITEM);fill(g,x,y,x+w,y+2,0xFF484D4D);
        fill(g,x+1,y+h-35,x+w-1,y+h-7,active?GREEN:RED);
        center(g,name,x+w/2,y+55,WHITE);
        center(g,sub,x+w/2,y+78,MUTED);
        center(g,detail,x+w/2,y+94,c);
        int xx=x+w/2, yy=y+28;
        // minimal vector icon motif; avoids external fonts and textures
        fill(g,xx-12,yy-10,xx+12,yy-8,c);
        fill(g,xx-12,yy+8,xx+12,yy+10,c);
        fill(g,xx-12,yy-8,xx-10,yy+8,c);
        fill(g,xx+10,yy-8,xx+12,yy+8,c);
    }
    private void title(class_332 g,String s,String hint){text(g,s,contentX+13,top+76,WHITE);text(g,hint,contentX+13,top+93,MUTED);}
    private void drawRtp(class_332 g){
        title(g,"RTP BOT   /   OPTIONS","Stop only if BOTH X and Z are within the range");
        text(g,"TARGET RANGE (+/- blocks)",contentX+13,top+118,MUTED);
        text(g,"Currently: "+hub.getLimit()+"   |   "+hub.getRegion(),contentX+13,top+195,0xFF75C8A4);
        text(g,"Attempts: "+hub.getAttempts()+"   "+hub.coords(),contentX+13,top+294,WHITE);
        text(g,crop(hub.rtpStatus(),55),contentX+13,top+311,MUTED);
    }
    private void drawNavigator(class_332 g){
        title(g,"COORDINATE NAVIGATOR","Built-in walking - stops at hazards / blocked terrain");
        text(g,"TARGET X",contentX+13,top+120,MUTED);
        text(g,"TARGET Z",contentX+23+Math.max(70,Math.min(160,(cw()-35)/2)),top+120,MUTED);
        text(g,"Navigation: built-in (no other mod)",contentX+13,top+280,GREEN);
        text(g,crop(hub.navStatus(),60),contentX+13,top+299,MUTED);
    }
    private void drawEsp(class_332 g){
        title(g,"ESP   /   OPTIONS","Bright markers over stone / terrain (loaded chunks)");
        text(g,"DETECTION DISTANCE  (8 to 128)",contentX+13,top+108,MUTED);
        text(g,"Detected: "+hub.getChestCount(),contentX+244,top+188,WHITE);
        text(g,"GOLD = Chests",contentX+244,top+211,0xFFFFC645);
        text(g,"PURPLE = Ender",contentX+244,top+234,0xFFC57BFF);
        text(g,"CYAN = Shulkers",contentX+244,top+257,0xFF47E5E1);
        text(g,"RED = Torches",contentX+244,top+280,0xFFFF7864);
        text(g,"Torches scan nearby 26x18 radius",contentX+13,top+316,MUTED);
        text(g,"Unloaded chunks cannot be detected",contentX+13,top+336,MUTED);
    }
    private void drawInstalled(class_332 g){
        title(g,"INSTALLED FABRIC MODS","Import JAR installs it for the NEXT game launch");
        List<String> items=hub.installedMods();
        int max=Math.max(1,Math.min(11,(height-170)/18));
        for(int i=0;i<Math.min(max,items.size());i++)text(g,crop(items.get(i),Math.max(20,cw()/6)),contentX+13,top+119+i*18,i%2==0?WHITE:MUTED);
        text(g,crop(hub.importStatus(),60),contentX+13,top+height-107,0xFF7DC7AF);
    }
    @Override public boolean method_25422(){return true;}
    @Override public boolean method_25404(class_11908 input){
        if(input.comp_4795()==344){hub.screenClosedWithShift();closeHub();return true;}
        return super.method_25404(input);
    }
}