package com.slipkprojects.sockshttp.activities;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.SharedPreferences;
import android.content.Context;
import android.widget.Toast;
import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.RadioGroup;
import android.support.v7.widget.AppCompatRadioButton;

import com.slipkprojects.sockshttp.R;
import com.slipkprojects.ultrasshservice.config.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

public class V2raySettingsActivity extends BaseActivity {

    private View layoutJsonEditor;
    private View scrollVisualForm;
    
    private TextView txtLineNumbers;
    private EditText editV2rayJson;
    
    private EditText editV2rayHost;
    private EditText editV2rayPort;
    private EditText editV2rayId;
    private SwitchCompat switchV2rayMux;
    private SwitchCompat switchV2rayTls;
    
    private RadioGroup radioGroupProtocol;
    private android.support.v7.widget.AppCompatRadioButton radioVmess;
    private android.support.v7.widget.AppCompatRadioButton radioVless;
    private TextView txtOutboundHeader;

    private RadioGroup radioGroupNetwork;
    private android.support.v7.widget.AppCompatRadioButton radioNetworkTcp;
    private android.support.v7.widget.AppCompatRadioButton radioNetworkWs;
    private View layoutWsSettings;
    private EditText editV2rayWsHost;
    private EditText editV2rayWsPath;
    private View layoutSniSettings;
    private EditText editV2raySni;
    
    private Button btnSaveV2raySettings;
    
    private Settings mConfig;
    private boolean isVisualMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_v2ray_settings);

        mConfig = new Settings(this);

        // Toolbar
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("V2ray Settings");
        }

        // Bind views
        layoutJsonEditor = findViewById(R.id.layout_json_editor);
        scrollVisualForm = findViewById(R.id.scroll_visual_form);
        
        txtLineNumbers = (TextView) findViewById(R.id.txtLineNumbers);
        editV2rayJson = (EditText) findViewById(R.id.editV2rayJson);
        
        editV2rayHost = (EditText) findViewById(R.id.editV2rayHost);
        editV2rayPort = (EditText) findViewById(R.id.editV2rayPort);
        editV2rayId = (EditText) findViewById(R.id.editV2rayId);
        switchV2rayMux = (SwitchCompat) findViewById(R.id.switchV2rayMux);
        switchV2rayTls = (SwitchCompat) findViewById(R.id.switchV2rayTls);
        
        radioGroupProtocol = (RadioGroup) findViewById(R.id.radioGroupProtocol);
        radioVmess = (android.support.v7.widget.AppCompatRadioButton) findViewById(R.id.radioVmess);
        radioVless = (android.support.v7.widget.AppCompatRadioButton) findViewById(R.id.radioVless);
        txtOutboundHeader = (TextView) findViewById(R.id.txtOutboundHeader);

        radioGroupNetwork = (RadioGroup) findViewById(R.id.radioGroupNetwork);
        radioNetworkTcp = (android.support.v7.widget.AppCompatRadioButton) findViewById(R.id.radioNetworkTcp);
        radioNetworkWs = (android.support.v7.widget.AppCompatRadioButton) findViewById(R.id.radioNetworkWs);
        layoutWsSettings = findViewById(R.id.layoutWsSettings);
        editV2rayWsHost = (EditText) findViewById(R.id.editV2rayWsHost);
        editV2rayWsPath = (EditText) findViewById(R.id.editV2rayWsPath);
        layoutSniSettings = findViewById(R.id.layoutSniSettings);
        editV2raySni = (EditText) findViewById(R.id.editV2raySni);

        radioGroupProtocol.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioVmess) {
                    txtOutboundHeader.setText("VMess Settings:");
                    editV2rayId.setHint("Vmess user");
                } else if (checkedId == R.id.radioVless) {
                    txtOutboundHeader.setText("VLESS Settings:");
                    editV2rayId.setHint("VLESS user (UUID)");
                }
            }
        });

        radioGroupNetwork.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioNetworkWs) {
                    layoutWsSettings.setVisibility(View.VISIBLE);
                } else {
                    layoutWsSettings.setVisibility(View.GONE);
                }
            }
        });

        switchV2rayTls.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    layoutSniSettings.setVisibility(View.VISIBLE);
                } else {
                    layoutSniSettings.setVisibility(View.GONE);
                }
            }
        });
        
        btnSaveV2raySettings = (Button) findViewById(R.id.btnSaveV2raySettings);

        // TextWatcher for line numbers
        editV2rayJson.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                editV2rayJson.post(new Runnable() {
                    @Override
                    public void run() {
                        updateLineNumbers();
                    }
                });
            }
        });

        // Load settings
        loadSettings();

        boolean isProtected = mConfig.getPrefsPrivate().getBoolean(Settings.CONFIG_PROTEGER_KEY, false);
        if (isProtected) {
            editV2rayJson.setEnabled(false);
            editV2rayHost.setEnabled(false);
            editV2rayPort.setEnabled(false);
            editV2rayId.setEnabled(false);
            switchV2rayMux.setEnabled(false);
            switchV2rayTls.setEnabled(false);
            radioVmess.setEnabled(false);
            radioVless.setEnabled(false);
            radioNetworkTcp.setEnabled(false);
            radioNetworkWs.setEnabled(false);
            editV2rayWsHost.setEnabled(false);
            editV2rayWsPath.setEnabled(false);
            editV2raySni.setEnabled(false);
            btnSaveV2raySettings.setVisibility(View.GONE);
        }

        // Save Button Listener
        btnSaveV2raySettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    private void updateLineNumbers() {
        int lines = editV2rayJson.getLineCount();
        if (lines <= 0) lines = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i).append("\n");
        }
        txtLineNumbers.setText(sb.toString());
    }

    private void loadSettings() {
        SharedPreferences prefs = mConfig.getPrefsPrivate();
        String jsonConfig = prefs.getString("v2ray_config", "");

        if (jsonConfig.isEmpty()) {
            try {
                jsonConfig = createDefaultJsonTemplate().toString(2);
            } catch (Exception e) {
                jsonConfig = "";
            }
        }

        editV2rayJson.setText(jsonConfig);
        updateLineNumbers();
        
        // Also populate visual fields immediately in background
        syncJsonToFieldsSilent();
    }

    private void saveSettings() {
        if (isVisualMode) {
            String generatedJson = generateJsonFromFields();
            editV2rayJson.setText(generatedJson);
        }

        String jsonConfig = editV2rayJson.getText().toString().trim();
        
        // Validate JSON
        if (!jsonConfig.isEmpty()) {
            try {
                new JSONObject(jsonConfig);
            } catch (Exception e) {
                Toast.makeText(this, "JSON inválido: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }

        SharedPreferences.Editor editor = mConfig.getPrefsPrivate().edit();
        editor.putString("v2ray_config", jsonConfig);
        editor.apply();

        Toast.makeText(this, "Ajustes de V2Ray guardados", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void syncJsonToFields() {
        String jsonConfig = editV2rayJson.getText().toString().trim();
        if (jsonConfig.isEmpty()) {
            return;
        }

        try {
            JSONObject json = new JSONObject(jsonConfig);
            JSONArray outbounds = json.optJSONArray("outbounds");
            if (outbounds != null && outbounds.length() > 0) {
                JSONObject outbound = null;
                for (int i = 0; i < outbounds.length(); i++) {
                    JSONObject out = outbounds.getJSONObject(i);
                    String protocol = out.optString("protocol", "");
                    if ("vmess".equalsIgnoreCase(protocol) || "vless".equalsIgnoreCase(protocol)) {
                        outbound = out;
                        break;
                    }
                }

                if (outbound != null) {
                    String protocol = outbound.optString("protocol", "vmess");
                    if ("vless".equalsIgnoreCase(protocol)) {
                        radioVless.setChecked(true);
                        txtOutboundHeader.setText("VLESS Settings:");
                        editV2rayId.setHint("VLESS user (UUID)");
                    } else {
                        radioVmess.setChecked(true);
                        txtOutboundHeader.setText("VMess Settings:");
                        editV2rayId.setHint("Vmess user");
                    }

                    JSONObject settings = outbound.optJSONObject("settings");
                    if (settings != null) {
                        JSONArray vnext = settings.optJSONArray("vnext");
                        if (vnext != null && vnext.length() > 0) {
                            JSONObject server = vnext.getJSONObject(0);
                            String address = server.optString("address", "");
                            int port = server.optInt("port", 443);

                            JSONArray users = server.optJSONArray("users");
                            String id = "";
                            if (users != null && users.length() > 0) {
                                id = users.getJSONObject(0).optString("id", "");
                            }

                            editV2rayHost.setText(address);
                            editV2rayPort.setText(String.valueOf(port));
                            editV2rayId.setText(id);
                        }
                    }

                    // Mux
                    JSONObject mux = outbound.optJSONObject("mux");
                    if (mux != null) {
                        switchV2rayMux.setChecked(mux.optBoolean("enabled", false));
                    } else {
                        switchV2rayMux.setChecked(false);
                    }

                    // Network, WS and TLS Settings
                    JSONObject streamSettings = outbound.optJSONObject("streamSettings");
                    if (streamSettings != null) {
                        String network = streamSettings.optString("network", "tcp");
                        if ("ws".equalsIgnoreCase(network)) {
                            radioNetworkWs.setChecked(true);
                            layoutWsSettings.setVisibility(View.VISIBLE);
                        } else {
                            radioNetworkTcp.setChecked(true);
                            layoutWsSettings.setVisibility(View.GONE);
                        }

                        JSONObject wsSettings = streamSettings.optJSONObject("wsSettings");
                        if (wsSettings != null) {
                            editV2rayWsPath.setText(wsSettings.optString("path", ""));
                            JSONObject headers = wsSettings.optJSONObject("headers");
                            if (headers != null) {
                                editV2rayWsHost.setText(headers.optString("Host", ""));
                            } else {
                                editV2rayWsHost.setText("");
                            }
                        } else {
                            editV2rayWsPath.setText("");
                            editV2rayWsHost.setText("");
                        }

                        String security = streamSettings.optString("security", "none");
                        boolean isTls = "tls".equalsIgnoreCase(security);
                        switchV2rayTls.setChecked(isTls);
                        if (isTls) {
                            layoutSniSettings.setVisibility(View.VISIBLE);
                            JSONObject tlsSettings = streamSettings.optJSONObject("tlsSettings");
                            if (tlsSettings != null) {
                                editV2raySni.setText(tlsSettings.optString("serverName", ""));
                            } else {
                                editV2raySni.setText("");
                            }
                        } else {
                            layoutSniSettings.setVisibility(View.GONE);
                            editV2raySni.setText("");
                        }
                    } else {
                        radioNetworkTcp.setChecked(true);
                        layoutWsSettings.setVisibility(View.GONE);
                        editV2rayWsHost.setText("");
                        editV2rayWsPath.setText("");
                        switchV2rayTls.setChecked(false);
                        layoutSniSettings.setVisibility(View.GONE);
                        editV2raySni.setText("");
                    }
                } else {
                    Toast.makeText(this, "No se encontró configuración VMess o VLESS en el JSON. Usando valores predeterminados.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al sincronizar con vista visual: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void syncJsonToFieldsSilent() {
        String jsonConfig = editV2rayJson.getText().toString().trim();
        if (jsonConfig.isEmpty()) return;

        try {
            JSONObject json = new JSONObject(jsonConfig);
            JSONArray outbounds = json.optJSONArray("outbounds");
            if (outbounds != null && outbounds.length() > 0) {
                JSONObject outbound = null;
                for (int i = 0; i < outbounds.length(); i++) {
                    JSONObject out = outbounds.getJSONObject(i);
                    String protocol = out.optString("protocol", "");
                    if ("vmess".equalsIgnoreCase(protocol) || "vless".equalsIgnoreCase(protocol)) {
                        outbound = out;
                        break;
                    }
                }

                if (outbound != null) {
                    String protocol = outbound.optString("protocol", "vmess");
                    if ("vless".equalsIgnoreCase(protocol)) {
                        radioVless.setChecked(true);
                        txtOutboundHeader.setText("VLESS Settings:");
                        editV2rayId.setHint("VLESS user (UUID)");
                    } else {
                        radioVmess.setChecked(true);
                        txtOutboundHeader.setText("VMess Settings:");
                        editV2rayId.setHint("Vmess user");
                    }

                    JSONObject settings = outbound.optJSONObject("settings");
                    if (settings != null) {
                        JSONArray vnext = settings.optJSONArray("vnext");
                        if (vnext != null && vnext.length() > 0) {
                            JSONObject server = vnext.getJSONObject(0);
                            String address = server.optString("address", "");
                            int port = server.optInt("port", 443);

                            JSONArray users = server.optJSONArray("users");
                            String id = "";
                            if (users != null && users.length() > 0) {
                                id = users.getJSONObject(0).optString("id", "");
                            }

                            editV2rayHost.setText(address);
                            editV2rayPort.setText(String.valueOf(port));
                            editV2rayId.setText(id);
                        }
                    }

                    // Mux
                    JSONObject mux = outbound.optJSONObject("mux");
                    if (mux != null) {
                        switchV2rayMux.setChecked(mux.optBoolean("enabled", false));
                    } else {
                        switchV2rayMux.setChecked(false);
                    }

                    // Network, WS and TLS Settings
                    JSONObject streamSettings = outbound.optJSONObject("streamSettings");
                    if (streamSettings != null) {
                        String network = streamSettings.optString("network", "tcp");
                        if ("ws".equalsIgnoreCase(network)) {
                            radioNetworkWs.setChecked(true);
                            layoutWsSettings.setVisibility(View.VISIBLE);
                        } else {
                            radioNetworkTcp.setChecked(true);
                            layoutWsSettings.setVisibility(View.GONE);
                        }

                        JSONObject wsSettings = streamSettings.optJSONObject("wsSettings");
                        if (wsSettings != null) {
                            editV2rayWsPath.setText(wsSettings.optString("path", ""));
                            JSONObject headers = wsSettings.optJSONObject("headers");
                            if (headers != null) {
                                editV2rayWsHost.setText(headers.optString("Host", ""));
                            } else {
                                editV2rayWsHost.setText("");
                            }
                        } else {
                            editV2rayWsPath.setText("");
                            editV2rayWsHost.setText("");
                        }

                        String security = streamSettings.optString("security", "none");
                        boolean isTls = "tls".equalsIgnoreCase(security);
                        switchV2rayTls.setChecked(isTls);
                        if (isTls) {
                            layoutSniSettings.setVisibility(View.VISIBLE);
                            JSONObject tlsSettings = streamSettings.optJSONObject("tlsSettings");
                            if (tlsSettings != null) {
                                editV2raySni.setText(tlsSettings.optString("serverName", ""));
                            } else {
                                editV2raySni.setText("");
                            }
                        } else {
                            layoutSniSettings.setVisibility(View.GONE);
                            editV2raySni.setText("");
                        }
                    } else {
                        radioNetworkTcp.setChecked(true);
                        layoutWsSettings.setVisibility(View.GONE);
                        editV2rayWsHost.setText("");
                        editV2rayWsPath.setText("");
                        switchV2rayTls.setChecked(false);
                        layoutSniSettings.setVisibility(View.GONE);
                        editV2raySni.setText("");
                    }
                }
            }
        } catch (Exception e) {}
    }

    private String generateJsonFromFields() {
        String host = editV2rayHost.getText().toString().trim();
        int port = 443;
        try {
            port = Integer.parseInt(editV2rayPort.getText().toString().trim());
        } catch (Exception e) {}
        String id = editV2rayId.getText().toString().trim();
        boolean isMux = switchV2rayMux.isChecked();
        boolean isTls = switchV2rayTls.isChecked();
        boolean isVless = radioVless.isChecked();
        String protocolName = isVless ? "vless" : "vmess";
        boolean isTcp = radioNetworkTcp.isChecked();
        String networkName = isTcp ? "tcp" : "ws";
        String wsHost = editV2rayWsHost.getText().toString().trim();
        String wsPath = editV2rayWsPath.getText().toString().trim();
        String sni = editV2raySni.getText().toString().trim();

        try {
            JSONObject json;
            String currentJson = editV2rayJson.getText().toString().trim();
            if (!currentJson.isEmpty()) {
                try {
                    json = new JSONObject(currentJson);
                } catch (Exception e) {
                    json = createDefaultJsonTemplate();
                }
            } else {
                json = createDefaultJsonTemplate();
            }

            JSONArray outbounds = json.optJSONArray("outbounds");
            if (outbounds == null || outbounds.length() == 0) {
                outbounds = new JSONArray();
                json.put("outbounds", outbounds);
            }

            JSONObject outbound = null;
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject out = outbounds.getJSONObject(i);
                String proto = out.optString("protocol", "");
                if ("vmess".equalsIgnoreCase(proto) || "vless".equalsIgnoreCase(proto)) {
                    outbound = out;
                    break;
                }
            }

            if (outbound == null) {
                outbound = new JSONObject();
                outbounds.put(outbound);
            }
            outbound.put("protocol", protocolName);

            JSONObject settings = outbound.optJSONObject("settings");
            if (settings == null) {
                settings = new JSONObject();
                outbound.put("settings", settings);
            }

            JSONArray vnext = settings.optJSONArray("vnext");
            if (vnext == null || vnext.length() == 0) {
                vnext = new JSONArray();
                settings.put("vnext", vnext);
            }

            JSONObject server = null;
            if (vnext.length() > 0) {
                server = vnext.getJSONObject(0);
            } else {
                server = new JSONObject();
                vnext.put(server);
            }

            server.put("address", host);
            server.put("port", port);

            JSONArray users = server.optJSONArray("users");
            if (users == null || users.length() == 0) {
                users = new JSONArray();
                server.put("users", users);
            }

            JSONObject user = new JSONObject();
            user.put("id", id);
            if (isVless) {
                user.put("encryption", "none");
                user.put("level", 8);
            } else {
                user.put("alterId", 0);
                user.put("security", "auto");
            }
            JSONArray newUsers = new JSONArray();
            newUsers.put(user);
            server.put("users", newUsers);

            // Mux
            JSONObject mux = outbound.optJSONObject("mux");
            if (mux == null) {
                mux = new JSONObject();
                outbound.put("mux", mux);
            }
            mux.put("enabled", isMux);
            if (!mux.has("concurrency")) {
                mux.put("concurrency", 8);
            }

            // StreamSettings
            JSONObject streamSettings = outbound.optJSONObject("streamSettings");
            if (streamSettings == null) {
                streamSettings = new JSONObject();
                outbound.put("streamSettings", streamSettings);
            }
            streamSettings.put("network", networkName);
            streamSettings.put("security", isTls ? "tls" : "none");

            if ("ws".equalsIgnoreCase(networkName)) {
                JSONObject wsSettings = streamSettings.optJSONObject("wsSettings");
                if (wsSettings == null) {
                    wsSettings = new JSONObject();
                    streamSettings.put("wsSettings", wsSettings);
                }
                wsSettings.put("path", wsPath);
                
                JSONObject headers = wsSettings.optJSONObject("headers");
                if (headers == null) {
                    headers = new JSONObject();
                    wsSettings.put("headers", headers);
                }
                headers.put("Host", wsHost);
            } else {
                streamSettings.remove("wsSettings");
            }

            if (isTls) {
                JSONObject tlsSettings = streamSettings.optJSONObject("tlsSettings");
                if (tlsSettings == null) {
                    tlsSettings = new JSONObject();
                    streamSettings.put("tlsSettings", tlsSettings);
                }
                tlsSettings.put("serverName", sni);
            } else {
                streamSettings.remove("tlsSettings");
            }

            if (!streamSettings.has("sockopt")) {
                JSONObject sockopt = new JSONObject();
                sockopt.put("mark", 0);
                streamSettings.put("sockopt", sockopt);
            }

            return json.toString(2);
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject createDefaultJsonTemplate() throws Exception {
        JSONObject json = new JSONObject();
        
        JSONObject log = new JSONObject();
        log.put("loglevel", "warning");
        json.put("log", log);

        JSONArray inbounds = new JSONArray();
        JSONObject inbound = new JSONObject();
        inbound.put("port", 1080);
        inbound.put("protocol", "socks");
        JSONObject inboundSettings = new JSONObject();
        inboundSettings.put("auth", "noauth");
        inboundSettings.put("udp", true);
        inbound.put("settings", inboundSettings);
        inbounds.put(inbound);
        json.put("inbounds", inbounds);

        JSONArray outbounds = new JSONArray();
        json.put("outbounds", outbounds);

        return json;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_v2ray_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_toggle_visual) {
            toggleVisualMode();
            return true;
        } else if (id == R.id.action_paste) {
            showPasteDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleVisualMode() {
        if (isVisualMode) {
            // Visual -> JSON
            String generatedJson = generateJsonFromFields();
            editV2rayJson.setText(generatedJson);
            updateLineNumbers();
            
            scrollVisualForm.setVisibility(View.GONE);
            layoutJsonEditor.setVisibility(View.VISIBLE);
            isVisualMode = false;
        } else {
            // JSON -> Visual
            String jsonConfig = editV2rayJson.getText().toString().trim();
            if (!jsonConfig.isEmpty()) {
                try {
                    new JSONObject(jsonConfig); // Check if valid
                } catch (Exception e) {
                    Toast.makeText(this, "JSON inválido, no se puede cambiar al modo visual: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            syncJsonToFields();
            
            layoutJsonEditor.setVisibility(View.GONE);
            scrollVisualForm.setVisibility(View.VISIBLE);
            isVisualMode = true;
        }
    }

    private void showPasteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Importar configuración");

        View view = getLayoutInflater().inflate(R.layout.pref_dialog_edittext_fix, null);
        final EditText editLink = (EditText) view.findViewById(android.R.id.edit);
        editLink.setHint("vmess://... vless://... o JSON");

        builder.setView(view);

        builder.setNeutralButton("Pegar", null); // Handled below to prevent auto-dismiss
        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                parseV2rayLink(editLink.getText().toString());
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    android.content.ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).getText();
                        if (text != null) {
                            editLink.setText(text.toString());
                            editLink.setSelection(editLink.getText().length());
                        }
                    }
                }
            }
        });
    }

    private void parseV2rayLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(this, "Enlace vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        link = link.trim();
        
        if (link.startsWith("vless://")) {
            try {
                java.net.URI uri = new java.net.URI(link);
                String uuid = uri.getUserInfo();
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) port = 443;
                
                editV2rayHost.setText(host);
                editV2rayPort.setText(String.valueOf(port));
                editV2rayId.setText(uuid);
                
                String query = uri.getQuery();
                boolean isTls = false;
                if (query != null) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        String[] idx = pair.split("=");
                        if (idx.length > 1) {
                            if ("security".equalsIgnoreCase(idx[0])) {
                                isTls = "tls".equalsIgnoreCase(idx[1]) || "xtls".equalsIgnoreCase(idx[1]);
                            }
                        }
                    }
                }
                switchV2rayTls.setChecked(isTls);
                switchV2rayMux.setChecked(false);
                radioVless.setChecked(true);
                txtOutboundHeader.setText("VLESS Settings:");
                editV2rayId.setHint("VLESS user (UUID)");

                String generatedJson = generateJsonFromFields();
                editV2rayJson.setText(generatedJson);
                updateLineNumbers();

                Toast.makeText(this, "Configuración VLESS importada. ¡No olvides Guardar!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error al parsear el enlace VLESS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!link.startsWith("vmess://")) {
            // Check if it's raw JSON
            try {
                new JSONObject(link);
                editV2rayJson.setText(link);
                updateLineNumbers();
                syncJsonToFieldsSilent();
                Toast.makeText(this, "Configuración JSON importada. ¡No olvides Guardar!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Esquema no soportado o JSON inválido", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try {
            String base64 = link.substring("vmess://".length());
            byte[] data;
            try {
                data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            } catch (Exception e) {
                data = android.util.Base64.decode(base64, android.util.Base64.URL_SAFE);
            }
            String jsonStr = new String(data, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);

            String add = json.optString("add", "");
            String port = json.optString("port", "443");
            String id = json.optString("id", "");
            String tls = json.optString("tls", "");
            
            editV2rayHost.setText(add);
            editV2rayPort.setText(port);
            editV2rayId.setText(id);
            switchV2rayTls.setChecked("tls".equalsIgnoreCase(tls));
            switchV2rayMux.setChecked(false);
            radioVmess.setChecked(true);
            txtOutboundHeader.setText("VMess Settings:");
            editV2rayId.setHint("Vmess user");

            String generatedJson = generateJsonFromFields();
            editV2rayJson.setText(generatedJson);
            updateLineNumbers();

            Toast.makeText(this, "Configuración VMess importada. ¡No olvides Guardar!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al parsear el enlace VMess: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
