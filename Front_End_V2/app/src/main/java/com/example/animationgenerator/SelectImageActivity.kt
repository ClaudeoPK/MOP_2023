package com.example.animationgenerator

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.animationgenerator.Ext.SELECTED_IMAGE_URI
import com.example.animationgenerator.Ext.UNDER_TIRAMISU_READ_EXTERNAL_STORAGE
import com.example.animationgenerator.Ext.UPPER_TIRAMISU_READ_EXTERNAL_STORAGE
import com.example.animationgenerator.Ext.checkIsUpperSdkVersion
import com.example.animationgenerator.databinding.ActivitySelectImageBinding
class SelectImageActivity : AppCompatActivity() {
    private var imageUri: Uri? = null
    private lateinit var binding: ActivitySelectImageBinding
    private val storagePermissions = if (Build.VERSION_CODES.TIRAMISU.checkIsUpperSdkVersion()) UPPER_TIRAMISU_READ_EXTERNAL_STORAGE else UNDER_TIRAMISU_READ_EXTERNAL_STORAGE
    private var toGallery = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectImage()
        navToSelectVideo()
    }

    private val fetchPicturesFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data
        if(uri != null) {
            setImageUri(uri)
            startCrop(uri)
        } else {

        }
    }

    private fun takePicture() {
        FileUtils().createTempImageFile(this)?.let { file ->
            FileProvider.getUriForFile(this, "${packageName}.provider", file)?.let { uri ->
                imageUri = uri
                cameraActivityLauncher.launch(uri)
            }
        }
    }

    private val cameraActivityLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSaved ->
        if (isSaved) {
            imageUri?.let {
                setImageUri(it)
                startCrop(it)
            }
        } else {
            setImageUri(null)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                if(toGallery) {
                    Intent(Intent.ACTION_PICK).apply {
                        type = MediaStore.Images.Media.CONTENT_TYPE
                        type = "image/*"
                        fetchPicturesFromGallery.launch(this)
                    }
                } else {
                    takePicture()
                }
            }
        }

    private fun selectImage() {
        binding.buttonGallery.setOnClickListener {
            toGallery = true
            requestPermissionLauncher.launch(storagePermissions)
        }
        binding.buttonCamera.setOnClickListener {
            toGallery = false
            requestPermissionLauncher.launch(storagePermissions)
        }
    }

    private fun navToSelectVideo() {
        binding.buttonSelectVideo.setOnClickListener {
            if(imageUri != null) {
                Intent(this, SelectVideoActivity::class.java).apply {
                    putExtra(SELECTED_IMAGE_URI, imageUri.toString())
                    startActivity(this)
                }
            } else {
                Toast.makeText(this, "사진을 첨부해주세요!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Use the returned uri.
            val uriContent = result.uriContent
            val uriFilePath = result.getUriFilePath(this) // optional usage
            setImageUri(uriContent)
        } else {
            // An error occurred.
            val exception = result.error
        }
    }

    private fun setImageUri(uri: Uri?) {
        imageUri = uri
        Glide.with(binding.imageviewSelected.context).load(uri).into(binding.imageviewSelected)
    }


    private fun startCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(uri, CropImageOptions())
        cropImage.launch(cropOptions)
    }
}