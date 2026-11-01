
package com.ava.pumpscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale

data class Coin(
    val name: String,
    val symbol: String,
    val price: Double,
    val change24: Double,
    val change7: Double,
    val volume: Double,
    val score: Int,
    val risk: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AVAApp() }
    }
}

@Composable
fun AVAApp() {
    var coins by remember { mutableStateOf(listOf<Coin>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            try {
                coins = withContext(Dispatchers.IO) {
                    loadCoins()
                }
            } catch (e: Exception) {
                error = "خطا در دریافت اطلاعات. اینترنت یا محدودیت API را بررسی کن."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101820)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "AVA PUMP SCANNER",
                    color = Color(0xFF00E5A8),
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "اسکن و رتبه‌بندی بازار ارز دیجیتال",
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { refresh() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "در حال دریافت..." else "به‌روزرسانی بازار")
                }

                Spacer(Modifier.height(12.dp))

                if (error.isNotEmpty()) {
                    Text(error, color = Color.Red)
                }

                Text(
                    "رتبه‌بندی بر اساس تغییرات قیمت، حجم و روند",
                    color = Color.LightGray
                )

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(coins) { coin ->
                        CoinCard(coin)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "اطلاعات آموزشی است؛ امتیاز بالا به معنی پامپ قطعی یا سیگنال خرید نیست.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun CoinCard(coin: Coin) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202D38)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${coin.name} (${coin.symbol.uppercase()})",
                    color = Color.White
                )
                Text(
                    "امتیاز ${coin.score}/100",
                    color = Color(0xFF00E5A8)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                String.format(Locale.US, "$%,.6f", coin.price),
                color = Color.White
            )

            Text(
                "تغییر ۲۴ ساعت: ${format(coin.change24)}%",
                color = if (coin.change24 >= 0) Color.Green else Color.Red
            )

            Text(
                "تغییر ۷ روز: ${format(coin.change7)}%",
                color = if (coin.change7 >= 0) Color.Green else Color.Red
            )

            Text(
                "حجم معاملات: ${compact(coin.volume)} دلار",
                color = Color.LightGray
            )

            Text(
                "ارزیابی ریسک: ${coin.risk}",
                color = Color(0xFFFFC857)
            )
        }
    }
}

fun loadCoins(): List<Coin> {
    val address =
        "https://api.coingecko.com/api/v3/coins/markets" +
        "?vs_currency=usd&order=volume_desc" +
        "&per_page=50&page=1&sparkline=false" +
        "&price_change_percentage=24h%2C7d"

    val connection = URL(address).openConnection() as HttpURLConnection

    try {
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")

        val response = connection.inputStream.bufferedReader().use {
            it.readText()
        }

        val array = JSONArray(response)
        val result = mutableListOf<Coin>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            val name = item.optString("name")
            val symbol = item.optString("symbol")
            val price = item.optDouble("current_price", 0.0)
            val change24 = item.optDouble("price_change_percentage_24h", 0.0)
            val change7 = item.optDouble(
                "price_change_percentage_7d_in_currency", 0.0
            )
            val volume = item.optDouble("total_volume", 0.0)
            val marketCap = item.optDouble("market_cap", 0.0)

            val volumeRatio = if (marketCap > 0) {
                volume / marketCap
            } else 0.0

            val score = (
                35 +
                change24.coerceIn(0.0, 15.0) * 1.5 +
                change7.coerceIn(0.0, 30.0) * 0.5 +
                volumeRatio.coerceIn(0.0, 0.15) * 100
            ).toInt().coerceIn(0, 100)

            val risk = when {
                change24 > 20 -> "زیاد"
                change24 > 10 -> "متوسط رو به زیاد"
                volumeRatio < 0.01 -> "نقدشوندگی نیازمند بررسی"
                else -> "معمولی"
            }

            if (price > 0) {
                result.add(
                    Coin(
                        name, symbol, price,
                        change24, change7, volume,
                        score, risk
                    )
                )
            }
        }

        return result.sortedByDescending { it.score }
    } finally {
        connection.disconnect()
    }
}

fun format(value: Double): String =
    String.format(Locale.US, "%+.2f", value)

fun compact(value: Double): String = when {
    value >= 1_000_000_000 ->
        String.format(Locale.US, "%.2fB", value / 1_000_000_000)
    value >= 1_000_000 ->
        String.format(Locale.US, "%.2fM", value / 1_000_000)
    else ->
        String.format(Locale.US, "%.0f", value)
}
