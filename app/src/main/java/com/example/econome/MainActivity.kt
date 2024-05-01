package com.example.econome

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    // Inicializacion de la instancia para la autenticacion
    private lateinit var auth: FirebaseAuth

    // Metodo onCreate que se llama al crear la actividad
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Esconder la barra de accion
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        // Handler usado para dejar unos segundos antes de redirigir a la pantalla de inicio
        Handler(Looper.getMainLooper()).postDelayed({
            val user = auth.currentUser

            if(user != null){
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)

                finish()
            }
            else{
                val intent = Intent(this, SignInActivity::class.java)
                startActivity(intent)

                finish()
            }
        }, 2000)
    }
}
