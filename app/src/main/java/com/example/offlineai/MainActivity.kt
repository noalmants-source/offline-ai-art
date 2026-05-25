package com.example.offlineai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    // Enlace con el puente de C++
    private external fun generateImageFromJNI(
        modelPath: String,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfgScale: Float,
        width: Int,
        height: Int,
        seed: Long
    ): ByteArray?

    companion object {
        init {
            System.loadLibrary("offlineai")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    secondary = Color(0xFF03DAC5),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OfflineAIAppScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun OfflineAIAppScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // Estados de la App
        var modelUri by remember { mutableStateOf<Uri?>(null) }
        var modelPath by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("A photo of a girl named Lucia, posing in Madrid, highly detailed, instagram style") }
        var negativePrompt by remember { mutableStateOf("low quality, blurry, deformed, bad hands, bad eyes") }
        var steps by remember { mutableStateOf("4") } // 4 pasos es ideal para modelos SDXS o SD-Turbo
        var cfgScale by remember { mutableStateOf("1.0") }
        var width by remember { mutableStateOf("512") }
        var height by remember { mutableStateOf("512") }
        var seed by remember { mutableStateOf("-1") }

        var isGenerating by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("Listo") }
        var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

        // Selector de archivos del modelo GGUF
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                modelUri = uri
                coroutineScope.launch {
                    statusText = "Preparando modelo..."
                    val path = getRealPathFromUri(context, uri)
                    if (path != null) {
                        modelPath = path
                        statusText = "Modelo cargado: ${File(path).name}"
                    } else {
                        statusText = "Error al obtener la ruta del modelo."
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título con Gradiente
            Text(
                text = "ARTE IA OFFLINE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC5))
                ),
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Generación de Imágenes 100% Local en S22 Ultra",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            // Tarjeta de Selección de Modelo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Configuración del Modelo",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (modelPath.isEmpty()) "Seleccionar Modelo (.gguf)" else "Cambiar Modelo")
                    }
                    if (modelPath.isNotEmpty()) {
                        Text(
                            text = "Archivo: ${File(modelPath).name}",
                            color = Color(0xFF03DAC5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Tarjeta de Prompts
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Prompts de Generación",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt Positivo") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = { negativePrompt = it },
                        label = { Text("Prompt Negativo") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }

            // Tarjeta de Ajustes Avanzados
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "3. Parámetros Avanzados",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = steps,
                            onValueChange = { steps = it },
                            label = { Text("Pasos") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cfgScale,
                            onValueChange = { cfgScale = it },
                            label = { Text("CFG") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = width,
                            onValueChange = { width = it },
                            label = { Text("Ancho") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Alto") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = seed,
                        onValueChange = { seed = it },
                        label = { Text("Semilla (-1 = Aleatorio)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Botón Generar y Estado
            Button(
                onClick = {
                    if (modelPath.isEmpty()) {
                        Toast.makeText(context, "Por favor, selecciona primero un modelo .gguf", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isGenerating = true
                    statusText = "Generando imagen de forma local... (Esto tardará 10-20 segs)"
                    coroutineScope.launch {
                        val genSteps = steps.toIntOrNull() ?: 4
                        val genCfg = cfgScale.toFloatOrNull() ?: 1.0f
                        val genW = width.toIntOrNull() ?: 512
                        val genH = height.toIntOrNull() ?: 512
                        var genSeed = seed.toLongOrNull() ?: -1L
                        if (genSeed < 0) {
                            genSeed = (0..Long.MAX_VALUE).random()
                        }

                        val bitmap = withContext(Dispatchers.Default) {
                            try {
                                val rgbaData = generateImageFromJNI(
                                    modelPath = modelPath,
                                    prompt = prompt,
                                    negativePrompt = negativePrompt,
                                    steps = genSteps,
                                    cfgScale = genCfg,
                                    width = genW,
                                    height = genH,
                                    seed = genSeed
                                )
                                if (rgbaData != null) {
                                    val bmp = Bitmap.createBitmap(genW, genH, Bitmap.Config.ARGB_8888)
                                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaData))
                                    bmp
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }

                        isGenerating = false
                        if (bitmap != null) {
                            generatedBitmap = bitmap
                            statusText = "¡Imagen generada con éxito!"
                        } else {
                            statusText = "Error en la generación. Verifica el formato del modelo."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBB86FC)
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generando en tu S22...")
                } else {
                    Text("GENERAR IMAGEN", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = statusText,
                color = if (statusText.contains("Error")) Color.Red else Color(0xFF03DAC5),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Mostrar Imagen Generada y Botón de Guardar
            generatedBitmap?.let { bitmap ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFBB86FC), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Imagen de IA Generada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val saved = saveBitmapToGallery(context, bitmap, "offline_ai_${System.currentTimeMillis()}")
                                if (saved) {
                                    Toast.makeText(context, "Imagen guardada en Galería", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar en Galería")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Copiar URI de Android SAF a un archivo local temporal para poder abrirlo en C++
    private suspend fun getRealPathFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = "temp_model.gguf"
            val tempFile = File(context.cacheDir, fileName)
            
            // Si el archivo ya existe y tiene contenido, podemos optar por reutilizarlo
            // o sobreescribirlo si el usuario elige otro.
            if (tempFile.exists()) {
                tempFile.delete()
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // Guardar Bitmap en la Galería mediante MediaStore (no requiere permisos de escritura en Android 13+)
    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/OfflineAIArt")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            false
        }
    }
}
