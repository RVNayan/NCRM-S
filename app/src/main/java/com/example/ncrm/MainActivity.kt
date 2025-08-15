
package com.example.ncrm

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ncrm.R.id.etDoctorName
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

class MainActivity : AppCompatActivity() {


    private val hospitals = mutableListOf<Hospital>()
    private val fileName = "hospital_data.json"
    private fun displayTree() {
        val treeView = findViewById<TextView>(R.id.treeTextView)
        val sb = StringBuilder()
        for (hospital in hospitals) {
            sb.append("🏥 ${hospital.name}\n")
            for (doctor in hospital.doctors) {
                appendDoctor(sb, doctor, 1)
            }
        }
        treeView.text = sb.toString()
    }

    private fun appendDoctor(sb: StringBuilder, doctor: Doctor, level: Int) {
        val indent = "    ".repeat(level)
        sb.append("$indent👨‍⚕️ ${doctor.name} - ${doctor.role} (${doctor.address})\n") // Add address here
        for (ref in doctor.referredDoctors) {
            appendDoctor(sb, ref, level + 1)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (hospitals.isEmpty()) {
            hospitals.add(Hospital("Default Hospital"))
        }


        loadData()
        displayTree()


        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            showAddEntityDialog()
        }
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

        AlertDialog.Builder(this)
            .setTitle("Add Hospital")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.etHospitalName)?.text.toString()
                //val address = dialogView.findViewById<EditText>(R.id.etHospitalAddress)?.text.toString()

                if (name.isNotEmpty()) {
                    val existing = hospitals.find { it.name.equals(name, true) }
                    if (existing == null) {
                        hospitals.add(Hospital(name))
                        saveData()
                        Toast.makeText(this, "Hospital added: $name", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Hospital already exists", Toast.LENGTH_SHORT).show()
                    }

                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showAddNewDoctorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_doctor, null)

        AlertDialog.Builder(this)
            .setTitle("Add New Doctor")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogView.findViewById<EditText>(etDoctorName)?.text.toString()
                val role = dialogView.findViewById<EditText>(R.id.etDoctorRole)?.text.toString()
                val address = dialogView.findViewById<EditText>(R.id.etDoctorAddress)?.text.toString()
                val hospitalName = dialogView.findViewById<EditText>(R.id.etHospitalName)?.text.toString()

                if (name.isEmpty() || role.isEmpty() || address.isEmpty() || hospitalName.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                var hospital = hospitals.find { it.name.equals(hospitalName, true) }

                if (hospital == null) {
                    // Create new hospital and add the doctor to it
                    hospital = Hospital(hospitalName)
                    hospitals.add(hospital)
                    Toast.makeText(this, "Created new hospital: $hospitalName", Toast.LENGTH_SHORT).show()
                }

                // Now add the doctor to the hospital (whether it existed or was newly created)
                hospital.doctors.add(Doctor(name, role, address))
                saveData()
                Toast.makeText(this, "Doctor added to $hospitalName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showAddReferredDoctorDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_referred_doctor, null)

            // Initialize AutoCompleteTextView for referring doctor
            val referredByField = dialogView.findViewById<AutoCompleteTextView>(R.id.etReferredBy).apply {
                threshold = 1 // Show suggestions after 1 character
                setAdapter(
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        hospitals.flatMap { hospital ->
                            hospital.doctors.map { doctor ->
                                "${doctor.name} - ${doctor.role} [${hospital.name}]"
                            }
                        }
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

    private fun handleSaveReferredDoctor(dialogView: View) {
        try {
            // Get all input fields
            val referredBy = dialogView.findViewById<AutoCompleteTextView>(R.id.etReferredBy).text
                ?.toString()
                ?.substringBefore(" -")
                ?.trim() ?: ""
            val name = dialogView.findViewById<EditText>(R.id.etDoctorName).text.toString()
            val role = dialogView.findViewById<EditText>(R.id.etDoctorRole).text.toString()
            val address = dialogView.findViewById<EditText>(R.id.etDoctorAddress).text.toString()
            val hospitalName = dialogView.findViewById<EditText>(R.id.etHospitalName).text.toString()

            // Validate all fields
            if (referredBy.isEmpty() || name.isEmpty() || role.isEmpty() || address.isEmpty() || hospitalName.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return
            }

            // Find referring doctor and their hospital
            val (referringDoctor, referringHospital) = findReferringDoctorAndHospital(referredBy)
                ?: run {
                    Toast.makeText(this, "Referring doctor not found", Toast.LENGTH_SHORT).show()
                    return
                }

            // Check if doctor exists anywhere in the system
            if (isDoctorExistsInAnyHospital(name)) {
                Toast.makeText(this, "Doctor already exists in the system", Toast.LENGTH_SHORT).show()
                return
            }

            // Check if referral already exists
            if (referringDoctor.referredDoctors.any { it.name.equals(name, true) }) {
                Toast.makeText(this, "This referral already exists", Toast.LENGTH_SHORT).show()
                return
            }

            // Create and add new referred doctor
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
}