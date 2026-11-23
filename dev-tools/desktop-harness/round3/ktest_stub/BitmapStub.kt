// The plain-JVM harness has no android.jar. MarkGeometry only reads these three members of a
// Bitmap, so a tiny stand-in is enough to compile and test the geometry.
package android.graphics

class Bitmap(
    val width: Int = 1,
    val height: Int = 1,
    val isRecycled: Boolean = false,
)
