package com.example.econome

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.Menu
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.example.econome.databinding.ActivityProfileBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityProfileBinding
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val IMAGE_PICK_CODE = 999
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

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
            deleteUserAndData()
        }

        binding.imgUser.setOnClickListener {
            openGalleryForImage()
        }

        loadManagers()
        loadProfile()
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE) {
            val imageUri = data?.data
            uploadImageToFirebaseStorage(imageUri)
        }
    }

    private fun uploadImageToFirebaseStorage(imageUri: Uri?) {
        imageUri?.let { uri ->
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val storageRef = FirebaseStorage.getInstance().reference.child("profileImages/$userId/profileImage.png")

            storageRef.putFile(uri).continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        throw it
                    }
                }
                storageRef.downloadUrl
            }.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUri = task.result.toString()
                    updateProfileImageUrl(downloadUri)
                    Glide.with(this).load(downloadUri).into(binding.imgUser)
                } else {
                    Toast.makeText(this, "Failed to upload image: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateProfileImageUrl(imageUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).update("profileImageUrl", imageUrl).addOnSuccessListener {
            Toast.makeText(this, "Profile image updated successfully", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to update profile image: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setupDrawerHeader()
    }

    private fun showChangePasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Password")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter new password"
        }
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
        FirebaseAuth.getInstance().currentUser?.updatePassword(newPassword)?.addOnCompleteListener { task ->
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
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = "Enter new email"
        }
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
        FirebaseAuth.getInstance().currentUser?.verifyBeforeUpdateEmail(newEmail)?.addOnCompleteListener { task ->
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
                        .circleCrop() // Si quieres la imagen en forma de círculo
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

    private fun loadProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = db.collection("users").document(currentUser.uid)
            userDocRef.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val name = documentSnapshot.getString("name") ?: "No Name Set"
                    val imageUrl = documentSnapshot.getString("profileImageUrl") ?: ""

                    // Establecer el nombre en TextView
                    binding.tvUserName.text = name

                    // Cargar la imagen de perfil con Glide
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.mipmap.ic_launcher_round) // Imagen de carga predeterminada
                        .error(R.mipmap.ic_launcher_round) // Imagen de error
                        .circleCrop() // Si quieres la imagen en forma de círculo
                        .into(binding.imgUser)

                } else {
                    Toast.makeText(this, "User document does not exist", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No User Logged In", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteUserAndData() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        // Referencia al documento del usuario en Firestore
        val userDocRef = db.collection("users").document(userId)

        // Obtener todos los managers creados por el usuario
        db.collection("managers").whereEqualTo("creatorId", userId).get()
            .addOnSuccessListener { documents ->
                // Crear un lote para eliminar todos los documentos
                val batch = db.batch()
                documents.forEach { documentSnapshot ->
                    batch.delete(documentSnapshot.reference)  // Eliminar cada manager
                }
                batch.delete(userDocRef)  // Eliminar el documento del usuario

                // Commit del lote
                batch.commit().addOnSuccessListener {
                    // Ahora eliminar el usuario de Firebase Auth
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Account and all related data deleted successfully", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, SignInActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Log.e("AuthDeletionError", "Failed to delete user from auth", task.exception)
                            Toast.makeText(this, "Failed to delete account: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete user data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreReadError", "Failed to read managers for deletion", e)
                Toast.makeText(this, "Failed to retrieve managers for deletion: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }



}
