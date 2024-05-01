package com.example.econome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.econome.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnSignup.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            if (checkAllField()) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val userName = email.substringBefore("@")
                        val bitmap = getBitmapFromDrawableResource(this, R.mipmap.ic_launcher_round)

                        // Convertir bitmap a byte array
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        val data = baos.toByteArray()

                        // Referencia a Firebase Storage
                        val storageRef = FirebaseStorage.getInstance().reference.child("profileImages/${user!!.uid}/profileImage.png")
                        val uploadTask = storageRef.putBytes(data)
                        uploadTask.continueWithTask { task ->
                            if (!task.isSuccessful) {
                                task.exception?.let {
                                    throw it
                                }
                            }
                            storageRef.downloadUrl
                        }.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val downloadUri = task.result
                                val userDocRef = db.collection("users").document(user.uid)
                                userDocRef.set(hashMapOf(
                                    "name" to userName,
                                    "email" to email,
                                    "profileImageUrl" to downloadUri.toString()
                                )).addOnSuccessListener {
                                    auth.signOut()
                                    Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, SignInActivity::class.java))
                                    finish()
                                }.addOnFailureListener { e ->
                                    Log.e("FirestoreError", "Failed to create user document", e)
                                    Toast.makeText(this, "Failed to create user document: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Log.e("StorageError", "Failed to upload profile image", task.exception)
                                Toast.makeText(this, "Failed to upload profile image: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Log.e("SignUpError", "Failed to register user", task.exception)
                        Toast.makeText(this, "Failed to create account: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    }

    private fun checkAllField(): Boolean {
        val email = binding.etEmail.text.toString()

        if(binding.etEmail.text.toString()==""){
            binding.textInputLayoutEmail.error="This field is required"
            return false
        }
        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.textInputLayoutEmail.error="Wrong email format"
            return false
        }
        if(binding.etPassword.text.toString()==""){
            binding.textInputLayoutPassword.error="This field is required"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }
        if(binding.etPassword.length()<=6){
            binding.textInputLayoutPassword.error="Password should at least have 6 characters"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }
        if(binding.etConfirm.text.toString()==""){
            binding.textInputLayoutConfirm.error="This field is required"
            binding.textInputLayoutConfirm.errorIconDrawable = null
            return false
        }
        if(binding.etPassword.text.toString()!=binding.etConfirm.text.toString()){
            binding.textInputLayoutConfirm.error="Password do not match"
            return false
        }
        return true
    }

    fun getBitmapFromDrawableResource(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)!!
        val canvas = Canvas()
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

}