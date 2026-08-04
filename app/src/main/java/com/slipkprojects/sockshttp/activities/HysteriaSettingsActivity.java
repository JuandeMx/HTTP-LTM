package com.slipkprojects.sockshttp.activities;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.SwitchCompat;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Button;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.SharedPreferences;
import android.content.Context;
import android.widget.Toast;
import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;

import com.slipkprojects.sockshttp.R;
import com.slipkprojects.ultrasshservice.config.Settings;
import com.slipkprojects.ultrasshservice.config.SettingsConstants;

public class HysteriaSettingsActivity extends BaseActivity {

    private EditText editHysteriaHost;
    private EditText editHysteriaPort;
    private EditText editHysteriaAuth;
    private EditText editHysteriaObfs;
    private EditText editHysteriaSni;
    private EditText editHysteriaAlpn;
    private SwitchCompat switchHysteriaInsecure;
    private TextView txtHysteriaCert;
    private EditText editHysteriaHopping;
    private EditText editHysteriaUp;
    private EditText editHysteriaDown;
    private TextView txtHysteriaQuicWindowValue;
    private SeekBar seekbarHysteriaQuicWindow;
    private SwitchCompat switchHysteriaDisableMtu;
    private Button btnSaveHysteriaSettings;

    private Settings mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hysteria_settings);

        mConfig = new Settings(this);

        // Toolbar
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Hysteria");
        }

        // Bind views
        editHysteriaHost = (EditText) findViewById(R.id.editHysteriaHost);
        editHysteriaPort = (EditText) findViewById(R.id.editHysteriaPort);
        editHysteriaAuth = (EditText) findViewById(R.id.editHysteriaAuth);
        editHysteriaObfs = (EditText) findViewById(R.id.editHysteriaObfs);
        editHysteriaSni = (EditText) findViewById(R.id.editHysteriaSni);
        editHysteriaAlpn = (EditText) findViewById(R.id.editHysteriaAlpn);
        switchHysteriaInsecure = (SwitchCompat) findViewById(R.id.switchHysteriaInsecure);
        txtHysteriaCert = (TextView) findViewById(R.id.txtHysteriaCert);
        editHysteriaHopping = (EditText) findViewById(R.id.editHysteriaHopping);
        editHysteriaUp = (EditText) findViewById(R.id.editHysteriaUp);
        editHysteriaDown = (EditText) findViewById(R.id.editHysteriaDown);
        txtHysteriaQuicWindowValue = (TextView) findViewById(R.id.txtHysteriaQuicWindowValue);
        seekbarHysteriaQuicWindow = (SeekBar) findViewById(R.id.seekbarHysteriaQuicWindow);
        switchHysteriaDisableMtu = (SwitchCompat) findViewById(R.id.switchHysteriaDisableMtu);
        btnSaveHysteriaSettings = (Button) findViewById(R.id.btnSaveHysteriaSettings);

        // Load values
        loadSettings();

        boolean isProtected = mConfig.getPrefsPrivate().getBoolean(Settings.CONFIG_PROTEGER_KEY, false);
        if (isProtected) {
            editHysteriaHost.setEnabled(false);
            editHysteriaPort.setEnabled(false);
            editHysteriaAuth.setEnabled(false);
            editHysteriaObfs.setEnabled(false);
            editHysteriaSni.setEnabled(false);
            editHysteriaAlpn.setEnabled(false);
            switchHysteriaInsecure.setEnabled(false);
            editHysteriaHopping.setEnabled(false);
            editHysteriaUp.setEnabled(false);
            editHysteriaDown.setEnabled(false);
            seekbarHysteriaQuicWindow.setEnabled(false);
            switchHysteriaDisableMtu.setEnabled(false);
            btnSaveHysteriaSettings.setVisibility(View.GONE);
        }

        // QUIC window SeekBar listener
        seekbarHysteriaQuicWindow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) {
                    progress = 1;
                    seekBar.setProgress(1);
                }
                txtHysteriaQuicWindowValue.setText(progress + " MB");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Save button listener
        btnSaveHysteriaSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = mConfig.getPrefsPrivate();

        editHysteriaHost.setText(prefs.getString("hysteria_host", ""));
        editHysteriaPort.setText(prefs.getString("hysteria_port", ""));
        editHysteriaAuth.setText(prefs.getString("hysteria_auth", ""));
        
        // Obfs fallback to general obfs key if specific not found
        editHysteriaObfs.setText(prefs.getString("hysteria_obfs", 
            prefs.getString(SettingsConstants.UDP_CUSTOM_OBFS_KEY, "")));
            
        editHysteriaSni.setText(prefs.getString("hysteria_sni", 
            prefs.getString(SettingsConstants.CUSTOM_SNI, "")));
            
        editHysteriaAlpn.setText(prefs.getString("hysteria_alpn", ""));
        switchHysteriaInsecure.setChecked(prefs.getBoolean("hysteria_insecure", true));
        txtHysteriaCert.setText(prefs.getString("hysteria_cert", "-"));
        editHysteriaHopping.setText(prefs.getString("hysteria_hopping", "10"));
        
        // Up/down fallbacks
        editHysteriaUp.setText(prefs.getString("hysteria_up", 
            prefs.getString(SettingsConstants.UDP_CUSTOM_UP_KEY, "50")));
        editHysteriaDown.setText(prefs.getString("hysteria_down", 
            prefs.getString(SettingsConstants.UDP_CUSTOM_DOWN_KEY, "100")));
            
        int quicWin = prefs.getInt("hysteria_quic_window", 8);
        seekbarHysteriaQuicWindow.setProgress(quicWin);
        txtHysteriaQuicWindowValue.setText(quicWin + " MB");
        
        switchHysteriaDisableMtu.setChecked(prefs.getBoolean("hysteria_disable_mtu", false));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = mConfig.getPrefsPrivate().edit();

        String host = editHysteriaHost.getText().toString().trim();
        String port = editHysteriaPort.getText().toString().trim();
        String auth = editHysteriaAuth.getText().toString().trim();
        String obfs = editHysteriaObfs.getText().toString().trim();
        String sni = editHysteriaSni.getText().toString().trim();
        String alpn = editHysteriaAlpn.getText().toString().trim();
        boolean insecure = switchHysteriaInsecure.isChecked();
        String hopping = editHysteriaHopping.getText().toString().trim();
        String up = editHysteriaUp.getText().toString().trim();
        String down = editHysteriaDown.getText().toString().trim();
        int quicWin = seekbarHysteriaQuicWindow.getProgress();
        boolean disableMtu = switchHysteriaDisableMtu.isChecked();

        editor.putString("hysteria_host", host);
        editor.putString("hysteria_port", port);
        editor.putString("hysteria_auth", auth);
        editor.putString("hysteria_obfs", obfs);
        editor.putString("hysteria_sni", sni);
        editor.putString("hysteria_alpn", alpn);
        editor.putBoolean("hysteria_insecure", insecure);
        editor.putString("hysteria_hopping", hopping);
        editor.putString("hysteria_up", up);
        editor.putString("hysteria_down", down);
        editor.putInt("hysteria_quic_window", quicWin);
        editor.putBoolean("hysteria_disable_mtu", disableMtu);

        // Map back to legacy keys for compatibility with parts of the service reading old properties
        editor.putString(SettingsConstants.UDP_CUSTOM_OBFS_KEY, obfs);
        editor.putString(SettingsConstants.UDP_CUSTOM_UP_KEY, up);
        editor.putString(SettingsConstants.UDP_CUSTOM_DOWN_KEY, down);
        if (!sni.isEmpty()) {
            editor.putString(SettingsConstants.CUSTOM_SNI, sni);
        }

        editor.apply();

        Toast.makeText(this, "Ajustes de Hysteria guardados", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_hysteria_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_import) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_help) {
            showHelpDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Importar configuración");

        View view = getLayoutInflater().inflate(R.layout.pref_dialog_edittext_fix, null);
        final EditText editLink = (EditText) view.findViewById(android.R.id.edit);
        editLink.setHint("hysteria2://auth@host:port/?key=value");

        builder.setView(view);

        builder.setNeutralButton("Pegar", null); // Handled separately below to prevent auto-dismiss
        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                parseHysteriaLink(editLink.getText().toString());
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override Neutral button to prevent dialog dismiss when clicking "Pegar"
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

    private void parseHysteriaLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(this, "Enlace vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        link = link.trim();
        String scheme = "";
        if (link.startsWith("hysteria2://")) {
            scheme = "hysteria2://";
        } else if (link.startsWith("hysteria://")) {
            scheme = "hysteria://";
        } else if (link.startsWith("hy2://")) {
            scheme = "hy2://";
        } else {
            Toast.makeText(this, "Esquema no soportado (debe ser hysteria://, hysteria2:// o hy2://)", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Remove scheme
            String remainder = link.substring(scheme.length());
            
            // Remove fragment if present (e.g. #Roman)
            int hashIndex = remainder.indexOf('#');
            if (hashIndex != -1) {
                remainder = remainder.substring(0, hashIndex);
            }
            
            // Separate main part and query parameters
            String mainPart = remainder;
            String queryPart = "";
            int queryIndex = remainder.indexOf('?');
            if (queryIndex != -1) {
                mainPart = remainder.substring(0, queryIndex);
                queryPart = remainder.substring(queryIndex + 1);
            }
            
            // Parse UserInfo (auth password) and Host:Port
            String auth = "";
            String hostPort = mainPart;
            int atIndex = mainPart.indexOf('@');
            if (atIndex != -1) {
                auth = mainPart.substring(0, atIndex);
                hostPort = mainPart.substring(atIndex + 1);
            }
            
            try {
                auth = Uri.decode(auth);
            } catch (Exception e) {}
            
            // Parse Host and Port (handling range e.g. 2000-5000)
            String host = hostPort;
            String port = "";
            int colonIndex = hostPort.lastIndexOf(':');
            if (colonIndex != -1) {
                host = hostPort.substring(0, colonIndex);
                port = hostPort.substring(colonIndex + 1);
            }
            
            // Parse query parameters
            String sni = "";
            String obfs = "";
            String obfsPassword = "";
            boolean insecure = false;
            String alpn = "";
            String up = "";
            String down = "";
            
            if (!queryPart.isEmpty()) {
                String[] params = queryPart.split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv.length >= 2) {
                        String key = Uri.decode(kv[0]).trim();
                        String val = Uri.decode(kv[1]).trim();
                        
                        if (key.equalsIgnoreCase("sni")) {
                            sni = val;
                        } else if (key.equalsIgnoreCase("obfs")) {
                            obfs = val;
                        } else if (key.equalsIgnoreCase("obfs-password")) {
                            obfsPassword = val;
                        } else if (key.equalsIgnoreCase("insecure")) {
                            insecure = "1".equals(val) || "true".equalsIgnoreCase(val);
                        } else if (key.equalsIgnoreCase("alpn")) {
                            alpn = val;
                        } else if (key.equalsIgnoreCase("up")) {
                            up = val;
                        } else if (key.equalsIgnoreCase("down")) {
                            down = val;
                        }
                    }
                }
            }
            
            if (obfsPassword.isEmpty()) {
                obfsPassword = obfs;
            }

            if (!host.isEmpty()) editHysteriaHost.setText(host);
            editHysteriaPort.setText(port);
            editHysteriaAuth.setText(auth);
            if (!obfsPassword.isEmpty()) editHysteriaObfs.setText(obfsPassword);
            if (!sni.isEmpty()) editHysteriaSni.setText(sni);
            if (!alpn.isEmpty()) editHysteriaAlpn.setText(alpn);
            switchHysteriaInsecure.setChecked(insecure);

            if (!up.isEmpty()) {
                String cleanUp = up.replaceAll("(?i)(mbps|m)", "").trim();
                editHysteriaUp.setText(cleanUp);
            }
            if (!down.isEmpty()) {
                String cleanDown = down.replaceAll("(?i)(mbps|m)", "").trim();
                editHysteriaDown.setText(cleanDown);
            }

            Toast.makeText(this, "Configuración importada. ¡No olvides Guardar!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al parsear el enlace: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Ayuda de Hysteria")
            .setMessage("Hysteria es un proxy/túnel de red ultrarrápido optimizado para entornos con pérdida de paquetes.\n\n" +
                        "Puede importar un enlace configurado en el portapapeles tocando el icono de archivo en la esquina superior derecha y presionando 'Pegar'.")
            .setPositiveButton("Entendido", null)
            .show();
    }
}
