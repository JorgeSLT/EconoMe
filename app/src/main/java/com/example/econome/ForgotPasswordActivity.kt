package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.econome.databinding.ActivityForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {
    // Declaración de las instancias para autenticacion y bbdd
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityForgotPasswordBinding

    // Metodo onCreate que se llama al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializacion del binding y de la vista
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        // Listener para el boton de recuperar contraseña
        binding.btnForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString()

            if(checkEmail()) {
                auth.sendPasswordResetEmail(email).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()

                        // Crea y comienza una nueva actividad
                        val intent = Intent(this, SignInActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    // Metodo para validar el correo electronico
    private fun checkEmail(): Boolean{
        val email = binding.etEmail.text.toString()

        if(email == ""){
            binding.textInputLayoutEmail.error = "This field is required"
            return false
        }
        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            binding.textInputLayoutEmail.error = "Wrong email format"
            return false
        }
        return true
    }
}
