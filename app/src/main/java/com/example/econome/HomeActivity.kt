package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.econome.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.fabAddManager.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_manager, null)
            val editTextName = dialogView.findViewById<EditText>(R.id.etManagerName)
            val editTextTotalMoney = dialogView.findViewById<EditText>(R.id.etTotalMoney)

            AlertDialog.Builder(this)
                .setTitle("Add New Manager")
                .setView(dialogView)
                .setPositiveButton("Okay") { dialog, which ->
                    val name = editTextName.text.toString()
                    val totalMoney = editTextTotalMoney.text.toString().toDouble()
                    if (name.isBlank()) {
                        Toast.makeText(this, "Manager name is required", Toast.LENGTH_SHORT).show()
                    } else {
                        checkAndCreateManager(name, totalMoney)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadManagers()
    }

    private fun checkAndCreateManager(name: String, totalMoney: Double) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            userDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val managers = document["managerNames"] as? List<String> ?: listOf()
                    if (managers.size >= 5) {
                        Toast.makeText(this, "You can only have up to 5 managers.", Toast.LENGTH_SHORT).show()
                    } else {
                        createManager(currentUser.uid, name, totalMoney)
                    }
                }
            }
        }
    }

    private fun createManager(userId: String, name: String, totalMoney: Double) {
        val newManagerDocRef = db.collection("managers").document()
        val managerId = newManagerDocRef.id
        newManagerDocRef.set(
            hashMapOf(
                "name" to name,
                "totalMoney" to totalMoney,
                "expenses" to listOf<Double>(),
                "currentExpense" to 0.0
            )
        ).addOnSuccessListener {
            val userDocRef = db.collection("users").document(userId)
            userDocRef.update("managerNames", FieldValue.arrayUnion(name), "managerIds", FieldValue.arrayUnion(managerId))
                .addOnSuccessListener {
                    Toast.makeText(this, "Manager '$name' created", Toast.LENGTH_SHORT).show()
                    loadManagers()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to create manager: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadManagers() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val managerNames = document["managerNames"] as? List<String> ?: listOf()
                    val managerIds = document["managerIds"] as? List<String> ?: listOf()
                    binding.layoutManagers.removeAllViews()
                    managerNames.zip(managerIds).forEach { (name, id) ->
                        val button = Button(this).apply {
                            text = name
                            setOnClickListener {
                                val intent = Intent(this@HomeActivity, ExpenseActivity::class.java)
                                intent.putExtra("managerId", id)
                                startActivity(intent)
                            }
                        }
                        binding.layoutManagers.addView(button)
                    }
                }
            }
        }
    }
}
