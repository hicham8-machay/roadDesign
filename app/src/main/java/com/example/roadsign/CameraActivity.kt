import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.highgui.HighGui
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME)

    val frameWidth = 640
    val frameHeight = 480
    val brightness = 180.0
    val threshold = 0.75
    val fontFace = Imgproc.FONT_HERSHEY_SIMPLEX
    val fontScale = 0.75
    val fontColor = org.opencv.core.Scalar(0.0, 0.0, 255.0)
    val fontThickness = 2

    // SETUP THE VIDEO CAMERA
    val cap = VideoCapture(0)
    cap.set(3, frameWidth.toDouble())
    cap.set(4, frameHeight.toDouble())
    cap.set(10, brightness)

    // LOAD THE TFLITE MODEL
    val interpreter = Interpreter(loadModelFile("model.tflite"))

    while (true) {
        // READ IMAGE
        val frame = Mat()
        cap.read(frame)

        // PROCESS IMAGE
        Imgproc.resize(frame, frame, Size(32.0, 32.0))
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY)
        Imgproc.equalizeHist(frame, frame)
        frame.convertTo(frame, CvType.CV_32F)
        frame /= 255.0

        // RESHAPE IMAGE
        val input = ByteBuffer.allocateDirect(4 * 32 * 32 * 1).order(ByteOrder.nativeOrder())
        input.rewind()
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val pixelValue = frame.get(y, x)[0].toDouble()
                input.putFloat(pixelValue.toFloat())
            }
        }

        // PREDICT IMAGE
        val output = Array(1) { FloatArray(43) }
        interpreter.run(input, output)

        val classIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val probabilityValue = output[0][classIndex].toDouble()

        // DISPLAY RESULTS
        val result = "CLASS: $classIndex ${getClassName(classIndex)}\nPROBABILITY: ${String.format("%.2f", probabilityValue * 100)}%"
        Imgproc.putText(frame, result, org.opencv.core.Point(20.0, 35.0), fontFace, fontScale, fontColor, fontThickness)

        HighGui.imshow("Result", frame)
        if (HighGui.waitKey(1) == 'q'.toInt()) {
            break
        }
    }

    cap.release()
    HighGui.destroyAllWindows()
}

fun loadModelFile(modelPath: String): ByteBuffer {
    val inputStream = ClassLoader.getSystemResourceAsStream(modelPath)
    val byteArray = inputStream.readBytes()
    return ByteBuffer.wrap(byteArray)
}

fun getClassName(classNo: Int): String {
    return when (classNo) {
        0 -> "Speed Limit 20 km/h"
        1 -> "Speed Limit 30 km/h"
        2 -> "Speed Limit 50 km/h"
        3 -> "Speed Limit 60 km/h"
        // Add the rest of the class names here
        else -> "Unknown"
    }
}