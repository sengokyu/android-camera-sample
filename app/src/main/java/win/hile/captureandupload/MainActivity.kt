package win.hile.captureandupload

import android.hardware.Camera
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView


class MainActivity : AppCompatActivity() {
    private lateinit var camera: Camera

    private val callback = object : Callback {
        override fun surfaceCreated(surfaceHolder: SurfaceHolder?) {
            camera = Camera.open()

            camera.setPreviewDisplay(surfaceHolder)
        }

        override fun surfaceChanged(var1: SurfaceHolder?, var2: Int, var3: Int, var4: Int) {
            camera.startPreview()
        }

        override fun surfaceDestroyed(var1: SurfaceHolder?) {
            camera.release()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.setContentView(R.layout.main)

        val view = this.findViewById(R.id.mainSurfaceView) as SurfaceView

        view.holder.addCallback(this.callback)
        view.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }
}