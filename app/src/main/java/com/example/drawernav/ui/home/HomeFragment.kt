package com.example.drawernav.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.drawernav.databinding.FragmentHomeBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

//    private lateinit var appBarConfiguration: AppBarConfiguration
    private var _binding: FragmentHomeBinding? = null
    private var imageCapture: ImageCapture? = null
    private val gHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            binding.textWelcome.text = msg.obj.toString()
        }
    }
    private lateinit var cameraExecutor: ExecutorService
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        val root: View = binding.root

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            this.activity?.let {
                ActivityCompat.requestPermissions(
                    it, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }

        binding.cameraBtn.setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        return root
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this.context,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                this.activity?.finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        this.context?.let { it1 ->
            ContextCompat.checkSelfPermission(
                it1, it)
        } == PackageManager.PERMISSION_GRANTED
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = this.activity?.applicationContext?.let {
            ImageCapture.OutputFileOptions
                .Builder(
                    it.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                .build()
        }

        this.context?.let { ContextCompat.getMainExecutor(it) }?.let { it ->
            if (outputOptions != null) {
                imageCapture.takePicture(
                    outputOptions,
                    it,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        }

                        @SuppressLint("Range")
                        override fun
                                onImageSaved(output: ImageCapture.OutputFileResults){
                            val msg = "Photo capture succeeded."
                            Log.e("image", msg)
                            val resolver = context?.contentResolver
                            val savedUri = output.savedUri
                            savedUri.apply {
                                val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                                if (resolver != null) {
                                    val cursor =
                                        resolver.query(savedUri!!, filePathColumn, null, null, null)
                                    if (cursor != null) {
                                        cursor.moveToFirst()
                                        val path =
                                            cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                                        cursor.close()
                                        sendImage(File(path))
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun sendImage(file: File) {

        val client = OkHttpClient.Builder()
            .connectTimeout(10000, TimeUnit.MILLISECONDS)
            .build()
        val mediaType =
            "multipart/form-data".toMediaType()

        Log.d("image", file.totalSpace.toString())
        val body = MultipartBody.Builder().setType(mediaType)
            .setType(mediaType)
            .addFormDataPart("img", file.absolutePath, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("http://47.108.210.169:8082/img_tensor")
            .post(body)
            .build()
        Log.d("image", "start request.:${file.absolutePath}")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val retstr = response.body!!.string()
                    val msg = Message()
                    msg.obj = retstr
                    Log.e("image", retstr)
                    gHandler.sendMessage(msg)
                }
            }
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = this.context?.let { ProcessCameraProvider.getInstance(it) }

        this.activity?.let { ContextCompat.getMainExecutor(it.baseContext) }?.let { it ->
            cameraProviderFuture?.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                    }
                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture)
                } catch(exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            }, it)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}