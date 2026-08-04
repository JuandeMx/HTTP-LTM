package com.slipkprojects.sockshttp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class UdpSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_udp_settings)

        val serverEdit = findViewById<EditText>(R.id.editServer)
        val passwordEdit = findViewById<EditText>(R.id.editPassword)
        val sniEdit = findViewById<EditText>(R.id.editSni)
        val obfsEdit = findViewById<EditText>(R.id.editObfs)
        val upEdit = findViewById<EditText>(R.id.editUp)
        val downEdit = findViewById<EditText>(R.id.editDown)
        val startBtn = findViewById<Button>(R.id.btnStartVpn)

        // Default values as requested
        serverEdit.setText("127.0.0.1:36712")
        upEdit.setText("10")
        downEdit.setText("50")

        startBtn.setOnClickListener {
            val intent = Intent(this, com.slipkprojects.sockshttp.vpn.NetFreeVpnService::class.java)
            intent.putExtra("SERVER_IP", serverEdit.text.toString().split(":")[0])
            intent.putExtra("SERVER_PORT", serverEdit.text.toString().split(":")[1].toInt())
            intent.putExtra("AUTH_PASSWORD", passwordEdit.text.toString())
            intent.putExtra("SNI", sniEdit.text.toString())
            intent.putExtra("OBFS_PASSWORD", obfsEdit.text.toString())
            intent.putExtra("UP_Mbps", upEdit.text.toString().toInt())
            intent.putExtra("DOWN_Mbps", downEdit.text.toString().toInt())
            startService(intent)
        }
    }
}
