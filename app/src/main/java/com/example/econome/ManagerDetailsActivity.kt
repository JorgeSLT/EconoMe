package com.example.econome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
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
    // Declaración de las instancias para autenticacion y bbdd
    private lateinit var binding: ActivityManagerDetailsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Metodo onCreate que se llama al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Listener para la vista de navegacion del drawer menu de la app
        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        // Obtencion del ID del gestor que se esta mostrando en pantalla
        val managerId = intent.getStringExtra("managerId") ?: ""

        // Llamada a las diferentes funciones para ofrecer una vista correcta
        setupManagerDetails(managerId)
        setupButtons(managerId)
        setupPieChart(managerId)
        loadManagers()
    }

    // Se cargan desde la bbdd los datos (nombre y presupuesto) del gestor
    private fun setupManagerDetails(managerId: String) {
        db.collection("managers").document(managerId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val managerName = document.getString("name") ?: "No name"
                val totalMoney = document.getDouble("totalMoney") ?: 0.0
                setTitle(managerName)
                binding.tvManagerName.text = managerName
                binding.tvTotalMoney.text = "Total: $totalMoney"
            }
        }
    }

    // Preparacion de los diferentes listeners de los botones
    private fun setupButtons(managerId: String) {
        binding.btnAddExpense.setOnClickListener {
            showAddExpenseDialog(managerId)
        }

        binding.btnDelExpense.setOnClickListener {
            showExpenseDeletionDialog(managerId)
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog(managerId)
        }

        binding.btnGetId.setOnClickListener {
            showManagerIdDialog(managerId)
        }
    }

    // Enseña un dialogo para añadir un nuevo gasto al gestor
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

    // Logica para añadir un gasto al documento del gestor en la bbdd
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

    // Enseña un dialogo para borrar un gestor
    private fun showDeleteConfirmationDialog(managerId: String) {
        AlertDialog.Builder(this).apply {
            setTitle("Confirm Deletion")
            setMessage("Are you sure you want to delete this manager?")
            setPositiveButton("Delete") { dialog, which ->
                handleManagerDeletion(managerId)
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    // Logica para borrar un gestor
    private fun handleManagerDeletion(managerId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            val managerDocRef = db.collection("managers").document(managerId)
            managerDocRef.get().addOnSuccessListener { managerDoc ->
                if (managerDoc.exists()) {
                    val managerName = managerDoc.getString("name") ?: ""
                    val creatorId = managerDoc.getString("creatorId") ?: ""

                    // Elimina el gestor de los arrays del usuario
                    userDocRef.update(
                        "managerIds", FieldValue.arrayRemove(managerId),
                        "managerNames", FieldValue.arrayRemove(managerName)
                    ).addOnSuccessListener {
                        // Si el usuario actual es el creador, también eliminamos el documento del gestor
                        if (creatorId == currentUser.uid) {
                            managerDocRef.delete().addOnSuccessListener {
                                Toast.makeText(this, "Manager deleted completely", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Manager removed from your list", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to remove manager: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Preparacion del grafico de pizza
    private fun setupPieChart(managerId: String) {
        db.collection("managers").document(managerId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val expenses = document.get("expenses") as List<Map<String, Any>>
                val totalMoney = document.getDouble("totalMoney") ?: 0.0
                val currentExpense = expenses.sumByDouble { it["amount"] as Double }

                val entries = ArrayList<PieEntry>()
                expenses.forEach {
                    entries.add(PieEntry((it["amount"] as Double).toFloat(), "${it["name"]}: ${(it["amount"] as Double)}"))
                }
                if (totalMoney > currentExpense) {
                    entries.add(PieEntry((totalMoney - currentExpense).toFloat(), "Unspent"))
                }

                val dataSet = PieDataSet(entries, "")
                dataSet.colors = ColorTemplate.JOYFUL_COLORS.toList()
                dataSet.valueTextSize = 12f
                dataSet.valueTextColor = android.graphics.Color.BLACK  // Color del texto de los valores
                dataSet.setDrawValues(true)  // Asegura que los valores se dibujen

                val data = PieData(dataSet)
                data.setValueTextSize(12f)
                data.setValueTextColor(android.graphics.Color.BLACK)  // Configura el color del texto de los valores a negro

                binding.pieChart.data = data
                binding.pieChart.legend.isEnabled = false  // Deshabilita la leyenda completamente
                binding.pieChart.description.isEnabled = false
                binding.pieChart.setEntryLabelTextSize(12f)
                binding.pieChart.setEntryLabelColor(android.graphics.Color.BLACK)  // Configura el color de las etiquetas de entrada
                binding.pieChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD)  // Establece las etiquetas en negrita
                binding.pieChart.centerText = "Total: $${totalMoney}"
                binding.pieChart.setUsePercentValues(true)
                binding.pieChart.invalidate()  // Refresca el grafico para aplicar cambios
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading chart data", Toast.LENGTH_SHORT).show()
        }
    }

    // Enseña un dialogo para obtener el ID del gestor
    private fun showManagerIdDialog(managerId: String) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Manager ID")
        dialogBuilder.setMessage("ID: $managerId")
        dialogBuilder.setPositiveButton(R.string.copy) { dialog, which ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Manager ID", managerId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.id_copied, Toast.LENGTH_SHORT).show()
        }
        dialogBuilder.setNegativeButton("Close", null)
        dialogBuilder.show()
    }

    // Cargar los gestores para el drawer menu
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

    // Recarga el drawer menu para que aparezcan los gestores en el drawer menu
    private fun updateNavigationDrawer(managerNames: List<String>, managerIds: List<String>) {
        val menu = binding.navView.menu
        // Limpiar el menú para evitar duplicaciones
        menu.clear()
        setupDrawerHeader()

        // Añadir ítems de "Home" y "Profile"
        menu.add(Menu.NONE, R.id.nav_home, Menu.NONE, R.string.home).setIcon(R.drawable.ic_home).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@ManagerDetailsActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }
        menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.profile).setIcon(R.drawable.ic_profile).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@ManagerDetailsActivity, ProfileActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }

        // Añadir un nuevo submenu para los gestores
        val managerGroup = menu.addSubMenu("Your Managers")

        // Añadir cada gestor al submenu
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

    // Prepara el header del drawer, donde aparece la foto de perfil y el nombre del usuario
    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val imageView = headerView.findViewById<ImageView>(R.id.imageView)
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val name = documentSnapshot.getString("name") ?: "No Name Set"
                    val imageUrl = documentSnapshot.getString("profileImageUrl") ?: ""

                    tvUserName.text = name

                    // Cargar imagen con Glide
                    Glide.with(this)
                        .load(imageUrl)
                        .circleCrop()
                        .into(imageView)
                } else {
                    tvUserName.text = "User not found"
                    Toast.makeText(this, "User document does not exist", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                tvUserName.text = "Error fetching user"
                Toast.makeText(this, "Error fetching user details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            tvUserName.text = "No User Logged In"
        }
    }

    // Enseña un dialogo para borrar un gasto del gestor
    private fun showExpenseDeletionDialog(managerId: String) {
        val managerDocRef = db.collection("managers").document(managerId)
        managerDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val expenses = document.get("expenses") as List<Map<String, Any>>?
                if (expenses.isNullOrEmpty()) {
                    Toast.makeText(this, "No expenses to delete", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val expenseNames = expenses.map { it["name"].toString() + ": $" + it["amount"].toString() }
                val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, expenseNames)

                AlertDialog.Builder(this)
                    .setTitle("Select an Expense to Delete")
                    .setAdapter(arrayAdapter) { dialog, which ->
                        val selectedExpense = expenses[which]
                        deleteExpense(managerId, selectedExpense)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    // Logica para eliminar un gasto de un gestor
    private fun deleteExpense(managerId: String, expense: Map<String, Any>) {
        val managerDocRef = db.collection("managers").document(managerId)
        db.runTransaction { transaction ->
            transaction.update(managerDocRef, "expenses", FieldValue.arrayRemove(expense))
            transaction.update(managerDocRef, "currentExpense", FieldValue.increment(-(expense["amount"] as Double)))
        }.addOnSuccessListener {
            Toast.makeText(this, "Expense deleted successfully", Toast.LENGTH_SHORT).show()
            // Recargar el grafico
            setupPieChart(managerId)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to delete expense: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

}
