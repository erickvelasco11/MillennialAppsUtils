package co.com.millennialapps.utils.firebase;

import android.content.Context;
import android.content.ContextWrapper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

/**
 * Created by erick on 9/9/2017.
 */

public class FStorageManager {

    private static FStorageManager fStorageManager;
    private StorageReference reference;

    public static FStorageManager getInstance() {
        return fStorageManager == null ? fStorageManager = new FStorageManager() : fStorageManager;
    }

    private StorageReference getReference() {
        return reference == null ? reference = FirebaseStorage.getInstance().getReference() : reference;
    }

    public void downloadFile(String path, OnSuccessListener listener) {
        reference = getReference();
        StorageReference ref = reference;
        ref = ref.child(path);
        ref.getBytes(1024 * 1024).addOnSuccessListener(listener);
    }

    public void downloadFileToFile(Context context, String path, String dirName, String fileName) {
        reference = getReference();
        StorageReference ref = reference;
        ref = ref.child(path);
        ContextWrapper cw = new ContextWrapper(context);
        File directory = cw.getDir(dirName, Context.MODE_PRIVATE);
        File mypath = new File(directory, fileName);
        ref.getFile(mypath);
    }

    public void downloadImage(Context context, ImageView img, String path) {
        reference = getReference();
        StorageReference ref = reference;
        ref = ref.child(path);
        Glide.with(context)
                .using(new FirebaseImageLoader())
                .load(ref)
                .into(img);
    }
}