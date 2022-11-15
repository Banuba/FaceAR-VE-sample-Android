package com.banuba.sdk.example.offscreen;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Size;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Helper class that allows to renders Offscreen output image on display
 */
public class GLI420Renderer implements GLSurfaceView.Renderer {
    private static final String VERTEX_SHADER_PROGRAM =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "layout (location = 0) in vec3 aPosition;\n" +
                    "layout (location = 1) in vec2 aTextureCoord;\n" +
                    "uniform mat4 uMatrix;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = vec4(aPosition, 1.0f) * uMatrix;\n" +
                    "  vTexCoord = aTextureCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_PROGRAM =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "uniform sampler2D uTextureY;\n" +
                    "uniform sampler2D uTextureU;\n" +
                    "uniform sampler2D uTextureV;\n" +
                    "in vec2 vTexCoord;\n" +
                    "out vec4 outFragColor;\n" +
                    "void main() {\n" +
                    "  float y = texture(uTextureY, vTexCoord).x * 1.164383562f;\n" +
                    "  float u = texture(uTextureU, vTexCoord).x;\n" +
                    "  float v = texture(uTextureV, vTexCoord).x;\n" +
                    "  outFragColor = vec4(\n" +
                    "    y + 1.5960267860f * v - 0.8742022179f,\n" +
                    "    y - 0.3917622901f * u - 0.8129676472f * v + 0.5316678235f,\n" +
                    "    y + 2.0172321430f * u - 1.0856307890f,\n" +
                    "    1.0f);\n" +
                    "}\n";

    /* input YUV image to draw */
    private ByteBuffer[] mPlanes = null;
    private int[] mPlaneStrides = null;
    private Size[] mPlaneSizes = null;
    private int mImageOrientation = 0;

    /* variables for working with OpenGL */
    private boolean mIsCreated = false;
    private GLShaderProgram mShaderProgram = null;
    private int mViewportWidth;
    private int mViewportHeight;
    private int[] mUniformTexture;
    private int mUniformMatrix;
    private int[] mVBO;
    private int[] mVAO;
    private int[] mTexture;

    final int vertLen = 4; /* Number of vertices */
    final int texturesNumber = 3; /* Number of GL textures */

    /* initialize the OpenGL drawing */
    private void create() {
        if (mIsCreated) {
            return;
        }
        final int floatSize = Float.SIZE / 8; /* Size of Float in bytes */
        final int xyzLen = 3; /* Number of components */
        final int xyzOffset = 0; /* Size in bytes */
        final int uvLen = 2;  /* Number of components */
        final int uvOffset = xyzLen * floatSize; /* Size in bytes */
        final int coordPerVert = xyzLen + uvLen; /* Number of components */
        final int vertStride = coordPerVert * floatSize; /* Vertex size in bytes */
        final int surfacesNum = 4; /* for each angle (0, 90, 180, 270) */
        final int drawingPlaneCoordsBufferSize = vertStride * vertLen * surfacesNum;
        final float[] drawingPlaneCoords = {
                /* X      Y     Z     U     V */
                /* 0 degrees */
                -1.0f, -1.0f, 0.0f, 0.0f, 1.0f, /* vertex 0 bottom left */
                1.0f,  -1.0f, 0.0f, 1.0f, 1.0f, /* vertex 1 bottom right */
                -1.0f,  1.0f, 0.0f, 0.0f, 0.0f, /* vertex 2 top left */
                1.0f,   1.0f, 0.0f, 1.0f, 0.0f,  /* vertex 3 top right */
                /* 90 degrees */
                -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, /* vertex 0 bottom left */
                1.0f,  -1.0f, 0.0f, 0.0f, 1.0f, /* vertex 1 bottom right */
                -1.0f,  1.0f, 0.0f, 1.0f, 0.0f, /* vertex 2 top left */
                1.0f,   1.0f, 0.0f, 1.0f, 1.0f,  /* vertex 3 top right */
                /* 180 degrees */
                -1.0f, -1.0f, 0.0f, 1.0f, 0.0f, /* vertex 0 bottom left */
                1.0f,  -1.0f, 0.0f, 0.0f, 0.0f, /* vertex 1 bottom right */
                -1.0f,  1.0f, 0.0f, 1.0f, 1.0f, /* vertex 2 top left */
                1.0f,   1.0f, 0.0f, 0.0f, 1.0f,  /* vertex 3 top right */
                /* 270 degrees */
                -1.0f, -1.0f, 0.0f, 1.0f, 1.0f, /* vertex 0 bottom left */
                1.0f,  -1.0f, 0.0f, 1.0f, 0.0f, /* vertex 1 bottom right */
                -1.0f,  1.0f, 0.0f, 0.0f, 1.0f, /* vertex 2 top left */
                1.0f,   1.0f, 0.0f, 0.0f, 0.0f  /* vertex 3 top right */
        };

        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        mVAO = new int[1];
        GLES30.glGenVertexArrays(mVAO.length, mVAO, 0);
        GLES30.glBindVertexArray(mVAO[0]);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVAO[0]);
        mVBO = new int[1];
        GLES20.glGenBuffers(mVBO.length, mVBO, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBO[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, drawingPlaneCoordsBufferSize, FloatBuffer.wrap(drawingPlaneCoords), GLES20.GL_STATIC_DRAW);
        GLES20.glVertexAttribPointer(0, xyzLen, GLES20.GL_FLOAT, false, vertStride, xyzOffset);
        GLES20.glVertexAttribPointer(1, uvLen, GLES20.GL_FLOAT, false, vertStride, uvOffset);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES30.glBindVertexArray(0);

        mTexture = new int[texturesNumber];
        GLES20.glGenTextures(texturesNumber, mTexture, 0);
        for (int i = 0; i < texturesNumber; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[i]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        try {
            mShaderProgram = new GLShaderProgram(VERTEX_SHADER_PROGRAM, FRAGMENT_SHADER_PROGRAM);
            mUniformTexture = new int[texturesNumber];
            mUniformTexture[0] = mShaderProgram.getUniformLocation("uTextureY");
            mUniformTexture[1] = mShaderProgram.getUniformLocation("uTextureU");
            mUniformTexture[2] = mShaderProgram.getUniformLocation("uTextureV");
            mUniformMatrix = mShaderProgram.getUniformLocation("uMatrix");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mIsCreated = true;
    }

    /* destructor */
    private void destroy() {
        if (mIsCreated) {
            mIsCreated = false;
            GLES20.glDeleteBuffers(1, mVBO, 0);
            GLES30.glDeleteVertexArrays(1, mVAO, 0);
            GLES20.glDeleteTextures(texturesNumber, mTexture, 0);
            mShaderProgram = null;
        }
    }

    protected void finalize() {
        /* Potential issue. The destructor must be called from the thread where there is a render context. */
        destroy();
    }

    /* push image to drawing */
    public void drawI420Image(ByteBuffer yPlane, int yPlaneStride, ByteBuffer uPlane, int uPlaneStride, ByteBuffer vPlane, int vPlaneStride, int width, int height, int imageOrientation) {
        if (mPlanes == null && mPlaneStrides == null && mPlaneSizes == null) {
            mPlanes = new ByteBuffer[3];
            mPlanes[0] = yPlane;
            mPlanes[1] = uPlane;
            mPlanes[2] = vPlane;
            mPlaneStrides = new int[3];
            mPlaneStrides[0] = yPlaneStride;
            mPlaneStrides[1] = uPlaneStride;
            mPlaneStrides[2] = vPlaneStride;
            mPlaneSizes = new Size[3];
            mPlaneSizes[0] = new Size(width, height);
            mPlaneSizes[1] = new Size(width / 2, height / 2);
            mPlaneSizes[2] = new Size(width / 2, height / 2);
            mImageOrientation = imageOrientation % 360;
            assert imageOrientation % 90 == 0;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        create();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mViewportWidth = width;
        mViewportHeight = height;
        GLES20.glViewport(0, 0, mViewportWidth, mViewportHeight);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        final ByteBuffer[] planes = mPlanes;
        final int[] planeStrides = mPlaneStrides;
        final Size[] planeSizes = mPlaneSizes;
        final int imageOrientation = mImageOrientation;
        if (mPlanes != null) {
            mPlaneStrides = null;
            mPlanes = null;
            mPlaneSizes = null;
        } else {
            /* Nothing to draw */
            return;
        }

        if (!mIsCreated) {
            return;
        }

        /* scaling */
        final boolean flipSizes = imageOrientation == 90 ||  imageOrientation == 270;
        final int imageWidth = flipSizes ? planeSizes[0].getHeight() : planeSizes[0].getWidth();
        final int imageHeight = flipSizes ? planeSizes[0].getWidth() : planeSizes[0].getHeight();
        final float viewportRatio = ((float)mViewportWidth) / ((float)mViewportHeight);
        final float imageRatio = ((float)imageWidth) / ((float)imageHeight);
        final float xScale = imageRatio > viewportRatio ? imageRatio / viewportRatio : 1.0f;
        final float yScale = viewportRatio > imageRatio  ? viewportRatio / imageRatio : 1.0f;

        final float[] mat4 = {
                xScale, 0.0f, 0.0f, 0.0f,
                0.0f, yScale, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };

        /* clear background */
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        /* bind vertex array */
        mShaderProgram.use();
        GLES30.glBindVertexArray(mVAO[0]);

        for (int i = 0; i < texturesNumber; i++) {
            final int width = planeSizes[i].getWidth();
            final int height = planeSizes[i].getHeight();
            final ByteBuffer data = planes[i];
            final int stride = planeStrides[i];

            /* update texture */
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[i]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, stride);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data);

            /* set uniforms */
            mShaderProgram.setUniformTexture(mUniformTexture[i], i);
        }
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 0);
        mShaderProgram.setUniformMat4(mUniformMatrix, mat4);

        /* draw */
        final int drawingSurfaceGeometryOffset = ((imageOrientation / 90) % 4) * vertLen;
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, drawingSurfaceGeometryOffset, vertLen);

        /* clear */
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES30.glBindVertexArray(0);
        mShaderProgram.unuse();
    }
}
