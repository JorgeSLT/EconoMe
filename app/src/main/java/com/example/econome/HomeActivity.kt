package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.econome.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSignout.setOnClickListener {
            auth.signOut()

            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)

            finish()
        }

        binding.btnUpdatePassword.setOnClickListener {
            val user = auth.currentUser
            val password = binding.etPassword.text.toString()

            if(checkPasswordField()){
                user?.updatePassword(password)?.addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("UpdatePasswordError", "Failed to update password", it.exception)
                        Toast.makeText(this, "Failed to update password: ${it.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnUpdateEmail.setOnClickListener {
            val user = auth.currentUser
            val email = binding.etEmail.text.toString()

            if(checkEmailField()){
                user?.verifyBeforeUpdateEmail(email)?.addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Email verification sent", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("UpdatePasswordError", "Failed to update email", it.exception)
                        Toast.makeText(this, "Failed to update email: ${it.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
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

    }

    private fun checkPasswordField(): Boolean{
        if(binding.etPassword.text.toString()==""){
            binding.textInputLayoutPassword.error="This field is required"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }
        if(binding.etPassword.length()<=6){
            binding.textInputLayoutPassword.error="Password should at least have 7 characters"
            binding.textInputLayoutPassword.errorIconDrawable = null
            return false
        }
        return true
    }

    private fun checkEmailField(): Boolean{
        val email = binding.etEmail.text.toString()

        if(binding.etEmail.text.toString()==""){
            binding.textInputLayoutEmail.error="This field is required"
            return false
        }
        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.textInputLayoutEmail.error="Wrong email format"
            return false
        }
        return true
    }
}