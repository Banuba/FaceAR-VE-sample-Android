package com.banuba.sdk.example.offscreen;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.banuba.sdk.camera.Facing;
import com.banuba.sdk.internal.utils.CameraUtils;
import com.banuba.sdk.internal.utils.Logger;
import com.banuba.sdk.offscreen.ImageOrientation;

import java.util.Collections;

@SuppressWarnings("WeakerAccess")
public class Camera2Simple {
    private static final String TAG = "Camera2Simple";

    private static final int FIXED_FRAME_RATE = 30;

    private final Handler mHandler;
    private final CameraManager mCameraManager;

    private int mDisplaySurfaceRotation = Surface.ROTATION_0;

    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;

    @NonNull
    private final Size mPreferredPreviewSize;
    private Size mPreviewSize;
    private Facing cameraFacing = Facing.FRONT;

    private boolean mIsCameraOpened = false;
    private boolean mCameraOpening = false;
    private int mFacing;
    private int mSensorOrientation;
    private int mDeviceOrientationAngle;
    private final FrameReadyCallback mFrameReadyCallback;

    public interface FrameReadyCallback {
        void onFrameReady(@NonNull Image image, @NonNull ImageOrientation imageOrientation);
    }

    public Camera2Simple(@NonNull Context context, @NonNull FrameReadyCallback frameReadyCallback, @NonNull Size preferredPreviewSize) {
        final WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
        if (windowManager != null) {
            mDisplaySurfaceRotation = windowManager.getDefaultDisplay().getRotation();
        }
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mFrameReadyCallback = frameReadyCallback;
        mPreferredPreviewSize = preferredPreviewSize;
        mPreviewSize = preferredPreviewSize;
        mHandler = new Handler(); // Creating handler here means that all camera events processing in this thread
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = this::pushFrame;

    private void pushFrame(ImageReader reader) {
        try {
            final Image image = reader.acquireLatestImage();
            if (image != null) {
                final boolean isRequireMirroring = mFacing == LENS_FACING_FRONT;
                ImageOrientation mEPImageFormat = getImageOrientation(isRequireMirroring);
                mFrameReadyCallback.onFrameReady(image, mEPImageFormat);
            }
            // Image will be closed in OffscreenEffectPlayer

        } catch (Exception e) {
            Logger.wtf("Error while pushing frame", e);
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(Facing cameraFacing) {
        if (!mIsCameraOpened) {
            Throwable error = null;
            String usedCameraId = null;

            try {
                if (mCameraManager != null) {
                    for (String cameraId : mCameraManager.getCameraIdList()) {
                        final CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (facing != null && facing == cameraFacing.getValue()) {
                            usedCameraId = cameraId;
                            setupCameraCharacteristics(characteristics);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.wtf(e);
                error = e;
            }

            if (usedCameraId != null) {
                try {
                    mIsCameraOpened = true;
                    mCameraOpening = true;
                    mCameraManager.openCamera(usedCameraId, mStateCallback, null);
                } catch (Exception e) {
                    mIsCameraOpened = false;
                    mCameraOpening = false;
                    Logger.wtf(e);
                    error = e;
                }
            }

            if (error != null) {
                Logger.wtf(error);
            }
        }
    }


    private void setupCameraCharacteristics(@NonNull CameraCharacteristics characteristics) {
        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map != null) {
            mFacing = getLensFacing(characteristics);
            mSensorOrientation = CameraUtils.getSensorOrientation(characteristics);
            mPreviewSize = CameraUtils.getPreviewSize(characteristics, mPreferredPreviewSize);
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            mCameraOpening = false;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            onCameraClosedState(cameraDevice);
            mCameraOpening = false;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            onCameraClosedState(cameraDevice);
            Logger.wtf((new Exception("Camera error: " + error).fillInStackTrace().toString()));
            mCameraOpening = false;
        }

        @Override
        public void onClosed(@NonNull CameraDevice cameraDevice) {
            onCameraClosedState(cameraDevice);
        }
    };

    private void onCameraClosedState(@NonNull CameraDevice cameraDevice) {
        cameraDevice.close();

        if (mCameraDevice == cameraDevice) {
            mCameraDevice = null;
            mIsCameraOpened = false;
        }
    }

    private void createCameraPreviewSession() {
        try {
            createPreviewRequest();

            // Here, we prepare a CameraCaptureSession for camera preview.

            mCameraDevice.createCaptureSession(
                    Collections.singletonList(mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                mCaptureSession.setRepeatingRequest(
                                        mPreviewRequestBuilder.build(), null, mHandler
                                );
                            } catch (CameraAccessException e) {
                                Logger.wtf(e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession
                        ) {
                            Logger.wtf((new RuntimeException(
                                    "CameraCaptureSession.StateCallback.onConfigureFailed"
                            ).fillInStackTrace()).toString());
                        }
                    },
                    null
            );

        } catch (CameraAccessException e) {
            Logger.wtf(e);
        }
    }

    private void createPreviewRequest() throws CameraAccessException {
        mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        if (mImageReader != null) {
            mImageReader.close();
        }

        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 5);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);

        mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

        mPreviewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
        );

        mPreviewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range.create(FIXED_FRAME_RATE, FIXED_FRAME_RATE)
        );
    }

    public void closeCamera() {
        mIsCameraOpened = false;
        mHandler.removeCallbacksAndMessages(null);

        final CameraCaptureSession cameraCaptureSession = mCaptureSession;
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException | IllegalStateException e) {
                Logger.i(e.getMessage());
            }
            cameraCaptureSession.close();
            mCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        final ImageReader reader = mImageReader;
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            // Do not close let this do GC
        }
    }

    private ImageOrientation getImageOrientation(boolean isRequireMirroring) {
        int rotation = 90 * mDisplaySurfaceRotation;
        int deviceOrientationAngle = mDeviceOrientationAngle;

        if (mFacing == CameraCharacteristics.LENS_FACING_BACK) {
            rotation = 360 - rotation;

            if (mDeviceOrientationAngle == 0) {
                deviceOrientationAngle = 180;
            } else if (mDeviceOrientationAngle == 180) {
                deviceOrientationAngle = 0;
            }
        }

        final int imageOrientation = (mSensorOrientation + rotation) % 360;

        return ImageOrientation.getForCamera(imageOrientation, deviceOrientationAngle, mDisplaySurfaceRotation, isRequireMirroring);
    }

    public void switchFacing() {
        if (mCameraOpening) {
            return;
        }

        closeCamera();
        cameraFacing = cameraFacing == Facing.FRONT ? Facing.BACK : Facing.FRONT;
        openCamera(cameraFacing);
    }

    public void applyOrientationAngles(int sensorAngle, int displaySurfaceRotation) {
        mDeviceOrientationAngle = sensorAngle;
        mDisplaySurfaceRotation = displaySurfaceRotation;
    }

    public void openCameraAndStartPreview() {
        openCamera(cameraFacing);
    }

    public static int getLensFacing(@NonNull CameraCharacteristics characteristics) {
        final Integer facingRaw = characteristics.get(CameraCharacteristics.LENS_FACING);
        return facingRaw != null ? facingRaw : LENS_FACING_FRONT;
    }
}
