package id.kinfolk.ui.tasks

import id.kinfolk.data.Task
import id.kinfolk.ui.appointment.dayMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// Task copy from design v3 `tasks`: "Tenggat 6 Okt", "Terlambat 2 hari", "Dari neurologi".

fun dueLabel(due: LocalDate, today: LocalDate) = if (due < today) "Terlambat ${due.daysUntil(today)} hari" else dueOn(due)

/** "Tenggat 8 Okt": also the due pill on a Next Step (approved in #26). */
fun dueOn(due: LocalDate) = "Tenggat ${dayMonth(due)}"

/** Open Tasks past their due date, the oldest first: red on `tasks`, counted on Home's row. */
fun List<Task>.overdue(today: LocalDate) = filter { !it.done && it.due < today }.sortedBy { it.due }

/** "Dari kontrol neurologi" (approved in #26): the Appointment's title, as in a sentence. */
fun fromWhat(title: String) = title.replaceFirstChar { it.lowercase() }
