package com.matrixdeveloper.chatsapp.Activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.matrixdeveloper.chatsapp.Activities.ChatActivity
import com.matrixdeveloper.chatsapp.Adapters.MessagesAdapter
import com.matrixdeveloper.chatsapp.BuildConfig
import com.matrixdeveloper.chatsapp.Models.Message
import com.matrixdeveloper.chatsapp.databinding.ActivityChatBinding
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class ChatActivity : AppCompatActivity() {
    var binding: ActivityChatBinding? = null
    var adapter: MessagesAdapter? = null
    var messages: ArrayList<Message?>? = null
    var senderRoom: String? = null
    var receiverRoom: String? = null
    var database: FirebaseDatabase? = null
    private var mFirebaseUser: FirebaseUser? = null
    var senderUid: String = ""
    private val mPhotoUrl = ""
    var receiverUid: String = ""
    private var title: String ="New Message"
    private var token: String ="token"
    private val FCM_API = "https://fcm.googleapis.com/fcm/send"
    private val serverKey = "key=" + "AAAAyGv7NeE:APA91bE51Q9lLGbUq5htj1itHU3bQLUlIn6HARJWN62A7o-4M3gPkIFzPOr13yn4_l7bOE4Q10O1GP-s9cnhGiN_ElQfBNPt_cSWcPoeg881mW1CrNEz_ZyS-WeUMOKSyHf3zw5p5lQj"
    private val contentType = "application/json"
    private val requestQueue: RequestQueue by lazy {
        Volley.newRequestQueue(this.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        messages = ArrayList()
        val name = intent.getStringExtra("name")
        receiverUid = intent.getStringExtra("uid").toString()
        senderUid = FirebaseAuth.getInstance().uid.toString()
        mFirebaseUser = FirebaseAuth.getInstance().currentUser
        senderRoom = senderUid + receiverUid
        receiverRoom = receiverUid + senderUid
        adapter = MessagesAdapter(this, messages, senderRoom, receiverRoom)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding!!.recyclerView.layoutManager = layoutManager
        binding!!.recyclerView.adapter = adapter
        binding!!.recyclerView.smoothScrollToPosition(adapter!!.itemCount)
        database = FirebaseDatabase.getInstance()
        //     mPhotoUrl = Objects.requireNonNull(mFirebaseUser.getPhotoUrl()).toString();
        val msgStatus = HashMap<String, Any>()
        msgStatus["admin"] = "0"
        database!!.reference.child("users").child(receiverUid!!).updateChildren(msgStatus)
        database!!.reference.child("chats")
                .child(senderRoom!!)
                .child("messages")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        messages!!.clear()
                        for (snapshot1 in snapshot.children) {
                            val message = snapshot1.getValue(Message::class.java)!!
                            message.messageId = snapshot1.key
                            messages!!.add(message)
                        }
                        adapter!!.notifyDataSetChanged()
                        binding!!.recyclerView.smoothScrollToPosition(adapter!!.itemCount)
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        binding!!.attachment.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }

//        database!!.reference.child("users").child(senderUid)
//                .addValueEventListener(object : ValueEventListener {
//                    override fun onDataChange(snapshot: DataSnapshot) {
//                        title = snapshot.child("name").value.toString()
//                    }
//                    override fun onCancelled(error: DatabaseError) {}
//                })

        database!!.reference.child("users").child(receiverUid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        token = snapshot.child("token").value.toString()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

        binding!!.sendBtn.setOnClickListener {
            val messageTxt = binding!!.messageBox.text.toString()
            val date = Date()
            val message = Message(messageTxt, senderUid, date.time, "", "")
            binding!!.messageBox.setText("")
            val randomKey = database!!.reference.push().key
            val lastMsgObj = HashMap<String, Any>()
            lastMsgObj["lastMsg"] = message.message
            lastMsgObj["lastMsgTime"] = date.time
            val msgStatus = HashMap<String, Any>()
            msgStatus["user"] = "1"
            msgStatus["lastMsgTime"] = date.time
            database!!.reference.child("users").child(receiverUid).updateChildren(msgStatus)
            database!!.reference.child("chats").child(senderRoom!!).updateChildren(lastMsgObj)
            database!!.reference.child("chats").child(receiverRoom!!).updateChildren(lastMsgObj)
            if (BuildConfig.DEBUG && randomKey == null) {
                error("Assertion failed")
            }
            database!!.reference.child("chats")
                    .child(senderRoom!!)
                    .child("messages")
                    .child(randomKey!!)
                    .setValue(message).addOnSuccessListener {
                        database!!.reference.child("chats")
                                .child(receiverRoom!!)
                                .child("messages")
                                .child(randomKey)
                                .setValue(message).addOnSuccessListener { }
                    }

            val topic = token

            val notification = JSONObject()
            val notifcationBody = JSONObject()

            try {
                notifcationBody.put("title", title)
                notifcationBody.put("message", message.message)   //Enter your notification message
                notification.put("to", topic)
                notification.put("data", notifcationBody)
                Log.e("TAG", "try")
            } catch (e: JSONException) {
                Log.e("TAG", "onCreate: " + e.message)
            }

            sendNotification(notification)
        }

        Objects.requireNonNull(supportActionBar)!!.title = name
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    private fun sendNotification(notification: JSONObject) {
        Log.e("TAG", "sendNotification")
        val jsonObjectRequest = object : JsonObjectRequest(FCM_API, notification,
                Response.Listener<JSONObject> { response ->
                    Log.i("ChatAct", "onResponse: $response")

                },
                Response.ErrorListener {
                    Toast.makeText(this@ChatActivity, "Request error", Toast.LENGTH_LONG).show()
                    Log.i("ChatAct", "onErrorResponse: Didn't work")
                }) {

            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["Authorization"] = serverKey
                params["Content-Type"] = contentType
                return params
            }
        }
        requestQueue.add(jsonObjectRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("TAG", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    val uri = data.data
                    Log.d("TAG", "Uri: " + uri.toString())
                    val date = Date()
                    val message = Message("", senderUid, date.time, mPhotoUrl, LOADING_IMAGE_URL)
                    binding!!.messageBox.setText("")
                    val randomKey = database!!.reference.push().key
                    val lastMsgObj = HashMap<String, Any>()
                    lastMsgObj["lastMsg"] = message.message
                    lastMsgObj["lastMsgTime"] = date.time
                    database!!.reference.child("chats").child(senderRoom!!).updateChildren(lastMsgObj)
                    database!!.reference.child("chats").child(receiverRoom!!).updateChildren(lastMsgObj)
                    assert(randomKey != null)
                    val storageReference = FirebaseStorage.getInstance()
                            .getReference(mFirebaseUser!!.uid)
                            .child(randomKey!!)
                            .child(uri!!.lastPathSegment!!)
                    putImageInStorage(storageReference, uri, randomKey)
                }
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri?, key: String?) {
        storageReference.putFile(uri!!).addOnCompleteListener(this@ChatActivity
        ) { task ->
            if (task.isSuccessful) {
                task.result!!.metadata!!.reference!!.downloadUrl
                        .addOnCompleteListener(this@ChatActivity
                        ) { task ->
                            if (task.isSuccessful) {
                                val date = Date()
                                val message = Message("", senderUid, date.time, mPhotoUrl, task.result.toString())
                                binding!!.messageBox.setText("")
                                val lastMsgObj = HashMap<String, Any>()
                                lastMsgObj["lastMsg"] = message.message
                                lastMsgObj["lastMsgTime"] = date.time
                                database!!.reference.child("chats").child(senderRoom!!).updateChildren(lastMsgObj)
                                database!!.reference.child("chats").child(receiverRoom!!).updateChildren(lastMsgObj)
                                assert(key != null)
                                database!!.reference.child("chats")
                                        .child(senderRoom!!)
                                        .child("messages")
                                        .child(key!!)
                                        .setValue(message).addOnSuccessListener {
                                            database!!.reference.child("chats")
                                                    .child(receiverRoom!!)
                                                    .child("messages")
                                                    .child(key)
                                                    .setValue(message).addOnSuccessListener { }
                                        }
                            }
                        }
            } else {
                Log.w("TAG", "Image upload task was not successful.",
                        task.exception)
            }
        }
    }

    //    @Override
    //    public boolean onCreateOptionsMenu(Menu menu) {
    //        getMenuInflater().inflate(R.menu.chat_menu, menu);
    //        return super.onCreateOptionsMenu(menu);
    //    }
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return super.onSupportNavigateUp()
    }

    companion object {
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
    }
}