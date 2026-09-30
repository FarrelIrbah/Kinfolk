package id.kinfolk

import androidx.compose.ui.graphics.Color
import id.kinfolk.ui.home.HomeState
import id.kinfolk.ui.home.NextAppointment
import id.kinfolk.ui.home.Person

// Initial state of the v3 prototype (Bahasa Indonesia). Replaced by Supabase data later.
object SampleData {
    private val budi = Person("Budi", Color(0xFFB0643A))

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
        week = emptyList(),
        dutyLegend = "",
        medsToday = 4,
        nextMed = "Omeprazole · sebelum sarapan",
        feed = emptyList(),
    )
}
