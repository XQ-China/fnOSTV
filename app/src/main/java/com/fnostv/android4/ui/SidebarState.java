package com.fnostv.android4.ui;

/** 侧栏折叠的全局状态：首页与文件浏览页共用，切页时保持同一形态。 */
public final class SidebarState {
    public static final int WIDE_DP = 260;
    public static final int NARROW_DP = 64;

    public static boolean collapsed = false;

    /** 折叠状态变化后的通知（Activity 用来同步其它视图与详情页边距）。 */
    public static Runnable onChanged;

    private SidebarState() {
    }
}
