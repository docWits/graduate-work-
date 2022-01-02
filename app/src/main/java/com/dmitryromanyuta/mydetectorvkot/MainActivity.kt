package com.dmitryromanyuta.mydetectorvkot

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(),View.OnClickListener {
    companion object{
        const val TAG = "TFLite - ODT"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        private const val MAX_FONT_SIZE = 96F
    }

    private lateinit var captureImageFab:Button
    private lateinit var inputImageView: ImageView
    private lateinit var imgSamleOne: ImageView
    private lateinit var imgSamleTwo: ImageView
    private lateinit var imgSamleThree: ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        captureImageFab = findViewById(R.id.captureImageFab)
        inputImageView = findViewById(R.id.imageView)
        imgSamleOne = findViewById(R.id.imgSampleOne)
        imgSamleTwo = findViewById(R.id.imgSampleTwo)
        imgSamleThree = findViewById(R.id.imgSampleThree)
        tvPlaceholder = findViewById(R.id.tvPlaceholder)

        captureImageFab.setOnClickListener(this)
        imgSamleOne.setOnClickListener(this)
        imgSamleTwo.setOnClickListener(this)
        imgSamleThree.setOnClickListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE &&
            resultCode == Activity.RESULT_OK
        ) {
            setViewAndDetect(getCapturedImage())
        }
    }
    /**
     * onClick(v: View?)
     * Обнаружение прикосновений к компонентам пользовательского интерфейса
     */
    override fun onClick(v: View?) {
        when (v?.id){
            R.id.captureImageFab ->{
                try{
                    dispatchTakePictureIntent()
                }catch (e: ActivityNotFoundException){
                    Log.e(TAG, e.message.toString())
                }
            }
            R.id.imgSampleOne ->{
                setViewAndDetect(getSampleImage(R.drawable.leno_11))
            }
            R.id.imgSampleTwo ->{
                setViewAndDetect(getSampleImage(R.drawable.lyaka_12))
            }
            R.id.imgSampleThree ->{
                setViewAndDetect(getSampleImage(R.drawable.ectot_4))
            }
        }
    }

    /**
    * runObjectDetection (растровое изображение: растровое изображение)
    * Функция обнаружения объектов TFLite
    */
    private fun runObjectDetection(bitmap: Bitmap){
        val image = TensorImage.fromBitmap(bitmap)

        // Шаг 2: Инициализируем объект детектора
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.3f)
            .build()
        val detector = ObjectDetector.createFromFileAndOptions(
            this,
            "ars_meta.tflite",
            options
        )
        // Шаг 3: подать данное изображение в детектор
        val result = detector.detect(image)

        // Шаг 4: проанализировать результат обнаружения и показать его
        val resultToDisplay = result.map {
            // Получение первой категории и создание отображаемого текста
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Создаем объект данных для отображения результата обнаружения
            DetectionResult(it.boundingBox, text)
        }
        // Рисуем результат обнаружения на растровом изображении и показываем его.
        val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)
        runOnUiThread{
            inputImageView.setImageBitmap(imgWithResult)
        }
    }

    /**
    * debugPrint (visionObjects: List <Detection>)
    * Распечатайте результат обнаружения в logcat для проверки
    */
    private fun debugPrint(results: List<Detection>){
        for ((i,obj) in results.withIndex()){
            val box = obj.boundingBox

            Log.d(TAG, "Объект обнаружен: ${i} ")
            Log.d(TAG, " boundingBox: (${box.left},${box.top}) - (${box.right},${box.bottom})")

            for ((j, category) in obj.categories.withIndex()){
                Log.d(TAG, "     Label $j: ${category.label}")
                val confidence: Int = category.score.times(100).toInt()
                Log.d(TAG, "     Confidence: ${confidence}%")
            }
        }
    }

    private fun setViewAndDetect(bitmap: Bitmap){
        // Отображение захваченного изображения
        inputImageView.setImageBitmap(bitmap)
        tvPlaceholder.visibility = View.INVISIBLE

        // Запускаем ODT и отображаем результат
        // Обратите внимание, что мы запускаем это в фоновом потоке, чтобы избежать блокировки пользовательского интерфейса приложения, потому что
        // Обнаружение объекта TFLite - это синхронизированный процесс.
        lifecycleScope.launch(Dispatchers.Default){runObjectDetection(bitmap)}
    }

    /**
    * getCapturedImage ():
    * Декодирует и обрезает захваченное изображение с камеры.
    */
    private fun getCapturedImage(): Bitmap{
        // Получаем размеры представления
        val targetW: Int = inputImageView.width
        val targetH: Int = inputImageView.height

        val  bmOptions = BitmapFactory.Options().apply {
            // Получаем размеры растрового изображения
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(currentPhotoPath, this)
            val  photoW: Int = outWidth
            val  photoH: Int = outHeight

            // Определяем, насколько уменьшить изображение
            val scaleFactory: Int = max(1,min(photoW / targetW, photoH / targetH))

            // Декодируем файл изображения в Bitmap размером, чтобы заполнить View
            inJustDecodeBounds = false
            inSampleSize = scaleFactory
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        val bitmap = BitmapFactory.decodeFile(currentPhotoPath,bmOptions)
        return when (orientation){
            ExifInterface.ORIENTATION_ROTATE_90 ->{
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 ->{
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else ->{
                bitmap
            }
        }
    }
    /**
    * getSampleImage ():
    * Получить форму изображения, которую можно рисовать и преобразовать в растровое изображение.
    */
    private fun getSampleImage(drawable: Int):Bitmap{
        return  BitmapFactory.decodeResource(resources,drawable,BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    /**
    * rotateImage ():
    * Декодирует и обрезает захваченное изображение с камеры.
    */
    private fun rotateImage(source: Bitmap, angle: Float):Bitmap{
        val matrix = Matrix()
        matrix.postRotate(angle)
        return  Bitmap.createBitmap(
            source, 0, 0 , source.width, source.height,
            matrix, true
        )
    }

    /**
    * createImageFile ():
    * Создает временный файл изображения для записи в приложение «Камера».
    */
    @Throws(IOException::class)
    private fun createImageFile():File{
        // Создаем имя файла изображения
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",/*префикс*/
            ".jpg", /* суффикс*/
            storageDir /*директория*/
        ).apply {
            // Сохраняем файл: путь для использования с намерениями ACTION_VIEW
            currentPhotoPath = absolutePath
        }
    }

    /**
    * dispatchTakePictureIntent ():
    * Запустите приложение «Камера», чтобы сделать снимок.
    */
    private  fun dispatchTakePictureIntent(){
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Убедитесь, что есть активность камеры для обработки намерения
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Создаем файл, в котором должно быть фото
                val photoFile: File? = try{
                    createImageFile()
                }catch (e:IOException){
                    Log.e(TAG, e.message.toString())
                    null
                }
                // Продолжаем, только если файл был успешно создан
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.dmitryromanyuta.mydetectorvkot.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /**
    * drawDetectionResult (растровое изображение: Bitmap, detectionResults: List <DetectionResult>
    * Нарисуйте рамку вокруг каждого объекта и покажите имя объекта.
    */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResult:List<DetectionResult>
    ): Bitmap{
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResult.forEach{
            // рисуем ограничивающую рамку
            pen.color = Color.RED
            pen.strokeWidth = 8F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box,pen)

            val tagSize = Rect(0,0,0,0)

            // вычисляем правильный размер шрифта

            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize *box.width() /tagSize.width()

            // настраиваем размер шрифта так, чтобы текст находился внутри ограничивающей рамки
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }
}

/**
* DetectionResult
* Класс для хранения информации визуализации обнаруженного объекта.
*/
data class DetectionResult(val boundingBox: RectF, val text: String)