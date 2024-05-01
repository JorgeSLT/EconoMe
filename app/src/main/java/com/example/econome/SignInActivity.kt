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
import com.example.econome.databinding.ActivitySignInBinding
import com.example.econome.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth

class SignInActivity : AppCompatActivity() {
    // Declaración de las instancias para autenticacion
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        // Listener para el boton de inicio de sesion
        binding.btnSignin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if(checkAllField()){
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                    if (it.isSuccessful) {
                        // Usuario logeado con exito
                        Toast.makeText(this, "Successfully sign in", Toast.LENGTH_SHORT).show()


                        val intent = Intent(this, HomeActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // Error durante el login
                        Log.e("SignInError", "Failed to sign in", it.exception)
                        Toast.makeText(this, "Failed to sign in: ${it.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Listener para el boton que te lleva a la pagina de registro
        binding.tvCreateAccount.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Listener para el boton para recuperar la contraseña
        binding.tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // Comprueba si los campos estan bien rellenados
    private fun checkAllField(): Boolean{
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
        return true
    }
}