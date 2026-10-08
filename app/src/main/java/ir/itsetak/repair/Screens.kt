@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.itsetak.repair

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* ---------- small building blocks ---------- */

@Composable
fun StatusBadge(label: String, color: String) {
    Box(
        Modifier.background(statusColor(color), RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 3.dp)
    ) { Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Banner(text: String, bad: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (bad) Color(0xFFFEF2F2) else Color(0xFFECFDF5))) {
        Text(text, Modifier.padding(12.dp), color = if (bad) Color(0xFF7F1D1D) else Color(0xFF065F46))
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
fun KeyValue(k: String, v: String) {
    if (v.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Color(0xFF64748B), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(v, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/* ---------- login ---------- */

@Composable
fun LoginScreen(onLogin: (Account) -> Unit) {
    var site by remember { mutableStateOf("https://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("تعمیرات ستاک", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("با «رمز برنامه» (Application Password) وردپرس وارد شوید؛ نه رمز اصلی حساب.", color = Color(0xFF64748B))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(site, { site = it }, label = { Text("آدرس سایت") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(user, { user = it }, label = { Text("نام کاربری وردپرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("رمز برنامه") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(Modifier.height(12.dp))
            Banner(it, true)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val acc = Account(site.trim().trimEnd('/'), user.trim(), pass.replace(" ", ""))
                        if (!acc.site.startsWith("https://")) throw ApiException("آدرس باید با https:// شروع شود.", 0)
                        SetakApi(acc).ping()
                        onLogin(acc)
                    } catch (e: Exception) {
                        error = e.message ?: "اتصال برقرار نشد."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "در حال اتصال…" else "ورود") }
    }
}

/* ---------- dashboard ---------- */

@Composable
fun DashboardScreen(
    dash: JSONObject?, loading: Boolean, error: String?,
    onRefresh: () -> Unit, onOpen: (Int) -> Unit, onStatus: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("خلاصه", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRefresh) { Text(if (loading) "…" else "به‌روزرسانی") }
            }
        }
        if (error != null) item { Banner(error, true) }
        if (dash != null) {
            val att = dash.arr("attention").objects()
            if (att.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("🔔 نیازمند توجه", fontWeight = FontWeight.Bold)
                            att.forEach { a ->
                                Text(
                                    a.str("code") + " — " + a.str("customer") + " — " + a.str("label"),
                                    modifier = Modifier.fillMaxWidth().clickable { onOpen(a.int("id")) }.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            val cards = dash.arr("cards").objects()
            items(cards.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        Card(
                            onClick = { onStatus(c.str("key")) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = statusColor(c.str("color")))
                        ) {
                            Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.int("count").toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text(c.str("label"), color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            val held = dash.arr("held").objects()
            if (held.isNotEmpty()) {
                item { Text("منتظر مشتری / ماندگار", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                items(held) { h ->
                    Card(Modifier.fillMaxWidth(), onClick = { onOpen(h.int("id")) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(h.str("code"), fontWeight = FontWeight.Bold)
                                Text(h.int("days").toString() + " روز", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                            }
                            Text(h.str("customer") + " — " + h.str("device"), fontSize = 13.sp)
                            Text(h.str("status_label") + (if (h.str("reason").isNotEmpty()) " — " + h.str("reason") else ""), fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
            val rating = dash.obj("rating")
            if (rating.int("count") > 0) {
                item { Text("میانگین امتیاز مشتریان (۹۰ روز): " + rating.optDouble("avg", 0.0) + " از ۵ (" + rating.int("count") + " نظر)", fontSize = 13.sp, color = Color(0xFF64748B)) }
            }
        }
    }
}

/* ---------- list ---------- */

@Composable
fun ListScreen(api: SetakApi, meta: JSONObject?, initialStatus: String, initialSearch: String, onOpen: (Int) -> Unit) {
    var search by remember { mutableStateOf(initialSearch) }
    var status by remember { mutableStateOf(initialStatus) }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load(p: Int) {
        loading = true
        error = null
        scope.launch {
            try {
                val res = api.repairs(search, status, p)
                val list = res.arr("items").objects()
                rows = if (p == 1) list else rows + list
                page = res.int("page")
                pages = res.int("pages")
                total = res.int("total")
            } catch (e: Exception) {
                error = e.message ?: "خطا"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search, status) {
        delay(350)
        load(1)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            search, { search = it }, singleLine = true, label = { Text("جستجو: کد، نام، موبایل، سریال، مدل") },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        val statuses = meta?.arr("statuses")?.objects() ?: emptyList()
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = status.isEmpty(), onClick = { status = "" }, label = { Text("همه") })
            statuses.forEach { s ->
                FilterChip(selected = status == s.str("key"), onClick = { status = s.str("key") }, label = { Text(s.str("label")) })
            }
        }
        error?.let { Box(Modifier.padding(horizontal = 16.dp)) { Banner(it, true) } }
        Text("$total پرونده", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = Color(0xFF64748B))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows) { r ->
                Card(Modifier.fillMaxWidth(), onClick = { onOpen(r.int("id")) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.str("code"), fontWeight = FontWeight.Bold)
                                if (r.str("attention").isNotEmpty()) Text("  🔴", fontSize = 12.sp)
                                if (r.str("case_type") == "warranty") Text("  گارانتی", fontSize = 12.sp, color = Color(0xFF6D28D9))
                            }
                            StatusBadge(r.str("status_label"), r.str("color"))
                        }
                        Text(r.str("customer") + (if (r.str("phone").isNotEmpty()) " — " + r.str("phone") else ""), fontSize = 13.sp)
                        Text(r.str("device"), fontSize = 13.sp, color = Color(0xFF475569))
                        Text("پذیرش " + r.str("received") + (if (r.int("amount") > 0) " — " + money(r.int("amount")) else ""), fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
            item {
                if (page < pages) {
                    OutlinedButton(onClick = { load(page + 1) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "…" else "بیشتر")
                    }
                } else if (loading) {
                    Text("در حال بارگذاری…", color = Color(0xFF64748B))
                }
            }
        }
    }
}

/* ---------- case detail ---------- */

@Composable
fun StatusDialog(
    meta: JSONObject?, current: String, onDismiss: () -> Unit,
    onSubmit: (status: String, message: String, note: String, reason: String) -> Unit
) {
    var selected by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val statuses = (meta?.arr("statuses")?.objects() ?: emptyList()).filter { it.str("key") != current }
    val reasons = meta?.arr("hold_reasons")?.options() ?: emptyList()
    val templates = meta?.arr("message_templates")?.strings() ?: emptyList()
    val needsReason = statuses.firstOrNull { it.str("key") == selected }?.bool("hold") == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر وضعیت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statuses.forEach { s ->
                        FilterChip(selected = selected == s.str("key"), onClick = { selected = s.str("key") }, label = { Text(s.str("label")) })
                    }
                }
                if (needsReason) {
                    Text("دلیل (الزامی)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { r -> FilterChip(selected = reason == r.key, onClick = { reason = r.key }, label = { Text(r.label) }) }
                    }
                }
                OutlinedTextField(message, { message = it }, label = { Text("پیام به مشتری (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                if (templates.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        templates.forEach { t -> AssistChip(onClick = { message = t }, label = { Text(t, maxLines = 1) }) }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("یادداشت داخلی (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selected, message, note, reason) }, enabled = selected.isNotEmpty() && (!needsReason || reason.isNotEmpty())) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun DiagnosisDialog(initialDiag: String, initialNotes: String, templates: List<String>, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var diag by remember { mutableStateOf(initialDiag) }
    var notes by remember { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخیص و یادداشت داخلی") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(diag, { diag = it }, label = { Text("تشخیص تکنسین") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (templates.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        templates.forEach { t -> AssistChip(onClick = { diag = if (diag.isBlank()) t else diag.trimEnd() + "\n" + t }, label = { Text(t, maxLines = 1) }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت داخلی") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(diag, notes) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun DetailScreen(
    d: JSONObject, onCall: (String) -> Unit, onSms: (String) -> Unit, onStatus: () -> Unit, onDiagnosis: () -> Unit
) {
    val cust = d.obj("customer")
    val dev = d.obj("device")
    val est = d.obj("estimate")
    val can = d.obj("can")
    val phone = cust.str("phone")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ink)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d.str("code"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    StatusBadge(d.str("status_label"), d.str("color"))
                }
                Text((dev.str("brand") + " " + dev.str("model")).trim() + " (" + dev.str("type_label") + ")", color = Color.White)
                Text(cust.str("name") + (if (phone.isNotEmpty()) " — " + phone else " — بدون شماره"), color = Color(0xFFCBD5E1), fontSize = 13.sp)
                if (d.str("case_type") == "warranty") Text("مراجعه گارانتی — بدون هزینه", color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (phone.isNotEmpty()) {
                OutlinedButton(onClick = { onCall(phone) }, modifier = Modifier.weight(1f)) { Text("تماس") }
                OutlinedButton(onClick = { onSms(phone) }, modifier = Modifier.weight(1f)) { Text("پیامک") }
            }
            if (can.bool("edit")) Button(onClick = onStatus, modifier = Modifier.weight(1.4f)) { Text("تغییر وضعیت") }
        }

        Section("خلاصه") {
            KeyValue("تاریخ پذیرش", d.str("received"))
            KeyValue("آخرین بروزرسانی", d.str("updated"))
            KeyValue("تحویل", d.str("delivered"))
            KeyValue("اولویت", d.str("priority"))
            KeyValue("تکنسین", d.str("technician"))
            KeyValue("سریال", dev.str("serial"))
            KeyValue("گارانتی", d.obj("warranty").str("label"))
            if (d.bool("is_legacy")) KeyValue("نوع", "پرونده قدیمی")
        }
        Section("شرح مشکل (از دید مشتری)") { Text(d.str("problem")) }

        val app = d.arr("appearance").strings()
        val acc = d.arr("accessories").strings()
        if (app.isNotEmpty() || acc.isNotEmpty()) {
            Section("وضعیت ظاهری و متعلقات") {
                if (app.isNotEmpty()) Text("ظاهر: " + app.joinToString("، ") + (if (d.str("appearance_note").isNotEmpty()) " — " + d.str("appearance_note") else ""))
                if (acc.isNotEmpty()) Text("متعلقات: " + acc.joinToString("، ") + (if (d.str("accessory_note").isNotEmpty()) " — " + d.str("accessory_note") else ""))
            }
        }

        Section("کارشناسی") {
            Text(if (d.str("diagnosis").isNotEmpty()) d.str("diagnosis") else "—")
            if (d.str("internal_notes").isNotEmpty()) Text("یادداشت داخلی: " + d.str("internal_notes"), color = Color(0xFF64748B), fontSize = 13.sp)
            if (can.bool("edit")) OutlinedButton(onClick = onDiagnosis) { Text("ویرایش تشخیص") }
        }

        val items = est.arr("items").objects()
        if (items.isNotEmpty()) {
            Section("برآورد (" + est.str("status_label") + ")") {
                items.forEach { i -> KeyValue(i.str("desc") + " ×" + i.int("qty"), money(i.int("line"))) }
                KeyValue("جمع", money(est.int("total")))
            }
        }
        if (d.int("final_cost") > 0) {
            Section("تحویل و پرداخت") {
                KeyValue("هزینه نهایی", money(d.int("final_cost")))
                if (!d.isNull("paid")) KeyValue("پرداخت‌شده", money(d.int("paid")))
            }
        }
        if (d.str("actions").isNotEmpty() || d.str("test_notes").isNotEmpty()) {
            Section("اقدامات و تست") {
                if (d.str("actions").isNotEmpty()) Text(d.str("actions"))
                if (d.str("test_notes").isNotEmpty()) Text("تست: " + d.str("test_notes"), color = Color(0xFF64748B), fontSize = 13.sp)
            }
        }
        val parts = d.arr("parts").objects()
        if (parts.isNotEmpty()) {
            Section("قطعات") { parts.forEach { p -> KeyValue(p.str("name") + " ×" + p.int("qty"), p.str("status")) } }
        }
        Section("تاریخچه") {
            d.arr("history").objects().forEach { h ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text((if (h.str("from").isNotEmpty()) h.str("from") + " ← " else "") + h.str("to"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(h.str("date") + (if (h.str("user").isNotEmpty()) " — " + h.str("user") else ""), fontSize = 12.sp, color = Color(0xFF64748B))
                    if (h.str("public_message").isNotEmpty()) Text("به مشتری: " + h.str("public_message"), fontSize = 12.sp)
                    if (h.str("internal_note").isNotEmpty()) Text("داخلی: " + h.str("internal_note"), fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------- new intake ---------- */

@Composable
fun NewScreen(api: SetakApi, meta: JSONObject?, onCreated: (Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("laptop") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var appearance by remember { mutableStateOf(setOf<String>()) }
    var accessories by remember { mutableStateOf(setOf<String>()) }
    var legacy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var known by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val types = meta?.arr("device_types")?.options() ?: emptyList()
    val appOptions = meta?.arr("appearance")?.options() ?: emptyList()
    val accOptions = meta?.arr("accessories")?.options() ?: emptyList()
    val brands = meta?.obj("brands")?.arr(type)?.strings() ?: emptyList()
    val shownBrands = brands.filter { brand.isBlank() || it.contains(brand.trim(), ignoreCase = true) }.filter { !it.equals(brand.trim(), ignoreCase = true) }.take(8)

    val digits = digitsOnly(phone)
    LaunchedEffect(digits) {
        known = emptyList()
        if (digits.length == 11 && digits.startsWith("09")) {
            delay(300)
            try {
                known = api.lookup(digits).objects()
            } catch (e: Exception) {
                // the hint is optional; ignore network errors here
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("پذیرش جدید", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            phone, { phone = it }, label = { Text(if (legacy) "موبایل (اختیاری)" else "موبایل *") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
        )
        known.forEach { k ->
            val c = k.obj("customer")
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مشتری قبلی: " + c.str("name"), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { name = c.str("name") }) { Text("پر کردن نام") }
                    k.arr("devices").objects().take(3).forEach { dv ->
                        OutlinedButton(onClick = {
                            type = dv.str("device_type"); brand = dv.str("brand"); model = dv.str("model"); serial = dv.str("serial_number")
                        }) { Text((dv.str("brand") + " " + dv.str("model")).trim()) }
                    }
                    k.arr("repairs").objects().take(3).forEach { r ->
                        Text(r.str("code") + " — " + r.str("device") + " — " + r.str("status") + (if (r.str("warranty").isNotEmpty()) " — " + r.str("warranty") else ""), fontSize = 12.sp, color = Color(0xFF475569))
                    }
                }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("نوع دستگاه *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { t -> FilterChip(selected = type == t.key, onClick = { type = t.key }, label = { Text(t.label) }) }
        }
        OutlinedTextField(brand, { brand = it }, label = { Text("برند *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (shownBrands.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shownBrands.forEach { b -> AssistChip(onClick = { brand = b }, label = { Text(b) }) }
            }
        }
        OutlinedTextField(model, { model = it }, label = { Text("مدل *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(serial, { serial = it }, label = { Text("شماره سریال (توصیه می‌شود)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(problem, { problem = it }, label = { Text("شرح مشکل از دید مشتری *") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Text("وضعیت ظاهری", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            appOptions.forEach { o ->
                FilterChip(selected = o.key in appearance, onClick = { appearance = if (o.key in appearance) appearance - o.key else appearance + o.key }, label = { Text(o.label) })
            }
        }
        Text("متعلقات تحویل‌گرفته‌شده", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            accOptions.forEach { o ->
                FilterChip(selected = o.key in accessories, onClick = { accessories = if (o.key in accessories) accessories - o.key else accessories + o.key }, label = { Text(o.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = legacy, onCheckedChange = { legacy = it })
            Text("پرونده قدیمی (موبایل اختیاری)", fontSize = 13.sp)
        }
        error?.let { Banner(it, true) }
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val acc = JSONObject()
                        accessories.forEach { acc.put(it, 1) }
                        val body = JSONObject()
                            .put("name", name.trim()).put("phone", digits).put("device_type", type)
                            .put("brand", brand.trim()).put("model", model.trim()).put("serial_number", serial.trim())
                            .put("customer_problem", problem.trim())
                            .put("appearance", JSONArray(appearance.toList()))
                            .put("accessories", acc)
                            .put("is_legacy", legacy)
                        val res = api.create(body)
                        onCreated(res.int("id"), res.str("code"))
                    } catch (e: Exception) {
                        error = e.message ?: "خطا"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text(if (busy) "در حال ثبت…" else "ثبت پذیرش") }
        Spacer(Modifier.height(30.dp))
    }
}
