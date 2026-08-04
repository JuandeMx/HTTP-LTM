package com.slipkprojects.sockshttp.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

class NetFreeVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var hysteriaProcess: Process? = null
    private var tun2socksProcess: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverIp = intent?.getStringExtra("SERVER_IP") ?: "127.0.0.1"
        val serverPort = intent?.getIntExtra("SERVER_PORT", 36712) ?: 36712
        val authPassword = intent?.getStringExtra("AUTH_PASSWORD") ?: ""
        val sni = intent?.getStringExtra("SNI") ?: ""
        val obfsPassword = intent?.getStringExtra("OBFS_PASSWORD") ?: ""
        val upMbps = intent?.getIntExtra("UP_Mbps", 10) ?: 10
        val downMbps = intent?.getIntExtra("DOWN_Mbps", 50) ?: 50
        val xorKey = "FreeLatamKey" // custom secret key

        startHysteriaClient(serverIp, serverPort, authPassword, sni, obfsPassword, upMbps, downMbps, xorKey)
        setupVpnInterface()
        startTun2Socks()
        return START_STICKY
    }

    private fun startHysteriaClient(
        serverIp: String,
        serverPort: Int,
        auth: String,
        sni: String,
        obfsPwd: String,
        up: Int,
        down: Int,
        xorKey: String
    ) {
        val libDir = applicationInfo.nativeLibraryDir
        val libHysteria = File(libDir, "libhysteria.so")

        val configJson = """
        {
          \"server\": \"$serverIp:$serverPort\",
          \"auth\": \"$auth\",
          \"tls\": {\n            \"sni\": \"$sni\",\n            \"insecure\": true\n          },\n          \"obfs\": {\n            \"type\": \"salamander\",\n            \"password\": \"$obfsPwd\"\n          },\n          \"up_mbps\": $up,
          \"down_mbps\": $down,
          \"socks5\": {\n            \"listen\": \"127.0.0.1:1080\"\n          }\n        }
        """.trimIndent()

        val encryptedConfig = xorEncrypt(configJson, xorKey)

        val builder = ProcessBuilder(
            libHysteria.absolutePath,
            "-c", encryptedConfig,
            "-s", "NO_SIGNATURE_CHECK"
        )
        builder.redirectErrorStream(true)
        Thread {
            try {
                hysteriaProcess = builder.start()
                hysteriaProcess?.inputStream?.bufferedReader()?.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        Log.d("HYSTERIA", line ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Error launching libhysteria.so", e)
            }
        }.start()
    }

    private fun setupVpnInterface() {
        val builder = Builder()
        builder.setMtu(1500)
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")
        builder.addDnsServer("1.1.1.1")
        builder.setSession("NetFree UDP Custom")
        vpnInterface = builder.establish()
    }

    private fun startTun2Socks() {
        val libDir = applicationInfo.nativeLibraryDir
        val libTun2Socks = File(libDir, "libtun2socks.so")
        val fd = vpnInterface?.fd ?: return
        val builder = ProcessBuilder(
            libTun2Socks.absolutePath,
            "--netif-ipaddr", "10.0.0.2",
            "--netif-netmask", "255.255.255.0",
            "--socks-server-addr", "127.0.0.1:1080",
            "--tunfd", fd.toString(),
            "--tunmtu", "1500"
        )
        Thread {
            try {
                tun2socksProcess = builder.start()
                tun2socksProcess?.waitFor()
            } catch (e: Exception) {
                Log.e("VPN", "Error launching tun2socks", e)
            }
        }.start()
    }

    private fun xorEncrypt(input: String, key: String): String {
        val sb = StringBuilder()
        for (i in input.indices) {
            sb.append((input[i].code xor key[i % key.length].code).toChar())
        }
        return sb.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        hysteriaProcess?.destroy()
        tun2socksProcess?.destroy()
        vpnInterface?.close()
    }
}
