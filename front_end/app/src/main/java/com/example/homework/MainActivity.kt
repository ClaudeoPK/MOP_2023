package com.example.homework

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.homework.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import java.io.IOException
import android.content.Context
import android.database.Cursor
import android.widget.Toast
import java.io.FileOutputStream
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody



class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageUri: Uri? = null
    private var imageBitmap: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 뷰 바인딩 초기화
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 권한 확인 및 요청
        checkAndRequestPermissions()


        // 버튼 클릭 리스너 설정
        binding.buttonCamera.setOnClickListener { openCamera() }
        binding.buttonGallery.setOnClickListener { openGallery() }
        binding.buttonSend.setOnClickListener { sendImage() }
    }


    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100 // 예시 코드, 실제 사용할 식별 코드 값을 입력
    }

    //카메라와 갤러리 접근 권한 설정 후의 액티비티
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 권한이 허용된 경우 = 공백으로 둬서 통과 처리
                } else {
                    // 권한이 거부된 경우
                    finish()//앱종료
                }
            }
        }
    }

    //권한 확인 및 요청 함수 정의 카메라와 갤러리 동시에 권한요청 함수
    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_CODE)
        }
    }

    //갤러리 에서 선택된 URI를 서버로 보내는 데이터 형식으로 변환함수(갤러리에서 선택한 URI 를 스트림으로 변환한 후 바이트 배열로 변환)
    private fun getPathFromUri(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(uri, proj, null, null, null)
            val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            return cursor?.getString(columnIndex ?: 0)
        } finally {
            cursor?.close()
        }
    }

    //카메라로 찍은 사진(Bitmap)을 파일 데이터 형식으로 변환함수(서버로 보내려고)
    private fun saveBitmapToFile(bitmap: Bitmap): File? {
        // 임시 파일 생성
        val file = File(getExternalFilesDir(null), "temp_image.jpg") // 임시 파일 경로
        try {
            file.createNewFile()
            val fout = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fout) // Bitmap을 JPEG 형태로 압축하여 저장
            fout.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return file
    }


    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraResultLauncher.launch(cameraIntent)
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResultLauncher.launch(galleryIntent)
    }

    private fun sendImage() {
        val client = OkHttpClient()


        //변환 함수를 통하여 갤러리의 URI, 카메라의 bitmap을 서버에 보낼 데이터 형식으로 변환후 변수에 담기
        val fileBody = when {
            imageBitmap != null -> {
                imageBitmap?.let {
                    saveBitmapToFile(it)?.asRequestBody("image/jpeg".toMediaTypeOrNull())
                }
            }
            imageUri != null -> {
                imageUri?.let { uri ->
                    File(getPathFromUri(this, uri)).asRequestBody("image/jpeg".toMediaTypeOrNull())
                }
            }
            else -> return
        }


        // 'MultipartBody'를 사용하여 요청 본문 구성
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", fileBody ?: return)
            .build()

        // 요청 객체 생성
        val request = Request.Builder()
            .url("http://모바일앱프로그래밍.com/전송") // 여기에 서버의 URL을 입력 (수정해야함)
            .post(requestBody)
            .build()

        // 비동기 요청 실행 ,현재 요청 결과에 따라 간단한 Toast 만 출력하게 설정해 놓았음
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 요청 실패 처리
                e.printStackTrace() // 로그에 에러 출력
                runOnUiThread {
                    Toast.makeText(applicationContext, "네트워크 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    // 응답 실패 처리
                    runOnUiThread {
                        Toast.makeText(applicationContext, "서버 응답 에러: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 응답 성공 처리 ( 수정되어야할 코드 )
                    runOnUiThread {
                        Toast.makeText(applicationContext, "이미지 전송 성공", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 카메라 결과 처리
    private val cameraResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageBitmap = result.data?.extras?.get("data") as Bitmap
            binding.imageView.setImageBitmap(imageBitmap)
        }
    }

    // 갤러리 결과 처리
    private val galleryResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageUri = result.data?.data
            binding.imageView.setImageURI(imageUri)
        }
    }
}