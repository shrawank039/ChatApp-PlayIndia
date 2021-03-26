package com.matrixdeveloper.chatsapp.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.matrixdeveloper.chatsapp.Adapters.MessagesAdapter;
import com.matrixdeveloper.chatsapp.Models.Message;
import com.matrixdeveloper.chatsapp.databinding.ActivityChatBinding;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class ChatActivity extends AppCompatActivity {

    ActivityChatBinding binding;
    MessagesAdapter adapter;
    ArrayList<Message> messages;
    private static final int REQUEST_IMAGE = 2;
    String senderRoom, receiverRoom;
    FirebaseDatabase database;
    private FirebaseUser mFirebaseUser;
    String senderUid;
    private String mPhotoUrl="";
    private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
    String receiverUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        messages = new ArrayList<>();


        String name = getIntent().getStringExtra("name");
        receiverUid = getIntent().getStringExtra("uid");

        senderUid = FirebaseAuth.getInstance().getUid();
        mFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        senderRoom = senderUid + receiverUid;
        receiverRoom = receiverUid + senderUid;

        adapter = new MessagesAdapter(this, messages, senderRoom, receiverRoom);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.smoothScrollToPosition(adapter.getItemCount());

        database = FirebaseDatabase.getInstance();
        //     mPhotoUrl = Objects.requireNonNull(mFirebaseUser.getPhotoUrl()).toString();

        HashMap<String, Object> msgStatus = new HashMap<>();
        msgStatus.put("admin", "0");
        database.getReference().child("users").child(receiverUid).updateChildren(msgStatus);

        database.getReference().child("chats")
                .child(senderRoom)
                .child("messages")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        messages.clear();
                        for(DataSnapshot snapshot1 : snapshot.getChildren()) {
                            Message message = snapshot1.getValue(Message.class);
                            assert message != null;
                            message.setMessageId(snapshot1.getKey());
                            messages.add(message);
                        }

                        adapter.notifyDataSetChanged();
                        binding.recyclerView.smoothScrollToPosition(adapter.getItemCount());

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });

        binding.attachment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE);
            }
        });

        binding.sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageTxt = binding.messageBox.getText().toString();

                Date date = new Date();
                Message message = new Message(messageTxt, senderUid, date.getTime(),"","");
                binding.messageBox.setText("");

                String randomKey = database.getReference().push().getKey();

                HashMap<String, Object> lastMsgObj = new HashMap<>();
                lastMsgObj.put("lastMsg", message.getMessage());
                lastMsgObj.put("lastMsgTime", date.getTime());

                HashMap<String, Object> msgStatus = new HashMap<>();
                msgStatus.put("user", "1");
                msgStatus.put("lastMsgTime", date.getTime());

                database.getReference().child("users").child(receiverUid).updateChildren(msgStatus);

                database.getReference().child("chats").child(senderRoom).updateChildren(lastMsgObj);
                database.getReference().child("chats").child(receiverRoom).updateChildren(lastMsgObj);

                assert randomKey != null;
                database.getReference().child("chats")
                        .child(senderRoom)
                        .child("messages")
                        .child(randomKey)
                        .setValue(message).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        database.getReference().child("chats")
                                .child(receiverRoom)
                                .child("messages")
                                .child(randomKey)
                                .setValue(message).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {

                            }
                        });
                    }
                });

            }
        });


        Objects.requireNonNull(getSupportActionBar()).setTitle(name);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("TAG", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    final Uri uri = data.getData();
                    Log.d("TAG", "Uri: " + uri.toString());

                    Date date = new Date();
                    Message message = new Message("", senderUid, date.getTime(),mPhotoUrl,LOADING_IMAGE_URL);
                    binding.messageBox.setText("");

                    String randomKey = database.getReference().push().getKey();

                    HashMap<String, Object> lastMsgObj = new HashMap<>();
                    lastMsgObj.put("lastMsg", message.getMessage());
                    lastMsgObj.put("lastMsgTime", date.getTime());

                    database.getReference().child("chats").child(senderRoom).updateChildren(lastMsgObj);
                    database.getReference().child("chats").child(receiverRoom).updateChildren(lastMsgObj);

                    assert randomKey != null;
                    StorageReference storageReference =
                            FirebaseStorage.getInstance()
                                    .getReference(mFirebaseUser.getUid())
                                    .child(randomKey)
                                    .child(uri.getLastPathSegment());

                    putImageInStorage(storageReference, uri, randomKey);

                }
            }
        }
    }

    private void putImageInStorage(StorageReference storageReference, Uri uri, final String key) {
        storageReference.putFile(uri).addOnCompleteListener(ChatActivity.this,
                new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            task.getResult().getMetadata().getReference().getDownloadUrl()
                                    .addOnCompleteListener(ChatActivity.this,
                                            new OnCompleteListener<Uri>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Uri> task) {
                                                    if (task.isSuccessful()) {

                                                        Date date = new Date();
                                                        Message message = new Message("", senderUid, date.getTime(),mPhotoUrl,task.getResult().toString());
                                                        binding.messageBox.setText("");

                                                        HashMap<String, Object> lastMsgObj = new HashMap<>();
                                                        lastMsgObj.put("lastMsg", message.getMessage());
                                                        lastMsgObj.put("lastMsgTime", date.getTime());

                                                        database.getReference().child("chats").child(senderRoom).updateChildren(lastMsgObj);
                                                        database.getReference().child("chats").child(receiverRoom).updateChildren(lastMsgObj);

                                                        assert key != null;
                                                        database.getReference().child("chats")
                                                                .child(senderRoom)
                                                                .child("messages")
                                                                .child(key)
                                                                .setValue(message).addOnSuccessListener(new OnSuccessListener<Void>() {
                                                            @Override
                                                            public void onSuccess(Void aVoid) {
                                                                database.getReference().child("chats")
                                                                        .child(receiverRoom)
                                                                        .child("messages")
                                                                        .child(key)
                                                                        .setValue(message).addOnSuccessListener(new OnSuccessListener<Void>() {
                                                                    @Override
                                                                    public void onSuccess(Void aVoid) {

                                                                    }
                                                                });
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                        } else {
                            Log.w("TAG", "Image upload task was not successful.",
                                    task.getException());
                        }
                    }
                });
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.chat_menu, menu);
//        return super.onCreateOptionsMenu(menu);
//    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }
}