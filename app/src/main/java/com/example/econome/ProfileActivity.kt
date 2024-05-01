package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.Menu
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.econome.databinding.ActivityProfileBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class ProfileActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityProfileBinding

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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

        binding.btnSignout.setOnClickListener {
            auth.signOut()

            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)

            finish()
        }

        binding.btnUpdatePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnUpdateEmail.setOnClickListener {
            showChangeEmailDialog()
        }

        binding.btnDelete.setOnClickListener {
            val user = auth.currentUser

            user?.delete()?.addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, SignInActivity::class.java)
                    startActivity(intent)

                    finish()
                } else {
                    Log.e("DeleteAccountError", "Failed to delete account", it.exception)
                    Toast.makeText(this, "Failed to delete account: ${it.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

        }

        loadManagers()
    }

    private fun showChangePasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Password")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Enter new password"
        builder.setView(input)

        builder.setPositiveButton("Update") { dialog, _ ->
            val newPassword = input.text.toString()
            if (newPassword.isNotEmpty() && newPassword.length > 6) {
                updatePassword(newPassword)
            } else {
                Toast.makeText(this, "Password must be at least 7 characters", Toast.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun updatePassword(newPassword: String) {
        FirebaseAuth.getInstance().currentUser?.updatePassword(newPassword)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to update password", Toast.LENGTH_LONG).show()
                }
            }
    }


    private fun showChangeEmailDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Email")

        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.hint = "Enter new email"
        builder.setView(input)

        builder.setPositiveButton("Update") { dialog, _ ->
            val newEmail = input.text.toString()
            if (newEmail.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                updateEmail(newEmail)
            } else {
                Toast.makeText(this, "Invalid email address", Toast.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun updateEmail(newEmail: String) {
        FirebaseAuth.getInstance().currentUser?.verifyBeforeUpdateEmail(newEmail)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Email verification sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to update email", Toast.LENGTH_LONG).show()
                }
            }
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
                val intent = Intent(this@ProfileActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
        }
        menu.add(Menu.NONE, R.id.nav_profile, Menu.NONE, "Profile").setIcon(R.drawable.ic_profile).apply {
            setOnMenuItemClickListener {
                val intent = Intent(this@ProfileActivity, ProfileActivity::class.java)
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
                    val intent = Intent(this@ProfileActivity, ManagerDetailsActivity::class.java).apply {
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