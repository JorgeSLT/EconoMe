package com.example.econome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.econome.databinding.ActivityManagerDetailsBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class ManagerDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManagerDetailsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,  // Asegúrate de que estos IDs coincidan
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Ahora configura los listeners para tus botones
        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                // Aquí puedes manejar más IDs si los managers dinámicos también necesitan ser manejados
                else -> false
            }
        }

        val managerId = intent.getStringExtra("managerId") ?: ""

        setupManagerDetails(managerId)
        setupButtons(managerId)
        setupPieChart(managerId)
        loadManagers()
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
            showAddExpenseDialog(managerId)
        }

        binding.btnDelete.setOnClickListener {
            handleManagerDeletion(managerId)
        }

        binding.btnGetId.setOnClickListener {
            showManagerIdDialog(managerId)
        }
    }

    private fun showAddExpenseDialog(managerId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val editTextExpenseName = dialogView.findViewById<EditText>(R.id.etExpenseName)
        val editTextExpenseAmount = dialogView.findViewById<EditText>(R.id.etExpenseAmount)

        AlertDialog.Builder(this)
            .setTitle("Add New Expense")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, which ->
                val name = editTextExpenseName.text.toString()
                val amount = editTextExpenseAmount.text.toString().toDoubleOrNull()
                if (name.isBlank() || amount == null || amount <= 0) {
                    Toast.makeText(this, "Valid name and amount greater than 0 are required", Toast.LENGTH_LONG).show()
                } else {
                    addExpense(managerId, name, amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addExpense(managerId: String, expenseName: String, expenseAmount: Double) {
        val managerDocRef = db.collection("managers").document(managerId)
        val expense = hashMapOf("name" to expenseName, "amount" to expenseAmount)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(managerDocRef)
            val currentExpense = snapshot.getDouble("currentExpense") ?: 0.0
            val totalMoney = snapshot.getDouble("totalMoney") ?: 0.0

            if (currentExpense + expenseAmount <= totalMoney) {
                transaction.update(managerDocRef, "expenses", FieldValue.arrayUnion(expense))
                transaction.update(managerDocRef, "currentExpense", FieldValue.increment(expenseAmount))
            } else {
                throw FirebaseFirestoreException("Expense exceeds the budget", FirebaseFirestoreException.Code.ABORTED)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Expense added successfully", Toast.LENGTH_SHORT).show()
            setupPieChart(managerId)  // Update pie chart
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

    private fun setupPieChart(managerId: String) {
        val pieChart = binding.pieChart

        db.collection("managers").document(managerId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val expenses = document.get("expenses") as List<Map<String, Any>>
                val totalMoney = document.getDouble("totalMoney") ?: 0.0
                val currentExpense = expenses.sumByDouble { it["amount"] as Double }

                val entries = ArrayList<PieEntry>()
                expenses.forEach {
                    entries.add(PieEntry((it["amount"] as Double).toFloat(), it["name"] as String))
                }
                if (totalMoney > currentExpense) {
                    entries.add(PieEntry((totalMoney - currentExpense).toFloat(), "Unspent"))
                }

                val dataSet = PieDataSet(entries, "Expenses")
                dataSet.setColors(*ColorTemplate.JOYFUL_COLORS)
                dataSet.valueTextSize = 12f

                val data = PieData(dataSet)
                pieChart.data = data
                pieChart.description.isEnabled = false
                pieChart.centerText = "Total: $${totalMoney}"
                pieChart.setUsePercentValues(true)
                pieChart.invalidate()  // Refreshes the pie chart
            }
        }.addOnFailureListener {
            // Handle errors
        }
    }

    private fun showManagerIdDialog(managerId: String) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Manager ID")
        dialogBuilder.setMessage("ID: $managerId")
        dialogBuilder.setPositiveButton("Copy") { dialog, which ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Manager ID", managerId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        dialogBuilder.setNegativeButton("Close", null)
        dialogBuilder.show()
    }

    private fun loadManagers() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            userDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val managerNames = document["managerNames"] as? List<String> ?: listOf()
                    val managerIds = document["managerIds"] as? List<String> ?: listOf()
                    updateNavigationDrawer(managerNames, managerIds)
                } else {
                    Toast.makeText(this, "No managers found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateNavigationDrawer(managerNames: List<String>, managerIds: List<String>) {
        val menu = binding.navView.menu
        menu.clear()  // Limpiar el menú para evitar duplicaciones
        setupDrawerHeader()

        // Añadir ítems de "Home" y "Profile"
        menu.add(Menu.NONE, R.id.nav_home, Menu.NONE, "Home").setIcon(R.drawable.ic_home).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@ManagerDetailsActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }
        menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, "Profile").setIcon(R.drawable.ic_profile).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@ManagerDetailsActivity, ProfileActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }

        // Añadir un nuevo submenú para los managers
        val managerGroup = menu.addSubMenu("Your Managers")

        // Añadir cada manager al submenú
        managerNames.zip(managerIds).forEachIndexed { index, (name, id) ->
            managerGroup.add(R.id.group_managers, Menu.NONE, index, name).apply {
                setOnMenuItemClickListener {
                    val intent = Intent(this@ManagerDetailsActivity, ManagerDetailsActivity::class.java).apply {
                        putExtra("managerId", id)
                    }
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }
    }

    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Referencia al documento del usuario
            val userDocRef = db.collection("users").document(currentUser.uid)

            // Obtener el documento del usuario
            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    // Asignar el nombre del documento a TextView
                    val name = documentSnapshot.getString("name") ?: "No Name Set"
                    tvUserName.text = name
                } else {
                    // Manejar caso donde no se encuentra el documento
                    tvUserName.text = "User not found"
                    Toast.makeText(this, "User document does not exist", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                // Manejar errores
                tvUserName.text = "Error fetching user"
                Toast.makeText(this, "Error fetching user details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Si no hay usuario autenticado, se muestra un texto por defecto o se redirige al login
            tvUserName.text = "No User Logged In"
        }
    }


}
