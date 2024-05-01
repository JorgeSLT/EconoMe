package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.example.econome.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {
    // Declaración de las instancias para autenticacion y bbdd
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomeBinding
    private lateinit var db: FirebaseFirestore

    // Metodo onCreate que se llama al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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

        // Listener para el boton de crear un nuevo gestor
        binding.btnCreateManager.setOnClickListener {
            showAddManagerDialog()
        }

        // Listener para el boton de unirse a un gestor
        binding.btnJoinManager.setOnClickListener {
            showJoinManagerDialog()
        }

        // Carga de los gestores del usuario
        loadManagers()
    }

    // Enseña un dialogo para añadir un nuevo gestor
    private fun showAddManagerDialog() {
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

    // Enseña un dialogo para unirse a un gestor
    private fun showJoinManagerDialog() {
        val editTextId = EditText(this)
        editTextId.hint = "Enter Manager ID"

        AlertDialog.Builder(this)
            .setTitle("Join a Manager")
            .setView(editTextId)
            .setPositiveButton("Join") { dialog, which ->
                val managerId = editTextId.text.toString()
                joinManager(managerId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Logica para unirse a un gestor
    private fun joinManager(managerId: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            userDocRef.get().addOnSuccessListener { document ->
                val existingManagerIds = document["managerIds"] as? List<String> ?: listOf()
                if (existingManagerIds.contains(managerId)) {
                    Toast.makeText(this, "You are already part of this manager.", Toast.LENGTH_SHORT).show()
                } else if (existingManagerIds.size >= 5) {
                    Toast.makeText(this, "You cannot join more than 5 managers.", Toast.LENGTH_SHORT).show()
                } else {
                    db.collection("managers").document(managerId).get().addOnSuccessListener { managerDoc ->
                        if (managerDoc.exists()) {
                            val managerName = managerDoc.getString("name") ?: "Unnamed Manager"
                            userDocRef.update("managerIds", FieldValue.arrayUnion(managerId), "managerNames", FieldValue.arrayUnion(managerName))
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Successfully joined the manager.", Toast.LENGTH_SHORT).show()
                                    loadManagers()
                                }
                        } else {
                            Toast.makeText(this, "Manager ID not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Logica para comprobar si se puede crear un nuevo manager
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

    // Logica para crear un nuevo manager
    private fun createManager(userId: String, name: String, totalMoney: Double) {
        val newManagerDocRef = db.collection("managers").document()
        val managerId = newManagerDocRef.id
        newManagerDocRef.set(
            hashMapOf(
                "name" to name,
                "totalMoney" to totalMoney,
                "expenses" to listOf<Double>(),
                "currentExpense" to 0.0,
                "creatorId" to userId
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

    // Recarga los gestores del usuario
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
                val intent = Intent(this@HomeActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }
        menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, R.string.profile).setIcon(R.drawable.ic_profile).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }

        // Añadir un nuevo submenu para los managers
        val managerGroup = menu.addSubMenu("Your Managers")

        // Añadir cada manager al submenu
        managerNames.zip(managerIds).forEachIndexed { index, (name, id) ->
            managerGroup.add(R.id.group_managers, Menu.NONE, index, name).apply {
                setOnMenuItemClickListener {
                    val intent = Intent(this@HomeActivity, ManagerDetailsActivity::class.java).apply {
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
                        .placeholder(R.mipmap.ic_launcher_round) // Imagen de carga predeterminada
                        .error(R.mipmap.ic_launcher_round) // Imagen de error
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
}
