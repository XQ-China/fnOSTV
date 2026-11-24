package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.MediaSeriesHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class NativeFileBrowserView {
    public interface Listener {
        void onFileEntrySelected(FnosFileEntry entry);

        void onFileFavoriteToggled(FnosFileEntry entry);

        boolean isFileFavorite(FnosFileEntry entry);

        void onBrowserAction(String action);

        /**
         * 网页端列表工具条「排序」变化：重新按服务端排序拉取整库。
         *
         * @param sortColumn create_time / favorite_time / release_date / title / vote_average
         * @param sortType   ASC / DESC
         */
        void onBrowserSortChanged(String sortColumn, String sortType);

        /** 网页端列表工具条「筛选」变化（资源类型）：全部 / 电影 / 电视节目 / 电视直播 / 其他。 */
        void onBrowserTypeFilterChanged(String category);

        /** 网页端收藏页分类标签变化：全部 / 电影 / 电视节目 / 单集 / 电视直播 / 人物。 */
        void onBrowserFavoriteTabChanged(String tabType);
    }

    /** 网页端布局：列表。 */
    public static final int VIEW_LIST = 0;
    /** 网页端布局：横幅海报。 */
    public static final int VIEW_POSTER_HORIZONTAL = 1;
    /** 网页端布局：竖幅海报。 */
    public static final int VIEW_POSTER_VERTICAL = 2;
    /** 网页端布局：海报墙。 */
    public static final int VIEW_POSTER_WALL = 3;

    /** 普通列表（不显示收藏分类标签）。 */
    public static final int MODE_NORMAL = 0;
    /** 收藏页（显示网页端 6 个收藏分类标签）。 */
    public static final int MODE_FAVORITES = 1;

    /** 网页端排序字段：layout.list.sort.sortField.*。 */
    public static final String SORT_CREATE_TIME = "create_time";
    public static final String SORT_FAVORITE_TIME = "favorite_time";
    public static final String SORT_RELEASE_DATE = "release_date";
    public static final String SORT_TITLE = "sort_title";
    public static final String SORT_VOTE_AVERAGE = "vote_average";

    private static final String[] SORT_COLUMNS = {
            SORT_CREATE_TIME, SORT_FAVORITE_TIME, SORT_RELEASE_DATE, SORT_TITLE, SORT_VOTE_AVERAGE
    };
    private static final String[] SORT_LABELS = {
            "添加日期", "收藏时间", "发行日期", "标题", "评分"
    };
    /**
     * 网页端文件夹页（/folder/:guid）的排序下拉只有「标题」「添加日期」两项，
     * 和列表页的下拉不是同一套，所以单独维护一份。
     */
    private static final String[] FOLDER_SORT_COLUMNS = {SORT_TITLE, SORT_CREATE_TIME};
    private static final String[] FOLDER_SORT_LABELS = {"标题", "添加日期"};
    /** 网页端筛选：layout.list.filter.type.*。 */
    private static final String[] TYPE_FILTERS = {"all", "movie", "tv", "live", "other"};
    private static final String[] TYPE_FILTER_LABELS = {"全部", "电影", "电视节目", "电视直播", "其他"};
    /** 网页端收藏分类标签：layout.list.favoriteTabs.*。 */
    private static final String[] FAVORITE_TABS = {
            "", "Movie", "TV", "Episode", "LiveChannel", "Person"
    };
    private static final String[] FAVORITE_TAB_LABELS = {
            "全部", "电影", "电视节目", "单集", "电视直播", "人物"
    };
    private static final String[] VIEW_TYPE_LABELS = {
            "列表", "横幅海报", "竖幅海报", "海报墙"
    };

    private final Context context;
    private final Listener listener;
    private final EntryAdapter adapter = new EntryAdapter();
    private final PosterLoader posterLoader = new PosterLoader();
    private LinearLayout view;
    private TextView titleView;
    private TextView countView;
    private TextView emptyView;
    private GridView gridView;
    private LinearLayout libraryNavContainer;
    /** action -> 侧栏行，用于「当前栏目」高亮。 */
    private final java.util.Map<String, NavItem> navItems = new java.util.HashMap<String, NavItem>();
    private LinearLayout sidebarView;
    private LinearLayout.LayoutParams sidebarParams;
    private LinearLayout brandRow;
    private final List<View> collapseHide = new ArrayList<View>();
    private boolean sidebarCollapsed;
    private boolean userViewTypeFixed;
    private String activeNavAction = NativeHomeView.ACTION_HOME;
    /** 页面标题行（正常页面显示；电视剧/季页由 hero 头部取代）。 */
    private LinearLayout pageHeaderRow;
    /** 电视剧 / 季页的 hero 头部（海报 + 标题 + 简介）。 */
    private LinearLayout seriesHeaderPanel;
    private FrameLayout seriesPosterFrame;
    private ImageView seriesPosterImage;
    private TextView seriesPosterFallback;
    private TextView seriesTitleView;
    private TextView seriesMetaView;
    private TextView seriesOverviewView;
    /** 多季剧集的「第 N 季」切换条。 */
    private LinearLayout seasonStrip;
    /** 列表工具条（筛选/排序/视图）；季页与剧集页隐藏。 */
    private LinearLayout toolsRow;
    private boolean seriesMode;
    private boolean episodeMode;
    /** 季页剧集卡片宽度（按可用宽度计算，默认 260dp）。 */
    private int episodeCardWidth;
    /** 横幅海报卡片按列宽铺满后的宽度（px）。 */
    private int horizontalCardWidth;
    /** 侧栏计数（与网页侧栏一致：收藏/分类各行都带数量）。 */
    private TextView navFavoriteCount;
    private TextView navAllCount;
    private TextView navMovieCount;
    private TextView navTvCount;
    private TextView navLiveCount;
    private TextView navOtherCount;
    private String posterBaseUrl = "";
    /** 网页端列表工具条状态。 */
    private String sortColumn = SORT_CREATE_TIME;
    private String sortType = "DESC";
    /** 当前页面可用的排序字段（列表页 5 项 / 文件夹页 2 项，网页端下拉同款）。 */
    private String[] sortColumns = SORT_COLUMNS;
    private String[] sortLabels = SORT_LABELS;
    private int viewType = VIEW_POSTER_HORIZONTAL;
    /** 记住用户在「非列表页」选过的布局，切回文件夹以外的页面时还原。 */
    private int lastGridViewType = VIEW_POSTER_VERTICAL;
    private int typeFilterIndex = 0;
    private int favoriteTabIndex = 0;
    private int mode = MODE_NORMAL;
    private TextView sortChip;
    private TextView sortTypeChip;
    private TextView viewTypeChip;
    private TextView filterChip;
    private LinearLayout tabStrip;
    private final List<TextView> tabViews = new ArrayList<TextView>();

    public NativeFileBrowserView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setBackgroundColor(FnosTheme.COLOR_APP_BG);
        view.setVisibility(View.GONE);

        sidebarView = sidebar();
        sidebarParams = new LinearLayout.LayoutParams(dp(SidebarState.WIDE_DP), ViewGroup.LayoutParams.MATCH_PARENT);
        view.addView(sidebarView, sidebarParams);
        view.addView(content(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return view;
    }

    public void show(String path, List<FnosFileEntry> entries) {
        showCustom("文件库", path == null || path.length() == 0 ? "根目录" : path, entries, true);
    }

    public void setPosterBaseUrl(String baseUrl) {
        posterBaseUrl = baseUrl == null ? "" : baseUrl;
        adapter.notifyDataSetChanged();
    }

    public void setPosterAuthorizationToken(String token) {
        posterLoader.setAuthorizationToken(token);
        adapter.notifyDataSetChanged();
    }

    public void showCustom(String title, String subtitle, List<FnosFileEntry> entries, boolean sortEntries) {
        applySeriesChrome(false, false);
        applySidebarCollapsed(SidebarState.collapsed);
        if (view != null) {
            view.setVisibility(View.VISIBLE);
        }
        titleView.setText(title == null || title.length() == 0 ? "文件库" : title);
        int count = entries == null ? 0 : entries.size();
        countView.setText("共 " + count + " 项" + subtitleText(subtitle));
        adapter.setEntries(entries, sortEntries);
        if (emptyView != null) {
            emptyView.setText(emptyText(title, subtitle));
        }
        if (tabStrip != null) {
            tabStrip.setVisibility(mode == MODE_FAVORITES ? View.VISIBLE : View.GONE);
        }
        applyViewType();
        if (gridView != null) {
            gridView.requestFocus();
            if (count > 0) {
                gridView.setSelection(0);
            }
        }
    }

    /**
     * 电视剧 / 季页（网页端 /tv/:guid、/tv/season/:guid）。
     *
     * <p>季页：hero 头部（剧名 + 第 N 季 + 简介 + 海报）+ 剧集卡片
     * （16:9 剧照、「第 N 集. 标题」、两行简介、时长），多季时顶部有季切换条。
     * 剧集页：hero 头部 + 季卡片网格。
     */
    private void applySeriesChrome(boolean series, boolean episodes) {
        seriesMode = series;
        episodeMode = episodes;
        if (pageHeaderRow != null) {
            pageHeaderRow.setVisibility(series ? View.GONE : View.VISIBLE);
        }
        if (seriesHeaderPanel != null) {
            seriesHeaderPanel.setVisibility(series ? View.VISIBLE : View.GONE);
        }
        if (toolsRow != null) {
            toolsRow.setVisibility(series ? View.GONE : View.VISIBLE);
        }
        if (seasonStrip != null) {
            seasonStrip.setVisibility(series && episodes ? View.VISIBLE : View.GONE);
        }
        if (tabStrip != null) {
            tabStrip.setVisibility(!series && mode == MODE_FAVORITES ? View.VISIBLE : View.GONE);
        }
    }

    private void renderSeriesHeader(String fallbackTitle, MediaSeriesHeader header) {
        if (seriesHeaderPanel == null) {
            return;
        }
        String title = header == null || header.title.length() == 0 ? fallbackTitle : header.title;
        seriesTitleView.setText(title == null ? "" : title);
        seriesMetaView.setText(seriesMeta(header));
        if (header != null && header.overview.length() > 0) {
            seriesOverviewView.setText(header.overview);
            seriesOverviewView.setVisibility(View.VISIBLE);
        } else {
            seriesOverviewView.setVisibility(View.GONE);
        }
        seriesPosterFallback.setText(title == null ? "" : title);
        if (header != null && header.posterPath.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, header.posterPath, 400, 480,
                    seriesPosterFrame, seriesPosterImage, seriesPosterFallback);
        } else {
            seriesPosterFrame.setTag("");
            seriesPosterImage.setImageDrawable(null);
            seriesPosterImage.setVisibility(View.GONE);
            seriesPosterFallback.setVisibility(View.VISIBLE);
        }
    }

    /** 网页头部信息行：「第 1 季 · 24 集 · 2023 · 8.2 分」。 */
    private String seriesMeta(MediaSeriesHeader header) {
        if (header == null) {
            return "";
        }
        StringBuilder meta = new StringBuilder();
        if (header.seasonTitle.length() > 0) {
            appendMeta(meta, header.seasonTitle);
        } else if (header.seasonCount > 0) {
            appendMeta(meta, "共 " + header.seasonCount + " 季");
        }
        if (header.episodeCount > 0) {
            appendMeta(meta, header.episodeCount + " 集");
        }
        if (header.year.length() > 0) {
            appendMeta(meta, header.year);
        }
        if (header.score.length() > 0) {
            appendMeta(meta, header.score + " 分");
        }
        return meta.toString();
    }

    private static void appendMeta(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append(value);
    }

    /** 多季剧集的季切换条（网页端「第 N 季」下拉的平铺版本）。 */
    private void renderSeasonStrip(List<FnosFileEntry> seasons, String currentSeasonGuid, boolean episodes) {
        if (seasonStrip == null) {
            return;
        }
        seasonStrip.removeAllViews();
        if (!episodes || seasons == null || seasons.size() <= 1) {
            seasonStrip.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < seasons.size(); i++) {
            final FnosFileEntry season = seasons.get(i);
            if (season == null) {
                continue;
            }
            boolean selected = season.path.length() > 0 && season.path.equals(currentSeasonGuid);
            TextView item = chip(season.seasonLabel());
            if (selected) {
                item.setTextColor(FnosTheme.COLOR_PRIMARY);
                item.setTypeface(null, 1);
            }
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onFileEntrySelected(season);
                }
            });
            seasonStrip.addView(item, chipParams());
        }
        seasonStrip.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    public boolean isVisible() {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private static final class NavItem {
        final LinearLayout row;
        final TextView text;
        final FnosSidebarIconView icon;
        final String action;

        NavItem(LinearLayout row, TextView text, FnosSidebarIconView icon, String action) {
            this.row = row;
            this.text = text;
            this.icon = icon;
            this.action = action;
        }
    }

    /** 侧栏收起 / 展开：收起后是一条只留图标的窄栏（与首页同一套形态）。 */
    public void toggleSidebar() {
        applySidebarCollapsed(!sidebarCollapsed);
        if (SidebarState.onChanged != null) {
            SidebarState.onChanged.run();
        }
    }

    public void applySidebarCollapsed(boolean collapsed) {
        if (sidebarView == null || sidebarParams == null) {
            return;
        }
        sidebarCollapsed = collapsed;
        SidebarState.collapsed = collapsed;
        sidebarParams.width = dp(collapsed ? SidebarState.NARROW_DP : SidebarState.WIDE_DP);
        sidebarView.setPadding(collapsed ? 0 : dp(20), dp(collapsed ? 12 : 28),
                collapsed ? 0 : dp(20), dp(28));
        for (int i = 0; i < collapseHide.size(); i++) {
            collapseHide.get(i).setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        for (NavItem item : navItems.values()) {
            for (int i = 1; i < item.row.getChildCount(); i++) {
                item.row.getChildAt(i).setVisibility(collapsed ? View.GONE : View.VISIBLE);
            }
            item.row.setPadding(collapsed ? 0 : dp(12), 0, collapsed ? 0 : dp(12), 0);
            item.row.setGravity(collapsed ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        }
        if (brandRow != null) {
            for (int i = 0; i < brandRow.getChildCount() - 1; i++) {
                brandRow.getChildAt(i).setVisibility(collapsed ? View.GONE : View.VISIBLE);
            }
            brandRow.setGravity(collapsed ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        }
        sidebarView.requestLayout();
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    /** 用户是否手动切换过布局（切过之后不再按页面类型自动改）。 */
    public boolean isUserViewTypeFixed() {
        return userViewTypeFixed;
    }

    private LinearLayout sidebar() {
        LinearLayout sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(20), dp(28), dp(20), dp(28));
        sidebar.setBackgroundColor(FnosTheme.COLOR_SIDEBAR);

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(new FnosLogoMarkView(context), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView logo = new TextView(context);
        logo.setText("fnOSTV");
        logo.setTextColor(FnosTheme.COLOR_TEXT_80);
        logo.setTextSize(17);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.setPadding(dp(8), 0, 0, 0);
        brand.addView(logo, new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView collapse = new TextView(context);
        collapse.setText("≡");
        collapse.setTextSize(20);
        collapse.setTextColor(FnosTheme.COLOR_TEXT);
        collapse.setGravity(Gravity.CENTER);
        collapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSidebar();
            }
        });
        brand.addView(collapse, new LinearLayout.LayoutParams(dp(34), dp(34)));
        brandRow = brand;
        sidebar.addView(brand, navParams(0, 28));

        sidebar.addView(navItem(FnosSidebarIconView.TYPE_HOME, "首页", NativeHomeView.ACTION_HOME), navParams(0, 8));
        navFavoriteCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_FAVORITE, "收藏", NativeHomeView.ACTION_FAVORITES, navFavoriteCount), navParams(0, 30));
        View librarySectionTitle = section("媒体库");
        collapseHide.add(librarySectionTitle);
        sidebar.addView(librarySectionTitle, navParams(0, 8));
        libraryNavContainer = new LinearLayout(context);
        libraryNavContainer.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(libraryNavContainer, rowParams(0, 28));
        View categorySectionTitle = section("分类");
        collapseHide.add(categorySectionTitle);
        sidebar.addView(categorySectionTitle, navParams(0, 8));
        navAllCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_ALL, "全部", NativeHomeView.ACTION_ALL, navAllCount), navParams(0, 8));
        navMovieCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_MOVIE, "电影", NativeHomeView.ACTION_MOVIES, navMovieCount), navParams(0, 8));
        navTvCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_TV, "电视节目", NativeHomeView.ACTION_TV, navTvCount), navParams(0, 8));
        navLiveCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_LIVE, "电视直播", NativeHomeView.ACTION_LIVE, navLiveCount), navParams(0, 8));
        navOtherCount = navCount();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_OTHER, "其他", NativeHomeView.ACTION_OTHER, navOtherCount), navParams(0, 8));
        return sidebar;
    }

    /** 网页侧栏按媒体库 category 换图标（Movie=胶片盘，TV=电视，其他=文件夹）。 */
    private static String libraryIconType(FnosFileEntry library) {
        String category = library == null || library.type == null ? "" : library.type.toLowerCase();
        if (category.indexOf("movie") >= 0) {
            return FnosSidebarIconView.TYPE_MOVIE;
        }
        if (category.indexOf("tv") >= 0) {
            return FnosSidebarIconView.TYPE_TV;
        }
        return FnosSidebarIconView.TYPE_OTHER;
    }

    private TextView navCount() {
        TextView count = new TextView(context);
        count.setTextColor(FnosTheme.COLOR_TEXT_60);
        count.setTextSize(13);
        return count;
    }

    /** 分类/收藏计数与首页侧栏保持一致（网页侧栏所有行都带数量）。 */
    public void updateCounts(int favoriteCount, int allCount, int movieCount, int tvCount, int otherCount) {
        setNavCount(navFavoriteCount, favoriteCount);
        setNavCount(navAllCount, allCount);
        setNavCount(navMovieCount, movieCount);
        setNavCount(navTvCount, tvCount);
        setNavCount(navOtherCount, otherCount);
    }

    public void updateLiveCount(int liveCount) {
        setNavCount(navLiveCount, liveCount);
    }

    private void setNavCount(TextView view, int value) {
        if (view != null) {
            view.setText(String.valueOf(Math.max(0, value)));
        }
    }

    /** 与首页一致：按 mediadb/list 动态渲染媒体库侧栏（标签带计数）。 */
    public void updateLibraries(final List<FnosFileEntry> libraries, java.util.Map<String, Integer> counts) {
        if (libraryNavContainer == null || libraries == null) {
            return;
        }
        libraryNavContainer.removeAllViews();
        java.util.Iterator<String> keys = navItems.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().startsWith(NativeHomeView.ACTION_LIBRARY_PREFIX)) {
                keys.remove();
            }
        }
        for (int i = 0; i < libraries.size(); i++) {
            final FnosFileEntry library = libraries.get(i);
            int count = counts == null ? 0 : (counts.get(library.path) == null ? 0 : counts.get(library.path));
            TextView countView = new TextView(context);
            countView.setText(String.valueOf(Math.max(0, count)));
            countView.setTextColor(FnosTheme.COLOR_TEXT_60);
            countView.setTextSize(13);
            libraryNavContainer.addView(navItem(
                    libraryIconType(library),
                    library.name,
                    NativeHomeView.ACTION_LIBRARY_PREFIX + library.path,
                    countView),
                    navParams(0, i == libraries.size() - 1 ? 0 : 2));
        }
    }

    private LinearLayout content() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(44), 0, dp(60), 0);
        content.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        pageHeaderRow = header;
        titleView = new TextView(context);
        titleView.setText("文件库");
        titleView.setTextColor(FnosTheme.COLOR_TEXT_80);
        titleView.setTextSize(20);
        titleView.setTypeface(null, 1);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        countView = new TextView(context);
        countView.setTextColor(FnosTheme.COLOR_TEXT_60);
        countView.setTextSize(14);
        countView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(countView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(header, rowHeightParams(0, 0, 80));

        content.addView(seriesHeaderView(), rowParams(0, 0));

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        toolsRow = tools;
        // 网页端列表工具条：筛选 / 排序字段 / 排序方向 / 布局
        filterChip = chip("筛选：" + TYPE_FILTER_LABELS[typeFilterIndex]);
        filterChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChipMenu(v, TYPE_FILTER_LABELS, typeFilterIndex, new ChipPick() {
                    @Override
                    public void onPick(int index) {
                        if (index == typeFilterIndex) {
                            return;
                        }
                        typeFilterIndex = index;
                        updateToolbarLabels();
                        listener.onBrowserTypeFilterChanged(TYPE_FILTERS[typeFilterIndex]);
                    }
                });
            }
        });
        tools.addView(filterChip, chipParams());
        sortChip = chip(sortLabel());
        sortChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = 0;
                for (int i = 0; i < sortColumns.length; i++) {
                    if (sortColumns[i].equals(sortColumn)) {
                        current = i;
                        break;
                    }
                }
                showChipMenu(v, sortLabels, current, new ChipPick() {
                    @Override
                    public void onPick(int index) {
                        sortColumn = sortColumns[index];
                        updateToolbarLabels();
                        listener.onBrowserSortChanged(sortColumn, sortType);
                    }
                });
            }
        });
        tools.addView(sortChip, chipParams());
        sortTypeChip = chip(sortTypeLabel());
        sortTypeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] orders = {"降序", "升序"};
                showChipMenu(v, orders, "DESC".equals(sortType) ? 0 : 1, new ChipPick() {
                    @Override
                    public void onPick(int index) {
                        sortType = index == 0 ? "DESC" : "ASC";
                        updateToolbarLabels();
                        listener.onBrowserSortChanged(sortColumn, sortType);
                    }
                });
            }
        });
        tools.addView(sortTypeChip, chipParams());
        viewTypeChip = chip(VIEW_TYPE_LABELS[viewType]);
        // 布局固定「横幅海报」：网页端的其它三种布局在平板上不好用，已去掉
        viewTypeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (viewType != VIEW_POSTER_HORIZONTAL) {
                    viewType = VIEW_POSTER_HORIZONTAL;
                    lastGridViewType = VIEW_POSTER_HORIZONTAL;
                    updateToolbarLabels();
                    applyViewType();
                }
            }
        });
        tools.addView(viewTypeChip, chipParams());
        TextView hint = new TextView(context);
        hint.setText("菜单键收藏 / 取消收藏");
        hint.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        hintParams.leftMargin = dp(12);
        tools.addView(hint, hintParams);
        content.addView(tools, rowParams(0, 8));

        // 网页端收藏页的分类标签：全部 / 电影 / 电视节目 / 单集 / 电视直播 / 人物
        tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setGravity(Gravity.CENTER_VERTICAL);
        tabStrip.setVisibility(View.GONE);
        for (int i = 0; i < FAVORITE_TAB_LABELS.length; i++) {
            final int index = i;
            TextView tab = chip(FAVORITE_TAB_LABELS[i]);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    favoriteTabIndex = index;
                    updateFavoriteTabStyles();
                    listener.onBrowserFavoriteTabChanged(FAVORITE_TABS[index]);
                }
            });
            tabViews.add(tab);
            tabStrip.addView(tab, chipParams());
        }
        content.addView(tabStrip, rowParams(0, 12));
        updateFavoriteTabStyles();

        seasonStrip = new LinearLayout(context);
        seasonStrip.setOrientation(LinearLayout.HORIZONTAL);
        seasonStrip.setGravity(Gravity.CENTER_VERTICAL);
        seasonStrip.setVisibility(View.GONE);
        content.addView(seasonStrip, rowParams(0, 12));

        gridView = new GridView(context);
        gridView.setAdapter(adapter);
        gridView.setFocusable(true);
        gridView.setNumColumns(GridView.AUTO_FIT);
        gridView.setColumnWidth(dp(158));
        gridView.setHorizontalSpacing(dp(20));
        gridView.setVerticalSpacing(dp(28));
        gridView.setStretchMode(GridView.NO_STRETCH);
        gridView.setCacheColorHint(Color.TRANSPARENT);
        gridView.setBackgroundColor(Color.TRANSPARENT);
        // 焦点框画在内容之上，且上下留出内边距，避免第一/最后一行被裁掉。
        gridView.setDrawSelectorOnTop(true);
        gridView.setClipToPadding(false);
        gridView.setPadding(0, dp(6), 0, dp(8));
        gridView.setSelector(FnosTheme.glassOutlineTopRounded(context, 12, 2));
        gridView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                int position = gridView.getSelectedItemPosition();
                if (position < 0 && adapter.getCount() > 0) {
                    // GridView 刚获焦时尚未选中任何格子，确认键应作用于第一项。
                    position = 0;
                }
                boolean valid = position >= 0 && position < adapter.getCount();
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    // GridView 默认不 clickable，D-pad 确认键不会触发 onItemClick，这里显式处理。
                    if (valid) {
                        gridView.setSelection(position);
                        listener.onFileEntrySelected(adapter.getItem(position));
                        return true;
                    }
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_MENU && valid) {
                    FnosFileEntry entry = adapter.getItem(position);
                    if (!entry.directory) {
                        listener.onFileFavoriteToggled(entry);
                        adapter.notifyDataSetChanged();
                        return true;
                    }
                }
                return false;
            }
        });
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View clicked, int position, long id) {
                FnosFileEntry item = adapter.getItem(position);
                listener.onFileEntrySelected(item);
            }
        });

        emptyView = new TextView(context);
        emptyView.setText("暂无内容");
        emptyView.setTextColor(FnosTheme.COLOR_TEXT_MUTED);
        emptyView.setTextSize(18);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        gridView.setEmptyView(emptyView);
        content.addView(gridView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        content.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        return content;
    }

    /** 电视剧 / 季页 hero 头部：左侧海报，右侧标题 + 信息行 + 简介。 */
    private View seriesHeaderView() {
        seriesHeaderPanel = new LinearLayout(context);
        seriesHeaderPanel.setOrientation(LinearLayout.HORIZONTAL);
        seriesHeaderPanel.setGravity(Gravity.TOP);
        seriesHeaderPanel.setVisibility(View.GONE);
        seriesHeaderPanel.setPadding(0, dp(6), 0, dp(14));

        seriesPosterFrame = new RoundedFrameLayout(context, 10);
        seriesPosterFrame.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, context));
        seriesPosterImage = new ImageView(context);
        seriesPosterImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seriesPosterImage.setVisibility(View.GONE);
        seriesPosterFrame.addView(seriesPosterImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        seriesPosterFallback = new TextView(context);
        seriesPosterFallback.setTextColor(FnosTheme.COLOR_TEXT_60);
        seriesPosterFallback.setTextSize(14);
        seriesPosterFallback.setGravity(Gravity.CENTER);
        seriesPosterFallback.setPadding(dp(10), dp(10), dp(10), dp(10));
        seriesPosterFrame.addView(seriesPosterFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        seriesHeaderPanel.addView(seriesPosterFrame, new LinearLayout.LayoutParams(dp(116), dp(174)));

        LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.TOP);

        seriesTitleView = new TextView(context);
        seriesTitleView.setTextColor(FnosTheme.COLOR_TEXT);
        seriesTitleView.setTextSize(26);
        seriesTitleView.setTypeface(null, 1);
        seriesTitleView.setSingleLine(true);
        seriesTitleView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(seriesTitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seriesMetaView = new TextView(context);
        seriesMetaView.setTextColor(FnosTheme.COLOR_TEXT_60);
        seriesMetaView.setTextSize(14);
        seriesMetaView.setSingleLine(true);
        seriesMetaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(6);
        textBlock.addView(seriesMetaView, metaParams);

        seriesOverviewView = new TextView(context);
        seriesOverviewView.setTextColor(FnosTheme.COLOR_TEXT_60);
        seriesOverviewView.setTextSize(14);
        seriesOverviewView.setMaxLines(4);
        seriesOverviewView.setEllipsize(TextUtils.TruncateAt.END);
        seriesOverviewView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overviewParams.topMargin = dp(10);
        textBlock.addView(seriesOverviewView, overviewParams);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = dp(24);
        seriesHeaderPanel.addView(textBlock, textParams);
        return seriesHeaderPanel;
    }

    private View navItem(String iconType, String label, final String action) {
        return navItem(iconType, label, action, null);
    }

    private View navItem(String iconType, String label, final String action, TextView countView) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SIDEBAR, 10, context));

        final FnosSidebarIconView iconView = new FnosSidebarIconView(context, iconType);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(22), dp(22)));

        final TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(FnosTheme.COLOR_TEXT_60);
        text.setTextSize(15);
        text.setPadding(dp(10), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (countView != null) {
            row.addView(countView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final NavItem item = new NavItem(row, text, iconView, action);
        navItems.put(action, item);
        applyNavStyle(item, false);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyNavStyle(item, hasFocus);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onBrowserAction(action);
            }
        });
        return row;
    }

    private void applyNavStyle(NavItem item, boolean focused) {
        boolean active = item.action.equals(activeNavAction);
        if (focused) {
            item.row.setBackgroundDrawable(FocusStyler.glowBackground(context, FnosTheme.COLOR_CARD, 10));
            item.text.setTextColor(FnosTheme.COLOR_TEXT);
            item.icon.setIconColor(FnosTheme.COLOR_TEXT);
        } else if (active) {
            item.row.setBackgroundDrawable(FnosTheme.rounded(0x1A2F7CFF, 10, context));
            item.text.setTextColor(FnosTheme.COLOR_PRIMARY);
            item.icon.setIconColor(FnosTheme.COLOR_PRIMARY);
        } else {
            item.row.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SIDEBAR, 10, context));
            item.text.setTextColor(FnosTheme.COLOR_TEXT_60);
            item.icon.setIconColor(FnosTheme.COLOR_TEXT_60);
        }
    }

    /** 当前栏目高亮（首页侧栏与列表页侧栏同步）。 */
    public void setActiveAction(String action) {
        activeNavAction = action == null ? "" : action;
        for (NavItem item : navItems.values()) {
            applyNavStyle(item, item.row.hasFocus());
        }
    }

    private TextView section(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(FnosTheme.COLOR_TEXT_MUTED);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(4), 0, 0, 0);
        return view;
    }

    private TextView chip(String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(0xFFEDEDED);
        chip.setTextSize(16);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(24), 0, dp(24), 0);
        chip.setFocusable(true);
        chip.setClickable(true);
        paintWebChip(chip);
        chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                paintWebChip((TextView) v);
            }
        });
        return chip;
    }

    /** 工具条胶囊被点开后的选项回调。 */
    private interface ChipPick {
        void onPick(int index);
    }

    /**
     * 网页端工具条的下拉菜单：点一下胶囊，弹出选项列表，选中即生效。
     * （原来是点一次换下一个，容易错过想要的布局。）
     */
    private void showChipMenu(final View anchor, String[] items, final int selectedIndex, final ChipPick pick) {
        final android.widget.PopupWindow popup = new android.widget.PopupWindow(context);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF26262A);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x33FFFFFF);
        box.setBackgroundDrawable(bg);
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                final int index = i;
                boolean selected = i == selectedIndex;
                TextView row = new TextView(context);
                row.setText(items[i]);
                row.setTextSize(15);
                row.setSingleLine(true);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), 0, dp(18), 0);
                row.setTextColor(selected ? 0xFF4C92FF : 0xFFEDEDED);
                android.graphics.drawable.GradientDrawable rowBg = new android.graphics.drawable.GradientDrawable();
                rowBg.setColor(selected ? 0x1F4C92FF : 0x00000000);
                rowBg.setCornerRadius(dp(10));
                row.setBackgroundDrawable(rowBg);
                row.setFocusable(true);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        popup.dismiss();
                        pick.onPick(index);
                    }
                });
                box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            }
        }
        int width = dp(200);
        popup.setContentView(box);
        popup.setWidth(width);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        popup.showAsDropDown(anchor, anchor.getWidth() - width, dp(6));
    }

    /** 网页端工具条胶囊：深灰实底 + 1dp 细边，聚焦时描边转蓝、文字提亮。 */
    private void paintWebChip(TextView chip) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF2A2A2C);
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), chip.isFocused() ? 0xFF4C92FF : 0x47FFFFFF);
        chip.setBackgroundDrawable(bg);
        chip.setTextColor(chip.isSelected() || chip.isFocused() ? 0xFFFFFFFF : 0xFFEDEDED);
    }

    private String sortLabel() {
        for (int i = 0; i < sortColumns.length; i++) {
            if (sortColumns[i].equals(sortColumn)) {
                return sortLabels[i];
            }
        }
        return sortLabels[0];
    }

    private String sortTypeLabel() {
        return "ASC".equals(sortType) ? "升序" : "降序";
    }

    private void updateToolbarLabels() {
        if (filterChip != null) {
            filterChip.setText("筛选：" + TYPE_FILTER_LABELS[typeFilterIndex]);
        }
        if (sortChip != null) {
            sortChip.setText(sortLabel());
        }
        if (sortTypeChip != null) {
            sortTypeChip.setText(sortTypeLabel());
        }
        if (viewTypeChip != null) {
            viewTypeChip.setText(VIEW_TYPE_LABELS[viewType]);
        }
    }

    private void updateFavoriteTabStyles() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tab = tabViews.get(i);
            if (tab == null) {
                continue;
            }
            boolean selected = i == favoriteTabIndex;
            tab.setTextColor(selected ? FnosTheme.COLOR_TEXT : FnosTheme.COLOR_TEXT_60);
            tab.setTypeface(null, selected ? 1 : 0);
            tab.setSelected(selected);
            paintWebChip(tab);
        }
    }

    /**
     * 应用网页端布局：列表 / 横幅海报 / 竖幅海报 / 海报墙。
     * 列表用 1 列整行，卡片按布局切换形状。
     */
    private void applyViewType() {
        if (gridView == null) {
            return;
        }
        if (episodeMode) {
            // 季页剧集卡片：网页端固定 260×146（16:9）。电视上按可用宽度等分放大，
            // 既保持 16:9，也不在右侧留下大片空白。
            int available = contentWidthPx();
            int gap = dp(20);
            int minCard = dp(260);
            int columns = Math.max(1, (available + gap) / (minCard + gap));
            int card = columns <= 1
                    ? Math.max(minCard, available)
                    : Math.max(minCard, (available - gap * (columns - 1)) / columns);
            episodeCardWidth = card;
            gridView.setNumColumns(columns);
            gridView.setColumnWidth(card);
            gridView.setHorizontalSpacing(gap);
            gridView.setVerticalSpacing(dp(24));
            gridView.setStretchMode(GridView.NO_STRETCH);
            adapter.notifyDataSetChanged();
            if (gridView.getCount() > 0) {
                gridView.setSelection(0);
            }
            return;
        }
        int cardWidthDp;
        int horizontalDp;
        int verticalDp;
        switch (viewType) {
            case VIEW_LIST:
                cardWidthDp = 0;
                horizontalDp = 0;
                verticalDp = 0;
                break;
            case VIEW_POSTER_HORIZONTAL:
                cardWidthDp = 243;
                horizontalDp = 18;
                verticalDp = 20;
                break;
            case VIEW_POSTER_WALL:
                cardWidthDp = 104;
                horizontalDp = 10;
                verticalDp = 12;
                break;
            case VIEW_POSTER_VERTICAL:
            default:
                cardWidthDp = 158;
                horizontalDp = 20;
                verticalDp = 28;
                break;
        }
        if (viewType == VIEW_POSTER_HORIZONTAL) {
            // 横幅海报：卡片随列宽铺满，右边不留空
            int available = contentWidthPx();
            int spacing = dp(horizontalDp);
            int minCard = dp(200);
            int columns = Math.max(1, (available + spacing) / (minCard + spacing));
            int card = columns <= 1 ? available : (available - spacing * (columns - 1)) / columns;
            horizontalCardWidth = card;
            gridView.setNumColumns(columns);
            gridView.setColumnWidth(card);
            gridView.setHorizontalSpacing(spacing);
            gridView.setVerticalSpacing(dp(verticalDp));
            gridView.setStretchMode(GridView.NO_STRETCH);
            adapter.notifyDataSetChanged();
            if (gridView.getCount() > 0) {
                gridView.setSelection(0);
            }
            return;
        }
        if (viewType == VIEW_LIST) {
            gridView.setNumColumns(1);
        } else {
            int available = contentWidthPx();
            int card = dp(cardWidthDp);
            int spacing = dp(horizontalDp);
            int columns = (available + spacing) / Math.max(1, card + spacing);
            gridView.setNumColumns(Math.max(1, columns));
        }
        gridView.setColumnWidth(dp(cardWidthDp == 0 ? 400 : cardWidthDp));
        gridView.setHorizontalSpacing(dp(horizontalDp));
        gridView.setVerticalSpacing(dp(verticalDp));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter.notifyDataSetChanged();
        if (gridView.getCount() > 0) {
            gridView.setSelection(0);
        }
    }

    /** 列表可用宽度（去掉侧栏与内边距），用来按屏幕宽度算列数。 */
    private int contentWidthPx() {
        if (gridView != null && gridView.getWidth() > 0) {
            return gridView.getWidth();
        }
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(320), screen - dp(364));
    }

    /** 当前布局对应的卡片形状。 */
    private int cardShape() {
        if (episodeMode) {
            return FnosMediaCardView.SHAPE_EPISODE;
        }
        switch (viewType) {
            case VIEW_LIST:
                return FnosMediaCardView.SHAPE_LIST;
            case VIEW_POSTER_HORIZONTAL:
                return FnosMediaCardView.SHAPE_LIBRARY;
            case VIEW_POSTER_WALL:
                return FnosMediaCardView.SHAPE_WALL;
            case VIEW_POSTER_VERTICAL:
            default:
                return FnosMediaCardView.SHAPE_POSTER;
        }
    }

    /** 网页端收藏页：显示 6 个收藏分类标签。 */
    public void setMode(int newMode) {
        mode = newMode;
        if (tabStrip != null) {
            tabStrip.setVisibility(mode == MODE_FAVORITES ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 按网页端页面类型切换默认布局与排序选项。
     *
     * <p>网页端文件夹页（{@code /folder/:guid}）默认是「列表」布局——里面是
     * 一长串视频文件名，海报墙显示效果很差；排序下拉也只有「标题」「添加日期」
     * 两项。其余列表页默认竖幅海报，排序下拉是完整的一套。
     * 用户在工具条上手动切过的布局会被记住，切回同类页面时还原。
     *
     * @param folderContext true = 网页端文件夹页
     */
    public void applyPageChrome(boolean folderContext) {
        // 用户手动切过布局后，不再按页面类型自动改布局，否则切页/刷新会把选择冲掉
        if (userViewTypeFixed) {
            updateToolbarLabels();
            return;
        }
        if (folderContext) {
            sortColumns = FOLDER_SORT_COLUMNS;
            sortLabels = FOLDER_SORT_LABELS;
        } else {
            sortColumns = SORT_COLUMNS;
            sortLabels = SORT_LABELS;
        }
        if (viewType != VIEW_POSTER_HORIZONTAL) {
            setViewType(VIEW_POSTER_HORIZONTAL);
        }
        updateToolbarLabels();
    }

    /** 设置当前布局并刷新。 */
    public void setViewType(int newViewType) {
        if (newViewType < 0 || newViewType >= VIEW_TYPE_LABELS.length) {
            return;
        }
        viewType = newViewType;
        if (viewTypeChip != null) {
            viewTypeChip.setText(VIEW_TYPE_LABELS[viewType]);
        }
        applyViewType();
    }

    /**
     * Sync the toolbar filter chip with the app state. The sidebar category and
     * the toolbar filter are the same dimension on the web, so navigating to a
     * category has to move the chip (otherwise the chip shows a stale filter).
     */
    public void setTypeFilter(String category) {
        if (category == null || category.length() == 0) {
            return;
        }
        for (int i = 0; i < TYPE_FILTERS.length; i++) {
            if (TYPE_FILTERS[i].equals(category)) {
                typeFilterIndex = i;
                break;
            }
        }
        updateToolbarLabels();
    }

    /**
     * The filter chip only applies to screens backed by item/list. The favorite
     * page has its own category tabs, and search / person / recent come from
     * endpoints without a type filter, so the chip is hidden there.
     */
    public void setFilterChipVisible(boolean visible) {
        if (filterChip != null) {
            filterChip.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 供 MainActivity 在重新加载后回填当前排序状态。 */
    public void setSortState(String column, String type) {
        if (column != null && column.length() > 0) {
            sortColumn = column;
        }
        if (type != null && type.length() > 0) {
            sortType = type;
        }
        updateToolbarLabels();
    }

    private String subtitleText(String subtitle) {
        if (subtitle == null || subtitle.length() == 0) {
            return "";
        }
        return "  |  " + subtitle;
    }

    private String emptyText(String title, String subtitle) {
        String value = subtitle == null || subtitle.length() == 0 ? "暂无内容" : subtitle;
        String prefix = title == null || title.length() == 0 ? "" : title + "\n";
        return prefix + value;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.rightMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams rowHeightParams(int top, int bottom, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(height));
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams navParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40));
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams rowParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }

    private final class EntryAdapter extends BaseAdapter {
        private final List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();

        void setEntries(List<FnosFileEntry> values, boolean sortEntries) {
            entries.clear();
            if (values != null) {
                entries.addAll(values);
                if (sortEntries) {
                    Collections.sort(entries, new Comparator<FnosFileEntry>() {
                        @Override
                        public int compare(FnosFileEntry left, FnosFileEntry right) {
                            if (left.directory != right.directory) {
                                return left.directory ? -1 : 1;
                            }
                            if (left.isVideo() != right.isVideo()) {
                                return left.isVideo() ? -1 : 1;
                            }
                            return left.name.compareToIgnoreCase(right.name);
                        }
                    });
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public FnosFileEntry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FnosMediaCardView card = convertView instanceof FnosMediaCardView
                    && ((FnosMediaCardView) convertView).shape() == cardShape()
                    ? (FnosMediaCardView) convertView
                    : cardView();
            // 复用卡片也要重新下发 posterLoader/posterBaseUrl（换服务器后 baseUrl 会变）。
            card.configure(posterLoader, posterBaseUrl);
            if (episodeMode) {
                card.setEpisodePosterWidth(episodeCardWidth);
            } else if (viewType == VIEW_POSTER_HORIZONTAL && horizontalCardWidth > 0) {
                card.setLibraryPosterWidth(horizontalCardWidth);
            }
            FnosFileEntry entry = getItem(position);
            card.bindEntry(entry, listener.isFileFavorite(entry));
            card.setContentDescription(entry.name);
            return card;
        }

        private FnosMediaCardView cardView() {
            FnosMediaCardView card = new FnosMediaCardView(context, cardShape());
            card.configure(posterLoader, posterBaseUrl);
            // 交给 GridView 处理点击/选中：卡片自身可点会吞掉按键与触摸事件。
            card.setFocusable(false);
            card.setClickable(false);
            return card;
        }
    }

}
