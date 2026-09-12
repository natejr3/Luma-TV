package com.cinejoy.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String BASE = "https://cinejoy.to/";
    private FrameLayout root;
    private RecyclerView rows;
    private TextView status;
    private WebView source;
    private final List<Section> sections = new ArrayList<>();
    private final Map<String, TextView> navButtons = new LinkedHashMap<>();
    private String currentRoute = BASE;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        buildNativeUi();
        buildSourceWebView();
        loadRoute("Home", BASE);
    }

    private void buildNativeUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 8, 12));
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(42), dp(24), dp(28), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(68)));

        TextView logo = new TextView(this);
        logo.setText("CINEJOY");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(26);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(dp(200), -1);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(logo, lpLogo);

        addNav(top, "Home", BASE);
        addNav(top, "Movies", BASE + "movies");
        addNav(top, "Shows", BASE + "shows");
        addNav(top, "My List", BASE + "my-list");

        TextView spacer = new TextView(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView openSite = makeButton("Open site");
        openSite.setOnClickListener(v -> openSite(currentRoute, "CineJoy"));
        top.addView(openSite, new LinearLayout.LayoutParams(dp(145), dp(46)));

        status = new TextView(this);
        status.setTextColor(Color.rgb(180, 184, 194));
        status.setTextSize(15);
        status.setText("Loading CineJoy…");
        status.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(status, new LinearLayout.LayoutParams(-1, dp(42)));

        rows = new RecyclerView(this);
        rows.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        rows.setItemViewCacheSize(10);
        rows.setHasFixedSize(false);
        rows.setClipToPadding(false);
        rows.setPadding(0, 0, 0, dp(40));
        rows.setAdapter(new SectionAdapter());
        page.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1));
        hideSystemUi();
    }

    private void addNav(LinearLayout bar, String label, String url) {
        TextView b = makeButton(label);
        b.setOnClickListener(v -> loadRoute(label, url));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(label.equals("My List") ? 135 : 120), dp(46));
        p.rightMargin = dp(10);
        bar.addView(b, p);
        navButtons.put(label, b);
    }

    private TextView makeButton(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.rgb(220, 223, 230));
        v.setTextSize(16);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(rounded(Color.rgb(24, 26, 33), Color.TRANSPARENT, 0));
        v.setOnFocusChangeListener((x, has) -> {
            x.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(110).start();
            x.setBackground(rounded(has ? Color.rgb(245,245,248) : Color.rgb(24,26,33), has ? Color.WHITE : Color.TRANSPARENT, has ? 2 : 0));
            ((TextView)x).setTextColor(has ? Color.BLACK : Color.rgb(220,223,230));
            x.setTranslationZ(has ? dp(12) : 0);
        });
        return v;
    }

    private void buildSourceWebView() {
        source = new WebView(this);
        source.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(2, 2);
        p.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        root.addView(source, p);
        WebSettings s = source.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " CineJoyTV/2.0 AndroidTV");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(source, true);
        source.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                currentRoute = url;
                scrapeCatalog();
            }
            @Override public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                status.setText("Couldn’t refresh catalog. Press Open site for fallback.");
            }
        });
    }

    private void loadRoute(String label, String url) {
        status.setText("Loading " + label + "…");
        for (Map.Entry<String,TextView> e : navButtons.entrySet()) {
            boolean active = e.getKey().equals(label);
            e.getValue().setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        source.loadUrl(url);
    }

    private void scrapeCatalog() {
        String js = "(function(){try{" +
                "var out=[],seen=new Set();" +
                "var links=[...document.querySelectorAll('a[href]')];" +
                "for(var i=0;i<links.length&&out.length<180;i++){var a=links[i],img=a.querySelector('img');if(!img)continue;" +
                "var href=a.href||'';if(!href||seen.has(href))continue;" +
                "var title=(img.alt||a.getAttribute('aria-label')||a.textContent||'').trim().replace(/\\s+/g,' ');if(title.length<1)continue;" +
                "var sec=a.closest('section');var h=sec&&sec.querySelector('h1,h2,h3,h4');var section=h?h.textContent.trim():'Featured';" +
                "var src=img.currentSrc||img.src||img.getAttribute('data-src')||'';" +
                "seen.add(href);out.push({section:section||'Featured',title:title,url:href,img:src});}" +
                "return JSON.stringify(out);}catch(e){return '[]';}})();";
        source.evaluateJavascript(js, value -> {
            try {
                String decoded = value;
                if (decoded != null && decoded.length() >= 2 && decoded.startsWith("\"") && decoded.endsWith("\"")) {
                    decoded = new JSONArray("[" + decoded + "]").getString(0);
                }
                JSONArray arr = new JSONArray(decoded == null ? "[]" : decoded);
                LinkedHashMap<String,Section> map = new LinkedHashMap<>();
                for (int i=0;i<arr.length();i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String name = clean(o.optString("section","Featured"));
                    if (name.length() > 42) name = "Featured";
                    Section sec = map.get(name);
                    if (sec == null) { sec = new Section(name); map.put(name, sec); }
                    if (sec.items.size() < 30) sec.items.add(new Item(clean(o.optString("title")), o.optString("url"), o.optString("img")));
                }
                sections.clear();
                for (Section s : map.values()) if (!s.items.isEmpty()) sections.add(s);
                rows.getAdapter().notifyDataSetChanged();
                status.setText(sections.isEmpty() ? "Catalog loaded empty — Open site is available." : sections.size()+" rows • Native TV mode");
                if (!sections.isEmpty()) rows.post(() -> rows.requestFocus());
            } catch (Exception e) {
                status.setText("Catalog parsing failed — Open site is available.");
            }
        });
    }

    private String clean(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }

    private void openSite(String url, String title) {
        Intent i = new Intent(this, SiteActivity.class);
        i.putExtra(SiteActivity.EXTRA_URL, url);
        i.putExtra(SiteActivity.EXTRA_TITLE, title);
        startActivity(i);
    }

    private GradientDrawable rounded(int fill, int stroke, int width) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(14));
        if (width > 0) g.setStroke(dp(width), stroke);
        return g;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private void hideSystemUi() { getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); }

    static class Item { String title,url,img; Item(String t,String u,String i){title=t;url=u;img=i;} }
    static class Section { String name; List<Item> items=new ArrayList<>(); Section(String n){name=n;} }

    class SectionAdapter extends RecyclerView.Adapter<SectionVH> {
        @Override public SectionVH onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout box=new LinearLayout(MainActivity.this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(8),0,dp(18));
            TextView title=new TextView(MainActivity.this);title.setTextColor(Color.WHITE);title.setTextSize(21);title.setTypeface(Typeface.DEFAULT_BOLD);title.setPadding(dp(4),0,0,dp(10));box.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
            RecyclerView rail=new RecyclerView(MainActivity.this);rail.setLayoutManager(new LinearLayoutManager(MainActivity.this,RecyclerView.HORIZONTAL,false));rail.setClipToPadding(false);rail.setItemViewCacheSize(16);rail.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);box.addView(rail,new LinearLayout.LayoutParams(-1,dp(270)));
            return new SectionVH(box,title,rail);
        }
        @Override public void onBindViewHolder(SectionVH h,int pos){Section s=sections.get(pos);h.title.setText(s.name);h.rail.setAdapter(new CardAdapter(s.items));}
        @Override public int getItemCount(){return sections.size();}
    }

    static class SectionVH extends RecyclerView.ViewHolder { TextView title;RecyclerView rail;SectionVH(View v,TextView t,RecyclerView r){super(v);title=t;rail=r;} }

    class CardAdapter extends RecyclerView.Adapter<CardVH> {
        final List<Item> data; CardAdapter(List<Item> d){data=d;}
        @Override public CardVH onCreateViewHolder(ViewGroup p,int type){
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setFocusable(true);card.setClickable(true);card.setPadding(dp(5),dp(5),dp(5),dp(5));card.setBackground(rounded(Color.rgb(17,18,23),Color.TRANSPARENT,0));
            ImageView img=new ImageView(MainActivity.this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);img.setBackgroundColor(Color.rgb(28,29,36));card.addView(img,new LinearLayout.LayoutParams(dp(168),dp(212)));
            TextView title=new TextView(MainActivity.this);title.setTextColor(Color.WHITE);title.setTextSize(15);title.setMaxLines(2);title.setGravity(Gravity.CENTER_VERTICAL);card.addView(title,new LinearLayout.LayoutParams(dp(168),dp(48)));
            RecyclerView.LayoutParams rp=new RecyclerView.LayoutParams(dp(190),dp(270));rp.rightMargin=dp(14);card.setLayoutParams(rp);
            card.setOnFocusChangeListener((v,has)->{v.animate().scaleX(has?1.09f:1f).scaleY(has?1.09f:1f).setDuration(115).start();v.setTranslationZ(has?dp(18):0);v.setBackground(rounded(has?Color.rgb(42,44,54):Color.rgb(17,18,23),has?Color.WHITE:Color.TRANSPARENT,has?3:0));});
            return new CardVH(card,img,title);
        }
        @Override public void onBindViewHolder(CardVH h,int pos){Item x=data.get(pos);h.title.setText(x.title);Glide.with(MainActivity.this).load(x.img).centerCrop().into(h.img);h.itemView.setOnClickListener(v->openSite(x.url,x.title));}
        @Override public int getItemCount(){return data.size();}
    }

    static class CardVH extends RecyclerView.ViewHolder {ImageView img;TextView title;CardVH(View v,ImageView i,TextView t){super(v);img=i;title=t;}}
}
