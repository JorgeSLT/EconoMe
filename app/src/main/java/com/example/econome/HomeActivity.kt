package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("UpdatePasswordError", "Failed to update password", it.exception)
                        Toast.makeText(this, "Failed to update password: ${it.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
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
}