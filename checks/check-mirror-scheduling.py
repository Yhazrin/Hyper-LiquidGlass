#!/usr/bin/env python3
"""Execute compiled production BiliTabMirror against a deterministic minimal UI fixture.
NOT an Android/device/CPU/GPU benchmark. No copy of sync/refresh logic is used.
Usage: python3 check_scheduling.py CLASSES_DIR ANDROID_JAR OUTPUT_DIR
Requires javac/java on PATH. CLASSES_DIR must come from the matching source compile.
"""
import pathlib,subprocess,sys,json
classes,android,out=map(lambda v:pathlib.Path(v).resolve(),sys.argv[1:])
src=out/'fixture'; binary=out/'fixture-classes';src.mkdir(parents=True,exist_ok=True);binary.mkdir(exist_ok=True)
files={
'android/content/Context.java': 'package android.content; public class Context {}',
'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long now=1000; public static long uptimeMillis(){ return now; } }',
'android/view/ViewParent.java':'package android.view; public interface ViewParent {}',
'android/view/View.java': '''package android.view;
import android.content.Context; import android.os.SystemClock; import java.util.*;
public class View {
 public ViewParent parent; public Object tag; public static long invalidations, serial;
 public static class Event { public long at,id; public Runnable action; Event(long a,Runnable r){at=a;action=r;id=serial++;} }
 public static PriorityQueue<Event> queue=new PriorityQueue<>(Comparator.<Event>comparingLong(e->e.at).thenComparingLong(e->e.id));
 public View(Context c){} public Context getContext(){return null;}
 public ViewParent getParent(){return parent;} public Object getTag(){return tag;} public void setTag(Object t){tag=t;}
 public boolean isAttachedToWindow(){return true;} public boolean isShown(){return true;}
 public int getWidth(){return 500;} public int getHeight(){return 80;} public int getLeft(){return 0;} public int getTop(){return 0;}
 public boolean postDelayed(Runnable r,long d){queue.add(new Event(SystemClock.now+d,r));return true;}
 public boolean removeCallbacks(Runnable r){return queue.removeIf(e->e.action==r);}
 public void postInvalidateOnAnimation(){invalidations++;}
 public int getLayoutDirection(){return 0;} protected void onDetachedFromWindow(){}
 public static void until(long time){while(!queue.isEmpty() && queue.peek().at<=time){Event e=queue.remove();SystemClock.now=e.at;e.action.run();}SystemClock.now=time;}
 public static void reset(){queue.clear();invalidations=0;SystemClock.now=1000;}
}''',
'android/view/ViewGroup.java':'''package android.view;
import android.content.Context; import java.util.*;
public class ViewGroup extends View implements ViewParent {
 public final List<View> kids=new ArrayList<>(); public ViewGroup(Context c){super(c);}
 public int getChildCount(){return kids.size();} public View getChildAt(int i){return kids.get(i);}
 public void add(View v){kids.add(v);v.parent=this;}
}''',
'android/widget/FrameLayout.java':'package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} }',
'io/github/liuran001/mmliquidglass/LiquidGlassHostLayout.java':'package io.github.liuran001.mmliquidglass; public class LiquidGlassHostLayout extends android.widget.FrameLayout {public LiquidGlassHostLayout(){super(null);}}',
'io/github/liuran001/mmliquidglass/HostApp.java':'package io.github.liuran001.mmliquidglass; public enum HostApp { BILIBILI }',
'io/github/liuran001/mmliquidglass/LiquidGlassModule.java':'''package io.github.liuran001.mmliquidglass;
public class LiquidGlassModule {static HostApp app(){return HostApp.BILIBILI;} static void log(int p,String m){} static void logErr(String m,Throwable e){throw new AssertionError(m,e);} }''',
'io/github/liuran001/mmliquidglass/LiquidGlassInstaller.java':'''package io.github.liuran001.mmliquidglass;
public class LiquidGlassInstaller {static boolean active=true;static boolean isRenderingActive(android.view.View v){return active && v!=null;} static void noteSyntheticRow(LiquidGlassHostLayout h,android.view.ViewGroup v){} }''',
'io/github/liuran001/mmliquidglass/TabBarBridge.java':'''package io.github.liuran001.mmliquidglass;
public class TabBarBridge {static boolean isTabView(android.view.View v){return v instanceof Probe.ComposeView;} }''',
'io/github/liuran001/mmliquidglass/Probe.java':'''package io.github.liuran001.mmliquidglass;
import android.view.*;import android.os.SystemClock;import java.lang.reflect.*;
public class Probe {
 public static class ComposeView extends View { public long reads;public ComposeView(){super(null);}public Object getSemanticsOwner(){reads++;return null;} }
 static Class<?> mirrorType;static LiquidGlassHostLayout host;static View mirror;static ComposeView tab;
 static void setup() throws Exception {
  View.reset();LiquidGlassInstaller.active=true;host=new LiquidGlassHostLayout();tab=new ComposeView();host.add(tab);
  mirrorType=Class.forName("io.github.liuran001.mmliquidglass.BiliTabMirror$Mirror");
  Constructor<?> ctor=mirrorType.getDeclaredConstructor(android.content.Context.class);ctor.setAccessible(true);mirror=(View)ctor.newInstance((Object)null);
  Field tag=BiliTabMirror.class.getDeclaredField("TAG");tag.setAccessible(true);mirror.setTag(tag.get(null));host.add(mirror);
 }
 static void request()throws Exception {Method m=mirrorType.getDeclaredMethod("requestRefresh",long.class);m.setAccessible(true);m.invoke(mirror,64L);}
 static void output(String scenario,int hz,int seconds,int requests){System.out.println("{\\"scenario\\":\\""+scenario+"\\",\\"hz\\":"+hz+",\\"seconds\\":"+seconds+",\\"external_sync_calls\\":"+requests+",\\"owner_reads\\":"+tab.reads+",\\"forced_invalidations\\":"+View.invalidations+",\\"pending_callbacks\\":"+View.queue.size()+"}");}
 public static void main(String[]args)throws Exception {
  for(int hz:new int[]{60,120}){
   setup();int n=60*hz;for(int i=0;i<n;i++){View.until(1000L+i*1000L/hz);BiliTabMirror.sync(host);}View.until(61100);output("unready_semantics",hz,60,n);
  }
  setup();request();View.until(1100);output("one_delayed_refresh",0,0,0);
  setup();request();LiquidGlassInstaller.active=false;
  try{Method suspend=BiliTabMirror.class.getDeclaredMethod("suspend",LiquidGlassHostLayout.class);suspend.setAccessible(true);suspend.invoke(null,host);}catch(NoSuchMethodException expected){}
  View.until(1100);output("pause_with_pending_refresh",0,0,0);
 }
}'''
}
# Quote escaping above is for Java JSON literals, not for user's source.
for name,body in files.items():
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
cmd=['javac','--release','11','-cp',str(classes)+':'+str(android),'-d',str(binary),*[str(src/name) for name in files]]
p=subprocess.run(cmd,text=True,capture_output=True)
ledger=[dict(command=cmd,stdout=p.stdout,stderr=p.stderr,exit_status=p.returncode)]
if p.returncode==0:
 cmd=['java','-cp',str(binary)+':'+str(classes)+':'+str(android),'io.github.liuran001.mmliquidglass.Probe'];p=subprocess.run(cmd,text=True,capture_output=True)
 ledger.append(dict(command=cmd,stdout=p.stdout,stderr=p.stderr,exit_status=p.returncode))
(out/'LEDGER.json').write_text(json.dumps(ledger,indent=2)+'\n')
print(p.stdout,end='');print(p.stderr,file=sys.stderr,end='');sys.exit(p.returncode)
