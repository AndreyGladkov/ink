package io.github.andreygladkov.inkgallery

import android.app.Activity
import android.content.Intent
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** What the system hands back after a request a test cannot answer for real, such as its delete dialog. */
fun Activity.deliverResult(requestCode: Int, resultCode: Int, data: Intent? = null) {
    ReflectionHelpers.callInstanceMethod<Unit>(this, "onActivityResult",
        ClassParameter.from(Int::class.java, requestCode), ClassParameter.from(Int::class.java, resultCode), ClassParameter.from(Intent::class.java, data))
}
