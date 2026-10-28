package android.view;
/** A deterministic layout fixture, not an Android rendering implementation. */
public class View implements ViewParent {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    private int left, top, width, height, visibility;
    private float alpha = 1f;
    private CharSequence description;
    ViewGroup parent;
    private final android.content.res.Resources resources = new android.content.res.Resources();
    public void layout(int l, int t, int r, int b) { left=l; top=t; width=r-l; height=b-t; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLeft() { return left; }
    public int getRight() { return left+width; }
    public int getTop() { return top; }
    public int getBottom() { return top+height; }
    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility=value; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float value) { alpha=value; }
    public ViewParent getParent() { return parent; }
    public View getRootView() { return parent == null ? this : parent.getRootView(); }
    public boolean isShown() { return visibility == VISIBLE && (parent == null || parent.isShown()); }
    public void getLocationOnScreen(int[] pos) {
        pos[0]=left; pos[1]=top;
        for (View p=parent; p!=null; p=(View)p.getParent()) { pos[0]+=p.left; pos[1]+=p.top; }
    }
    public android.content.res.Resources getResources() { return resources; }
    public CharSequence getContentDescription() { return description; }
    public void setContentDescription(CharSequence value) { description=value; }
}
