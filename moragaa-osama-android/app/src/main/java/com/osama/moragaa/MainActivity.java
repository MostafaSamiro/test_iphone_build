package com.osama.moragaa;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.content.ContentResolver;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public final class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/index.html";
    private static final int FILE_REQUEST = 1001;
    private WebView webView;
    private ValueCallback<Uri[]> uploadCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PDFBoxResourceLoader.init(getApplicationContext());
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11,16,32));
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Object(){
            @JavascriptInterface public void extractPdf(String uriText){
                new Thread(() -> {
                    String result="", error="";
                    try {
                        Uri uri=Uri.parse(uriText);
                        try(InputStream stream=getContentResolver().openInputStream(uri)){
                            if(stream==null)throw new Exception("تعذر فتح الملف");
                            try(PDDocument doc=PDDocument.load(stream)){
                                PDFTextStripper stripper=new PDFTextStripper();
                                result=stripper.getText(doc);
                                if(result.trim().isEmpty())error="ملف PDF ممسوح ضوئيًا ولا يحتوي على نص قابل للاستخراج. يحتاج OCR.";
                            }
                        }
                    }catch(Exception ex){error="تعذر قراءة PDF: "+ex.getMessage();}
                    final String payload=JSONObject.quote(result);
                    final String failure=JSONObject.quote(error);
                    mainHandler.post(()->{if(webView!=null)webView.evaluateJavascript("window.onPdfExtracted("+payload+","+failure+")",null);});
                }).start();
            }
        },"NativePdf");
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                          FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                    return true;
                } catch (Exception error) {
                    uploadCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });
        if (state == null) webView.loadUrl(HOME);
        else webView.restoreState(state);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            if(resultCode==RESULT_OK && data!=null){
                Uri chosen=data.getData();
                if(chosen==null && data.getClipData()!=null && data.getClipData().getItemCount()>0)chosen=data.getClipData().getItemAt(0).getUri();
                if(chosen!=null){
                    final Uri fileUri=chosen;
                    String mime=getContentResolver().getType(fileUri);
                    String displayName=""; try(Cursor cursor=getContentResolver().query(fileUri,null,null,null,null)){if(cursor!=null && cursor.moveToFirst()){int ix=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ix>=0)displayName=cursor.getString(ix);}}catch(Exception ignored){}\n                    if("application/pdf".equalsIgnoreCase(mime)||displayName.toLowerCase().endsWith(".pdf")||fileUri.toString().toLowerCase().endsWith(".pdf")){
                        webView.evaluateJavascript("window.nativeSelectedPdf("+JSONObject.quote(fileUri.toString())+")",null);
                    }
                }
            }
            uploadCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
