package id.kinfolk.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MedicationContactTest {

    @Test
    fun `a Member records Medications and the circle sees them in time order`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single().id
        val budi = signedInSibling(sri, circle)

        sri.addMedication(MedicationDraft(circle, tukiman, "Atorvastatin", "20 mg", "malam, 21.00", LocalTime(21, 0)))
        sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi, sesudah makan", LocalTime(7, 0)))

        val meds = budi.medications(circle)
        assertEquals(listOf("Clopidogrel", "Atorvastatin"), meds.map { it.name })
        assertEquals("75 mg", meds.first().dose)
        assertEquals("pagi, sesudah makan", meds.first().schedule)
        assertEquals(LocalTime(7, 0), meds.first().timeOfDay)
        assertTrue(meds.all { it.active })
    }

    @Test
    fun `an inactive Medication doesn't count as current or come next`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single().id
        val clop = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        sri.addMedication(MedicationDraft(circle, tukiman, "Omeprazole", "20 mg", "sebelum sarapan", LocalTime(6, 30)))

        sri.editMedication(clop.id, clop.draft().copy(dose = "150 mg", active = false))

        val meds = sri.medications(circle)
        assertEquals(listOf("Omeprazole"), meds.current().map { it.name })
        assertEquals("Omeprazole", meds.current().progress(emptyList()).next?.name)
        assertEquals("150 mg", meds.single { !it.active }.dose)

        sri.editMedication(clop.id, clop.draft())
        assertEquals(2, sri.medications(circle).current().size)
    }

    @Test
    fun `a Member flags Care Contacts as emergency contacts, edits and removes them`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val rao = sri.addCareContact(CareContactDraft(circle, "Dr. Anand Rao", "Dokter saraf", "+6281234567890", ContactGroup.Medical, emergency = true))
        val siti = sri.addCareContact(CareContactDraft(circle, "Bu Siti", "Tetangga, sebelah kanan", "+6281200001111", ContactGroup.Home, note = "Memegang kunci cadangan rumah"))
        sri.addCareContact(CareContactDraft(circle, "IGD RS Kariadi", "Rumah sakit pilihan", "+62248413476", ContactGroup.Emergency))

        assertEquals(listOf("Dr. Anand Rao"), sri.careContacts(circle).filter { it.emergency }.map { it.name })
        assertEquals(ContactGroup.Home, sri.careContacts(circle).single { it.id == siti.id }.group)
        assertEquals("Memegang kunci cadangan rumah", sri.careContacts(circle).single { it.id == siti.id }.note)
        assertEquals("", rao.note)

        sri.editCareContact(siti.id, siti.draft().copy(emergency = true, phone = "+6281200002222"))
        sri.removeCareContact(rao.id)

        val contacts = sri.careContacts(circle)
        assertEquals(listOf("Bu Siti", "IGD RS Kariadi"), contacts.map { it.name })
        assertEquals(listOf("Bu Siti"), contacts.filter { it.emergency }.map { it.name })
        assertEquals("+6281200002222", contacts.first().phone)

        sri.editCareContact(siti.id, siti.draft())
        assertTrue(sri.careContacts(circle).none { it.emergency })
    }

    @Test
    fun `a Member of another Care Circle can't see or change its Medications and Care Contacts`() = runBlocking {
        val sri = signedInNewcomer()
        val circle = sri.createCareCircle("Tukiman", null, emptySet())
        val tukiman = sri.careRecipients(circle).single().id
        val med = sri.addMedication(MedicationDraft(circle, tukiman, "Clopidogrel", "75 mg", "pagi", LocalTime(7, 0)))
        val contact = sri.addCareContact(CareContactDraft(circle, "Bu Siti", "Tetangga", "+6281200001111", ContactGroup.Home))
        val rudi = signedInNewcomer().apply { createCareCircle("Warsini", null, emptySet()) }

        assertTrue(rudi.medications(circle).isEmpty())
        assertTrue(rudi.careContacts(circle).isEmpty())
        assertFails { rudi.addMedication(MedicationDraft(circle, tukiman, "Palsu", "", "", LocalTime(8, 0))) }
        assertFails { rudi.addCareContact(CareContactDraft(circle, "Palsu", "", "+628100", ContactGroup.Home)) }
        rudi.editMedication(med.id, med.draft().copy(active = false))
        rudi.editCareContact(contact.id, contact.draft().copy(name = "Palsu"))
        rudi.removeCareContact(contact.id)

        assertTrue(sri.medications(circle).single().active)
        assertEquals("Bu Siti", sri.careContacts(circle).single().name)
    }
}
