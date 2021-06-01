package hideo.miyauchi.android

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Html
import android.text.Html.FROM_HTML_MODE_COMPACT
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import hideo.miyauchi.android.databinding.FragmentContactmeBinding
import java.io.IOException
import java.io.InputStream

class ContactMeFragment : Fragment(), Html.ImageGetter {

    private lateinit var _activity: FragmentActivity
    private lateinit var _context: Context
    private lateinit var binding: FragmentContactmeBinding

    private val aboutmeText: String = """
        <html>
            <img src="ContactMe/copyright.png">
            <h3>Hideo Miyauchi</h3>
            <p>Hello! My name is Hideo Miyauchi.
            <p>These are collection of apps that I developed that run on Android.
            <p>Please check the links below.
            <p>Have fun!
            <p>
            <a href="https://www.facebook.com/hideo.miyauchi"><img src="ContactMe/facebook.png"></a>
            &nbsp;
            <a href="https://hideomiyauchi.github.io/"><img src="ContactMe/githubpages.png"></a>
            &nbsp;            
            <a href="https://github.com/HideoMiyauchi"><img src="ContactMe/github.png"></a>
            &nbsp;            
            <a href="https://www.youtube.com/channel/UCyheSr7bp_ySygeQwl76yMg"><img src="ContactMe/youtube.png"></a>                                                                               
        </html>
    """.trimIndent()

    private val flightsimulationText: String = """
        <html>
            <h3>Flight Simulation (AR)</h3>
            Flight simulator using Google's Augmented-Reality (AR) technology.
            This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy.
            To power this session, Google will process visual data from your camera.
        </html>
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _activity = requireActivity()
        _context = requireContext()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentContactmeBinding.inflate(inflater, container, false)

        // for contact me
        binding.contactmeTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.contactmeTextView.text = Html.fromHtml(aboutmeText, FROM_HTML_MODE_COMPACT, this, null)

        // for flightsimulation
        binding.flightsimulationTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.flightsimulationTextView.text = Html.fromHtml(flightsimulationText, FROM_HTML_MODE_COMPACT, this, null)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
    }

    // <img src=xxxx> Html.ImageGetter
    override fun getDrawable(filename: String): Drawable? {
        var drawable: Drawable? = null
        try {
            val ims: InputStream = _activity.getAssets().open(filename)
            drawable = Drawable.createFromStream(ims, null)
        } catch (e: IOException) {
            Log.e(LOG_TAG,"getDrawable IOException", e)
        }
        drawable?.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())
        return drawable
    }

    companion object {
        private val LOG_TAG = this::class.java.simpleName
    }
}