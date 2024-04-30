package com.example.econome

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.econome.databinding.ActivityManagerDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class ManagerDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManagerDetailsBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()

        val managerId = intent.getStringExtra("managerId") ?: ""

        setupManagerDetails(managerId)
        setupButtons(managerId)
    }

    private fun setupManagerDetails(managerId: String) {
        db.collection("managers").document(managerId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val managerName = document.getString("name") ?: "No name"
                val totalMoney = document.getDouble("totalMoney") ?: 0.0
                setTitle(managerName)  // Set the title of the activity to the manager's name
                binding.tvTotalMoney.text = "Total Money: $totalMoney"
            }
        }
    }

    private fun setupButtons(managerId: String) {
        binding.btnAddExpense.setOnClickListener {
            handleExpenseAddition(managerId)
        }

        binding.btnDeleteManager.setOnClickListener {
            handleManagerDeletion(managerId)
        }
    }

    private fun handleExpenseAddition(managerId: String) {
        val expense = binding.etExpense.text.toString().toDouble()
        if (expense <= 0) {
            Toast.makeText(this, "Expense must be greater than 0", Toast.LENGTH_LONG).show()
            return
        }

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
            binding.etExpense.setText("")  // Clear the input field
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to add expense: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleManagerDeletion(managerId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            val managerDocRef = db.collection("managers").document(managerId)
            managerDocRef.get().addOnSuccessListener { managerDoc ->
                if (managerDoc.exists()) {
                    val managerName = managerDoc.getString("name") ?: ""
                    val creatorId = managerDoc.getString("creatorId") ?: ""

                    // Remover el manager de los arrays del usuario
                    userDocRef.update(
                        "managerIds", FieldValue.arrayRemove(managerId),
                        "managerNames", FieldValue.arrayRemove(managerName)
                    ).addOnSuccessListener {
                        // Si el usuario actual es el creador, también eliminamos el documento del manager
                        if (creatorId == currentUser.uid) {
                            managerDocRef.delete().addOnSuccessListener {
                                Toast.makeText(this, "Manager deleted completely", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Manager removed from your list", Toast.LENGTH_SHORT).show()
                        }
                        finish()  // Cierra la actividad y regresa a la anterior
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to remove manager: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()  // Close this activity and return to the previous one
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
