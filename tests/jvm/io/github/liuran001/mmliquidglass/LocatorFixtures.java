package io.github.liuran001.mmliquidglass;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
final class LocatorFixtures {
    static final NavigationProfile PROFILE = HostApp.DOUYIN.navigationProfile;
    static FrameLayout frame(int l,int t,int r,int b) {
        FrameLayout f=new FrameLayout(); f.layout(l,t,r,b); return f;
    }
    static ViewGroup row() {
        FrameLayout row=frame(0,2220,1080,2400);
        String[] names={"首页","朋友","","消息","我"};
        for(int i=0;i<names.length;i++) {
            FrameLayout tab=frame(i*216,0,(i+1)*216,180);
            TextView label=new TextView(); label.layout(24,100,192,150); label.setText(names[i]);
            tab.addView(label); row.addView(tab);
        }
        return row;
    }
    static FrameLayout screen(int contentRows) {
        FrameLayout root=frame(0,0,1080,2400);
        FrameLayout page=frame(0,0,1080,2400);root.addView(page);
        for(int i=0;i<contentRows;i++) {
            FrameLayout item=frame(0,i*8,1080,i*8+80);
            for(int j=0;j<5;j++) {
                TextView text=new TextView();text.layout(j*200,0,j*200+180,50);
                text.setText("内容 "+i+":"+j);item.addView(text);
            }
            page.addView(item);
        }
        root.addView(row()); return root;
    }
    static TextView label(ViewGroup row,int index) {
        return (TextView)((ViewGroup)row.getChildAt(index)).getChildAt(0);
    }
}
