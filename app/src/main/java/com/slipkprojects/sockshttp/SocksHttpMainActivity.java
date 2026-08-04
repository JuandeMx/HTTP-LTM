package com.slipkprojects.sockshttp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;

import com.slipkprojects.sockshttp.activities.BaseActivity;
import com.slipkprojects.sockshttp.util.MaximusApiManager;
import com.slipkprojects.sockshttp.util.MaximusApiManager.MethodModel;
import com.slipkprojects.ultrasshservice.LaunchVpn;
import com.slipkprojects.ultrasshservice.config.ConfigParser;
import com.slipkprojects.ultrasshservice.config.Settings;
import com.slipkprojects.ultrasshservice.logger.ConnectionStatus;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import com.slipkprojects.ultrasshservice.tunnel.TunnelManagerHelper;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Activity Principal - DARCK Net / MAXIMUS Net Redesign
 * Control 100% por Panel Maestro API en Puerto 8080
 */
public class SocksHttpMainActivity extends BaseActivity
    implements DrawerLayout.DrawerListener,
            View.OnClickListener, RadioGroup.OnCheckedChangeListener,
                CompoundButton.OnCheckedChangeListener, SkStatus.StateListener, NavigationView.OnNavigationItemSelectedListener
{
    private static final String TAG = SocksHttpMainActivity.class.getSimpleName();
    private static final String UPDATE_VIEWS = "MainUpdate";
    public static final String OPEN_LOGS = "com.httpltm.app:openLogs";
    
    private DrawerLog mDrawer;
    private DrawerPanelMain mDrawerPanel;
    
    private Settings mConfig;
    private Toolbar toolbar_main;
    private Handler mHandler;
    private String mPreviousState = "";
    
    private EditText inputUnified;
    private Spinner spinnerMethods;
    private List<MaximusApiManager.NodeModel> mNodesList = new ArrayList<>();
    private MaximusApiManager.NodeModel mSelectedNode = null;
    private List<MethodModel> mMethodsList = new ArrayList<>();
    private CheckBox chkUsePayload, chkSSL, chkEnhanced, chkSlowDns, chkEnableDNS, chkUdpCustom, chkPsiphon, chkV2ray;
    private Button starterButton;
    private LinearLayout noteLayout;
    private WebView noteWebView;
    private LinearLayout configBody;
    
    private TextView uploadText, downloadText, pingText;
    
    // New UI Elements (DARCK Net Style)
    private TextView txtSelectedServerName;
    private TextView txtSelectedConfigName;
    private EditText inputUsername, inputPassword;
    private ImageView btnTogglePassword;
    private ImageView imgShieldCenter;
    private View circleBackground;
    private android.widget.ProgressBar progressRingConnection;
    private TextView txtConnectLabel;
    private TextView statusText, txtNetworkType, txtIpAddress, txtPingMs, txtAppVersion;
    private LinearLayout statusCapsule;

    private Handler speedHandler = new Handler();
    private long lastRxBytes = 0;
    private long lastTxBytes = 0;
    private Runnable speedRunnable;

    private BroadcastReceiver mActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doUpdateLayout();
        }
    };

    public static void updateMainViews(Context context) {
        Intent updateIntent = new Intent(UPDATE_VIEWS);
        LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent);
    }

    public void showShareNetDialog() {}
    public void showPayloadDialog() {}
    public void showSNIDialog() {}

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        if (!checkSecurity()) {
            return;
        }
        
        mHandler = new Handler();
        mConfig = new Settings(this);
        SharedPreferences prefs = mConfig.getPrefsPrivate();

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }

        doLayout();

        boolean showFirstTime = prefs.getBoolean("connect_first_time", true);
        int lastVersion = prefs.getInt("last_version", 0);

        if (showFirstTime) {
            SharedPreferences.Editor pEdit = prefs.edit();
            pEdit.putBoolean("connect_first_time", false);
            pEdit.apply();
            Settings.setDefaultConfig(this);
            showBoasVindas();
        }

        try {
            int idAtual = ConfigParser.getBuildId(this);
            if (lastVersion < idAtual) {
                SharedPreferences.Editor pEdit = prefs.edit();
                pEdit.putInt("last_version", idAtual);
                pEdit.apply();

                if (!showFirstTime && lastVersion <= 12) {
                    Settings.setDefaultConfig(this);
                    Settings.clearSettings(this);
                    Toast.makeText(this, "As configurações foram limpas para evitar bugs", Toast.LENGTH_LONG).show();
                }
            }
        } catch(IOException e) {}

        IntentFilter filter = new IntentFilter();
        filter.addAction(UPDATE_VIEWS);
        filter.addAction(OPEN_LOGS);
        
        LocalBroadcastManager.getInstance(this).registerReceiver(mActivityReceiver, filter);
            
        doUpdateLayout();
        com.slipkprojects.sockshttp.util.UpdateManager.checkUpdate(this, false);
    }

    private void doLayout() {
        setContentView(R.layout.activity_main_drawer);

        toolbar_main = (Toolbar) findViewById(R.id.toolbar_main);
        if (toolbar_main != null) {
            setSupportActionBar(toolbar_main);
        }

        mDrawer = new DrawerLog(this);
        mDrawerPanel = new DrawerPanelMain(this);

        if (mDrawerPanel != null && toolbar_main != null) {
            mDrawerPanel.setDrawer(toolbar_main);
        }
        if (mDrawer != null) {
            mDrawer.setDrawer(this);
        }

        NavigationView navigationView = (NavigationView) findViewById(R.id.drawerNavigationView);
        if (navigationView != null) {
            // Listener is set by DrawerPanelMain.setDrawer() — do NOT override it here
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView appVersionText = headerView.findViewById(R.id.nav_headerAppVersion);
                if (appVersionText != null) {
                    try {
                        appVersionText.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                    } catch (PackageManager.NameNotFoundException e) {}
                }
            }
        }

        starterButton = (Button) findViewById(R.id.activity_starterButtonMain);
        View starterLayout = findViewById(R.id.activity_starterButtonMainLayout);
        circleBackground = findViewById(R.id.circleBackground);
        progressRingConnection = (android.widget.ProgressBar) findViewById(R.id.progressRingConnection);
        imgShieldCenter = (ImageView) findViewById(R.id.imgShieldCenter);
        statusText = (TextView) findViewById(R.id.statusText);
        txtConnectLabel = (TextView) findViewById(R.id.txtConnectLabel);
        statusCapsule = (LinearLayout) findViewById(R.id.statusCapsule);

        inputUnified = (EditText) findViewById(R.id.activity_mainInputUnified);
        spinnerMethods = (Spinner) findViewById(R.id.spinnerMethods);

        chkUsePayload = (CheckBox) findViewById(R.id.chkUsePayload);
        chkSSL = (CheckBox) findViewById(R.id.chkSSL);
        chkEnhanced = (CheckBox) findViewById(R.id.chkEnhanced);
        chkSlowDns = (CheckBox) findViewById(R.id.chkSlowDns);
        chkEnableDNS = (CheckBox) findViewById(R.id.chkEnableDNS);
        chkUdpCustom = (CheckBox) findViewById(R.id.chkUdpCustom);
        chkPsiphon = (CheckBox) findViewById(R.id.chkPsiphon);
        chkV2ray = (CheckBox) findViewById(R.id.chkV2ray);

        if (starterButton != null) starterButton.setOnClickListener(this);
        if (starterLayout != null) starterLayout.setOnClickListener(this);

        // Header Pill Buttons
        View btnHeaderUpdate = findViewById(R.id.btnHeaderUpdate);
        View btnHeaderLogs = findViewById(R.id.btnHeaderLogs);
        View btnHeaderMenu = findViewById(R.id.btnHeaderMenu);

        if (btnHeaderUpdate != null) {
            btnHeaderUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    processLtPlantillas();
                    Toast.makeText(SocksHttpMainActivity.this, "Actualizando métodos del Maestro...", Toast.LENGTH_SHORT).show();
                    refreshMethodsFromMaster();
                }
            });
            // Mantener presionado = Ver Log de la API
            btnHeaderUpdate.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showApiLogDialog();
                    return true;
                }
            });
        }

        if (btnHeaderLogs != null) {
            btnHeaderLogs.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DrawerLayout logDrawer = (DrawerLayout) findViewById(R.id.drawerLayout);
                    if (logDrawer != null) logDrawer.openDrawer(GravityCompat.END);
                }
            });
        }

        if (btnHeaderMenu != null) {
            btnHeaderMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawerLayoutMain);
                    if (drawer != null) drawer.openDrawer(GravityCompat.START);
                }
            });
        }

        // Method Selector Item Card
        txtSelectedServerName = (TextView) findViewById(R.id.txtSelectedServerName);
        View cardServerSelect = findViewById(R.id.cardServerSelect);
        if (cardServerSelect != null) {
            cardServerSelect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showServersPickerDialog();
                }
            });
        }

        txtSelectedConfigName = (TextView) findViewById(R.id.txtSelectedConfigName);
        View cardConfigSelect = findViewById(R.id.cardConfigSelect);
        if (cardConfigSelect != null) {
            cardConfigSelect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMethodsPickerDialog();
                }
            });
        }

        // Credentials Input Fields
        inputUsername = (EditText) findViewById(R.id.inputUsername);
        inputPassword = (EditText) findViewById(R.id.inputPassword);
        btnTogglePassword = (ImageView) findViewById(R.id.btnTogglePassword);

        if (inputUsername != null) {
            String savedUser = mConfig.getPrivString(Settings.USUARIO_KEY);
            inputUsername.setText(savedUser);
        }

        if (inputPassword != null) {
            String savedPass = mConfig.getPrivString(Settings.SENHA_KEY);
            inputPassword.setText(savedPass);
        }

        if (btnTogglePassword != null && inputPassword != null) {
            btnTogglePassword.setOnClickListener(new View.OnClickListener() {
                private boolean isVisible = false;
                @Override
                public void onClick(View v) {
                    isVisible = !isVisible;
                    if (isVisible) {
                        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    } else {
                        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    }
                    inputPassword.setSelection(inputPassword.getText().length());
                }
            });
        }

        // Circular Connection Button (main connection trigger)
        View circularButton = findViewById(R.id.activity_starterButtonMainLayout);
        if (circularButton != null) {
            circularButton.setOnClickListener(this);
        }

        imgShieldCenter = (ImageView) findViewById(R.id.imgShieldCenter);
        circleBackground = findViewById(R.id.circleBackground);
        progressRingConnection = (android.widget.ProgressBar) findViewById(R.id.progressRingConnection);
        txtConnectLabel = (TextView) findViewById(R.id.txtConnectLabel);

        // Status Pill Text
        statusText = (TextView) findViewById(R.id.statusText);
        statusCapsule = (LinearLayout) findViewById(R.id.statusCapsule);

        // Metrics
        txtNetworkType = (TextView) findViewById(R.id.txtNetworkType);
        txtIpAddress = (TextView) findViewById(R.id.txtIpAddress);
        txtPingMs = (TextView) findViewById(R.id.txtPingMs);
        txtAppVersion = (TextView) findViewById(R.id.txtAppVersion);

        updateNetworkMetrics();
        refreshNodesFromMaster();
        startSpeedometer();
    }

    private void showServersPickerDialog() {
        if (mNodesList == null || mNodesList.isEmpty()) {
            Toast.makeText(this, "Cargando lista de servidores desde el Maestro...", Toast.LENGTH_SHORT).show();
            refreshNodesFromMaster();
            return;
        }

        final android.support.v7.app.AlertDialog dialog = new android.support.v7.app.AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_methods_picker, null);
        TextView txtTitle = view.findViewById(R.id.listViewMethods).getRootView().findViewWithTag("title");
        ListView listView = (ListView) view.findViewById(R.id.listViewMethods);

        ArrayAdapter<MaximusApiManager.NodeModel> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mNodesList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < mNodesList.size()) {
                    MaximusApiManager.NodeModel selectedNode = mNodesList.get(position);
                    mSelectedNode = selectedNode;
                    // Guardar nodo seleccionado para recordarlo al reabrir/refrescar
                    mConfig.getPrefsPrivate().edit().putString("last_selected_node", selectedNode.name).apply();
                    if (txtSelectedServerName != null) {
                        txtSelectedServerName.setText(selectedNode.name);
                    }
                    if (txtSelectedConfigName != null) {
                        txtSelectedConfigName.setText("SELECCIONAR MÉTODOS");
                    }
                    // Cargar los 7 métodos compilados para este nodo
                    refreshMethodsFromMasterForNode(selectedNode.name);
                }
                dialog.dismiss();
            }
        });

        dialog.setView(view);
        dialog.show();
    }

    private void showMethodsPickerDialog() {
        if (mMethodsList == null || mMethodsList.isEmpty()) {
            Toast.makeText(this, "Cargando métodos para " + (mSelectedNode != null ? mSelectedNode.name : "el servidor") + "...", Toast.LENGTH_SHORT).show();
            if (mSelectedNode != null) {
                refreshMethodsFromMasterForNode(mSelectedNode.name);
            } else {
                refreshNodesFromMaster();
            }
            return;
        }

        final android.support.v7.app.AlertDialog dialog = new android.support.v7.app.AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_methods_picker, null);
        ListView listView = (ListView) view.findViewById(R.id.listViewMethods);

        ArrayAdapter<MethodModel> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mMethodsList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < mMethodsList.size()) {
                    MethodModel selected = mMethodsList.get(position);
                    if (txtSelectedConfigName != null) {
                        txtSelectedConfigName.setText(selected.name);
                    }
                    applySelectedMethod(selected);
                }
                dialog.dismiss();
            }
        });

        dialog.setView(view);
        dialog.show();
    }

    private void updateNetworkMetrics() {
        if (txtAppVersion != null) {
            try {
                int buildId = ConfigParser.getBuildId(this);
                String vName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                txtAppVersion.setText(buildId + "/" + vName);
            } catch (Exception e) {
                txtAppVersion.setText("188/4.5.8");
            }
        }

        if (txtNetworkType != null || txtIpAddress != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;

            String netType = "DESCONECTADO";
            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    netType = "WIFI";
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    netType = "DATOS";
                } else {
                    netType = "RED";
                }
            }
            if (txtNetworkType != null) txtNetworkType.setText(netType);

            String ip = getLocalIpAddress();
            if (txtIpAddress != null) txtIpAddress.setText(ip);
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {}
        return "127.0.0.1";
    }

    public void refreshNodesFromMaster() {
        String masterUrl = mConfig.getPrefsPrivate().getString("master_url", MaximusApiManager.DEFAULT_MASTER_URL);
        MaximusApiManager.writeLog(this, "INFO", "refreshNodesFromMaster: Solicitando desde " + masterUrl);
        MaximusApiManager.fetchNodes(this, masterUrl, new MaximusApiManager.OnNodesLoadedListener() {
            @Override
            public void onSuccess(final List<MaximusApiManager.NodeModel> nodes) {
                mNodesList = nodes;
                MaximusApiManager.writeLog(SocksHttpMainActivity.this, "OK", "refreshNodesFromMaster: " + (nodes != null ? nodes.size() : 0) + " servidores cargados");
                if (nodes != null && !nodes.isEmpty()) {
                    // Intentar restaurar el último nodo seleccionado por el usuario
                    String lastNodeName = mConfig.getPrefsPrivate().getString("last_selected_node", "");
                    MaximusApiManager.NodeModel targetNode = null;
                    if (!lastNodeName.isEmpty()) {
                        for (MaximusApiManager.NodeModel n : nodes) {
                            if (n.name != null && n.name.equals(lastNodeName)) {
                                targetNode = n;
                                break;
                            }
                        }
                    }
                    // Si no encontró el nodo guardado, usar el primero
                    if (targetNode == null) {
                        targetNode = nodes.get(0);
                    }
                    mSelectedNode = targetNode;
                    if (txtSelectedServerName != null) {
                        txtSelectedServerName.setText(targetNode.name);
                    }
                    refreshMethodsFromMasterForNode(targetNode.name);
                } else {
                    Toast.makeText(SocksHttpMainActivity.this, "No se encontraron servidores en el Maestro.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(final String errorMessage) {
                MaximusApiManager.writeLog(SocksHttpMainActivity.this, "ERROR", "refreshNodesFromMaster: " + errorMessage);
                Toast.makeText(SocksHttpMainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void refreshMethodsFromMaster() {
        if (mSelectedNode != null) {
            refreshMethodsFromMasterForNode(mSelectedNode.name);
        } else {
            refreshNodesFromMaster();
        }
    }

    public void refreshMethodsFromMasterForNode(final String nodeName) {
        String masterUrl = mConfig.getPrefsPrivate().getString("master_url", MaximusApiManager.DEFAULT_MASTER_URL);
        MaximusApiManager.writeLog(this, "INFO", "refreshMethodsFromMasterForNode: Solicitando para nodo=" + nodeName);
        MaximusApiManager.fetchMethods(this, masterUrl, nodeName, new MaximusApiManager.OnMethodsLoadedListener() {
            @Override
            public void onSuccess(final List<MethodModel> methods) {
                mMethodsList = methods;
                MaximusApiManager.writeLog(SocksHttpMainActivity.this, "OK", "refreshMethodsFromMasterForNode: " + (methods != null ? methods.size() : 0) + " métodos cargados para " + nodeName);
                if (methods != null && !methods.isEmpty()) {
                    Toast.makeText(SocksHttpMainActivity.this, "Métodos cargados para " + nodeName + " (" + methods.size() + ")", Toast.LENGTH_SHORT).show();
                    MethodModel first = methods.get(0);
                    if (txtSelectedConfigName != null) {
                        txtSelectedConfigName.setText(first.name);
                    }
                    applySelectedMethod(first);
                } else {
                    Toast.makeText(SocksHttpMainActivity.this, "El servidor Maestro no devolvió métodos para " + nodeName, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(final String errorMessage) {
                MaximusApiManager.writeLog(SocksHttpMainActivity.this, "ERROR", "refreshMethodsFromMasterForNode: " + errorMessage);
                Toast.makeText(SocksHttpMainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applySelectedMethod(MethodModel method) {
        if (method == null) return;

        SharedPreferences prefs = mConfig.getPrefsPrivate();
        SharedPreferences.Editor edit = prefs.edit();

        String cfDomain = (mSelectedNode != null && mSelectedNode.domainCf != null && !mSelectedNode.domainCf.isEmpty()) ? mSelectedNode.domainCf : (mSelectedNode != null ? mSelectedNode.ip : "");
        String cftDomain = (mSelectedNode != null && mSelectedNode.domainCft != null && !mSelectedNode.domainCft.isEmpty()) ? mSelectedNode.domainCft : (mSelectedNode != null ? mSelectedNode.ip : "");
        String nodeIp = mSelectedNode != null ? mSelectedNode.ip : "";

        if (method.sshHost != null && !method.sshHost.isEmpty()) {
            String realSshHost = method.sshHost
                .replace("[CFT]", cftDomain)
                .replace("[CLOUDFRONT]", cftDomain)
                .replace("[CF]", cfDomain)
                .replace("[IP]", nodeIp);
            edit.putString(Settings.SERVIDOR_KEY, realSshHost);
        }

        edit.putString(Settings.SERVIDOR_PORTA_KEY, String.valueOf(method.sshPort));

        if (method.sni != null) {
            String realSni = method.sni
                .replace("[CFT]", cftDomain)
                .replace("[CLOUDFRONT]", cftDomain)
                .replace("[CF]", cfDomain)
                .replace("[IP]", nodeIp);
            edit.putString(Settings.CUSTOM_SNI, realSni);
        }

        if (method.payload != null) {
            String realPayload = method.payload
                .replace("[rotate=[CFT]]", fRotateCft(cftDomain))
                .replace("[CFT]", cftDomain)
                .replace("[CLOUDFRONT]", cftDomain)
                .replace("[CF]", cfDomain)
                .replace("[IP]", nodeIp)
                .replace("[crlf]", "\r\n").replace("[CRLF]", "\r\n")
                .replace("[lf]", "\n").replace("[LF]", "\n");

            edit.putString(Settings.CUSTOM_PAYLOAD_KEY, realPayload);
        }

        String proto = method.protocol != null ? method.protocol.toUpperCase() : "";
        boolean isSsl = proto.contains("SSL");
        boolean isPayload = proto.contains("PAYLOAD") || proto.contains("WEBSOCKET");

        edit.putBoolean("use_ssl", isSsl);
        edit.putBoolean("use_payload", isPayload);

        if (chkSSL != null) chkSSL.setChecked(isSsl);
        if (chkUsePayload != null) chkUsePayload.setChecked(isPayload);

        if (method.sshUser != null && !method.sshUser.isEmpty()) {
            edit.putString(Settings.USUARIO_KEY, method.sshUser);
            if (method.sshPass != null) edit.putString(Settings.SENHA_KEY, method.sshPass);

            if (inputUsername != null) inputUsername.setText(method.sshUser);
            if (inputPassword != null) inputPassword.setText(method.sshPass != null ? method.sshPass : "");
        }

        edit.apply();
    }

    private void startSpeedometer() {
        if (speedRunnable != null) return;
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        speedRunnable = new Runnable() {
            private int pingTick = 0;

            @Override
            public void run() {
                if (!SkStatus.SSH_CONECTADO.equals(SkStatus.getLastState())) {
                    if (txtPingMs != null) txtPingMs.setText("00");
                    pingTick = 0;
                } else {
                    pingTick++;
                    if (pingTick >= 5 || pingTick == 1) {
                        if (pingTick >= 5) pingTick = 0;

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                long startTime = System.currentTimeMillis();
                                int finalPing = -1;
                                java.net.Socket socket = null;
                                try {
                                    Settings config = new Settings(SocksHttpMainActivity.this);
                                    String server = config.getPrivString(Settings.SERVIDOR_KEY);
                                    int port = 443;
                                    try {
                                        port = Integer.parseInt(config.getPrivString(Settings.SERVIDOR_PORTA_KEY));
                                    } catch(Exception ep) {}
                                    
                                    if (server != null && !server.isEmpty()) {
                                        socket = new java.net.Socket();
                                        socket.connect(new java.net.InetSocketAddress(server, port), 3000);
                                        finalPing = (int) (System.currentTimeMillis() - startTime);
                                    }
                                } catch (Exception e) {
                                    finalPing = -1;
                                } finally {
                                    if (socket != null) {
                                        try { socket.close(); } catch (Exception e) {}
                                    }
                                }
                                
                                final int pingResult = finalPing;
                                if (txtPingMs != null) {
                                    txtPingMs.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (pingResult >= 0) {
                                                txtPingMs.setText(pingResult + " ms");
                                            } else {
                                                txtPingMs.setText("00");
                                            }
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                }
                speedHandler.postDelayed(this, 1000);
            }
        };
        speedHandler.post(speedRunnable);
    }

    private synchronized void doSaveData() {
        SharedPreferences prefs = mConfig.getPrefsPrivate();
        SharedPreferences.Editor edit = prefs.edit();

        String u = inputUsername != null ? inputUsername.getText().toString().trim() : "";
        String p = inputPassword != null ? inputPassword.getText().toString().trim() : "";

        edit.putString(Settings.USUARIO_KEY, u);
        edit.putString(Settings.SENHA_KEY, p);

        if (!prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
            String server = prefs.getString(Settings.SERVIDOR_KEY, "187.127.17.250");
            String port = prefs.getString(Settings.SERVIDOR_PORTA_KEY, "22");
            if (inputUnified != null) {
                inputUnified.setText(server + ":" + port + "@" + u + ":" + p);
            }
        }

        edit.apply();
    }

    public void startOrStopTunnel(final Activity activity) {
        android.os.Vibrator v = (android.os.Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(20);
        }
        
        if (SkStatus.isTunnelActive()) {
            TunnelManagerHelper.stopSocksHttp(activity);
        }
        else {
            doSaveData();

            final Settings config = new Settings(activity);
            Intent intent = new Intent(activity, LaunchVpn.class);
            intent.setAction(Intent.ACTION_MAIN);
            if (config.getHideLog()) {
                intent.putExtra(LaunchVpn.EXTRA_HIDELOG, true);
            }
            activity.startActivity(intent);
        }
    }

    private void doUpdateLayout() {
        String state = SkStatus.getLastState();

        boolean isConnected = SkStatus.SSH_CONECTADO.equals(state);
        boolean isConnecting = SkStatus.SSH_CONECTANDO.equals(state) || SkStatus.SSH_INICIANDO.equals(state);

        // Status Text
        if (statusText != null) {
            if (isConnected) {
                statusText.setText("● CONECTADO");
                statusText.setTextColor(Color.parseColor("#00E676"));
            } else if (isConnecting) {
                statusText.setText("● CONECTANDO...");
                statusText.setTextColor(Color.parseColor("#FFB020"));
            } else {
                statusText.setText("● DESCONECTADO");
                statusText.setTextColor(Color.parseColor("#8C93A6"));
            }
        }

        // Circular Button Animation
        if (circleBackground != null) {
            if (isConnected) {
                circleBackground.setBackgroundResource(R.drawable.bg_circle_connected);
            } else if (isConnecting) {
                circleBackground.setBackgroundResource(R.drawable.bg_circle_connecting);
            } else {
                circleBackground.setBackgroundResource(R.drawable.bg_circle_disconnected);
            }
        }

        // Shield Icon Tint
        if (imgShieldCenter != null) {
            if (isConnected) {
                imgShieldCenter.setColorFilter(Color.parseColor("#00E676"));
            } else if (isConnecting) {
                imgShieldCenter.setColorFilter(Color.parseColor("#FFB020"));
            } else {
                imgShieldCenter.setColorFilter(Color.parseColor("#4A54E1"));
            }
        }

        // Progress Ring (spinning animation)
        if (progressRingConnection != null) {
            progressRingConnection.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
        }

        // Connection Label
        if (txtConnectLabel != null) {
            if (isConnected) {
                txtConnectLabel.setText("TOCA PARA DESCONECTAR");
                txtConnectLabel.setTextColor(Color.parseColor("#00E676"));
            } else if (isConnecting) {
                txtConnectLabel.setText("CONECTANDO...");
                txtConnectLabel.setTextColor(Color.parseColor("#FFB020"));
            } else {
                txtConnectLabel.setText("TOCA PARA CONECTAR");
                txtConnectLabel.setTextColor(Color.parseColor("#5A6078"));
            }
        }

        updateNetworkMetrics();
    }

    @Override
    public void onClick(View p1)
    {
        int id = p1.getId();
        if (id == R.id.activity_starterButtonMain || id == R.id.activity_starterButtonMainLayout) {
            if (!checkSecurity()) return;
            doSaveData();
            startOrStopTunnel(this);
        }
    }

    @Override public void onCheckedChanged(RadioGroup p1, int p2) {}
    @Override public void onCheckedChanged(CompoundButton p1, boolean p2) {}

    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level, Intent intent) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                doUpdateLayout();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawerLayoutMain);
        if (drawer != null) drawer.closeDrawers();
        return true;
    }

    @Override public void onDrawerSlide(View drawerView, float slideOffset) {}
    @Override public void onDrawerOpened(View drawerView) {}
    @Override public void onDrawerClosed(View drawerView) {}
    @Override public void onDrawerStateChanged(int newState) {}

    @Override
    protected void onResume() {
        super.onResume();
        SkStatus.addStateListener(this);
        doUpdateLayout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SkStatus.removeStateListener(this);
    }

    private boolean checkSecurity() {
        return true;
    }

    private void showBoasVindas() {}

    /**
     * Muestra un diálogo con el log completo de la comunicación con la API del Maestro.
     * Se abre manteniendo presionado el botón de Actualizar.
     */
    private void showApiLogDialog() {
        String logContent = MaximusApiManager.readLog(this);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setPadding(24, 24, 24, 24);

        TextView logText = new TextView(this);
        logText.setText(logContent);
        logText.setTextSize(10f);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setTextColor(Color.parseColor("#E0E0E0"));
        logText.setBackgroundColor(Color.parseColor("#1A1A2E"));
        logText.setPadding(16, 16, 16, 16);
        scrollView.addView(logText);

        new android.support.v7.app.AlertDialog.Builder(this)
            .setTitle("📋 Log de API Maestro")
            .setView(scrollView)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Limpiar Log", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    MaximusApiManager.clearLog(SocksHttpMainActivity.this);
                    Toast.makeText(SocksHttpMainActivity.this, "Log limpiado", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Copiar", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Maximus API Log", MaximusApiManager.readLog(SocksHttpMainActivity.this));
                    if (clipboard != null) clipboard.setPrimaryClip(clip);
                    Toast.makeText(SocksHttpMainActivity.this, "Log copiado al portapapeles", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void processLtPlantillas() {
        try {
            java.io.File dir = new java.io.File("d:/apk/plantillas");
            if (!dir.exists()) {
                dir = new java.io.File("/sdcard/plantillas");
            }
            if (!dir.exists()) return;
            java.io.File[] files = dir.listFiles();
            if (files == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append("========================================================================\n");
            sb.append("CONFIGURACIONES EXTRAÍDAS DESDE LAS PLANTILLAS .LT\n");
            sb.append("========================================================================\n\n");

            Settings settings = new Settings(this);
            for (java.io.File f : files) {
                if (f.getName().toUpperCase().endsWith(".LT")) {
                    try {
                        java.io.FileInputStream fis = new java.io.FileInputStream(f);
                        boolean ok = ConfigParser.convertInputAndSave(fis, this);
                        fis.close();
                        if (ok) {
                            SharedPreferences prefs = settings.getPrefsPrivate();
                            String host = prefs.getString(Settings.SERVIDOR_KEY, "");
                            String port = prefs.getString(Settings.SERVIDOR_PORTA_KEY, "22");
                            String user = prefs.getString(Settings.USUARIO_KEY, "");
                            String pass = prefs.getString(Settings.SENHA_KEY, "");
                            String payload = prefs.getString(Settings.CUSTOM_PAYLOAD_KEY, "");
                            String sni = prefs.getString(Settings.CUSTOM_SNI, "");
                            boolean ssl = prefs.getBoolean("use_ssl", false);
                            boolean payloadUse = prefs.getBoolean("use_payload", false);

                            sb.append("========================================\n");
                            sb.append("PLANTILLA: ").append(f.getName()).append("\n");
                            sb.append("========================================\n");
                            sb.append("NOMBRE: ").append(f.getName().replace(".LT","").replace(".lt","")).append("\n");
                            sb.append("SSH_HOST: ").append(host).append("\n");
                            sb.append("SSH_PORT: ").append(port).append("\n");
                            sb.append("SSH_USER: ").append(user).append("\n");
                            sb.append("SSH_PASS: ").append(pass).append("\n");
                            sb.append("SNI: ").append(sni).append("\n");
                            sb.append("PAYLOAD: ").append(payload.replace("\r\n", "[crlf]").replace("\n", "[crlf]")).append("\n\n");
                        }
                    } catch (Exception e) {
                        sb.append("ERROR EN ").append(f.getName()).append(": ").append(e.getMessage()).append("\n\n");
                    }
                }
            }

            java.io.File out = new java.io.File("d:/apk/plantillas_descifradas.txt");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

        } catch (Exception e) {}
    }

    private String fRotateCft(String domain) {
        return domain != null ? domain : "";
    }
}
