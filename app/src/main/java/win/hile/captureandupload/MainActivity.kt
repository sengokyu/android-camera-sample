package win.hile.captureandupload

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.Camera.open
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import com.android.volley.Request
import com.android.volley.Request.Method
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TimeZone.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity(), CoroutineScope {
    private var camera: Camera? = null
    private val handler = Handler()
    private val job = SupervisorJob()

    // HTTP Request queue
    private lateinit var requestQueue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.requestQueue = Volley.newRequestQueue(this)

        this.setContentView(R.layout.main)

        val view = this.findViewById(R.id.mainSurfaceView) as SurfaceView

        // previewに必要
        view.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        view.holder.addCallback(this.callback)
    }

    private val callback = object : Callback {
        override fun surfaceCreated(surfaceHolder: SurfaceHolder?) {
            Log.d(MainActivity::class.simpleName, "Opening camera.")
            openCamera()
            camera!!.setPreviewDisplay(surfaceHolder)

            // 1秒後にスタート
            handler.postDelayed(tickTask, 1000)
        }

        override fun surfaceChanged(var1: SurfaceHolder?, var2: Int, var3: Int, var4: Int) {
            openCamera()
            camera!!.startPreview()
        }

        override fun surfaceDestroyed(var1: SurfaceHolder?) {
            Log.d(MainActivity::class.simpleName, "Releasing camera.")

            if (camera != null) {
                camera!!.release()
                camera = null
            }
        }
    }

    // 定期実行
    private val tickTask = object : Runnable {
        override fun run() {
            Log.d(MainActivity::class.simpleName, "Tick task invoked.")
            takePicture()

            handler.postDelayed(this, 5 * 60 * 1000)
        }

        fun takePicture() {
            if (camera == null) return

            camera!!.autoFocus(autofocusCallback)
        }
    }

    // Autofocusコールバック
    private val autofocusCallback = object : Camera.AutoFocusCallback {
        override fun onAutoFocus(p0: Boolean, pCamera: Camera?) {
            Log.d(MainActivity::class.simpleName, "Auto focus done.")

            if (pCamera == null) {
                Log.w(MainActivity::class.simpleName, "Auto focused camera is null.")
                return
            }

            // takePicture しないで、PreviewCallback で取得
            pCamera.setOneShotPreviewCallback(previewCallback)
        }
    }

    // Previewコールバック
    private val previewCallback = object : Camera.PreviewCallback {
        override fun onPreviewFrame(bytes: ByteArray?, pCamera: Camera?) {
            Log.d(MainActivity::class.simpleName, "Preview callback invoked.")

            if (pCamera == null) {
                return
            }

            val format = pCamera.parameters.previewFormat
            val width = pCamera.parameters.previewSize.width
            val height = pCamera.parameters.previewSize.height

            val yuvImage = YuvImage(bytes, format, width, height, null)

            Log.d(MainActivity::class.simpleName, "YuvImage created: $yuvImage")

            val output = ByteArrayOutputStream()

            output.use {
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, output)
                val blob = output.toByteArray()

                launchPutBlob(blob)

                //
                Log.d(MainActivity::class.simpleName, "Adding a request to the queue.")
            }
        }
    }

    private fun openCamera() {
        if (camera == null) {
            camera = Camera.open()
        }
    }

    fun launchPutBlob(blob: ByteArray) = launch {
        refreshAccessToken()
        putBlob((blob))
    }

    private suspend fun refreshAccessToken(): Unit = suspendCoroutine {
        val url = "https://login.microsoftonline.com/${BuildConfig.azureTenantId}/oauth2/v2.0/token"
        val contentType = "application/x-www-form-urlencoded; charset=UTF-8"
        val params = mutableMapOf<String, String>(
            "client_id" to BuildConfig.azureClientId,
            "scope" to ".default",
            "client_secret" to BuildConfig.azureClientSecret,
            "grant_type" to "client_credentials"
        )

        val request = object : JsonObjectRequest(
            url,
            { response ->
                val accessToken = response.getString("access_token")
                val sharedPref = getPreferences(Context.MODE_PRIVATE)

                with(sharedPref.edit()) {
                    putString("AccessToken", accessToken)
                    apply()
                }
                it.resume(Unit)
            },
            { error ->
                it.resumeWithException(HttpException("Get access token failed. ${error.message}"))
            }) {

            override fun getBodyContentType(): String {
                return contentType
            }

            override fun getParams(): MutableMap<String, String> {
                return params
            }
        }

        this.requestQueue.add(request)
    }

    private suspend fun putBlob(blob: ByteArray): Unit = suspendCoroutine {
        val accessToken = getPreferences(Context.MODE_PRIVATE).getString("AccessToken", null)
        val url =
            "https://${BuildConfig.azureStorageName}.blob.core.windows.net${BuildConfig.azureStoragePath}"
        val headers = mutableMapOf<String, String>(
            "Authorization" to "Bearer $accessToken",
            "x-ms-date" to this.getFormatedDate(),
            "x-ms-version" to BuildConfig.azureVersion,
            "Content-Type" to "image/jpeg",
            "Content-Length" to blob.size.toString()
        )

        val request = object : StringRequest(url,
            { _ ->
                it.resume(Unit)
            },
            { error ->
                it.resumeWithException(HttpException("Put blob failed. ${error.message}"))
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                return headers
            }

            override fun getBody(): ByteArray {
                return blob
            }
        }

        this.requestQueue.add(request)
    }

    private fun getFormatedDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        return dateFormat.format(Date())
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
}