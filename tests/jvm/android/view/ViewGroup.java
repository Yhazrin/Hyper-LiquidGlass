package android.view;
import java.util.ArrayList;
public class ViewGroup extends View {
    private final ArrayList<View> children = new ArrayList<>();
    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return children.get(index); }
    public int indexOfChild(View child) { return children.indexOf(child); }
    public void addView(View child) { if(child.parent!=null) throw new IllegalStateException(); children.add(child); child.parent=this; }
    public void removeView(View child) { if(children.remove(child)) child.parent=null; }
}
