package com.example.recruitai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.recruitai.Model.InterviewResponse
import com.example.recruitai.Model.ResumeResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main2.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.*

class MainActivity2 : AppCompatActivity() {
    var database: FirebaseDatabase? = null
    var dat: DatabaseReference? = null
    val TAG = "MainActivity2"
    var isRecording = false
    var CAMERA_PERMISSION = Manifest.permission.CAMERA
    var RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    var RC_PERMISSION = 101
    lateinit var timer:CountDownTimer
    lateinit var storage: StorageReference
    lateinit var file: File
    lateinit var uri: Uri
    var uid: String? = null
    var jobid: String? = null
    var firebaseAuth: FirebaseAuth? = null
    var user: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val recordFiles = ContextCompat.getExternalFilesDirs(this, Environment.DIRECTORY_MOVIES)
        val storageDirectory = recordFiles[0]
        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth!!.currentUser
        uid = user!!.getUid()
        database = FirebaseDatabase.getInstance()
        dat = database!!.getReference("Jobs")
        val videoRecordingFilePath = "${storageDirectory.absoluteFile}/${System.currentTimeMillis()}_video.mp4"
        if (checkPermissions()) startCameraSession() else requestPermissions()
        if (intent != null) {
            jobid = intent.getStringExtra("JobID")
        }

        video_record.setOnClickListener {
            if (isRecording) {
                isRecording = false
                video_record.text = "Record Video"
                Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show()
                camera_view.stopRecording()
                timer.cancel()
                textView15.setText("60")
            } else {
                isRecording = true
                video_record.text = "Stop Recording"
                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
                recordVideo(videoRecordingFilePath)
                timer = object: CountDownTimer(60000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        textView15.setText((millisUntilFinished / 1000).toString());
                    }
                    override fun onFinish() {
                        isRecording = false
                        video_record.text = "Record Video"
                        Toast.makeText(this@MainActivity2, "Time is up. Recording Stopped", Toast.LENGTH_SHORT).show()
                        camera_view.stopRecording()
                    }
                }
                timer.start()
            }
        }

        button.setOnClickListener{
            Toast.makeText(this, "Uploading to Firebase", Toast.LENGTH_SHORT).show()
            storage= FirebaseStorage.getInstance().reference
            val mReference = storage.child("users/" + uid + "/interview_video.mp4")
            mReference.putFile(uri).addOnSuccessListener { taskSnapshot -> val url=taskSnapshot.storage.downloadUrl.toString()
                Toast.makeText(this@MainActivity2, url, Toast.LENGTH_SHORT).show()
                dat!!.child(jobid!!).child("Juser").child(uid!!).child("status").setValue("Interview uploaded").addOnSuccessListener { taskSnapshot -> Toast.makeText(this@MainActivity2, "Status updated", Toast.LENGTH_SHORT).show()
                    apicall()
                }

            }.addOnFailureListener{ exception->
                Toast.makeText(this@MainActivity2, exception.toString(), Toast.LENGTH_SHORT).show()
                Log.d("upload", exception.toString())
            }
        }}

    private fun apicall() {
        val retrofit = Retrofit.Builder()
                .baseUrl("https://4815-2409-40f4-2b-dadd-aa6e-d02e-5fe9-9c5a.in.ngrok.io")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        val api = retrofit.create(API::class.java)
        val call = api.interview(uid, jobid)

        call.enqueue(object : Callback<InterviewResponse?> {
            override fun onResponse(call: Call<InterviewResponse?>, response: Response<InterviewResponse?>) {
                Log.d("api", "onResponse Called")
                Log.d("api", "Code: " + response.code())
                var objecti: InterviewResponse? = response.body()
                if (Objects.requireNonNull(objecti)!!.id == uid) {
                    Toast.makeText(this@MainActivity2, "Interview is being analysed", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity2, User::class.java))
                }
            }

            override fun onFailure(call: Call<InterviewResponse?>, t: Throwable) {
                Log.d("api", "onFailure Called")
                Log.d("api", t.message!!)
            }
        })

    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION), RC_PERMISSION)
    }

    private fun checkPermissions(): Boolean {
        return ((ActivityCompat.checkSelfPermission(this, CAMERA_PERMISSION)) == PackageManager.PERMISSION_GRANTED
                && (ActivityCompat.checkSelfPermission(this, RECORD_AUDIO_PERMISSION)) == PackageManager.PERMISSION_GRANTED)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            RC_PERMISSION -> {
                var allPermissionsGranted = false
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        break
                    } else {
                        allPermissionsGranted = true
                    }
                }
                if (allPermissionsGranted) startCameraSession() else permissionsNotGranted()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCameraSession() {
        camera_view.bindToLifecycle(this)
    }

    private fun permissionsNotGranted() {
        AlertDialog.Builder(this).setTitle("Permissions required")
                .setMessage("These permissions are required to use this app. Please allow Camera and Audio permissions first")
                .setCancelable(false)
                .setPositiveButton("Grant") { dialog, which -> requestPermissions() }
                .show()
    }

    private fun recordVideo(videoRecordingFilePath: String) {
        camera_view.startRecording(File(videoRecordingFilePath), ContextCompat.getMainExecutor(this), object : VideoCapture.OnVideoSavedCallback {
            override fun onVideoSaved(file: File) {
                Toast.makeText(this@MainActivity2, "Recording Saved", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "onVideoSaved $videoRecordingFilePath")
                this@MainActivity2.file = file
                uri = Uri.fromFile(file)
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@MainActivity2, "Recording Failed", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onError $videoCaptureError $message")
            }
        })
    }
}
