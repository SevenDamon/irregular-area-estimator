package cn.codex.irregulararea;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final int PICK_PHOTO = 1001;
    private static final int TAKE_PHOTO = 1002;
    private static final int CAMERA_PERMISSION = 1003;
    private static final int CAMERA_CAPTURE_PERMISSION = 1004;
    private WebView webView;
    private ValueCallback<Uri[]> pendingFiles;
    private Uri pendingCameraUri;
    private PermissionRequest pendingWebCamera;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!"https".equals(url.getScheme()) || !HOST.equals(url.getHost())) return null;
                String path = url.getPath();
                if (path == null || !path.startsWith("/assets/")) return missing();
                path = path.substring("/assets/".length());
                if (path.isEmpty()) path = "index.html";
                if (path.contains("..") || path.contains("\\")) return missing();
                try {
                    InputStream stream = getAssets().open(path);
                    return new WebResourceResponse(mime(path), "UTF-8", stream);
                } catch (IOException error) {
                    return missing();
                }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                return !("https".equals(url.getScheme()) && HOST.equals(url.getHost()));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                        FileChooserParams params) {
                cancelPendingFiles();
                pendingFiles = callback;
                if (params.isCaptureEnabled()) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        openCamera();
                    else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CAPTURE_PERMISSION);
                }
                else openPicker();
                return true;
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                if (!HOME.startsWith(request.getOrigin().toString())) {
                    request.deny();
                    return;
                }
                boolean video = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) video = true;
                    else { request.deny(); return; }
                }
                if (!video) { request.deny(); return; }
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingWebCamera = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
                    }
                });
            }
        });
        webView.loadUrl(HOME);
    }

    private static WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "text/javascript";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".task") || path.endsWith(".tflite")) return "application/octet-stream";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webmanifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    private void openPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_PHOTO); }
        catch (ActivityNotFoundException error) {
            cancelPendingFiles();
            Toast.makeText(this, "没有找到可用的照片选择器", Toast.LENGTH_LONG).show();
        }
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "面积估测_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/面积估测器");
        pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingCameraUri == null) {
            cancelPendingFiles();
            Toast.makeText(this, "无法为照片创建存储位置", Toast.LENGTH_LONG).show();
            return;
        }
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(camera, TAKE_PHOTO); }
        catch (ActivityNotFoundException error) {
            getContentResolver().delete(pendingCameraUri, null, null);
            pendingCameraUri = null;
            cancelPendingFiles();
            Toast.makeText(this, "没有找到可用的相机应用", Toast.LENGTH_LONG).show();
        }
    }

    private void cancelPendingFiles() {
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles = null; }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTO && requestCode != TAKE_PHOTO) return;
        Uri answer = null;
        if (resultCode == RESULT_OK) {
            if (requestCode == TAKE_PHOTO) answer = pendingCameraUri;
            else if (data != null && data.getData() != null &&
                    "content".equals(data.getData().getScheme())) answer = data.getData();
        }
        if (requestCode == TAKE_PHOTO && answer == null && pendingCameraUri != null)
            getContentResolver().delete(pendingCameraUri, null, null);
        if (pendingFiles != null) pendingFiles.onReceiveValue(answer == null ? null : new Uri[]{answer});
        pendingFiles = null;
        pendingCameraUri = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_CAPTURE_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else {
                cancelPendingFiles();
                Toast.makeText(this, "需要相机权限才能拍照；也可以从相册选择", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != CAMERA_PERMISSION || pendingWebCamera == null) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
            pendingWebCamera.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        else pendingWebCamera.deny();
        pendingWebCamera = null;
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        cancelPendingFiles();
        webView.destroy();
        super.onDestroy();
    }
}
