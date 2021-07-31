package com.arraystack.usi

import alirezat775.lib.downloader.Downloader
import alirezat775.lib.downloader.core.OnDownloadListener
import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arraystack.usi.databinding.ActivityMainBinding
import com.arraystack.usi.databinding.ProgressBinding
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var downloader: Downloader? = null
    private val TAG: String = this::class.java.name

    private val handler: Handler = Handler()


    lateinit var binding: ActivityMainBinding

    private val PERMISSION_CODE = 1000

    private val GALLERY_REQUEST = 9

    private val CAMERA_REQUEST = 11

    private var filePath: Uri? = null

    private var storageReference: StorageReference? = null

    private lateinit var progressBinding: ProgressBinding
    lateinit var builder: AlertDialog.Builder
    lateinit var dialog: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getDownloader()
        storageReference = FirebaseStorage.getInstance().reference

        binding.bUpload.setOnClickListener(View.OnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED
                ) {
                    val permission = arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )

                    requestPermissions(permission, PERMISSION_CODE)
                } else {
                    showImageOptionDialog()
                }
            } else {
                showImageOptionDialog()
            }
        })

        binding.startDownloadBtn.setOnClickListener {
            getDownloader()
            downloader?.download()
        }
        binding.cancelDownloadBtn.setOnClickListener {
            downloader?.cancelDownload()
        }
        binding.pauseDownloadBtn.setOnClickListener {
            downloader?.pauseDownload()
        }
        binding.resumeDownloadBtn.setOnClickListener {
            getDownloader()
            downloader?.resumeDownload()
        }

    }

    private fun showImageOptionDialog() {
        val options = Constants.profilePictureOptions
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.select_one))
            .setItems(options, DialogInterface.OnClickListener { dialog, which ->
                when (which) {
                    0 -> getImageFromGallery()
                    1 -> capturePictureFromCamera()
                }
            })
        val dialog = builder.create()
        dialog.show()
    }

    private fun capturePictureFromCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        filePath = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, filePath)
        startActivityForResult(cameraIntent, CAMERA_REQUEST)
    }

    private fun getImageFromGallery() {
        val intent = Intent()
        intent.action = Intent.ACTION_GET_CONTENT
        intent.type = "image/*"
        startActivityForResult(intent, GALLERY_REQUEST)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            filePath = data.data
            try {
                var selectedImage: Uri? = filePath
                binding.ivUploaded.setImageURI(selectedImage)
                if (filePath != null)
                    uploadFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            try {
                var selectedImage: Uri? = filePath
                binding.ivUploaded.setImageURI(selectedImage)
                if (filePath != null)
                    uploadFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }



    private fun uploadFile() {
        builder = AlertDialog.Builder(this)
        progressBinding = ProgressBinding.inflate(layoutInflater)
        builder.setView(progressBinding.root)
        dialog = builder.create()
        if (filePath != null) {
            dialog.show()
            val sRef = storageReference!!.child(
                Constants.STORAGE_PATH_UPLOADS + System.currentTimeMillis() + "." + getFileExtension(
                    filePath
                )
            )
            var bitmap: Bitmap? = null

            if (Build.VERSION.SDK_INT < 28) {
                bitmap = MediaStore.Images.Media.getBitmap(
                    this.contentResolver,
                    filePath
                )
            } else {
                val source = ImageDecoder.createSource(this.contentResolver, filePath!!)
                bitmap = ImageDecoder.decodeBitmap(source)
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 18, byteArrayOutputStream)
            val data = byteArrayOutputStream.toByteArray()

            sRef.putBytes(data)
                .addOnSuccessListener { taskSnapshot ->
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener(
                        OnSuccessListener {
                            FirebaseAdapter(this).addNewImage(
                                it.toString(),
                                object : com.arraystack.usi.OnCompleteListener {
                                    override fun onCallback(value: Boolean) {
                                        dialog.dismiss()
                                        if (value) {
                                            Toast.makeText(
                                                applicationContext,
                                                "File Uploaded ",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            Glide.with(applicationContext).load(it.toString())
                                                .placeholder(R.drawable.ic_launcher_background)
                                                .into(binding.ivUploaded)
                                        }
                                    }
                                })
                        })
                }
                .addOnFailureListener { exception ->
                    dialog.dismiss()
                    Toast.makeText(applicationContext, exception.message, Toast.LENGTH_LONG).show()
                }
                .addOnProgressListener { taskSnapshot ->
                    val progress =
                        100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount
                }
        }
    }

    private fun getFileExtension(uri: Uri?): String? {
        val cR = contentResolver
        val mime = MimeTypeMap.getSingleton()
        return mime.getExtensionFromMimeType(cR.getType(uri!!))
    }

    private fun getDownloader() {
        downloader = Downloader.Builder(
            this,
            "https://firebasestorage.googleapis.com/v0/b/uploadstorageimage-2832c.appspot.com/o/mybucket%2F1626934481170.png?alt=media&token=149fa850-32bd-4131-83f1-ea639dd71689"
        ).downloadListener(object : OnDownloadListener {
            override fun onStart() {
//                handler.post { current_status_txt.text = "onStart" }
                Log.d(TAG, "onStart")
            }

            override fun onPause() {
//                handler.post { current_status_txt.text = "onPause" }
                Log.d(TAG, "onPause")
            }

            override fun onResume() {
//                handler.post { current_status_txt.text = "onResume" }
                Log.d(TAG, "onResume")
            }

            override fun onProgressUpdate(percent: Int, downloadedSize: Int, totalSize: Int) {
                handler.post {
//                    current_status_txt.text = "onProgressUpdate"
                    binding.percentTxt.text = percent.toString().plus("%")
                    binding.sizeTxt.text = getSize(downloadedSize)
                    binding.totalSizeTxt.text = getSize(totalSize)
                    binding.downloadProgress.progress = percent
                }
                Log.d(
                    TAG,
                    "onProgressUpdate: percent --> $percent downloadedSize --> $downloadedSize totalSize --> $totalSize "
                )
            }

            override fun onCompleted(file: File?) {
//                handler.post { current_status_txt.text = "onCompleted" }
                Log.d(TAG, "onCompleted: file --> $file")
            }

            override fun onFailure(reason: String?) {
//                handler.post { current_status_txt.text = "onFailure: reason --> $reason" }
                Log.d(TAG, "onFailure: reason --> $reason")
            }

            override fun onCancel() {
//                handler.post { current_status_txt.text = "onCancel" }
                Log.d(TAG, "onCancel")
            }
        }).build()
    }

    fun getSize(size: Int): String {
        var s = ""
        val kb = (size / 1024).toDouble()
        val mb = kb / 1024
        val gb = kb / 1024
        val tb = kb / 1024
        if (size < 1024) {
            s = "$size Bytes"
        } else if (size >= 1024 && size < 1024 * 1024) {
            s = String.format("%.2f", kb) + " KB"
        } else if (size >= 1024 * 1024 && size < 1024 * 1024 * 1024) {
            s = String.format("%.2f", mb) + " MB"
        } else if (size >= 1024 * 1024 * 1024 && size < 1024 * 1024 * 1024 * 1024) {
            s = String.format("%.2f", gb) + " GB"
        } else if (size >= 1024 * 1024 * 1024 * 1024) {
            s = String.format("%.2f", tb) + " TB"
        }
        return s
    }


}