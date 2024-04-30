package com.example.econome

import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.econome.databinding.ActivityExpenseBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class ExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpenseBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()

        val managerId = intent.getStringExtra("managerId") ?: ""

        binding.btnAddExpense.setOnClickListener {
            val expense = binding.etExpense.text.toString().toDouble()
            if (expense <= 0) {
                Toast.makeText(this, "Expense must be greater than 0", Toast.LENGTH_LONG).show()
                return@setOnClickListener
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
                binding.etExpense.setText("") // Limpiar el campo de texto después de añadir el gasto
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add expense: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()  // Cierra la actividad y vuelve a la anterior
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}
