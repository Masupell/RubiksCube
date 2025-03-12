package com.example.rubixcube;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
{
    ImageButton capture, toggleFlash, flipCamera;
    private PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;

    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>()
    {
        @Override
        public void onActivityResult(Boolean result)
        {
            if (result)
            {
                startCamera(cameraFacing);
            }
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.cameraPreview);
        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        }
        else
        {
            startCamera(cameraFacing);
        }

        flipCamera.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                {
                    cameraFacing = CameraSelector.LENS_FACING_FRONT;
                }
                else
                {
                    cameraFacing = CameraSelector.LENS_FACING_BACK;
                }
                startCamera(cameraFacing);
            }
        });
    }

    public void startCamera(int cameraFacing)
    {
        int aspectRatio = aspectRatio(previewView.getWidth(), previewView.getHeight());
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() ->
        {
            try
            {
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) listenableFuture.get();

                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();

                ImageCapture imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();

                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                capture.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        takePicture(imageCapture);
                    }
                });

                toggleFlash.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        setFlashIcon(camera);
                    }
                });

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
            }
            catch (ExecutionException | InterruptedException e)
            {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public void takePicture(ImageCapture imageCapture)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() + ".jpg");
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RubixCube");

        Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues);

        if (imageUri == null)
        {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Could not create MediaStore entry", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        try
        {
            OutputStream outputStream = getContentResolver().openOutputStream(imageUri);

            assert outputStream != null;
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(outputStream).build();

            imageCapture.takePicture(outputFileOptions, Executors.newSingleThreadExecutor(), new ImageCapture.OnImageSavedCallback()
            {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults)
                {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Image saved in: Pictures/RubixCube", Toast.LENGTH_SHORT).show()
                    );
                    startCamera(cameraFacing);
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception)
                {
                    Log.e("CameraX", "Error saving image", exception);
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Could not save: " + exception.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                    startCamera(cameraFacing);
                }
            });
        }
        catch (IOException e)
        {
            Log.e("CameraX", "Error opening output stream", e);
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Could not open output stream", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void setFlashIcon(Camera camera)
    {
        if (camera.getCameraInfo().hasFlashUnit())
        {
            if (camera.getCameraInfo().getTorchState().getValue() == 0)
            {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.round_flash_off_24);
            }
            else
            {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.round_flash_on_24);
            }
        }
        else
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(MainActivity.this, "You have no flash", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private int aspectRatio(int width, int height)
    {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) { return AspectRatio.RATIO_4_3; }
        return AspectRatio.RATIO_16_9;
    }
}