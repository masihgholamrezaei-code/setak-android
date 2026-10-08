@file:OptIn(ExperimentalMaterial3Api::class)

package ir.itsetak.repair

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface Route {
    data object Home : Route
    data class Detail(val id: Int) : Route
}

@Composable
fun SetakApp() {
    val ctx = LocalContext.current
    var account by remember { mutableStateOf(Prefs.load(ctx)) }
    val acc = account
    if (acc == null) {
        LoginScreen { a ->
            Prefs.save(ctx, a)
            account = a
        }
    } else {
        MainApp(acc) {
            WorkManager.getInstance(ctx).cancelUniqueWork("setak_attention")
            Prefs.clear(ctx)
            account = null
        }
    }
}

@Composable
fun MainApp(acc: Account, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember(acc) { SetakApi(acc) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var meta by remember { mutableStateOf<JSONObject?>(null) }
    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var dashLoading by remember { mutableStateOf(false) }
    var dashError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var tab by remember { mutableStateOf(0) }
    var listStatus by remember { mutableStateOf("") }
    var listSearch by remember { mutableStateOf("") }
    var listKey by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showStatus by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }

    fun refreshDash() {
        dashLoading = true
        dashError = null
        scope.launch {
            try {
                dash = api.dashboard()
            } catch (e: Exception) {
                dashError = e.message ?: "خطا"
            } finally {
                dashLoading = false
            }
        }
    }

    fun loadDetail(id: Int) {
        detailError = null
        scope.launch {
            try {
                detail = api.repair(id)
            } catch (e: Exception) {
                detailError = e.message ?: "خطا"
            }
        }
    }

    fun openDetail(id: Int) {
        detail = null
        route = Route.Detail(id)
        loadDetail(id)
    }

    fun handleScan(text: String) {
        val uri = try { Uri.parse(text) } catch (e: Exception) { null }
        val repairId = uri?.getQueryParameter("repair")?.toIntOrNull()
        val code = uri?.getQueryParameter("c")
        if (repairId != null) {
            openDetail(repairId)
        } else if (!code.isNullOrBlank()) {
            scope.launch {
                try {
                    val list = api.repairs(code, "", 1).arr("items").objects()
                    if (list.size == 1) {
                        openDetail(list[0].int("id"))
                    } else {
                        listSearch = code
                        listStatus = ""
                        listKey += 1
                        tab = 1
                        route = Route.Home
                    }
                } catch (e: Exception) {
                    toast = e.message ?: "خطا"
                }
            }
        } else {
            toast = "این QR مربوط به ستاک نیست."
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) handleScan(text)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(acc) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val req = PeriodicWorkRequestBuilder<AttentionWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("setak_attention", ExistingPeriodicWorkPolicy.KEEP, req)
        try {
            meta = api.meta()
        } catch (e: Exception) {
            toast = e.message ?: "خطا"
        }
        refreshDash()
    }

    LaunchedEffect(toast) {
        val t = toast
        if (t != null) {
            snack.showSnackbar(t)
            toast = null
        }
    }

    BackHandler(enabled = route != Route.Home) { route = Route.Home; refreshDash() }

    val onDetail = route is Route.Detail
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(if (onDetail) "پرونده" else "تعمیرات ستاک") },
                navigationIcon = {
                    if (onDetail) {
                        TextButton(
                            onClick = { route = Route.Home; refreshDash() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) { Text("‹ بازگشت") }
                    }
                },
                actions = {
                    if (!onDetail) {
                        TextButton(
                            onClick = {
                                scanLauncher.launch(
                                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt("QR برچسب یا رسید را اسکن کنید")
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(false)
                                )
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) { Text("اسکن QR") }
                        TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("خروج") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink, titleContentColor = Color.White,
                    actionIconContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (!onDetail) {
                NavigationBar {
                    val items = listOf("داشبورد" to "🏠", "پرونده‌ها" to "📋", "پذیرش" to "➕")
                    items.forEachIndexed { i, p ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(p.second) },
                            label = { Text(p.first) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val r = route
            if (r is Route.Detail) {
                val d = detail
                val err = detailError
                if (d != null) {
                    DetailScreen(
                        d,
                        onCall = { ph -> ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) },
                        onSms = { ph -> ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$ph"))) },
                        onStatus = { showStatus = true },
                        onDiagnosis = { showDiag = true }
                    )
                    if (showStatus) {
                        StatusDialog(meta, d.str("status"), onDismiss = { showStatus = false }) { status, message, note, reason ->
                            showStatus = false
                            scope.launch {
                                try {
                                    api.setStatus(r.id, status, message, note, reason)
                                    toast = "وضعیت تغییر کرد."
                                    loadDetail(r.id)
                                } catch (e: Exception) {
                                    toast = e.message ?: "خطا"
                                }
                            }
                        }
                    }
                    if (showDiag) {
                        DiagnosisDialog(
                            d.str("diagnosis"), d.str("internal_notes"),
                            meta?.arr("diagnosis_templates")?.strings() ?: emptyList(),
                            onDismiss = { showDiag = false }
                        ) { diag, notes ->
                            showDiag = false
                            scope.launch {
                                try {
                                    api.setDiagnosis(r.id, diag, notes)
                                    toast = "ذخیره شد."
                                    loadDetail(r.id)
                                } catch (e: Exception) {
                                    toast = e.message ?: "خطا"
                                }
                            }
                        }
                    }
                } else if (err != null) {
                    Box(Modifier.padding(16.dp)) { Banner(err, true) }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                when (tab) {
                    0 -> DashboardScreen(
                        dash, dashLoading, dashError,
                        onRefresh = { refreshDash() },
                        onOpen = { id -> openDetail(id) },
                        onStatus = { key ->
                            if (key != "today") {
                                listStatus = key
                                listSearch = ""
                                listKey += 1
                                tab = 1
                            }
                        }
                    )
                    1 -> key(listKey) {
                        ListScreen(api, meta, listStatus, listSearch) { id -> openDetail(id) }
                    }
                    else -> NewScreen(api, meta) { id, code ->
                        toast = "پرونده $code ثبت شد."
                        tab = 0
                        openDetail(id)
                    }
                }
            }
        }
    }
}
