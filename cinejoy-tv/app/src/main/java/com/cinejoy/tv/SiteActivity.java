package com.cinejoy.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.util.Locale;

public class SiteActivity extends Activity {
    public static final String EXTRA_URL="url", EXTRA_TITLE="title";
    private WebView web; private FrameLayout root; private View full; private WebChromeClient.CustomViewCallback cb;
    @Override protected void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.setFocusable(true);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setup();String u=getIntent().getStringExtra(EXTRA_URL);web.loadUrl(u==null?"https://cinejoy.to/":u);}
    private void setup(){WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setUseWideViewPort(true);s.setCacheMode(WebSettings.LOAD_DEFAULT);s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);s.setUserAgentString(s.getUserAgentString()+" CineJoyTV/2.0 AndroidTV");CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){String u=r.getUrl().toString();if(media(u)){play(u);return true;}return false;}@Override public void onPageFinished(WebView v,String u){CookieManager.getInstance().flush();inject(v);}});
        web.setWebChromeClient(new WebChromeClient(){@Override public void onShowCustomView(View v,CustomViewCallback c){if(full!=null){c.onCustomViewHidden();return;}full=v;cb=c;web.setVisibility(View.GONE);root.addView(v,new FrameLayout.LayoutParams(-1,-1));v.requestFocus();hide();}@Override public void onHideCustomView(){exitFull();}});hide();}
    private void inject(WebView v){String js="(function(){if(window.__cj2)return;window.__cj2=1;var s=document.createElement('style');s.textContent='*:focus{outline:5px solid white!important;outline-offset:5px!important;border-radius:12px!important;transform:scale(1.04)!important;z-index:99999!important}html{scroll-behavior:smooth!important}::-webkit-scrollbar{display:none!important}';document.head.appendChild(s);var els=[...document.querySelectorAll('a[href],button,[role=button],input,[tabindex]:not([tabindex=\\\"-1\\\"])')].filter(e=>e.offsetWidth&&e.offsetHeight);els.forEach((e,i)=>e.tabIndex=i+1);if(els[0])els[0].focus();})();";v.evaluateJavascript(js,null);}
    private boolean media(String u){u=u.toLowerCase(Locale.US);return u.contains(".m3u8")||u.contains(".mpd")||u.matches(".*\\.(mp4|m4v|webm)(\\?.*)?$");}
    private void play(String u){Intent i=new Intent(this,PlayerActivity.class);i.putExtra(PlayerActivity.EXTRA_URL,u);String c=CookieManager.getInstance().getCookie(u);if(c!=null)i.putExtra(PlayerActivity.EXTRA_COOKIE,c);i.putExtra(PlayerActivity.EXTRA_USER_AGENT,web.getSettings().getUserAgentString());startActivity(i);}
    private void hide(){getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    private void exitFull(){if(full==null)return;root.removeView(full);full=null;web.setVisibility(View.VISIBLE);web.requestFocus();if(cb!=null){cb.onCustomViewHidden();cb=null;}hide();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(full!=null){exitFull();return true;}if(web.canGoBack()){web.goBack();return true;}}return super.dispatchKeyEvent(e);}
    @Override protected void onResume(){super.onResume();web.onResume();hide();}
    @Override protected void onPause(){CookieManager.getInstance().flush();web.onPause();super.onPause();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
