package com.example.otwieraczdonotyfikacjiemail

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(intent)
                }
            }
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("messages_channel", "Nowe wiadomości", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppNavigation(initialIntent: Intent) {
        val navController = rememberNavController()
        var configs by remember { mutableStateOf(ConfigLoader.loadConfigs(this@MainActivity)) }
        val initialUrl = initialIntent.getStringExtra("OPEN_URL")

        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportConfigsToUri(it, configs) }
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                if (importConfigsFromUri(it)) {
                    configs = ConfigLoader.loadConfigs(this@MainActivity)
                    AppLogger.log(this@MainActivity, "INFO", message = "Zaimportowano konfigurację.")
                }
            }
        }

        NavHost(navController = navController, startDestination = "list") {
            composable("list") {
                ConfigListScreen(
                    configs = configs,
                    onOpen = { url -> navController.navigate("webview/${URLEncoder.encode(url, "UTF-8")}") },
                    onEdit = { id -> navController.navigate("edit/$id") },
                    onAdd = { navController.navigate("edit/NEW_CONFIG") },
                    onExport = { exportLauncher.launch("otwieracz_backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                    onHelp = { navController.navigate("help") },
                    onShowLogs = { navController.navigate("logs") },
                    onOpenNotificationSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onOpenBatterySettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                )
            }
            composable("help") { HelpScreen(onBack = { navController.popBackStack() }) }
            composable("logs?configId={configId}", arguments = listOf(navArgument("configId") { nullable = true; type = NavType.StringType })) { bse ->
                val configId = bse.arguments?.getString("configId")
                LogsScreen(configId = configId, onBack = { navController.popBackStack() })
            }
            composable("edit/{configId}", arguments = listOf(navArgument("configId") { type = NavType.StringType })) { bse ->
                val id = bse.arguments?.getString("configId")
                val config = if (id == "NEW_CONFIG") null else configs.find { it.id == id }
                EditConfigScreen(config, configs, { updated ->
                    val newList = if (configs.none { it.id == updated.id }) configs + updated else configs.map { if (it.id == updated.id) updated else it }
                    ConfigLoader.saveConfigs(this@MainActivity, newList)
                    configs = ConfigLoader.loadConfigs(this@MainActivity)
                }, { deletedId ->
                    val configToDelete = configs.find { it.id == deletedId }
                    AppLogger.log(this@MainActivity, "INFO", deletedId, configToDelete?.name, "Usunięto konfigurację.")
                    val newList = configs.filter { it.id != deletedId }
                    ConfigLoader.saveConfigs(this@MainActivity, newList)
                    configs = ConfigLoader.loadConfigs(this@MainActivity)
                    navController.popBackStack()
                }, { navController.popBackStack() }, 
                onShowConfigLogs = { targetId -> navController.navigate("logs?configId=$targetId") })
            }
            composable("webview/{url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) { bse ->
                val url = URLDecoder.decode(bse.arguments?.getString("url") ?: "", "UTF-8")
                WebViewScreen(url, onBack = { navController.popBackStack() })
            }
        }

        LaunchedEffect(initialUrl) {
            initialUrl?.let {
                navController.navigate("webview/${URLEncoder.encode(it, "UTF-8")}")
                initialIntent.removeExtra("OPEN_URL")
            }
        }
    }

    private fun exportConfigsToUri(uri: Uri, configs: List<NotificationConfig>) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                val arr = org.json.JSONArray()
                configs.forEach { c ->
                    val o = JSONObject(); o.put("id", c.id); o.put("name", c.name); o.put("startUrl", c.startUrl)
                    o.put("messageUrl", c.messageUrl); o.put("emailSender", c.emailSender); o.put("subjectKeyword", c.subjectKeyword)
                    arr.put(o)
                }
                os.write(arr.toString(2).toByteArray())
                AppLogger.log(this, "INFO", message = "Wyeksportowano konfigurację.")
            }
        } catch (ignored: Exception) { }
    }

    private fun importConfigsFromUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val s = ins.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(s)
                val list = mutableListOf<NotificationConfig>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (!o.has("name") || !o.has("messageUrl") || !o.has("emailSender")) return false
                    list.add(NotificationConfig(o.optString("id", java.util.UUID.randomUUID().toString()), o.getString("name"), o.optString("startUrl", ""), o.getString("messageUrl"), o.getString("emailSender"), o.optString("subjectKeyword", "")))
                }
                ConfigLoader.saveConfigs(this, list); true
            } ?: false
        } catch (ignored: Exception) { false }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ConfigListScreen(configs: List<NotificationConfig>, onOpen: (String) -> Unit, onEdit: (String) -> Unit, onAdd: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onHelp: () -> Unit, onShowLogs: () -> Unit, onOpenNotificationSettings: () -> Unit, onOpenBatterySettings: () -> Unit) {
        var menu by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text("Otwieracz", style = MaterialTheme.typography.titleMedium) } },
                    actions = {
                        IconButton(onClick = onShowLogs) { Icon(Icons.AutoMirrored.Filled.List, "Historia") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Eksportuj") }, onClick = { menu = false; onExport() }, leadingIcon = { Icon(Icons.Default.Upload, null) })
                            DropdownMenuItem(text = { Text("Importuj") }, onClick = { menu = false; onImport() }, leadingIcon = { Icon(Icons.Default.Download, null) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Powiadomienia") }, onClick = { menu = false; onOpenNotificationSettings() }, leadingIcon = { Icon(Icons.Default.NotificationsActive, null) })
                            DropdownMenuItem(text = { Text("Bateria") }, onClick = { menu = false; onOpenBatterySettings() }, leadingIcon = { Icon(Icons.Default.BatteryChargingFull, null) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Pomoc") }, onClick = { menu = false; onHelp() }, leadingIcon = { Icon(Icons.Default.Help, null) })
                        }
                    }
                )
            },
            floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Dodaj") } }
        ) { p -> LazyColumn(Modifier.padding(p).fillMaxSize()) { items(configs) { c -> ConfigItem(c, onOpen, onEdit) } } }
    }

    @Composable
    fun ConfigItem(c: NotificationConfig, onOpen: (String) -> Unit, onEdit: (String) -> Unit) {
        val url = if (!c.startUrl.isNullOrEmpty()) c.startUrl else c.messageUrl
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpen(url) }) { Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(c.emailSender, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                IconButton(onClick = { onOpen(url) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Otwórz") }
                IconButton(onClick = { onEdit(c.id) }) { Icon(Icons.Default.Edit, "Edytuj") }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditConfigScreen(config: NotificationConfig?, allConfigs: List<NotificationConfig>, onSave: (NotificationConfig) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit, onShowConfigLogs: (String) -> Unit) {
        val currentId = remember { config?.id ?: java.util.UUID.randomUUID().toString() }
        var name by remember { mutableStateOf(config?.name ?: "") }
        var startUrl by remember { mutableStateOf(config?.startUrl ?: "") }
        var messageUrl by remember { mutableStateOf(config?.messageUrl ?: "") }
        var emailSender by remember { mutableStateOf(config?.emailSender ?: "") }
        var subjectKeyword by remember { mutableStateOf(config?.subjectKeyword ?: "") }
        
        var isDirty by remember { mutableStateOf(false) }
        var showDel by remember { mutableStateOf(false) }
        var showTestPrompt by remember { mutableStateOf(false) }
        var testSuccess by remember { mutableStateOf<Boolean?>(null) }
        
        val context = LocalContext.current
        val unique = config != null || allConfigs.none { it.name.equals(name, ignoreCase = true) }

        LaunchedEffect(name, startUrl, messageUrl, emailSender, subjectKeyword) {
            if (name.isNotBlank() && unique && messageUrl.isNotBlank() && emailSender.isNotBlank()) {
                onSave(NotificationConfig(currentId, name, startUrl, messageUrl, emailSender, subjectKeyword))
            }
        }

        val receiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.example.otwieracz.TEST_RESULT") {
                        testSuccess = intent.getBooleanExtra("success", false)
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            val filter = IntentFilter("com.example.otwieracz.TEST_RESULT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            onDispose { context.unregisterReceiver(receiver) }
        }

        Scaffold(topBar = { 
            TopAppBar(
                title = { Text(if (config == null) "Nowa" else "Edytuj") }, 
                navigationIcon = { IconButton(onClick = {
                    if (isDirty) AppLogger.log(context, "INFO", currentId, name, if (config == null) "Dodano konfigurację." else "Zaktualizowano parametry.")
                    onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
                actions = {
                    if (config != null) {
                        IconButton(onClick = {
                            if (isDirty) {
                                AppLogger.log(context, "INFO", currentId, name, "Zaktualizowano parametry przed wejściem w logi.")
                                isDirty = false
                            }
                            onShowConfigLogs(currentId)
                        }) { Icon(Icons.AutoMirrored.Filled.List, "Logi") }
                        IconButton(onClick = { showTestPrompt = true }) { Icon(Icons.Default.PlayArrow, "Test") }
                    }
                }
            ) 
        }) { p ->
            Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it; isDirty = true }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth(), isError = !unique, keyboardOptions = KeyboardOptions(KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp)); OutlinedTextField(startUrl, { startUrl = it; isDirty = true }, label = { Text("Start URL") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(messageUrl, { messageUrl = it; isDirty = true }, label = { Text("Message URL") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(emailSender, { emailSender = it; isDirty = true }, label = { Text("Nadawca") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(subjectKeyword, { subjectKeyword = it; isDirty = true }, label = { Text("Słowo kluczowe") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    if (isDirty) AppLogger.log(context, "INFO", currentId, name, if (config == null) "Dodano konfigurację." else "Zaktualizowano parametry.")
                    onBack()
                }, modifier = Modifier.fillMaxWidth()) { Text("Zamknij") }
                if (config != null) { TextButton({ showDel = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Usuń") } }
            }
        }

        if (showTestPrompt) {
            AlertDialog({ showTestPrompt = false }, title = { Text("Testuj") }, text = { Text("Upewnij się, że masz nieodczytane powiadomienie Gmail.") }, confirmButton = { Button({ 
                if (isDirty) {
                    AppLogger.log(context, "INFO", currentId, name, "Zaktualizowano parametry przed testem.")
                    isDirty = false
                }
                showTestPrompt = false; 
                context.sendBroadcast(Intent("com.example.otwieracz.CHECK_NOTIFICATIONS").putExtra("configId", currentId)) 
            }) { Text("Testuj") } }, dismissButton = { TextButton({ showTestPrompt = false }) { Text("Anuluj") } })
        }

        if (testSuccess == false) {
            AlertDialog({ testSuccess = null },
                title = { Text("Porażka") },
                text = { Text("Brak pasującego powiadomienia na pasku. Sprawdź konfigurację.") },
                confirmButton = { Button({ testSuccess = null }) { Text("OK") } }
            )
        }

        if (showDel) { AlertDialog({ showDel = false }, title = { Text("Usuń?") }, text = { Text("Na pewno?") }, confirmButton = { Button({ showDel = false; onDelete(currentId) }) { Text("Tak") } }, dismissButton = { TextButton({ showDel = false }) { Text("Nie") } }) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogsScreen(configId: String?, onBack: () -> Unit) {
        val context = LocalContext.current
        var logs by remember { mutableStateOf(AppLogger.loadLogs(context)) }
        var logLimit by remember { mutableIntStateOf(AppLogger.getLogLimit(context)) }
        var showLimitDialog by remember { mutableStateOf(false) }
        val filteredLogs = if (configId != null) logs.filter { it.configId == configId } else logs

        Scaffold(topBar = { TopAppBar(title = { Text(if (configId != null) "Logi" else "Historia") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = { IconButton(onClick = { showLimitDialog = true }) { Icon(Icons.Default.Settings, null) }; IconButton(onClick = { AppLogger.clearLogs(context); AppLogger.log(context, "INFO", message = "Wyczyszczono historię."); logs = AppLogger.loadLogs(context) }) { Icon(Icons.Default.Delete, null) } }) }) { p ->
            LazyColumn(Modifier.padding(p).fillMaxSize()) { items(filteredLogs) { log -> LogItem(log); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)) } }
        }

        if (showLimitDialog) {
            var tempLimit by remember { mutableStateOf(logLimit.toString()) }
            AlertDialog({ showLimitDialog = false }, title = { Text("Limit logów") }, text = { OutlinedTextField(tempLimit, { tempLimit = it }, label = { Text("Max wpisów") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }, confirmButton = { Button({ val n = tempLimit.toIntOrNull() ?: 1000; AppLogger.setLogLimit(context, n); logLimit = AppLogger.getLogLimit(context); showLimitDialog = false }) { Text("Zapisz") } })
        }
    }

    @Composable
    fun LogItem(log: LogEntry) {
        val color = when (log.type) { "ERROR" -> Color.Red; "TEST" -> Color.Cyan; "DEBUG" -> Color.Gray; else -> MaterialTheme.colorScheme.primary }
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(log.formatTimestamp(), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(8.dp)); Surface(color = color.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) { Text(log.type, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color) } }
            if (log.configName != null) Text(log.configName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(log.message, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HelpScreen(onBack: () -> Unit) {
        val helpJson = remember { try { JSONObject(assets.open("help.json").bufferedReader().use { it.readText() }) } catch (ignored: Exception) { JSONObject() } }
        Scaffold(topBar = { TopAppBar(title = { Text("Pomoc") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } }) }) { p ->
            Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(helpJson.optString("about_title", "Pomoc"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Text(helpJson.optString("about_text", ""), Modifier.padding(vertical = 8.dp)); HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(helpJson.optString("permissions_title", "Uprawnienia"), style = MaterialTheme.typography.titleMedium); Text(helpJson.optString("perm_1", ""), Modifier.padding(top = 4.dp)); Text(helpJson.optString("perm_2", ""), Modifier.padding(top = 4.dp)); HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(helpJson.optString("filters_title", "Filtry"), style = MaterialTheme.typography.titleMedium); Text(helpJson.optString("filter_sender", ""), Modifier.padding(top = 4.dp)); Text(helpJson.optString("filter_keyword", ""), Modifier.padding(top = 4.dp)); HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(helpJson.optString("backup_title", "Kopia zapasowa"), style = MaterialTheme.typography.titleMedium); Text(helpJson.optString("backup_text", ""), Modifier.padding(top = 4.dp))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun WebViewScreen(url: String, onBack: () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            AndroidView({ c -> WebView(c).apply { webViewClient = WebViewClient(); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"; settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW; loadUrl(url) } }, Modifier.fillMaxSize())
            BackHandler { onBack() }
        }
    }
}