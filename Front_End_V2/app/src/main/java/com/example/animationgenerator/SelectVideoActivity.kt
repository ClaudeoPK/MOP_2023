package com.example.animationgenerator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.animationgenerator.Ext.SELECTED_IMAGE_URI
import com.example.animationgenerator.Ext.checkIsUpperSdkVersion
import com.example.animationgenerator.FormDataUtil.asMultipart
import com.example.animationgenerator.databinding.ActivitySelectVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

class SelectVideoActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectVideoBinding
    private val storagePermissions = if (Build.VERSION_CODES.TIRAMISU.checkIsUpperSdkVersion()) Ext.UPPER_TIRAMISU_READ_EXTERNAL_STORAGE else Ext.UNDER_TIRAMISU_READ_EXTERNAL_STORAGE
    private val fileUtils = FileUtils()
    private var imageUri: Uri? = null
    private var videoUri: Uri? = null
    private val apiService = NetworkModule.apiService
    private var ticket = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getSelectedImage()
        selectVideo()
        uploadMediaFile()
    }

    private fun getSelectedImage() {
        intent.getStringExtra(SELECTED_IMAGE_URI)
            ?.let { uriString ->
                imageUri = Uri.parse(uriString)
                Glide.with(binding.imageviewSelectedPicture.context).load(Uri.parse(uriString)).into(binding.imageviewSelectedPicture)
            }
    }

    private fun selectVideo() {
        binding.buttonSelectVideo.setOnClickListener {
            requestGalleryVideoPermission.launch(storagePermissions)
        }
    }

    private val requestGalleryVideoPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Intent(Intent.ACTION_PICK).apply {
                    type = MediaStore.Images.Media.CONTENT_TYPE
                    type = "video/*"
                    fetchPicturesFromGallery.launch(this)
                }
            }
        }

    private val fetchPicturesFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data
        if(uri != null) {
            videoUri = uri

            if(fileUtils.isFileInInternalStorage(this, videoUri.toString())) {
                val targetUri = FileProvider.getUriForFile(this, "${packageName}.provider", File(videoUri.toString()))
                loadVideoThumbnail(this, targetUri, binding.imageviewSelectedVideo)
            } else {
                contentResolver.run {
                    kotlin.runCatching {
                        val isFilePathUri = fileUtils.checkIsFilePath(videoUri.toString())
                        val uri = if(isFilePathUri) fileUtils.filePathToUri(true, videoUri.toString(), contentResolver) else Uri.parse( videoUri.toString())
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            uri?.let { loadThumbnail(it, Size(480, 480), null) }
                        } else {
                            MediaStore.Images.Thumbnails.getThumbnail(
                                this,
                                (uri?.lastPathSegment ?: "").toLong(),
                                MediaStore.Images.Thumbnails.MINI_KIND,
                                null
                            )
                        }
                        Glide.with(this@SelectVideoActivity).load(bitmap).into(binding.imageviewSelectedVideo)
                    }.getOrElse {
                        it.printStackTrace()
                    }
                }
            }
        }
    }

    fun loadVideoThumbnail(context: Context, videoUri: Uri, imageView: ImageView) {
        GlobalScope.launch(Dispatchers.Main) {
            val thumbnail = getVideoThumbnail(context, videoUri)
            imageView.setImageBitmap(thumbnail)
        }
    }

    private suspend fun getVideoThumbnail(context: Context, videoUri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                val interval = duration / 10
                val frameTime = interval * 1000L
                val bitmap = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun uploadMediaFile() {
        binding.buttonCreateAnimation.setOnClickListener {
            lifecycleScope.launch {
                showLoadingMessage(UPLOAD_MESSAGE)
                val imageMultipartList = mutableListOf<MultipartBody.Part>()
                imageUri?.asMultipart("src_image", contentResolver)?.let {
                    imageMultipartList.add(it)
                }
                videoUri?.asMultipart("drv_video", contentResolver)?.let {
                    imageMultipartList.add(it)
                }
                showLoading(true)

                withContext(Dispatchers.IO) {
                    val apiCall = apiService.postImageAndVideo(imageMultipartList)
                    if(apiCall.isSuccessful) {
                        if(apiCall.body() != null) {
                            apiCall.body()?.string()?.let {
                                ticket = it
                                getAnimation(it)
                            }
                        }  else {
                            showLoadingMessage("")
                            showLoading(false)
                        }
                    } else {
                        showLoadingMessage("")
                        showLoading(false)
                    }
                }
            }
        }
    }

    private fun getAnimation(ticket: String) {
        var tryCount = 0
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                showLoadingMessage(GET_ANIMATION_MESSAGE)
                val requestMap = hashMapOf<String, RequestBody>()
                requestMap["ticket"] = ticket.toTextPlainRequestBody()
                delay(8000)
                val apiCall = apiService.getAnimation(requestMap)
                if(apiCall.isSuccessful) {
                    if(apiCall.body()?.contentType()?.equals("video/mp4".toMediaTypeOrNull()) == true) {
                        DownloadDialogFragment()
                            .setDownloadListener(object: DownloadDialogFragment.DownloadListener{
                                override fun onClickDownload() {
                                    val downloadResult = writeToDisk(apiCall.body()!!)
                                    if(downloadResult) { navToMain() }
                                }
                            }).show(supportFragmentManager, "")
                        showLoadingMessage("")
                        showLoading(false)
                    } else {
                        if(tryCount < 10) {
                            tryCount++
                            getAnimation(ticket)
                        }
                    }
                } else {
                    showLoadingMessage("")
                    showLoading(false)
                }
            }
        }
    }

    private fun navToMain() {
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(this)
        }
    }

    private suspend fun showLoading(visible: Boolean) {
        withContext(Dispatchers.Main) {
            if(visible) {
                binding.viewLoading.visibility = View.VISIBLE
                binding.progressbar.visibility = View.VISIBLE
                binding.textviewLoadingMessage.visibility = View.VISIBLE
            }else {
                binding.viewLoading.visibility = View.GONE
                binding.progressbar.visibility = View.GONE
                binding.textviewLoadingMessage.visibility = View.GONE
            }
        }
    }

    private suspend fun showLoadingMessage(message: String) {
        withContext(Dispatchers.Main) {
            binding.textviewLoadingMessage.text = message
        }
    }

    private fun writeToDisk(body: ResponseBody): Boolean {
     //   try {
        if (!checkDirectoryExist()) {
            Toast.makeText(this@SelectVideoActivity, "폴더를 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return false
        }
            

            val videoFile = File(
                Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}/${IMAGE_FOLDER_NAME}"),
                "${FileUtils().imageFileNameDateFormat.format(Date())}_animation.mp4"
            )
            val inputStream =  body!!.byteStream()
            val outputStream = FileOutputStream(videoFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            outputStream.close()

            MediaScannerConnection.scanFile(this, arrayOf(videoFile.absolutePath), null) { _, uri ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (uri != null) {
                        Toast.makeText(this@SelectVideoActivity, "동영상 다운로드 완료 [갤러리/AnimationGenerator 폴더]", Toast.LENGTH_SHORT).show()
                        val intent : Intent = Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "video/*");
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        }

                    } else {
                        Toast.makeText(this@SelectVideoActivity, "동영상 다운로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return true
       // } catch (e: IOException) {
          //  return false
        //}
    }

    private fun checkDirectoryExist() : Boolean {
        val directory = File(Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}"), IMAGE_FOLDER_NAME)
        if(!directory.exists()) {
            return directory.mkdirs()
        }
        return true
    }

    fun String.toTextPlainRequestBody(): RequestBody = this.toRequestBody("text/plain".toMediaTypeOrNull())

    companion object {
        private const val UPLOAD_MESSAGE = "서버에 파일을 전송하는 중입니다."
        private const val GET_ANIMATION_MESSAGE = "애니메이션을 생성하는 중입니다."
        private const val IMAGE_FOLDER_NAME = "AnimationGenerator"
    }
}