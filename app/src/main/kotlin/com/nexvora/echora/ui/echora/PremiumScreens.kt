package com.nexvora.echora.ui.echora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.payment.ReferenceValidator
import com.nexvora.echora.premium.Entitlement
import com.nexvora.echora.premium.Plan
import com.nexvora.echora.premium.PlanCatalog
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun PremiumScreen(c: AppContainer, onUpgrade: () -> Unit, onHistory: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ent by c.entitlements.entitlement.collectAsStateWithLifecycle(Entitlement.Free)
    val now = System.currentTimeMillis(); val plan = ent.effectivePlan(now)
    var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val backendOn = c.backendApi != null
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Premium")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Your plan: ${plan.name.lowercase().replaceFirstChar { it.uppercase() }}", color = Echora.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (plan != Plan.FREE) {
                    InfoRow("Expires", DateFormat.getDateInstance().format(Date(ent.expiresAtMs)))
                    InfoRow("Source", if (ent.source == Entitlement.Source.SERVER) "Verified by server" else "Cached (re-verify when online)")
                    InfoRow("Last verified", DateFormat.getDateTimeInstance().format(Date(ent.verifiedAtMs)))
                    Text("Plans do not auto-renew. Submit a new payment before the expiry date to continue.", color = TextLo, fontSize = 12.sp)
                } else Text("Offline AI transcription stays free for everyone.", color = TextMid)
                if (!backendOn) Text("Premium needs a configured backend to verify payments and plans (see PAYMENT_SETUP.md). Until then everyone is on Free.", color = TextLo, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlineAction("Refresh status", { scope.launch { busy = true; msg = c.entitlements.refresh().fold({ "Status updated: ${it.plan.name}." }, { "Could not refresh: ${it.message}" }); busy = false } }, icon = Icons.Outlined.Refresh, enabled = backendOn && !busy)
                    OutlineAction("Payment history", onHistory)
                }
                msg?.let { Text(it, color = Echora.Cyan, fontSize = 12.sp) }
            }
            PlanCatalog.defaults.forEach { info ->
                val current = info.plan == plan
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(info.title.uppercase(), color = if (info.plan == Plan.PRO) Echora.Gold else TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (info.priceBdtPerMonth == 0) "৳0" else "৳${info.priceBdtPerMonth}/month", color = Echora.Cyan, fontWeight = FontWeight.Bold)
                    }
                    info.features.forEach { f -> Row { Icon(Icons.Outlined.Check, null, tint = Echora.Cyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(f, color = TextMid, fontSize = 13.sp) } }
                    if (info.plan != Plan.FREE) GradientButton(if (current) "Current plan — renew" else "Upgrade to ${info.title}", { c.pendingPlan = info.plan; onUpgrade() }, Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PaymentScreen(c: AppContainer, onBack: () -> Unit, onHistory: () -> Unit) {
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf(c.pendingPlan.takeIf { it != Plan.FREE } ?: Plan.PLUS) }
    var ref by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val info = PlanCatalog.defaults.first { it.plan == plan }
    val instructions = c.payments.instructions(plan)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Payment", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedPills(listOf(Plan.PLUS to "Plus ৳${PlanCatalog.defaults[1].priceBdtPerMonth}", Plan.PRO to "Pro ৳${PlanCatalog.defaults[2].priceBdtPerMonth}"), plan, { plan = it })
            GlassCard(Modifier.fillMaxWidth()) {
                Text("How to pay", color = TextHi, fontWeight = FontWeight.SemiBold)
                Text(instructions ?: "Manual payments are not configured in this build by the operator, so payment can't be submitted yet.", color = TextMid, modifier = Modifier.padding(top = 4.dp))
                Text("Amount: ৳${info.priceBdtPerMonth} for 30 days.", color = Echora.Cyan, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedTextField(ref, { ref = it.filter { ch -> ch.isLetterOrDigit() }.take(32) }, Modifier.fillMaxWidth(), label = { Text("Transaction ID") }, singleLine = true,
                supportingText = { Text("Exactly as in your payment receipt (6–32 letters/digits).") }, isError = ref.isNotEmpty() && !ReferenceValidator.isValid(ref))
            GradientButton("Submit for verification", { scope.launch {
                busy = true; err = null; msg = null
                c.payments.submit(plan, ref).fold({ msg = "Submitted. Premium is activated only after an administrator verifies your payment — this can take some time."; ref = "" }, { err = it.message })
                busy = false } }, Modifier.fillMaxWidth(), enabled = !busy && ReferenceValidator.isValid(ref) && instructions != null)
            OutlineAction("Check payment status", { scope.launch { busy = true; err = null; msg = null; c.payments.sync().fold({ msg = "Status refreshed." }, { err = it.message }); busy = false } }, enabled = !busy)
            OutlineAction("Payment history", onHistory)
            msg?.let { Text(it, color = Echora.Cyan) }; err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("ECHORA never stores card or wallet credentials. Nothing is unlocked by pressing a button here.", color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
fun PaymentHistoryScreen(c: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val list by c.repository.payments().collectAsStateWithLifecycle(emptyList())
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Payment History", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlineAction("Sync with server", { scope.launch { msg = c.payments.sync().fold({ "Synced." }, { it.message }) } })
            msg?.let { Text(it, color = Echora.Cyan, fontSize = 12.sp) }
            if (list.isEmpty()) Text("No payments yet.", color = TextLo, modifier = Modifier.padding(top = 16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(list, key = { it.id }) { p ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.plan} • ৳${p.amountBdt}", color = TextHi)
                            Chip(p.status, when (p.status) { "VERIFIED" -> Color(0xFF2ECC71); "PENDING" -> Echora.Gold; "REJECTED", "EXPIRED" -> Color(0xFFFF7373); else -> TextMid })
                        }
                        Text("Ref ${p.reference} • ${DateFormat.getDateTimeInstance().format(Date(p.createdAt))}", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
