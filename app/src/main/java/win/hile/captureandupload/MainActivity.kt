package win.hile.captureandupload

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.Camera.open
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import com.android.volley.Request
import com.android.volley.Request.Method
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity() {
    private var camera: Camera? = null
    private val handler = Handler()

    // HTTP Request queue
    private val requestQueue = Volley.newRequestQueue(this)

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

            val tokenRequest = object : JsonObjectRequest(
                "https://login.microsoftonline.com/${BuildConfig.azureTenantId}/oauth2/v2.0/token",
                { response ->
                    {
                        val accessToken = response.get("access_token")

                    }
                },
                { error ->
                    Log.e(
                        MainActivity::class.simpleName,
                        error.message
                    )
                })


            /*
                        {
                            override fun getBodyContentType(): String {
                                return "application/x-www-form-urlencoded; charset=UTF-8"
                            }

                            override fun getParams(): MutableMap<String, String> {
                                return mutableMapOf<String, String>(
                                    "client_id" to BuildConfig.azureClientId,
                                    "scope" to ".default",
                                    "client_secret" to BuildConfig.azureClientSecret,
                                    "grant_type" to "client_credentials"
                                )
                            }
                        }
            */



            requestQueue.add(tokenRequest)

            val format = pCamera.parameters.previewFormat
            val width = pCamera.parameters.previewSize.width
            val height = pCamera.parameters.previewSize.height

            val yuvImage = YuvImage(bytes, format, width, height, null)

            Log.d(MainActivity::class.simpleName, "YuvImage created: $yuvImage")

            val output = ByteArrayOutputStream()
            output.use {
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, output)

                val dateFormat = DateFormat.getDateTimeInstance()
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = dateFormat.format(Date())

                val blob = output.toByteArray()

                val request = object : StringRequest(Request.Method.PUT,
                    "https://${BuildConfig.azureStorageName}.blob.core.windows.net${BuildConfig.azureStoragePath}",
                    Response.Listener<String> { response ->
                        {
                            Log.i(MainActivity::class.simpleName, response)
                        }
                    },
                    Response.ErrorListener { error: VolleyError? ->
                        {
                            Log.e(MainActivity::class.simpleName, error.toString())
                        }
                    }) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val headers = hashMapOf<String, String>(
                            "Authorization" to BuildConfig.azureStorageAuthorization,
                            "x-ms-date" to android.text.format.DateFormat.format(
                                "",
                                Calendar.getInstance()
                            ).toString(),
                            "x-ms-version" to BuildConfig.azureVersion,
                            "Content-Type" to "image/jpeg",
                            "Content-Length" to blob.size.toString(),
                        )
                        return headers;
                    }

                    override fun getBody(): ByteArray {
                        return blob
                    }
                }
                //
                Log.d(MainActivity::class.simpleName, "Adding a request to the queue.")
                queue.add(request)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.setContentView(R.layout.main)

        val view = this.findViewById(R.id.mainSurfaceView) as SurfaceView

        // previewに必要
        view.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        view.holder.addCallback(this.callback)
    }

    private fun openCamera() {
        if (camera == null) {
            camera = Camera.open()
        }
    }

    suspend fun refreshAccessToken(): Nothing = suspendCoroutine {
        val url = "https://login.microsoftonline.com/${BuildConfig.azureTenantId}/oauth2/v2.0/token"
        val body = mutableMapOf<String, String>(
            "client_id" to BuildConfig.azureClientId,
            "scope" to ".default",
            "client_secret" to BuildConfig.azureClientSecret,
            "grant_type" to "client_credentials"
        )
        val contentType = "application/x-www-form-urlencoded; charset=UTF-8"

        val request = object : JsonObjectRequest(
            url,
            { response ->
                val accessToken = response.getString("access_token")
                val sharedPref = getPreferences(Context.MODE_PRIVATE)

                with(sharedPref.edit()) {
                    putString("AccessToken", accessToken)
                    apply()
                }

                it.resumeWith(Result.success())
            },
            { error ->
                it.resumeWithException(HttpException("Get access token failed. ${error.message}"))
            }) {
            override fun getBodyContentType(): String {
                return contentType
            }

            override fun getParams(): MutableMap<String, String>? {
                return mutableMapOf<String, String>(
                    "client_id" to BuildConfig.azureClientId,
                    "scope" to ".default",
                    "client_secret" to BuildConfig.azureClientSecret,
                    "grant_type" to "client_credentials"
                )
            }
        }

        this.requestQueue.add(request)
    }

    suspend fun putBlob(blob: ByteArray) = suspendCoroutine<Void> {
        val accessToken = getPreferences(Context.MODE_PRIVATE).getString("AccessToken", null)
        val url =
            "https://${BuildConfig.azureStorageName}.blob.core.windows.net${BuildConfig.azureStoragePath}"
        val headers = mutableMapOf<String, String>(
            "Authorization" to "Bearer $accessToken",
            "x-ms-date" to android.text.format.DateFormat.format(
                "YYYY-MM-DDTHH:MM.SS.000z",
                Date()
            ).toString(),
            "x-ms-version" to BuildConfig.azureVersion,
            "Content-Type" to "image/jpeg",
            "Content-Length" to blob.size.toString()
        )
        val request = object:StringRequest(url, response->
        , error -> it.) {}

    }
}