package id.kinfolk.ui.appointment

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Date copy: "Hari ini · 14.30", "3 jam 10 mnt lagi" and "Selasa, 29 Sept" from design v3; the rest approved in #6.

private val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
private val shortDays = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sept", "Okt", "Nov", "Des")

fun longDate(d: LocalDate) = "${days[d.dayOfWeek.ordinal]}, ${d.day} ${months[d.month.ordinal]}"
/** "Sen" */
fun dayName(d: LocalDate) = days[d.dayOfWeek.ordinal]
fun shortDay(d: LocalDate) = shortDays[d.dayOfWeek.ordinal]
fun shortDate(d: LocalDate) = "${shortDays[d.dayOfWeek.ordinal]}, ${d.day} ${months[d.month.ordinal]}"
/** "12 Mar 1948", Form Info darurat's date of birth (#34). */
fun fullDate(d: LocalDate) = "${d.day} ${months[d.month.ordinal]} ${d.year}"
fun hm(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}.${t.minute.toString().padStart(2, '0')}"

/** Four typed digits ("1430") as a clock time, or null. */
fun parseHm(digits: String): LocalTime? {
    if (digits.length != 4 || !digits.all(Char::isDigit)) return null
    val h = digits.take(2).toInt()
    val m = digits.drop(2).toInt()
    return if (h < 24 && m < 60) LocalTime(h, m) else null
}

/** "24 Sept", for "Dari kunjungan 24 Sept" (approved in #7). */
fun dayMonth(d: LocalDate) = "${d.day} ${months[d.month.ordinal]}"

/** "Hari ini", "Besok" or "Kam, 1 Okt". */
fun dayLabel(startsAt: Instant, now: Instant, tz: TimeZone): String {
    val at = startsAt.toLocalDateTime(tz).date
    return when (now.toLocalDateTime(tz).date.daysUntil(at)) {
        0 -> "Hari ini"
        1 -> "Besok"
        else -> shortDate(at)
    }
}

fun whenLabel(startsAt: Instant, now: Instant, tz: TimeZone) = "${dayLabel(startsAt, now, tz)} · ${hm(startsAt.toLocalDateTime(tz).time)}"

fun countdown(startsAt: Instant, now: Instant, tz: TimeZone): String {
    if (startsAt <= now) return "Sedang berlangsung"
    val mins = (startsAt - now).inWholeMinutes
    val days = now.toLocalDateTime(tz).date.daysUntil(startsAt.toLocalDateTime(tz).date)
    return when {
        mins < 60 -> "$mins mnt lagi"
        days > 0 -> "$days hari lagi"
        mins % 60 == 0L -> "${mins / 60} jam lagi"
        else -> "${mins / 60} jam ${mins % 60} mnt lagi"
    }
}

/** When a Timeline entry happened: "Hari ini, 15.10", "Sen, 19.10" within the week, else "24 Sept" (approved in #8). */
fun ago(at: Instant, now: Instant, tz: TimeZone): String {
    val t = at.toLocalDateTime(tz)
    return when (t.date.daysUntil(now.toLocalDateTime(tz).date)) {
        0 -> "Hari ini, ${hm(t.time)}"
        in 1..6 -> "${shortDays[t.date.dayOfWeek.ordinal]}, ${hm(t.time)}"
        else -> dayMonth(t.date)
    }
}

/**
 * Under a `member` "Riwayat perubahan" line (#20): "Baru saja" within the hour, else "19 Sept"; then "atas
 * permintaan Tukiman" when the Care Recipient [recipient] made it on their own behalf, else "oleh Sri".
 */
fun changeMeta(at: Instant, now: Instant, tz: TimeZone, by: String, recipient: String?): String {
    val ago = if (now - at < 1.hours) "Baru saja" else dayMonth(at.toLocalDateTime(tz).date)
    return "$ago · " + (recipient?.let { "atas permintaan $it" } ?: "oleh $by")
}

/** How old the offline copy is: "2 jam lalu" from the prototype, with minutes and days like [countdown] (#14). */
fun updatedAgo(savedAt: Instant, now: Instant): String {
    val mins = (now - savedAt).inWholeMinutes.coerceAtLeast(1)
    return when {
        mins < 60 -> "$mins mnt lalu"
        mins < 24 * 60 -> "${mins / 60} jam lalu"
        else -> "${mins / (24 * 60)} hari lalu"
    }
}
