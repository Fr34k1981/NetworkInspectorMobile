package br.com.networkinspector

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
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

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var resultCard: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var resultText: TextView
    private lateinit var status: TextView
    private var currentReport = "Nenhum teste executado ainda."
    private val bg = Color.rgb(12, 19, 34)
    private val card = Color.rgb(25, 36, 56)
    private val accent = Color.rgb(77, 155, 255)
    private val muted = Color.rgb(166, 183, 207)
    private val white = Color.rgb(245, 248, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 40)
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        val brand = TextView(this).apply {
            text = "NETWORK INSPECTOR"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
        }
        page.addView(brand)

        val heading = TextView(this).apply {
            text = "Sua rede,\nsob controle."
            textSize = 30f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }
        page.addView(heading)

        status = TextView(this).apply {
            text = "Escolha uma análise para começar."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(18))
        }
        page.addView(status)

        val section = TextView(this).apply {
            text = "FERRAMENTAS DE DIAGNÓSTICO"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }
        page.addView(section)

        addTool(page, "◉", "Informações do Wi-Fi", "Nome, IP, sinal e frequência") { runTask("Informações do Wi-Fi") { wifiInfo() } }
        addTool(page, "↗", "Ping e estabilidade", "Latência e respostas do servidor") { runTask("Ping e estabilidade") { pingTest() } }
        addTool(page, "⌘", "Teste de DNS", "Verifique a resolução de nomes") { runTask("Teste de DNS") { dnsTest() } }
        addTool(page, "▦", "Dispositivos na rede", "Procure aparelhos que respondem na rede") { runTask("Dispositivos na rede") { scanLan() } }
        addTool(page, "⇅", "Velocidade de download", "Medição aproximada da conexão") { runTask("Velocidade de download") { speedTest() } }
        addTool(page, "▣", "Senha do Wi-Fi", "Acesse o compartilhamento seguro do Android") { showPasswordHelp() }
        addTool(page, "✦", "Diagnóstico completo", "Execute todas as análises disponíveis") {
            runTask("Diagnóstico completo") { wifiInfo(); pingTest(); dnsTest(); scanLan(); speedTest() }
        }

        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = rounded(card, 18)
        }
        resultTitle = TextView(this).apply {
            text = "RESULTADO"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
        }
        resultText = TextView(this).apply {
            text = "Os resultados aparecerão aqui somente depois que você tocar em uma ferramenta."
            textSize = 14f
            setTextColor(white)
            setPadding(0, dp(12), 0, 0)
            setTextIsSelectable(true)
        }
        resultCard.addView(resultTitle)
        resultCard.addView(resultText)
        val resultParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        resultParams.topMargin = dp(14)
        page.addView(resultCard, resultParams)

        val copy = Button(this).apply {
            text = "Copiar resultado"
            isAllCaps = false
            setTextColor(white)
            background = rounded(Color.rgb(39, 61, 91), 14)
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Network Inspector", currentReport))
                Toast.makeText(this@MainActivity, "Resultado copiado", Toast.LENGTH_SHORT).show()
            }
        }
        val copyParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        copyParams.topMargin = dp(12)
        page.addView(copy, copyParams)

        val foot = TextView(this).apply {
            text = "Alguns aparelhos podem não responder à busca na rede. Os resultados dependem do Android, do roteador e das permissões."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(2), dp(14), dp(2), 0)
        }
        page.addView(foot)
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun addTool(parent: LinearLayout, symbol: String, title: String, subtitle: String, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(card, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val icon = TextView(this).apply {
            text = symbol
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(Color.rgb(34, 56, 83), 12)
        }
        item.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        item.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        item.addView(TextView(this).apply {
            text = "›"
            textSize = 25f
            setTextColor(muted)
        })
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(9)
        parent.addView(item, params)
    }

    private fun runTask(label: String, task: () -> Unit) {
        if (resultCard.childCount > 2) resultCard.removeViewAt(2)
        status.text = "Executando: $label..."
        resultTitle.text = label.uppercase()
        resultText.text = "Aguarde enquanto a análise é realizada..."
        currentReport = "$label\nAnálise em andamento..."
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                showResult("Erro: ${e.message ?: e.javaClass.simpleName}")
            }
            runOnUiThread { status.text = "Análise concluída · $label" }
        }
    }

    @Synchronized private fun showResult(text: String) {
        currentReport = text
        runOnUiThread { resultText.text = text }
    }

    private fun showPasswordHelp() {
        val message = "Por segurança, o Android não permite que aplicativos comuns leiam diretamente a senha salva.\n\nNa tela de Wi-Fi, abra a rede à qual você está conectado e procure Compartilhar ou QR Code. O próprio Android pode pedir sua digital, PIN ou senha para revelar o acesso.\n\nO Network Inspector não coleta nem envia sua senha."
        resultTitle.text = "SENHA DO WI-FI"
        resultText.text = message
        currentReport = "Senha do Wi-Fi\n$message"
        status.text = "A senha é protegida pelo Android."
        val openSettings = Button(this).apply {
            text = "Abrir configurações de Wi-Fi"
            isAllCaps = false
            setTextColor(white)
            background = rounded(Color.rgb(39, 91, 145), 12)
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "Não foi possível abrir as configurações.", Toast.LENGTH_SHORT).show() }
            }
        }
        if (resultCard.childCount > 2) resultCard.removeViewAt(2)
        if (resultCard.childCount == 2) resultCard.addView(openSettings, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(14) })
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
        showResult("Rede: $ssid\nIP do celular: $ip\nFrequência: ${if (frequency > 0) "$frequency MHz ($band)" else band}\nSinal: ${info.rssi} dBm\nVelocidade do link: ${info.linkSpeed} Mbps\nBSSID: ${info.bssid ?: "indisponível"}")
    }

    private fun pingTest() {
        val output = StringBuilder()
        val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "5", "-W", "2", "1.1.1.1"))
        val finished = process.waitFor(18, TimeUnit.SECONDS)
        if (!finished) { process.destroyForcibly(); showResult("PING\nTempo esgotado ao testar 1.1.1.1."); return }
        process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.append(it).append("\n") } }
        showResult("PING PARA 1.1.1.1\n$output")
    }

    private fun dnsTest() {
        val start = System.nanoTime()
        val address = InetAddress.getByName("example.com")
        val ms = (System.nanoTime() - start) / 1_000_000
        showResult("Domínio: example.com\nEndereço encontrado: ${address.hostAddress}\nTempo de resolução: $ms ms")
    }

    private fun scanLan() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo
        val ip = dhcp.ipAddress
        val mask = dhcp.netmask
        if (ip == 0 || mask == 0) { showResult("Não foi possível obter a sub-rede Wi-Fi."); return }
        val network = ip and mask
        val broadcast = network or mask.inv()
        val found = java.util.Collections.synchronizedList(mutableListOf<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(48)
        val tasks = (1 until (broadcast - network)).map { host ->
            pool.submit {
                val candidate = network + host
                val addr = InetAddress.getByName(Formatter.formatIpAddress(candidate))
                try { if (addr.isReachable(650)) found.add(addr.hostAddress ?: "") } catch (_: Exception) {}
            }
        }
        tasks.forEach { try { it.get(3, TimeUnit.SECONDS) } catch (_: Exception) {} }
        pool.shutdownNow()
        val gateway = Formatter.formatIpAddress(dhcp.gateway)
        showResult("Gateway: $gateway\nDispositivos que responderam: ${found.size}\n${found.sorted().joinToString("\n").ifBlank { "Nenhum dispositivo respondeu. Muitos aparelhos e roteadores bloqueiam esse tipo de busca." }}")
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
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n }
            total
        }
        connection.disconnect()
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        val mbps = if (seconds > 0) bytes * 8.0 / seconds / 1_000_000.0 else 0.0
        showResult("Dados recebidos: ${"%.2f".format(bytes / 1_000_000.0)} MB\nTempo: ${"%.2f".format(seconds)} s\nDownload aproximado: ${"%.2f".format(mbps)} Mbps\nServidor: Cloudflare")
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
