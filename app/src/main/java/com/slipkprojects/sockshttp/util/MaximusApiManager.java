package com.slipkprojects.sockshttp.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.slipkprojects.ultrasshservice.config.ConfigParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MaximusApiManager - Cliente de Comunicación con Backend Maestro MAXIMUS
 * Soporta respuestas JSON O la descarga directa de archivos cifrados raw .MX
 * Incluye sistema de LOG detallado en archivo maximus_api.log
 */
public class MaximusApiManager {
    private static final String TAG = "MaximusApiManager";
    public static final String DEFAULT_MASTER_URL = "http://187.127.17.250:8080";

    // =====================================================================
    // SISTEMA DE LOG EN ARCHIVO
    // =====================================================================
    private static final String LOG_FILENAME = "maximus_api.log";

    /**
     * Escribe una línea de log con timestamp al archivo maximus_api.log
     * dentro del almacenamiento interno de la app.
     * Para leerlo: adb shell run-as com.slipkprojects.sockshttp cat files/maximus_api.log
     */
    public static synchronized void writeLog(Context ctx, String level, String message) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String line = "[" + timestamp + "] [" + level + "] " + message + "\n";

            // También mostrar en Logcat
            if ("ERROR".equals(level)) {
                Log.e(TAG, message);
            } else {
                Log.d(TAG, message);
            }

            // Escribir al archivo de log persistente
            File logFile = new File(ctx.getFilesDir(), LOG_FILENAME);
            FileWriter fw = new FileWriter(logFile, true); // append=true
            fw.write(line);
            fw.flush();
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Error escribiendo log: " + e.getMessage());
        }
    }

    /**
     * Lee todo el contenido del archivo de log
     */
    public static String readLog(Context ctx) {
        try {
            File logFile = new File(ctx.getFilesDir(), LOG_FILENAME);
            if (!logFile.exists()) return "(Log vacío)";

            java.io.FileInputStream fis = new java.io.FileInputStream(logFile);
            byte[] data = new byte[(int) logFile.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "Error leyendo log: " + e.getMessage();
        }
    }

    /**
     * Limpia el archivo de log
     */
    public static void clearLog(Context ctx) {
        try {
            File logFile = new File(ctx.getFilesDir(), LOG_FILENAME);
            if (logFile.exists()) logFile.delete();
        } catch (Exception e) {}
    }

    /**
     * Obtiene el stacktrace de una excepción como String
     */
    private static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // =====================================================================
    // INTERFACES Y MODELOS
    // =====================================================================
    public interface OnNodesLoadedListener {
        void onSuccess(List<NodeModel> nodes);
        void onError(String errorMessage);
    }

    public interface OnMethodsLoadedListener {
        void onSuccess(List<MethodModel> methods);
        void onError(String errorMessage);
    }

    public interface OnValidationListener {
        void onResult(boolean valid, String expDate, int daysLeft, String message);
    }

    public static class NodeModel {
        public long id;
        public String name;
        public String ip;
        public String domainCf;
        public String domainCft;

        @Override
        public String toString() {
            return name != null ? name : "Nodo #" + id;
        }
    }

    public static class MethodModel {
        public long id;
        public String name;
        public String vpsName;
        public String sshHost;
        public int sshPort;
        public String sshUser;
        public String sshPass;
        public String protocol;
        public String sni;
        public String payload;
        public String mxContent; // Base64 Cifrado de Archivo .MX

        @Override
        public String toString() {
            return name != null ? name : "Método #" + id;
        }
    }

    /**
     * OBTENER LISTA DE NODOS / SERVIDORES VPS
     * GET /api/nodes
     */
    public static void fetchNodes(final Context context, final String baseUrl, final OnNodesLoadedListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                try {
                    String cleanUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : DEFAULT_MASTER_URL;
                    if (!cleanUrl.endsWith("/")) cleanUrl += "/";
                    String fullUrl = cleanUrl + "api/nodes";

                    writeLog(context, "INFO", "fetchNodes: Solicitando servidores VPS desde " + fullUrl);

                    URL url = new URL(fullUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "MAXIMUS-VPN-AndroidApp");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int status = conn.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new Exception("Error HTTP " + status + " obteniendo nodos");
                    }

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    conn.disconnect();

                    String rawStr = out.toString("UTF-8").trim();
                    if (rawStr.startsWith("\uFEFF")) rawStr = rawStr.substring(1).trim();

                    JSONArray arr = null;
                    if (rawStr.startsWith("[")) {
                        arr = new JSONArray(rawStr);
                    } else {
                        JSONObject root = new JSONObject(rawStr);
                        if (root.has("nodes")) arr = root.getJSONArray("nodes");
                        else if (root.has("servers")) arr = root.getJSONArray("servers");
                        else if (root.has("data")) arr = root.getJSONArray("data");
                    }

                    final List<NodeModel> list = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            NodeModel node = new NodeModel();
                            node.id = obj.optLong("id", i + 1);
                            node.name = obj.optString("name", "Servidor " + (i + 1));
                            node.ip = obj.optString("ip", "");
                            node.domainCf = obj.optString("domain_cf", "");
                            node.domainCft = obj.optString("domain_cft", "");
                            list.add(node);
                        }
                    }

                    writeLog(context, "OK", "fetchNodes: " + list.size() + " servidores cargados");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onSuccess(list);
                        }
                    });

                } catch (final Exception e) {
                    writeLog(context, "ERROR", "fetchNodes EXCEPCIÓN: " + e.getMessage());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onError("Error al obtener servidores: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Importa y descifra un archivo/contenido .MX en la aplicación Android
     */
    public static boolean importMxConfig(Context context, String mxContent) {
        if (mxContent == null || mxContent.trim().isEmpty()) {
            writeLog(context, "WARN", "importMxConfig: mxContent está vacío o es null");
            return false;
        }
        try {
            writeLog(context, "INFO", "importMxConfig: Descifrando perfil .MX (" + mxContent.length() + " chars)...");
            byte[] bytes = mxContent.trim().getBytes("UTF-8");
            InputStream is = new ByteArrayInputStream(bytes);
            boolean result = ConfigParser.convertInputAndSave(is, context);
            writeLog(context, result ? "OK" : "ERROR", "importMxConfig: Resultado descifrado = " + result);
            return result;
        } catch (Exception e) {
            writeLog(context, "ERROR", "importMxConfig: EXCEPCIÓN al descifrar: " + e.getMessage());
            writeLog(context, "ERROR", "importMxConfig: StackTrace:\n" + getStackTrace(e));
            return false;
        }
    }

    public static void fetchMethods(final Context context, final String baseUrl, final OnMethodsLoadedListener listener) {
        fetchMethods(context, baseUrl, null, listener);
    }

    /**
     * 1. OBTENER PERFILES DE CONEXIÓN (FILTRADOS POR NODO/MÁQUINA VPS)
     * GET /api/methods?node_name=Brasil 1
     */
    public static void fetchMethods(final Context context, final String baseUrl, final String nodeName, final OnMethodsLoadedListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                try {
                    String cleanUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : DEFAULT_MASTER_URL;
                    if (!cleanUrl.endsWith("/")) cleanUrl += "/";
                    String fullUrl = cleanUrl + "api/methods";
                    if (nodeName != null && !nodeName.trim().isEmpty()) {
                        fullUrl += "?node_name=" + java.net.URLEncoder.encode(nodeName.trim(), "UTF-8");
                    }

                    writeLog(context, "INFO", "════════════════════════════════════════");
                    writeLog(context, "INFO", "fetchMethods: INICIANDO DESCARGA DE MÉTODOS");
                    writeLog(context, "INFO", "fetchMethods: URL = " + fullUrl);
                    writeLog(context, "INFO", "fetchMethods: Master URL base = " + cleanUrl);

                    URL url = new URL(fullUrl);
                    writeLog(context, "INFO", "fetchMethods: Abriendo conexión HTTP...");

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "MAXIMUS-VPN-AndroidApp");
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);

                    writeLog(context, "INFO", "fetchMethods: Esperando respuesta del servidor...");
                    int status = conn.getResponseCode();
                    writeLog(context, "INFO", "fetchMethods: HTTP Status = " + status);

                    if (status != HttpURLConnection.HTTP_OK) {
                        // Intentar leer el cuerpo de error
                        String errorBody = "";
                        try {
                            InputStream errStream = conn.getErrorStream();
                            if (errStream != null) {
                                ByteArrayOutputStream errOut = new ByteArrayOutputStream();
                                byte[] buf = new byte[1024];
                                int l;
                                while ((l = errStream.read(buf)) != -1) errOut.write(buf, 0, l);
                                errStream.close();
                                errorBody = errOut.toString("UTF-8");
                            }
                        } catch (Exception ignored) {}
                        writeLog(context, "ERROR", "fetchMethods: ERROR HTTP " + status + " | Body: " + errorBody);
                        throw new Exception("Error HTTP " + status + " | " + errorBody);
                    }

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    conn.disconnect();

                    String rawStr = out.toString("UTF-8").trim();
                    if (rawStr.startsWith("\uFEFF")) {
                        rawStr = rawStr.substring(1).trim();
                    }

                    writeLog(context, "INFO", "fetchMethods: Respuesta recibida (" + rawStr.length() + " bytes)");
                    // Mostrar los primeros 500 chars de la respuesta
                    writeLog(context, "INFO", "fetchMethods: Contenido (preview): " + rawStr.substring(0, Math.min(rawStr.length(), 500)));

                    final List<MethodModel> list = new ArrayList<>();

                    // SI LA RESPUESTA ES UN ARCHIVO RAW .MX DIRECTAMENTE
                    if (!rawStr.startsWith("[") && !rawStr.startsWith("{")) {
                        writeLog(context, "INFO", "fetchMethods: Respuesta es archivo RAW .MX (no JSON)");
                        boolean ok = importMxConfig(context, rawStr);
                        if (ok) {
                            MethodModel m = new MethodModel();
                            m.id = 1;
                            m.name = "Perfil Maestro (.MX)";
                            m.mxContent = rawStr;
                            list.add(m);
                            writeLog(context, "OK", "fetchMethods: Archivo .MX importado exitosamente");
                        } else {
                            writeLog(context, "ERROR", "fetchMethods: No se pudo descifrar archivo .MX raw");
                            throw new Exception("El servidor envió un archivo .MX pero no se pudo descifrar (Formato no válido)");
                        }
                    } else {
                        // SI LA RESPUESTA ES UN ARREGLO O ESTRUCTURA JSON
                        writeLog(context, "INFO", "fetchMethods: Respuesta es JSON, parseando...");
                        JSONArray arr = null;
                        if (rawStr.startsWith("[")) {
                            arr = new JSONArray(rawStr);
                        } else {
                            JSONObject root = new JSONObject(rawStr);
                            if (root.has("methods")) arr = root.getJSONArray("methods");
                            else if (root.has("data")) arr = root.getJSONArray("data");
                            else if (root.has("configs")) arr = root.getJSONArray("configs");
                        }

                        if (arr == null) {
                            writeLog(context, "ERROR", "fetchMethods: JSON sin lista de métodos válida");
                            throw new Exception("Respuesta JSON sin lista de métodos válida");
                        }

                        writeLog(context, "INFO", "fetchMethods: Se encontraron " + arr.length() + " métodos en el JSON");

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            MethodModel m = new MethodModel();
                            m.id = obj.optLong("id", i + 1);

                            if (obj.has("name")) m.name = obj.optString("name");
                            else if (obj.has("title")) m.name = obj.optString("title");
                            else if (obj.has("nombre")) m.name = obj.optString("nombre");
                            else m.name = "Método " + (i + 1);

                            if (obj.has("ssh_host")) m.sshHost = obj.optString("ssh_host");
                            else if (obj.has("sshHost")) m.sshHost = obj.optString("sshHost");
                            else if (obj.has("host")) m.sshHost = obj.optString("host");
                            else if (obj.has("server")) m.sshHost = obj.optString("server");

                            if (obj.has("ssh_port")) m.sshPort = obj.optInt("ssh_port", 22);
                            else if (obj.has("sshPort")) m.sshPort = obj.optInt("sshPort", 22);
                            else if (obj.has("port")) m.sshPort = obj.optInt("port", 22);

                            if (obj.has("ssh_user")) m.sshUser = obj.optString("ssh_user");
                            else if (obj.has("sshUser")) m.sshUser = obj.optString("sshUser");

                            if (obj.has("ssh_pass")) m.sshPass = obj.optString("ssh_pass");
                            else if (obj.has("sshPass")) m.sshPass = obj.optString("sshPass");

                            if (obj.has("vps_name")) m.vpsName = obj.optString("vps_name");
                            else if (obj.has("vpsName")) m.vpsName = obj.optString("vpsName");
                            else if (obj.has("node_name")) m.vpsName = obj.optString("node_name");

                            if (obj.has("protocol")) m.protocol = obj.optString("protocol");
                            if (obj.has("sni")) m.sni = obj.optString("sni");
                            if (obj.has("payload")) m.payload = obj.optString("payload");

                            if (obj.has("mx_content")) m.mxContent = obj.optString("mx_content");
                            else if (obj.has("mxContent")) m.mxContent = obj.optString("mxContent");
                            else if (obj.has("encrypted_data")) m.mxContent = obj.optString("encrypted_data");

                            if (m.payload != null) {
                                m.payload = m.payload.replace("[crlf]", "\r\n").replace("[CRLF]", "\r\n").replace("[lf]", "\n").replace("[LF]", "\n");
                            }

                            writeLog(context, "INFO", "  → Método #" + (i+1) + ": " + m.name
                                + " | Host=" + m.sshHost + ":" + m.sshPort
                                + " | SNI=" + m.sni
                                + " | MX=" + (m.mxContent != null && !m.mxContent.isEmpty() ? m.mxContent.length() + " chars" : "vacío"));

                            list.add(m);
                        }
                    }

                    writeLog(context, "OK", "fetchMethods: COMPLETADO - " + list.size() + " métodos cargados");
                    writeLog(context, "INFO", "════════════════════════════════════════");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onSuccess(list);
                        }
                    });

                } catch (final Exception e) {
                    writeLog(context, "ERROR", "fetchMethods: EXCEPCIÓN: " + e.getMessage());
                    writeLog(context, "ERROR", "fetchMethods: StackTrace:\n" + getStackTrace(e));
                    writeLog(context, "INFO", "════════════════════════════════════════");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onError("Error al conectar con el Maestro: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 2. AUTENTICAR / VALIDAR CLIENTE (LOGIN)
     * POST /api/client/validate
     */
    public static void validateClient(final Context context, final String baseUrl, final String username, final String password, final OnValidationListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                try {
                    String cleanUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : DEFAULT_MASTER_URL;
                    if (!cleanUrl.endsWith("/")) cleanUrl += "/";
                    String fullUrl = cleanUrl + "api/client/validate";

                    writeLog(context, "INFO", "════════════════════════════════════════");
                    writeLog(context, "INFO", "validateClient: VALIDANDO USUARIO");
                    writeLog(context, "INFO", "validateClient: URL = " + fullUrl);
                    writeLog(context, "INFO", "validateClient: Usuario = " + username);

                    URL url = new URL(fullUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(20000);
                    conn.setDoOutput(true);

                    JSONObject body = new JSONObject();
                    body.put("username", username);
                    body.put("password", password);

                    writeLog(context, "INFO", "validateClient: Enviando POST con body: " + body.toString());

                    byte[] postBytes = body.toString().getBytes("UTF-8");
                    OutputStream os = conn.getOutputStream();
                    os.write(postBytes);
                    os.flush();
                    os.close();

                    int status = conn.getResponseCode();
                    writeLog(context, "INFO", "validateClient: HTTP Status = " + status);

                    InputStream in;
                    if (status >= 200 && status < 300) {
                        in = new BufferedInputStream(conn.getInputStream());
                    } else {
                        in = new BufferedInputStream(conn.getErrorStream());
                    }

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while (in != null && (len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    if (in != null) in.close();
                    conn.disconnect();

                    String respStr = out.toString("UTF-8");
                    writeLog(context, "INFO", "validateClient: Respuesta: " + respStr);

                    JSONObject resObj = new JSONObject(respStr);

                    final boolean valid = resObj.optBoolean("valid", false);
                    final String expDate = resObj.optString("exp_date", "");
                    final int daysLeft = resObj.optInt("days_left", 0);
                    final String message = resObj.optString("message", valid ? "Acceso concedido." : "Usuario/contraseña incorrectos o cuenta vencida.");

                    writeLog(context, valid ? "OK" : "WARN", "validateClient: valid=" + valid + " | expDate=" + expDate + " | daysLeft=" + daysLeft);
                    writeLog(context, "INFO", "════════════════════════════════════════");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onResult(valid, expDate, daysLeft, message);
                        }
                    });

                } catch (final Exception e) {
                    writeLog(context, "ERROR", "validateClient: EXCEPCIÓN: " + e.getMessage());
                    writeLog(context, "ERROR", "validateClient: StackTrace:\n" + getStackTrace(e));
                    writeLog(context, "INFO", "════════════════════════════════════════");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onResult(false, "", 0, "Error de red: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
