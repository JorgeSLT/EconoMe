package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.econome.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

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

        binding.btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnCreateManager.setOnClickListener {
            val totalMoney = binding.etTotalMoney.text.toString().toDouble()
            val newManagerDocRef = db.collection("managers").document()
            val managerId = newManagerDocRef.id

            newManagerDocRef.set(
                hashMapOf(
                    "totalMoney" to totalMoney,
                    "expenses" to listOf<Double>(), // Crear una lista vacía para almacenar los gastos
                    "currentExpense" to 0.0
                )
            ).addOnSuccessListener {
                Toast.makeText(this, "Manager created with ID: $managerId", Toast.LENGTH_SHORT).show()
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userDocRef = db.collection("users").document(currentUser.uid)
                    userDocRef.update("managerIds", FieldValue.arrayUnion(managerId))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Manager ID added to user", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to add manager ID to user: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create manager: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnAddExpense.setOnClickListener {
            val expense = binding.etExpense.text.toString().toDouble()
            if (expense <= 0) {
                Toast.makeText(this, "Expense must be greater than 0", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userDocRef = db.collection("users").document(currentUser.uid)
                userDocRef.get().addOnSuccessListener { document ->
                    if (document.exists()) {
                        val managerIds = document.get("managerIds") as List<String>
                        if (managerIds.isNotEmpty()) {
                            val managerId = managerIds[0]  // Use the first managerId for example
                            val managerDocRef = db.collection("managers").document(managerId)

                            db.runTransaction { transaction ->
                                val snapshot = transaction.get(managerDocRef)
                                val currentExpense = snapshot.getDouble("currentExpense") ?: 0.0
                                val totalMoney = snapshot.getDouble("totalMoney") ?: 0.0

                                if (currentExpense + expense <= totalMoney) {
                                    transaction.update(managerDocRef, "expenses", FieldValue.arrayUnion(expense))
                                    transaction.update(managerDocRef, "currentExpense", FieldValue.increment(expense))
                                } else {
                                    throw FirebaseFirestoreException("Expense exceeds the budget", FirebaseFirestoreException.Code.ABORTED)
                                }
                            }.addOnSuccessListener {
                                Toast.makeText(this, "Expense added successfully", Toast.LENGTH_SHORT).show()
                                binding.etExpense.setText("") // Limpiar el campo de texto después de añadir el gasto
                            }.addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to add expense: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, "No manager ID found", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
