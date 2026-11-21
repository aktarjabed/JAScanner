package aktarjabed.com.jaascanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class JAScannerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(applicationContext)
    }
}
