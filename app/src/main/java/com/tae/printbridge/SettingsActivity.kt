package com.tae.printbridge

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "tae_print_config"
        private const val ORANGE = "#F58220"
        private const val DARK = "#1F2937"
        private const val LIGHT_BG = "#F5F6FA"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(LIGHT_BG))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(12), 0)
            setBackgroundColor(Color.parseColor(ORANGE))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.mtelmx_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        topBar.addView(
            logo,
            LinearLayout.LayoutParams(dp(120), dp(42))
        )

        val titleTop = TextView(this).apply {
            text = "Configuración"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        topBar.addView(
            titleTop,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        val closeTop = TextView(this).apply {
            text = "✕"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }

        topBar.addView(
            closeTop,
            LinearLayout.LayoutParams(dp(48), dp(52))
        )

        root.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        val scroll = ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }

        fun label(textValue: String): TextView =
            TextView(this).apply {
                text = textValue
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(DARK))
                setPadding(0, dp(14), 0, dp(6))
            }

        fun input(hintValue: String): EditText =
            EditText(this).apply {
                hint = hintValue
                textSize = 15f
                setPadding(dp(14), 0, dp(14), 0)
                setBackgroundColor(Color.WHITE)
            }

        val title = TextView(this).apply {
            text = "Ajustes de impresión"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(DARK))
            setPadding(0, 0, 0, dp(8))
        }

        val subtitle = TextView(this).apply {
            text = "Configura el método de impresión para USB o TCP/IP."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(16))
        }

        val modeLabel = label("Modo de impresión")

        val modeSpinner = Spinner(this)
        val modes = arrayOf("USB", "TCP/IP")
        modeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes
        )

        val savedMode = prefs.getString("print_mode", "usb") ?: "usb"
        modeSpinner.setSelection(if (savedMode == "tcp") 1 else 0)

        val ipLabel = label("IP de la impresora")
        val ipInput = input("192.168.1.100").apply {
            setText(prefs.getString("printer_ip", ""))
        }

        val portLabel = label("Puerto")
        val portInput = input("9100").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("printer_port", 9100).toString())
        }

        val usbLabel = label("Nombre de impresora USB")
        val usbInput = input("USB automática").apply {
            setText(prefs.getString("usb_printer_name", ""))
        }

        val paperLabel = label("Tamaño de papel")

        val paperSpinner = Spinner(this)
        val papers = arrayOf("80 mm", "58 mm")
        paperSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            papers
        )

        val savedPaper = prefs.getString("paper_size", "80") ?: "80"
        paperSpinner.setSelection(if (savedPaper == "58") 1 else 0)

        val cutCheck = CheckBox(this).apply {
            text = "Cortar al final"
            textSize = 15f
            isChecked = prefs.getBoolean("cut", true)
        }

        val drawerCheck = CheckBox(this).apply {
            text = "Abrir cajón"
            textSize = 15f
            isChecked = prefs.getBoolean("open_drawer", false)
        }

        val logoLabel = label("Logo en Base64")

        val logoInput = EditText(this).apply {
            minLines = 5
            maxLines = 8
            hint = "Pega aquí el logo en Base64"
            setText(prefs.getString("logo_base64", ""))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val saveButton = Button(this).apply {
            text = "Guardar configuración"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(ORANGE))

            setOnClickListener {
                val mode = if (modeSpinner.selectedItem.toString() == "TCP/IP") "tcp" else "usb"
                val paper = if (paperSpinner.selectedItem.toString() == "58 mm") "58" else "80"

                prefs.edit()
                    .putString("print_mode", mode)
                    .putString("printer_ip", ipInput.text.toString().trim())
                    .putInt("printer_port", portInput.text.toString().toIntOrNull() ?: 9100)
                    .putString("usb_printer_name", usbInput.text.toString().trim())
                    .putString("paper_size", paper)
                    .putBoolean("cut", cutCheck.isChecked)
                    .putBoolean("open_drawer", drawerCheck.isChecked)
                    .putString("logo_base64", logoInput.text.toString().trim())
                    .putInt("logo_max_width", 160)
                    .apply()

                Toast.makeText(
                    this@SettingsActivity,
                    "Configuración guardada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val closeButton = Button(this).apply {
            text = "Cerrar"
            setOnClickListener { finish() }
        }

        fun updateModeVisibility() {
            val isTcp = modeSpinner.selectedItem.toString() == "TCP/IP"

            ipLabel.visibility = if (isTcp) View.VISIBLE else View.GONE
            ipInput.visibility = if (isTcp) View.VISIBLE else View.GONE
            portLabel.visibility = if (isTcp) View.VISIBLE else View.GONE
            portInput.visibility = if (isTcp) View.VISIBLE else View.GONE

            usbLabel.visibility = if (isTcp) View.GONE else View.VISIBLE
            usbInput.visibility = if (isTcp) View.GONE else View.VISIBLE
        }

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModeVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        layout.addView(title)
        layout.addView(subtitle)

        layout.addView(modeLabel)
        layout.addView(modeSpinner)

        layout.addView(ipLabel)
        layout.addView(ipInput)

        layout.addView(portLabel)
        layout.addView(portInput)

        layout.addView(usbLabel)
        layout.addView(usbInput)

        layout.addView(paperLabel)
        layout.addView(paperSpinner)

        layout.addView(cutCheck)
        layout.addView(drawerCheck)

        layout.addView(logoLabel)
        layout.addView(logoInput)

        layout.addView(saveButton)
        layout.addView(closeButton)

        scroll.addView(layout)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        updateModeVisibility()
    }
}
