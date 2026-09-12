package com.cinejoy.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://cinejoy.to/";

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private TextView statusText;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        buildUi();
        configureWebView();
        webView.loadUrl(HOME_URL);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 8, 10));
        setContentView(root);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 8, 10));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(webView, match());

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(32, 32, 32, 32);

        progress = new ProgressBar(this);
        loading.addView(progress, new LinearLayout.LayoutParams(72, 72));

        statusText = new TextView(this);
        statusText.setText("Opening CineJoy…");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18f);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = 20;
        loading.addView(statusText, textParams);

        root.addView(loading, match());
        loading.setTag("loading");
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " CineJoyTV/1.1 AndroidTV");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) s.setOffscreenPreRaster(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isDirectMedia(uri.toString())) {
                    openNativePlayer(uri.toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                hideLoading();
                CookieManager.getInstance().flush();
                injectTvEnhancements(view);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showLoading("Couldn’t load CineJoy. Press OK to retry.");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 85) showLoading("Loading " + newProgress + "%"); else hideLoading();
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, match());
                view.requestFocus();
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.requestFocus();
        hideSystemUi();
    }

    private void injectTvEnhancements(WebView view) {
        String js = "(function(){" +
                "if(window.__cinejoyTV)return;window.__cinejoyTV=true;" +
                "var st=document.createElement('style');st.textContent=`" +
                "*:focus{outline:4px solid #fff!important;outline-offset:4px!important;border-radius:10px!important;transform:scale(1.035);transition:transform .11s ease,outline .11s ease;z-index:9999!important;}" +
                "a,button,[role=button],input,select,textarea{scroll-margin:110px;}" +
                "html{scroll-behavior:smooth!important;}body{overscroll-behavior:none!important;}" +
                "::-webkit-scrollbar{width:0;height:0;}" +
                "`;document.head.appendChild(st);" +
                "function focusables(){return Array.from(document.querySelectorAll('a[href],button,[role=button],input,select,textarea,[tabindex]:not([tabindex=\"-1\"])')).filter(function(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0&&!e.disabled;});}" +
                "function nearest(dir){var c=document.activeElement;if(!c||c===document.body){var f=focusables();if(f[0])f[0].focus();return;}var cr=c.getBoundingClientRect(),cx=cr.left+cr.width/2,cy=cr.top+cr.height/2,b=null,bs=1e12;focusables().forEach(function(e){if(e===c)return;var r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,dx=x-cx,dy=y-cy;if(dir==='l'&&dx>=-4)return;if(dir==='r'&&dx<=4)return;if(dir==='u'&&dy>=-4)return;if(dir==='d'&&dy<=4)return;var primary=(dir==='l'||dir==='r')?Math.abs(dx):Math.abs(dy),cross=(dir==='l'||dir==='r')?Math.abs(dy):Math.abs(dx),score=primary+cross*2.4;if(score<bs){bs=score;b=e;}});if(b){b.focus({preventScroll:true});b.scrollIntoView({behavior:'smooth',block:'nearest',inline:'nearest'});}}" +
                "document.addEventListener('keydown',function(e){var k=e.key;if(k==='ArrowLeft'||k==='ArrowRight'||k==='ArrowUp'||k==='ArrowDown'){e.preventDefault();nearest(k==='ArrowLeft'?'l':k==='ArrowRight'?'r':k==='ArrowUp'?'u':'d');}else if((k==='Enter'||k===' ')&&document.activeElement&&document.activeElement.click){e.preventDefault();document.activeElement.click();}},true);" +
                "setTimeout(function(){if(document.activeElement===document.body){var f=focusables();if(f[0])f[0].focus();}},300);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private boolean isDirectMedia(String url) {
        String u = url.toLowerCase(Locale.US);
        return u.contains(".m3u8") || u.contains(".mpd") || u.matches(".*\\.(mp4|m4v|webm)(\\?.*)?$");
    }

    private void openNativePlayer(String url) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, url);
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) i.putExtra(PlayerActivity.EXTRA_COOKIE, cookie);
        i.putExtra(PlayerActivity.EXTRA_USER_AGENT, webView.getSettings().getUserAgentString());
        startActivity(i);
    }

    private void showLoading(String text) {
        View loading = root.findViewWithTag("loading");
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (statusText != null) statusText.setText(text);
    }

    private void hideLoading() {
        View loading = root.findViewWithTag("loading");
        if (loading != null) loading.setVisibility(View.GONE);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        hideSystemUi();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (fullscreenView != null) {
                    exitFullscreen();
                    return true;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
            }
            if ((event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && root.findViewWithTag("loading") != null && root.findViewWithTag("loading").getVisibility() == View.VISIBLE) {
                webView.reload();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
