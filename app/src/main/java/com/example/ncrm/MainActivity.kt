
package com.example.ncrm

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ncrm.DetailsActivity.Companion.TYPE_DOCTOR
import com.example.ncrm.DetailsActivity.Companion.TYPE_HOSPITAL
import com.example.ncrm.R.id.etDoctorName
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

data class Doctor(
    val name: String,
    val role: String,
    val address: String,
    val referredDoctors: MutableList<Doctor> = mutableListOf()
)

data class Hospital(
    val name: String,
    //val address: String,
    val doctors: MutableList<Doctor> = mutableListOf()
)

// DetailsActivity.kt
class DetailsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_NAME = "name"
        const val EXTRA_HOSPITAL = "hospital"
        const val TYPE_HOSPITAL = "hospital"
        const val TYPE_DOCTOR = "doctor"
    }

    private val hospitals = mutableListOf<Hospital>()
    private val fileNameHospital = "hospital_data.json"
    private val fileNameNotes = "doctor_notes.json"

    private lateinit var notesContainer: LinearLayout
    private lateinit var doctorName: String
    private lateinit var hospitalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)


        val type = intent.getStringExtra(EXTRA_TYPE)
        val name = intent.getStringExtra(EXTRA_NAME)
        hospitalName = intent.getStringExtra(EXTRA_HOSPITAL) ?: ""
        doctorName = name ?: ""

        loadData()

        when (type) {
            TYPE_HOSPITAL -> {
                supportActionBar?.title = "Hospital: $name"
                findViewById<TextView>(R.id.detailsTextView).visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.doctorLayout).visibility = View.GONE
                displayHospitalHierarchy(name ?: "")
            }
            TYPE_DOCTOR -> {
                supportActionBar?.title = "Doctor: $name"
                findViewById<TextView>(R.id.detailsTextView).visibility = View.GONE
                findViewById<LinearLayout>(R.id.doctorLayout).visibility = View.VISIBLE
                setupDoctorUI()
                displayDoctorDetails(name ?: "")
            }
        }

    }

    // --------------------------
    // 📍 HOSPITAL VIEW
    // --------------------------
    private fun displayHospitalHierarchy(hospitalName: String) {
        val treeView = findViewById<TextView>(R.id.detailsTextView)
        val sb = SpannableStringBuilder()

        val hospital = hospitals.find { it.name.equals(hospitalName, true) }
        if (hospital != null) {
            sb.append("🏥 ${hospital.name}\n\n") // Show hospital name at top
            for (doctor in hospital.doctors) {
                appendDoctor(sb, doctor, 0, hospital.name)
            }
        }

        treeView.movementMethod = LinkMovementMethod.getInstance()
        treeView.text = sb

        // ✅ Long press hospital name to edit
        treeView.setOnLongClickListener {
            showEditHospitalNameDialog(hospitalName)
            true
        }
    }


    private fun appendDoctor(sb: SpannableStringBuilder, doctor: Doctor, level: Int, hospital: String) {
        val indent = "    ".repeat(level)
        val start = sb.length
        sb.append("$indent️${doctor.name} - ${doctor.role} (${doctor.address})\n")
        val end = sb.length

        sb.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@DetailsActivity, DetailsActivity::class.java).apply {
                    putExtra(EXTRA_TYPE, TYPE_DOCTOR)
                    putExtra(EXTRA_NAME, doctor.name)
                    putExtra(EXTRA_HOSPITAL, hospital)
                }
                startActivity(intent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ds.linkColor
                ds.isUnderlineText = false
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        for (ref in doctor.referredDoctors) {
            appendDoctor(sb, ref, level + 1, hospital)
        }
    }

    // --------------------------
    // 📍 DOCTOR VIEW
    // --------------------------
    private fun setupDoctorUI() {
        notesContainer = findViewById(R.id.notesContainer)

        val tvDoctorInfo = findViewById<TextView>(R.id.tvDoctorInfo)
        tvDoctorInfo.text = "👨‍⚕️ $doctorName\n🏥 $hospitalName"

// First click = edit doctor name
        tvDoctorInfo.setOnLongClickListener {
            showEditDoctorNameDialog(doctorName, hospitalName)
            true
        }

// Normal click = show phone numbers
        tvDoctorInfo.setOnClickListener {
            showPhoneNumbersDialog()
        }


        loadNotes()

        findViewById<Button>(R.id.btnAddNote).setOnClickListener {
            showAddNoteDialog()
        }
    }

    private fun displayDoctorDetails(doctorName: String) {
        val doctor = findDoctorRecursive(hospitals.flatMap { it.doctors }, doctorName)
        val treeView = findViewById<TextView>(R.id.detailsTextView)

        if (doctor != null) {
            val hospital = findHospitalForDoctor(doctorName)
            val header = "${doctor.name}\n🏥 ${hospital?.name ?: "Unknown Hospital"}\n\n"
            treeView.text = header
        } else {
            treeView.text = "Doctor not found"
        }
    }

    // --------------------------
    // 📍 NOTES HANDLING
    // --------------------------
    private fun showAddNoteDialog(existingDate: String? = null, existingDesc: String? = null, editIndex: Int? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_note, null)
        val etDate = dialogView.findViewById<EditText>(R.id.etNoteDate)
        val etDesc = dialogView.findViewById<EditText>(R.id.etNoteDesc)

        etDate.setText(existingDate ?: "")
        etDesc.setText(existingDesc ?: "")

        etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                etDate.setText("%02d/%02d/%04d".format(day, month + 1, year))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "Add Note" else "Edit Note")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val date = etDate.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (date.isNotEmpty() && desc.isNotEmpty()) {
                    if (editIndex == null) {
                        saveNoteWithDate(date, desc)
                        addNoteView(date, desc, notesContainer.childCount)
                    } else {
                        updateNoteAtIndex(editIndex, date, desc)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    private fun addNoteView(date: String, desc: String, index: Int) {
        val preview = if (desc.length > 40) desc.take(40) + "..." else desc

        val noteView = TextView(this).apply {
            text = "📅 $date\n$preview"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                AlertDialog.Builder(this@DetailsActivity)
                    .setTitle("Note on $date")
                    .setMessage(desc)
                    .setPositiveButton("Edit") { _, _ ->
                        showAddNoteDialog(date, desc, index)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
        notesContainer.addView(noteView)
    }

    private fun saveNoteWithDate(date: String, desc: String) {
        val file = File(getExternalFilesDir(null), fileNameNotes)
        val jsonArray = if (file.exists()) JSONArray(file.readText()) else JSONArray()

        var doctorObj: JSONObject? = null
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("doctor") == doctorName && obj.getString("hospital") == hospitalName) {
                doctorObj = obj
                break
            }
        }

        if (doctorObj == null) {
            doctorObj = JSONObject().apply {
                put("doctor", doctorName)
                put("hospital", hospitalName)
                put("phones", JSONArray())
                put("notes", JSONArray().put(JSONObject().apply {
                    put("date", date)
                    put("desc", desc)
                }))
            }
            jsonArray.put(doctorObj)
        } else {
            val notesArray = doctorObj.optJSONArray("notes") ?: JSONArray()
            notesArray.put(JSONObject().apply {
                put("date", date)
                put("desc", desc)
            })
            doctorObj.put("notes", notesArray)
        }

        file.writeText(jsonArray.toString())
    }

    private fun loadNotes() {
        val file = File(getExternalFilesDir(null), fileNameNotes)
        if (!file.exists()) return

        val jsonArray = JSONArray(file.readText())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("doctor") == doctorName && obj.getString("hospital") == hospitalName) {
                val notesArray = obj.getJSONArray("notes")
                for (j in 0 until notesArray.length()) {
                    val noteObj = notesArray.getJSONObject(j)
                    addNoteView(noteObj.getString("date"), noteObj.getString("desc"), j)
                }
            }
        }
    }

    private fun updateNoteAtIndex(index: Int, newDate: String, newDesc: String) {
        val file = File(getExternalFilesDir(null), fileNameNotes)
        if (!file.exists()) return
        val jsonArray = JSONArray(file.readText())

        var doctorObj: JSONObject? = null
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("doctor") == doctorName && obj.getString("hospital") == hospitalName) {
                doctorObj = obj
                break
            }
        }

        doctorObj?.let { dObj ->
            val notesArray = dObj.optJSONArray("notes") ?: JSONArray()
            if (index in 0 until notesArray.length()) {
                val noteObj = notesArray.getJSONObject(index)
                noteObj.put("date", newDate)
                noteObj.put("desc", newDesc)

                file.writeText(jsonArray.toString())
                notesContainer.removeAllViews()
                for (i in 0 until notesArray.length()) {
                    val note = notesArray.getJSONObject(i)
                    addNoteView(note.getString("date"), note.getString("desc"), i)
                }
            }
        }
    }

    // --------------------------
    // 📍 PHONE NUMBERS
    // --------------------------
    private fun showPhoneNumbersDialog() {
        val file = File(getExternalFilesDir(null), fileNameNotes)
        val phones = mutableListOf<String>()

        if (file.exists()) {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("doctor") == doctorName && obj.getString("hospital") == hospitalName) {
                    val phoneArray = obj.optJSONArray("phones") ?: JSONArray()
                    for (j in 0 until phoneArray.length()) {
                        phones.add(phoneArray.getString(j))
                    }
                }
            }
        }

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val phoneInputs = mutableListOf<EditText>()
        if (phones.isEmpty()) {
            val input = EditText(this).apply { hint = "Enter phone number" }
            phoneInputs.add(input)
            dialogView.addView(input)
        } else {
            phones.forEach { number ->
                val input = EditText(this).apply { setText(number) }
                phoneInputs.add(input)
                dialogView.addView(input)
            }
        }

        val addMoreBtn = Button(this).apply {
            text = "➕ Add Another Number"
            setOnClickListener {
                val newInput = EditText(this@DetailsActivity).apply {
                    hint = "Enter phone number"
                }
                phoneInputs.add(newInput)
                dialogView.addView(newInput)
            }
        }
        dialogView.addView(addMoreBtn)

        AlertDialog.Builder(this)
            .setTitle("📞 Phone Numbers")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updatedPhones = phoneInputs.mapNotNull { it.text.toString().trim().takeIf { txt -> txt.isNotEmpty() } }
                savePhoneNumbers(updatedPhones)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePhoneNumbers(numbers: List<String>) {
        val file = File(getExternalFilesDir(null), fileNameNotes)
        val jsonArray = if (file.exists()) JSONArray(file.readText()) else JSONArray()

        var doctorObj: JSONObject? = null
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("doctor") == doctorName && obj.getString("hospital") == hospitalName) {
                doctorObj = obj
                break
            }
        }

        if (doctorObj == null) {
            doctorObj = JSONObject().apply {
                put("doctor", doctorName)
                put("hospital", hospitalName)
                put("phones", JSONArray(numbers))
                put("notes", JSONArray())
            }
            jsonArray.put(doctorObj)
        } else {
            doctorObj.put("phones", JSONArray(numbers))
        }

        file.writeText(jsonArray.toString())
        Toast.makeText(this, "Saved phone numbers", Toast.LENGTH_SHORT).show()
    }

    // --------------------------
    // 📍 DATA HELPERS
    // --------------------------
    private fun loadData() {
        val file = File(getExternalFilesDir(null), fileNameHospital)
        if (file.exists()) {
            val jsonText = file.readText()
            val jsonArray = JSONArray(jsonText)
            hospitals.clear()
            for (i in 0 until jsonArray.length()) {
                val hObj = jsonArray.getJSONObject(i)
                val doctors = mutableListOf<Doctor>()
                val doctorArray = hObj.getJSONArray("doctors")
                for (j in 0 until doctorArray.length()) {
                    doctors.add(jsonToDoctor(doctorArray.getJSONObject(j)))
                }
                hospitals.add(Hospital(hObj.getString("name"), doctors))
            }
        }
    }

    private fun jsonToDoctor(obj: JSONObject): Doctor {
        val name = obj.getString("name")
        val role = obj.getString("role")
        val address = obj.getString("address")
        val referredArray = obj.getJSONArray("referredDoctors")
        val referredDoctors = mutableListOf<Doctor>()
        for (i in 0 until referredArray.length()) {
            referredDoctors.add(jsonToDoctor(referredArray.getJSONObject(i)))
        }
        return Doctor(name, role, address, referredDoctors)
    }

    private fun findHospitalForDoctor(doctorName: String): Hospital? {
        return hospitals.find { hospital ->
            findDoctorRecursive(hospital.doctors, doctorName) != null
        }
    }

    private fun findDoctorRecursive(doctors: List<Doctor>, name: String): Doctor? {
        doctors.forEach { doctor ->
            if (doctor.name.equals(name, true)) return doctor
            findDoctorRecursive(doctor.referredDoctors, name)?.let { return it }
        }
        return null
    }

    // --------------------------
    // 📍 Name and Hospital edits
    // --------------------------
    private fun showEditHospitalNameDialog(oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
        }

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Hospital Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    if (isHospitalExists(newName)) {
                        Toast.makeText(this, "⚠️ Hospital with this name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        updateHospitalName(oldName, newName)
                        Toast.makeText(this, "✅ Hospital name updated", Toast.LENGTH_SHORT).show()
                        reloadDataAndRefresh()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDoctorNameDialog(oldName: String, hospital: String) {
        val input = EditText(this).apply {
            setText(oldName)
        }

        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Doctor Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    if (isDoctorExists(newName)) {
                        Toast.makeText(this, "⚠️ Doctor with this name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        updateDoctorName(hospital, oldName, newName)
                        Toast.makeText(this, "✅ Doctor name updated", Toast.LENGTH_SHORT).show()
                        reloadDataAndRefresh()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun isHospitalExists(name: String): Boolean {
        val file = File(getExternalFilesDir(null), "hospital_data.json")
        if (!file.exists()) return false
        val array = JSONArray(file.readText())
        for (i in 0 until array.length()) {
            if (array.getJSONObject(i).getString("name").equals(name, true)) {
                return true
            }
        }
        return false
    }

    private fun isDoctorExists(name: String): Boolean {
        val file = File(getExternalFilesDir(null), "hospital_data.json")
        if (!file.exists()) return false
        val array = JSONArray(file.readText())
        for (i in 0 until array.length()) {
            val doctorsArray = array.getJSONObject(i).getJSONArray("doctors")
            if (findDoctorRecursive(doctorsArray, name)) return true
        }
        return false
    }

    private fun findDoctorRecursive(doctorsArray: JSONArray, name: String): Boolean {
        for (i in 0 until doctorsArray.length()) {
            val doc = doctorsArray.getJSONObject(i)
            if (doc.getString("name").equals(name, true)) {
                return true
            }
            if (findDoctorRecursive(doc.getJSONArray("referredDoctors"), name)) return true
        }
        return false
    }
    private fun reloadDataAndRefresh() {
        loadData() // your existing function that reloads JSON into memory
        val type = intent.getStringExtra(EXTRA_TYPE)
        val name = intent.getStringExtra(EXTRA_NAME)

        when (type) {
            TYPE_HOSPITAL -> displayHospitalHierarchy(name ?: "")
            TYPE_DOCTOR -> displayDoctorDetails(name ?: "")
        }
    }

    private fun updateHospitalName(oldName: String, newName: String) {
        // Update hospital_data.json
        val hospitalFile = File(getExternalFilesDir(null), "hospital_data.json")
        if (hospitalFile.exists()) {
            val array = JSONArray(hospitalFile.readText())
            for (i in 0 until array.length()) {
                val hObj = array.getJSONObject(i)
                if (hObj.getString("name") == oldName) {
                    hObj.put("name", newName)
                }
            }
            hospitalFile.writeText(array.toString())
        }

        // Update doctor_notes.json
        val notesFile = File(getExternalFilesDir(null), "doctor_notes.json")
        if (notesFile.exists()) {
            val array = JSONArray(notesFile.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("hospital") == oldName) {
                    obj.put("hospital", newName)
                }
            }
            notesFile.writeText(array.toString())
        }
    }
    private fun updateDoctorName(hospitalName: String, oldDoctor: String, newDoctor: String) {
        // Update hospital_data.json
        val hospitalFile = File(getExternalFilesDir(null), "hospital_data.json")
        if (hospitalFile.exists()) {
            val array = JSONArray(hospitalFile.readText())
            for (i in 0 until array.length()) {
                val hObj = array.getJSONObject(i)
                if (hObj.getString("name") == hospitalName) {
                    val doctorsArray = hObj.getJSONArray("doctors")
                    updateDoctorRecursive(doctorsArray, oldDoctor, newDoctor)
                }
            }
            hospitalFile.writeText(array.toString())
        }

        // Update doctor_notes.json
        val notesFile = File(getExternalFilesDir(null), "doctor_notes.json")
        if (notesFile.exists()) {
            val array = JSONArray(notesFile.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("hospital") == hospitalName &&
                    obj.getString("doctor") == oldDoctor) {
                    obj.put("doctor", newDoctor)
                }
            }
            notesFile.writeText(array.toString())
        }
    }

    private fun updateDoctorRecursive(doctorsArray: JSONArray, oldName: String, newName: String) {
        for (i in 0 until doctorsArray.length()) {
            val doc = doctorsArray.getJSONObject(i)
            if (doc.getString("name") == oldName) {
                doc.put("name", newName)
            }
            val refs = doc.getJSONArray("referredDoctors")
            updateDoctorRecursive(refs, oldName, newName)
        }
    }

}

//end of details Activity.kt
class MainActivity : AppCompatActivity() {


    private val hospitals = mutableListOf<Hospital>()
    private val fileName = "hospital_data.json"

    private fun displayTree() {
        val treeView = findViewById<TextView>(R.id.treeTextView)
        val sb = SpannableStringBuilder()

        for (hospital in hospitals) {
            // Make hospital name clickable
            val hospitalStart = sb.length
            sb.append("🏥 ${hospital.name}\n")
            val hospitalEnd = sb.length
            sb.setSpan(
                createClickableSpan(TYPE_HOSPITAL, hospital.name),
                hospitalStart, hospitalEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            for (doctor in hospital.doctors) {
                appendDoctor(sb, doctor, 1)
            }
        }

        treeView.movementMethod = LinkMovementMethod.getInstance()
        treeView.text = sb
    }

    private fun appendDoctor(sb: SpannableStringBuilder, doctor: Doctor, level: Int) {
        val indent = "    ".repeat(level)
        val doctorStart = sb.length
        sb.append("$indent${doctor.name} - ${doctor.role} (${doctor.address})\n")
        val doctorEnd = sb.length

        // Make doctor name clickable
        sb.setSpan(
            createClickableSpan(TYPE_DOCTOR, doctor.name),
            doctorStart, doctorEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        for (ref in doctor.referredDoctors) {
            appendDoctor(sb, ref, level + 1)
        }
    }

    private fun createClickableSpan(type: String, name: String): ClickableSpan {
        return object : ClickableSpan() {
            override fun onClick(widget: View) {
                var hospitalName = ""
                if (type == TYPE_DOCTOR) {
                    // Find which hospital this doctor belongs to
                    hospitals.forEach { hospital ->
                        if (findDoctorRecursive(hospital.doctors, name) != null) {
                            hospitalName = hospital.name
                        }
                    }
                }

                val intent = Intent(this@MainActivity, DetailsActivity::class.java).apply {
                    putExtra("type", type)
                    putExtra("name", name)
                    putExtra("hospital", hospitalName)
                }
                startActivity(intent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ds.linkColor
                ds.isUnderlineText = false
            }
        }
    }


//    private fun openDetail(type: String, name: String) {
//        val intent = Intent(this, DetailActivity::class.java)
//        intent.putExtra("type", type)
//        intent.putExtra("name", name)
//        startActivity(intent)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importData()
        }

        if (hospitals.isEmpty()) {
            hospitals.add(Hospital("Default Hospital"))
        }

        loadData()
        displayTree()

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { showAddEntityDialog() }

        // Search setup
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText?.trim() ?: ""
                if (q.isEmpty()) {
                    // If user cleared the search box, show original full hierarchy
                    displayTree()
                } else {
                    performSearch(q)
                }
                return true
            }
        })

    }
    private fun doctorMatchesRecursive(doc: Doctor, qLower: String): Boolean {
        // check name, role, address (add other fields if you want)
        if (doc.name.lowercase().contains(qLower)
            || doc.role.lowercase().contains(qLower)
            || doc.address.lowercase().contains(qLower)
        ) return true

        // check referred doctors recursively
        for (ref in doc.referredDoctors) {
            if (doctorMatchesRecursive(ref, qLower)) return true
        }
        return false
    }

    private fun performSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            displayTree()
            return
        }

        val lowerQuery = q.lowercase()
        val treeView = findViewById<TextView>(R.id.treeTextView)
        val sb = SpannableStringBuilder()

        for (hospital in hospitals) {
            // does hospital itself match OR any doctor (recursively) match?
            val hospitalMatch = hospital.name.lowercase().contains(lowerQuery)
            val matchedDoctors = hospital.doctors.filter { doctorMatchesRecursive(it, lowerQuery) }

            if (hospitalMatch || matchedDoctors.isNotEmpty()) {
                // Append hospital
                val hospitalStart = sb.length
                sb.append("🏥 ${hospital.name}\n")
                val hospitalEnd = sb.length
                sb.setSpan(
                    createClickableSpan(TYPE_HOSPITAL, hospital.name),
                    hospitalStart, hospitalEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // Append only those top-level doctors whose subtree contains matches
                for (doctor in hospital.doctors) {
                    if (doctorMatchesRecursive(doctor, lowerQuery)) {
                        appendDoctor(sb, doctor, 1) // this will include referred doctors (the subtree)
                    }
                }
            }
        }

        treeView.movementMethod = LinkMovementMethod.getInstance()
        treeView.text = if (sb.isNotEmpty()) sb else SpannableStringBuilder("No matches found")
    }

    private fun showAddEntityDialog() {
        val options = arrayOf("Add Hospital", "Add New Doctor", "Add Referred Doctor")

        AlertDialog.Builder(this)
            .setTitle("Add Entity")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddHospitalDialog()
                    1 -> showAddNewDoctorDialog()
                    2 -> showAddReferredDoctorDialog()
                }
            }
            .show()
    }

    private fun showAddHospitalDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_hospital, null)
        val etHospitalName = dialogView.findViewById<AutoCompleteTextView>(R.id.etHospitalName)

        // Suggestions
        setupAutoComplete(etHospitalName, getHospitalNames())

        AlertDialog.Builder(this)
            .setTitle("Add Hospital")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etHospitalName.text.toString().trim()

                if (name.isNotEmpty()) {
                    val existing = hospitals.find { it.name.equals(name, true) }
                    if (existing == null) {
                        hospitals.add(Hospital(name))
                        saveData()
                        displayTree()
                        Toast.makeText(this, "Hospital added: $name", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "⚠️ Hospital already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    @SuppressLint("MissingInflatedId")
    private fun showAddNewDoctorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_doctor, null)

        val etDoctorName = dialogView.findViewById<AutoCompleteTextView>(R.id.etDoctorName)
        val etHospitalName = dialogView.findViewById<AutoCompleteTextView>(R.id.etHospitalName)

        // Suggestions
        setupAutoComplete(etDoctorName, getDoctorNames())
        setupAutoComplete(etHospitalName, getHospitalNames())

        AlertDialog.Builder(this)
            .setTitle("Add New Doctor")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etDoctorName.text.toString().trim()
                val role = dialogView.findViewById<EditText>(R.id.etDoctorRole).text.toString().trim()
                val address = dialogView.findViewById<EditText>(R.id.etDoctorAddress).text.toString().trim()
                val hospitalName = etHospitalName.text.toString().trim()

                if (name.isEmpty() || role.isEmpty() || address.isEmpty() || hospitalName.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                var hospital = hospitals.find { it.name.equals(hospitalName, true) }
                if (hospital == null) {
                    hospital = Hospital(hospitalName)
                    hospitals.add(hospital)
                    Toast.makeText(this, "Created new hospital: $hospitalName", Toast.LENGTH_SHORT).show()
                }

                // Prevent duplicate doctors in this hospital
                if (hospital.doctors.any { it.name.equals(name, true) }) {
                    Toast.makeText(this, "⚠️ Doctor already exists in $hospitalName", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                hospital.doctors.add(Doctor(name, role, address))
                saveData()
                displayTree()
                Toast.makeText(this, "Doctor added to $hospitalName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    @SuppressLint("MissingInflatedId")
    private fun showAddReferredDoctorDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_referred_doctor, null)

            // 🔹 Collect ALL doctors from all hospitals (including referred ones)
            val allDoctorStrings = mutableListOf<String>()
            for (hospital in hospitals) {
                collectAllDoctors(hospital.doctors, hospital.name, allDoctorStrings)
            }

            // Initialize AutoCompleteTextView for referring doctor
            val referredByField = dialogView.findViewById<AutoCompleteTextView>(R.id.etReferredBy).apply {
                threshold = 1 // Show suggestions after 1 character
                setAdapter(
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        allDoctorStrings
                    )
                )
            }

            // 🔹 Add hospital suggestions too (optional, for the Hospital Name field)
            val hospitalField = dialogView.findViewById<AutoCompleteTextView>(R.id.etHospitalName).apply {
                threshold = 1
                setAdapter(
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        hospitals.map { it.name }
                    )
                )
            }

            // 🔹 Add doctor name suggestions (to avoid duplicates + speed entry)
            val doctorNameField = dialogView.findViewById<AutoCompleteTextView>(R.id.etDoctorName).apply {
                threshold = 1
                setAdapter(
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        allDoctorStrings.map { it.substringBefore(" -") }.distinct() // just doctor names
                    )
                )
            }

            AlertDialog.Builder(this)
                .setTitle("Add Referred Doctor")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    handleSaveReferredDoctor(dialogView)
                }
                .setNegativeButton("Cancel", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error showing dialog: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("DialogError", "Failed to show dialog", e)
        }
    }
    // Recursively collect ALL doctors in a hospital (including referred ones)
    private fun collectAllDoctors(doctors: List<Doctor>, hospitalName: String, result: MutableList<String>) {
        for (doctor in doctors) {
            result.add("${doctor.name} - ${doctor.role} [${hospitalName}]")
            if (doctor.referredDoctors.isNotEmpty()) {
                collectAllDoctors(doctor.referredDoctors, hospitalName, result)
            }
        }
    }


    private fun handleSaveReferredDoctor(dialogView: View) {
        try {
            // Get all input fields
            val referredBy = dialogView.findViewById<AutoCompleteTextView>(R.id.etReferredBy).text
                ?.toString()
                ?.substringBefore(" -")
                ?.trim() ?: ""

            val name = dialogView.findViewById<AutoCompleteTextView>(R.id.etDoctorName).text.toString().trim()
            val role = dialogView.findViewById<EditText>(R.id.etDoctorRole).text.toString().trim()
            val address = dialogView.findViewById<EditText>(R.id.etDoctorAddress).text.toString().trim()
            val hospitalName = dialogView.findViewById<AutoCompleteTextView>(R.id.etHospitalName).text.toString().trim()

            // ✅ Validate all fields first
            if (referredBy.isEmpty() || name.isEmpty() || role.isEmpty() || address.isEmpty() || hospitalName.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return
            }

            // ✅ Check if doctor already exists anywhere
            if (isDoctorExistsInAnyHospital(name)) {
                Toast.makeText(this, "⚠️ Doctor already exists in the system", Toast.LENGTH_SHORT).show()
                return
            }

            // ✅ Find referring doctor and hospital
            val (referringDoctor, referringHospital) = findReferringDoctorAndHospital(referredBy)
                ?: run {
                    Toast.makeText(this, "Referring doctor not found", Toast.LENGTH_SHORT).show()
                    return
                }

            // ✅ Check if referral already exists
            if (referringDoctor.referredDoctors.any { it.name.equals(name, true) }) {
                Toast.makeText(this, "This referral already exists", Toast.LENGTH_SHORT).show()
                return
            }

            // ✅ Create and add new referred doctor
            referringDoctor.referredDoctors.add(Doctor(name, role, address))
            saveData()
            displayTree()
            Toast.makeText(this, "Added referral to $referredBy", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("SaveError", "Failed to save referral", e)
        }
    }


    // Helper function to check if doctor exists anywhere
    private fun isDoctorExistsInAnyHospital(doctorName: String): Boolean {
        return hospitals.any { hospital ->
            findDoctorRecursive(hospital.doctors, doctorName) != null
        }
    }

    // Helper to find doctor and their hospital
    private fun findReferringDoctorAndHospital(doctorName: String): Pair<Doctor, Hospital>? {
        hospitals.forEach { hospital ->
            findDoctorRecursive(hospital.doctors, doctorName)?.let { doctor ->
                return Pair(doctor, hospital)
            }
        }
        return null
    }

    // Recursive doctor search
    private fun findDoctorRecursive(doctors: List<Doctor>, name: String): Doctor? {
        doctors.forEach { doctor ->
            if (doctor.name.equals(name, true)) return doctor
            findDoctorRecursive(doctor.referredDoctors, name)?.let { return it }
        }
        return null
    }



    private fun saveData() {
        val jsonArray = JSONArray()
        for (hospital in hospitals) {
            val hObj = JSONObject()
            hObj.put("name", hospital.name)
//            hObj.put("address", hospital.address)
            val doctorArray = JSONArray()
            for (doctor in hospital.doctors) {
                doctorArray.put(doctorToJson(doctor))
            }
            hObj.put("doctors", doctorArray)
            jsonArray.put(hObj)
            displayTree()
        }
        val file = File(getExternalFilesDir(null), fileName)
        file.writeText(jsonArray.toString())
        Toast.makeText(this, "Data saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun loadData() {
        val file = File(getExternalFilesDir(null), fileName)
        if (file.exists()) {
            val jsonText = file.readText()
            val jsonArray = JSONArray(jsonText)
            hospitals.clear()
            for (i in 0 until jsonArray.length()) {
                val hObj = jsonArray.getJSONObject(i)
                val doctors = mutableListOf<Doctor>()
                val doctorArray = hObj.getJSONArray("doctors")
                for (j in 0 until doctorArray.length()) {
                    doctors.add(jsonToDoctor(doctorArray.getJSONObject(j)))
                }
                hospitals.add(Hospital(hObj.getString("name"), doctors))

            }
        }
    }

    private fun jsonToDoctor(obj: JSONObject): Doctor {
        val name = obj.getString("name")
        val role = obj.getString("role")
        val address = obj.getString("address") // Add this line
        val referredArray = obj.getJSONArray("referredDoctors")
        val referredDoctors = mutableListOf<Doctor>()
        for (i in 0 until referredArray.length()) {
            referredDoctors.add(jsonToDoctor(referredArray.getJSONObject(i)))
        }
        return Doctor(name, role, address, referredDoctors) // Remove null, use address
    }

    // Update doctorToJson to save address:
    private fun doctorToJson(doc: Doctor): JSONObject {
        val obj = JSONObject()
        obj.put("name", doc.name)
        obj.put("role", doc.role)
        obj.put("address", doc.address) // Add this line
        val referredArray = JSONArray()
        for (r in doc.referredDoctors) {
            referredArray.put(doctorToJson(r))
        }
        obj.put("referredDoctors", referredArray)
        return obj
    }


    // For preventing Duplicates
    private fun getHospitalNames(): List<String> {
        return hospitals.map { it.name }.distinct()
    }

    private fun getDoctorNames(): List<String> {
        return hospitals.flatMap { it.doctors.map { doc -> doc.name } }.distinct()
    }

    private fun setupAutoComplete(view: AutoCompleteTextView, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        view.setAdapter(adapter)
    }

    private val IMPORT_HOSPITAL_REQUEST_CODE = 2001
    private val IMPORT_NOTES_REQUEST_CODE = 2002

    private var importedHospitalJson: String? = null
    private var importedNotesJson: String? = null

    // Start import process
    private fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_HOSPITAL_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) return

        try {
            val uri = data.data!!
            val json = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: ""

            when (requestCode) {
                IMPORT_HOSPITAL_REQUEST_CODE -> {
                    if (json.isNotBlank()) {
                        importedHospitalJson = json
                        Toast.makeText(this, "✅ Hospital data selected", Toast.LENGTH_SHORT).show()

                        // Now ask for doctor_notes.json
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                        }
                        startActivityForResult(intent, IMPORT_NOTES_REQUEST_CODE)
                    }
                }

                IMPORT_NOTES_REQUEST_CODE -> {
                    if (json.isNotBlank()) {
                        importedNotesJson = json
                        Toast.makeText(this, "✅ Doctor notes selected", Toast.LENGTH_SHORT).show()

                        // Now write both files
                        if (importedHospitalJson != null && importedNotesJson != null) {
                            overwriteJsonFile("hospital_data.json", importedHospitalJson!!)
                            overwriteJsonFile("doctor_notes.json", importedNotesJson!!)
                            Toast.makeText(this, "🎉 Import successful", Toast.LENGTH_LONG).show()

                            // Refresh UI
                            loadData()
                            displayTree()

                            // reset after import
                            importedHospitalJson = null
                            importedNotesJson = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun overwriteJsonFile(fileName: String, content: String) {
        val file = File(getExternalFilesDir(null), fileName)
        file.writeText(content)
    }

}