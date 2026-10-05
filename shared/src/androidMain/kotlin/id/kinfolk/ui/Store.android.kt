package id.kinfolk.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import id.kinfolk.data.HOSTED_REVENUECAT_KEY
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

// RevenueCat's current Offering has two packages (scripts/go-live.sh): "family" (entitlement family) and
// "family_transcription" (entitlements family and transcription), monthly, each with a 14-day free trial.
@Composable
actual fun rememberStore(): Store {
    val context = LocalContext.current
    return remember(context) {
        suspend fun packages(circleId: String): Pair<Package, Package>? {
            if (HOSTED_REVENUECAT_KEY.isEmpty()) return null // the local stack sells nothing
            if (!Purchases.isConfigured) {
                Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, HOSTED_REVENUECAT_KEY).appUserID(circleId).build())
            }
            val p = Purchases.sharedInstance
            if (p.appUserID != circleId) p.awaitLogIn(circleId)
            val all = p.awaitOfferings().current?.availablePackages.orEmpty()
            return (all.find { it.identifier == "family" } ?: return null) to (all.find { it.identifier == "family_transcription" } ?: return null)
        }
        Store(
            offer = { circleId ->
                runCatching { packages(circleId) }.getOrNull()?.let { (plan, plus) ->
                    val price = plan.product.price
                    val addOn = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
                        currency = Currency.getInstance(price.currencyCode)
                        if ((plus.product.price.amountMicros - price.amountMicros) % 1_000_000 == 0L) maximumFractionDigits = 0
                    }.format((plus.product.price.amountMicros - price.amountMicros) / 1_000_000.0)
                    Offer(price.formatted, addOn, plus.product.price.formatted, plus.product.defaultOption?.freePhase != null)
                }
            },
            // ponytail: a plan-only payer adding the add-on gets a second subscription, not a replacement (the plan may
            // be another Member's Google account); the webhook reads the add-on first. oldProductId when it matters.
            buy = { circleId, transcription ->
                val (plan, plus) = packages(circleId) ?: error("store unavailable")
                try {
                    Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(context as Activity, if (transcription) plus else plan).build())
                    true
                } catch (e: PurchasesTransactionException) {
                    if (e.userCancelled) false else throw e
                }
            },
            manage = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) },
        )
    }
}
