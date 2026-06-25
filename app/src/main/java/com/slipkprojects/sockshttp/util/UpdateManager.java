package com.slipkprojects.sockshttp.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.slipkprojects.sockshttp.R;
import com.slipkprojects.ultrasshservice.config.Settings;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    public static final String DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/JuandeMx/HTTP-LTM/main/update.json";

    public static void checkUpdate(final Context context, final boolean manual) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        final AlertDialog[] searchDialog = new AlertDialog[1];
        if (manual) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    searchDialog[0] = new AlertDialog.Builder(context)
                        .setTitle(R.string.attention)
                        .setMessage(R.string.checking_updates)
                        .setCancelable(false)
                        .create();
                    searchDialog[0].show();
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Settings config = new Settings(context);
                    String updateUrl = config.getPrefsPrivate().getString("update_url", DEFAULT_UPDATE_URL);
                    if (updateUrl.isEmpty()) {
                        updateUrl = DEFAULT_UPDATE_URL;
                    }
                    
                    if (updateUrl.contains("?")) {
                        updateUrl += "&t=" + System.currentTimeMillis();
                    } else {
                        updateUrl += "?t=" + System.currentTimeMillis();
                    }

                    java.net.Proxy proxy = java.net.Proxy.NO_PROXY;
                    if (com.slipkprojects.ultrasshservice.logger.SkStatus.isTunnelActive()) {
                        int socksPort = 1080;
                        try {
                            socksPort = Integer.parseInt(config.getPrivString(Settings.PORTA_LOCAL_KEY));
                        } catch (Exception e) {}
                        proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress.createUnresolved("127.0.0.1", socksPort));
                    }

                    HttpURLConnection conn = (HttpURLConnection) new URL(updateUrl).openConnection(proxy);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    
                    int status = conn.getResponseCode();
                    int redirectCount = 0;
                    while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) && redirectCount < 5) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
                        status = conn.getResponseCode();
                        redirectCount++;
                    }

                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new Exception("HTTP error code: " + status);
                    }

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    conn.disconnect();

                    String jsonStr = out.toString("UTF-8");
                    JSONObject json = new JSONObject(jsonStr);
                    final int newVersionCode = json.getInt("versionCode");
                    final String newVersionName = json.getString("versionName");
                    final String apkUrl = json.getString("apkUrl");
                    final String changelog = json.optString("changelog", "Sin registro de cambios.");

                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    final int currentVersionCode = pInfo.versionCode;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (manual && searchDialog[0] != null) {
                                searchDialog[0].dismiss();
                            }

                            if (newVersionCode > currentVersionCode) {
                                showUpdateAvailableDialog(context, newVersionName, changelog, apkUrl);
                            } else {
                                if (manual) {
                                    Toast.makeText(context, R.string.app_is_up_to_date, Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Error checking update", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (manual && searchDialog[0] != null) {
                                searchDialog[0].dismiss();
                            }
                            if (manual) {
                                Toast.makeText(context, "Error al buscar actualización: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private static void showUpdateAvailableDialog(final Context context, String versionName, String changelog, final String apkUrl) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(R.string.update_available)
            .setMessage(context.getString(R.string.update_dialog_message, versionName, changelog))
            .setCancelable(true)
            .setPositiveButton("Actualizar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!context.getPackageManager().canRequestPackageInstalls()) {
                            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                            if (!(context instanceof android.app.Activity)) {
                                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            }
                            try {
                                context.startActivity(settingsIntent);
                                Toast.makeText(context, "Por favor, permite la instalación de fuentes desconocidas para esta aplicación y vuelve a intentarlo.", Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Log.e(TAG, "Error opening settings", e);
                            }
                            return;
                        }
                    }
                    downloadAndInstallApk(context, apkUrl);
                }
            })
            .setNegativeButton("Más tarde", null)
            .create();
        dialog.show();
    }

    private static void downloadAndInstallApk(final Context context, final String apkUrl) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        // Build progress dialog programmatically to keep code self-contained and clean
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 50, 60, 50);

        TextView titleView = new TextView(context);
        titleView.setText(R.string.downloading_update);
        titleView.setTextSize(18);
        titleView.setTextColor(android.graphics.Color.BLACK);
        layout.addView(titleView);

        final ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setPadding(0, 40, 0, 20);
        layout.addView(progressBar);

        final TextView percentView = new TextView(context);
        percentView.setText("0%");
        percentView.setGravity(Gravity.RIGHT);
        percentView.setTextColor(android.graphics.Color.BLACK);
        layout.addView(percentView);

        final AlertDialog downloadDialog = new AlertDialog.Builder(context)
            .setView(layout)
            .setCancelable(false)
            .create();

        downloadDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Settings config = new Settings(context);
                    java.net.Proxy proxy = java.net.Proxy.NO_PROXY;
                    if (com.slipkprojects.ultrasshservice.logger.SkStatus.isTunnelActive()) {
                        int socksPort = 1080;
                        try {
                            socksPort = Integer.parseInt(config.getPrivString(Settings.PORTA_LOCAL_KEY));
                        } catch (Exception e) {}
                        proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress.createUnresolved("127.0.0.1", socksPort));
                    }

                    HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection(proxy);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);

                    int status = conn.getResponseCode();
                    int redirectCount = 0;
                    while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) && redirectCount < 5) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                        conn.setConnectTimeout(30000);
                        conn.setReadTimeout(30000);
                        status = conn.getResponseCode();
                        redirectCount++;
                    }

                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new Exception("HTTP error code: " + status);
                    }

                    final int fileLength = conn.getContentLength();
                    InputStream input = new BufferedInputStream(conn.getInputStream());

                    final File apkFile = new File(context.getCacheDir(), "update.apk");
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                    OutputStream output = new FileOutputStream(apkFile);

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setProgress(progress);
                                    percentView.setText(progress + "%");
                                }
                            });
                        }
                    }

                    output.flush();
                    output.close();
                    input.close();
                    conn.disconnect();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadDialog.dismiss();
                            installApk(context, apkFile);
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Error downloading update", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadDialog.dismiss();
                            Toast.makeText(context, context.getString(R.string.error_downloading_update) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static void installApk(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent settingsIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(settingsIntent);
                    Toast.makeText(context, "Por favor, permite la instalación de fuentes desconocidas para esta aplicación y vuelve a intentarlo.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error starting manage unknown app sources settings", e);
                }
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String authority = context.getPackageName() + ".provider";
            Uri apkUri = FileProvider.getUriForFile(context, authority, file);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting installation intent", e);
            Toast.makeText(context, "Error al abrir el instalador del APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
