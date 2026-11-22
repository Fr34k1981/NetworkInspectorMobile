package br.com.networkinspector

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var report: TextView
    private lateinit var status: TextView
    private val results = StringBuilder("Relatório de diagnóstico da rede\n")
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 23 && permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 40)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFF101827.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "DIAGNÓSTICO WI-FI"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        status = TextView(this).apply {
            text = "Analise sua rede doméstica"
            textSize = 15f
            setTextColor(0xFFB9C7DA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        report = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE5EDF8.toInt())
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1D293B.toInt())
            text = "Toque em uma opção para começar.\n\nObservação: a descoberta de dispositivos depende do roteador e das permissões do Android."
        }
        content.addView(title)
        content.addView(status)
        addAction(content, "INFORMAÇÕES DO WI-FI") { runTask { wifiInfo() } }
        addAction(content, "TESTAR PING E PERDA DE PACOTES") { runTask { pingTest() } }
        addAction(content, "TESTAR DNS") { runTask { dnsTest() } }
        addAction(content, "PROCURAR DISPOSITIVOS NA REDE") { runTask { scanLan() } }
        addAction(content, "TESTAR VELOCIDADE") { runTask { speedTest() } }
        addAction(content, "EXECUTAR DIAGNÓSTICO COMPLETO") {
            runTask { wifiInfo(); pingTest(); dnsTest(); scanLan(); speedTest() }
        }
        addAction(content, "COPIAR RELATÓRIO") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Wi-Fi", results.toString()))
            Toast.makeText(this, "Relatório copiado", Toast.LENGTH_SHORT).show()
        }
        content.addView(report, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8
        })
    }

    private fun runTask(task: () -> Unit) {
        status.text = "Executando teste..."
        executor.execute {
            try { task() } catch (e: Exception) { append("Erro: ${e.message ?: e.javaClass.simpleName}") }
            runOnUiThread { status.text = "Teste concluído" }
        }
    }

    @Synchronized private fun append(line: String) {
        results.append("\n").append(line).append("\n")
        val snapshot = results.toString()
        runOnUiThread { report.text = snapshot }
    }

    private fun wifiInfo() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        val ip = Formatter.formatIpAddress(info.ipAddress)
        val ssid = info.ssid?.replace("\"", "") ?: "indisponível"
        val frequency = if (Build.VERSION.SDK_INT >= 21) info.frequency else 0
        val band = when {
            frequency in 2400..2500 -> "2,4 GHz"
            frequency in 4900..5900 -> "5 GHz"
            frequency >= 5925 -> "6 GHz"
            else -> "não identificada"
        }
        append("WI-FI\nRede: $ssid\nIP do celular: $ip\nFrequência: ${if (frequency > 0) "$frequency MHz ($band)" else band}\nSinal: ${info.rssi} dBm\nVelocidade de link: ${info.linkSpeed} Mbps\nBSSID: ${info.bssid ?: "indisponível"}")
    }

    private fun pingTest() {
        val output = StringBuilder()
        val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "5", "-W", "2", "1.1.1.1"))
        val finished = process.waitFor(18, TimeUnit.SECONDS)
        if (!finished) { process.destroyForcibly(); append("PING: tempo esgotado"); return }
        process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.append(it).append("\n") } }
        append("PING PARA 1.1.1.1\n$output")
    }

    private fun dnsTest() {
        val start = System.nanoTime()
        val address = InetAddress.getByName("example.com")
        val ms = (System.nanoTime() - start) / 1_000_000
        append("DNS\nexample.com resolvido para ${address.hostAddress}\nTempo de resolução: $ms ms")
    }

    private fun scanLan() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo
        val ip = dhcp.ipAddress
        val mask = dhcp.netmask
        if (ip == 0 || mask == 0) { append("DISPOSITIVOS: não foi possível obter a sub-rede Wi-Fi"); return }
        val network = ip and mask
        val broadcast = network or mask.inv()
        val base = network
        val found = java.util.Collections.synchronizedList(mutableListOf<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(48)
        val tasks = (1 until (broadcast - network)).map { host ->
            pool.submit {
                val candidate = base + host
                val addr = InetAddress.getByName(Formatter.formatIpAddress(candidate))
                try {
                    if (addr.isReachable(650)) found.add(addr.hostAddress ?: "")
                } catch (_: Exception) {}
            }
        }
        tasks.forEach { try { it.get(3, TimeUnit.SECONDS) } catch (_: Exception) {} }
        pool.shutdownNow()
        val gateway = Formatter.formatIpAddress(dhcp.gateway)
        append("DISPOSITIVOS ENCONTRADOS (resposta a ping)\nGateway: $gateway\nTotal respondendo: ${found.size}\n${found.sorted().joinToString("\n").ifBlank { "Nenhum dispositivo respondeu. Muitos aparelhos/roteadores bloqueiam esse tipo de varredura." }}")
    }

    private fun speedTest() {
        val url = URL("https://speed.cloudflare.com/__down?bytes=5000000")
        val start = System.nanoTime()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            requestMethod = "GET"
        }
        val bytes = connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
        connection.disconnect()
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        val mbps = if (seconds > 0) bytes * 8.0 / seconds / 1_000_000.0 else 0.0
        append("VELOCIDADE DE DOWNLOAD\nDados recebidos: ${bytes / 1_000_000.0} MB\nTempo: ${"%.2f".format(seconds)} s\nDownload aproximado: ${"%.2f".format(mbps)} Mbps\nServidor: Cloudflare")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
