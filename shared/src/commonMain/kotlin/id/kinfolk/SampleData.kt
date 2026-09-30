package id.kinfolk

import androidx.compose.ui.graphics.Color
import id.kinfolk.ui.home.DutyDay
import id.kinfolk.ui.home.FeedItem
import id.kinfolk.ui.home.HomeState
import id.kinfolk.ui.home.NextAppointment
import id.kinfolk.ui.home.Person

// Initial state of the v3 prototype (Bahasa Indonesia). Replaced by Supabase data later.
object SampleData {
    private val sri = Person("Sri", Color(0xFF2F5D4A))
    private val budi = Person("Budi", Color(0xFFB0643A))
    private val dewi = Person("Dewi", Color(0xFF6C5A8E))
    private val agus = Person("Agus", Color(0xFF3E6E8E))
    private val rina = Person("Rina", Color(0xFF9A7A2F))

    private val evening = listOf(budi, sri, dewi, agus, rina, budi, sri)
    private val days = listOf("Sen" to 28, "Sel" to 29, "Rab" to 30, "Kam" to 1, "Jum" to 2, "Sab" to 3, "Min" to 4)

    val home = HomeState(
        todayLabel = "Selasa, 29 Sept",
        circleName = "Tukiman",
        memberCount = 6,
        next = NextAppointment(
            whenLabel = "Hari ini · 14.30",
            countdown = "3 jam 10 mnt lagi",
            title = "Kontrol neurologi",
            provider = "Dr. Anand Rao · Peninsula Neurology",
            driver = budi,
            leavesAt = "13.45",
            questionCount = 4,
            noteReady = false,
        ),
        week = days.mapIndexed { i, (dow, num) -> DutyDay(dow, num, evening[i], isToday = i == 1) },
        dutyLegend = "Telepon cek malam, 19.00. Malam ini giliran Anda.",
        medsToday = 4,
        nextMed = "Omeprazole · sebelum sarapan",
        feed = listOf(
            FeedItem(budi, "Obat pagi diberikan: clopidogrel, amlodipine.", "Hari ini, 08.05"),
            FeedItem(budi, "Konfirmasi via SMS: mengantar ke neurologi jam 13.45.", "Hari ini, 07.40"),
            FeedItem(budi, "Telepon malam: tensi 132/84, sudah makan malam, jalan ke teras pakai tongkat. Suasana hati baik.", "Sen, 19.10"),
        ),
    )
}
