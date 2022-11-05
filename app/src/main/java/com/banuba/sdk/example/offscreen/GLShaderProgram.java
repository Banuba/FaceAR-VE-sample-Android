package com.banuba.sdk.example.offscreen;

import android.opengl.GLES20;

public class GLShaderProgram {
    private static final String TAG = "GLShaderProgram";
    private int mShaderProgram = 0;

    GLShaderProgram(String vertexShaderSource, String fragmentShaderSource) throws Exception {
        int vertexShaderObject = 0;
        int fragmentShaderObject = 0;
        try {
            vertexShaderObject = compileShaderObject(vertexShaderSource, GLES20.GL_VERTEX_SHADER);
            fragmentShaderObject = compileShaderObject(fragmentShaderSource, GLES20.GL_FRAGMENT_SHADER);
            mShaderProgram = linkShaderProgram(vertexShaderObject, fragmentShaderObject);
        } catch (Exception e) {
            throw e;
        } finally {
            if (vertexShaderObject != 0) {
                deleteShaderObject(vertexShaderObject);
            }
            if (fragmentShaderObject != 0) {
                deleteShaderObject(fragmentShaderObject);
            }
        }
    }

    public void use() {
        GLES20.glUseProgram(mShaderProgram);
    }

    public void unuse() {
        GLES20.glUseProgram(0);
    }

    public int getAttributeLocation(String attributeName) throws Exception {
        final int attribute = GLES20.glGetAttribLocation(mShaderProgram, attributeName);
        if (attribute == -1) {
            doThrow("Unknown attribute name: " + attributeName);
        }
        return attribute;
    }

    public int getUniformLocation(String uniformName) throws Exception {
        final int uniform = GLES20.glGetUniformLocation(mShaderProgram, uniformName);
        if (uniform == -1) {
            doThrow("Unknown uniform name: " + uniformName);
        }
        return uniform;
    }

    public void setUniformTexture(int uniform, int texture) {
        GLES20.glUniform1i(uniform, texture);
    }

    public void setUniformMat4(int uniform, float[] mat4) {
        GLES20.glUniformMatrix4fv(uniform, 1, false, mat4, 0);
    }

    protected void finalize() {
        if (mShaderProgram != 0) {
            deleteShaderProgram(mShaderProgram);
        }
    }

    private int compileShaderObject(String shaderCode, int type) throws Exception {
        /* create shader object */
        final int shaderObject = GLES20.glCreateShader(type);
        if(shaderObject == 0) {
            doThrow("Unable to create shader object.");
        }
        /* compile shader */
        GLES20.glShaderSource(shaderObject, shaderCode);
        GLES20.glCompileShader(shaderObject);
        /* check compile status */
        final int[] status = new int[1];
        GLES20.glGetShaderiv(shaderObject, GLES20.GL_COMPILE_STATUS, status, 0);
        if(status[0] == GLES20.GL_FALSE) {
            String errorMessage = GLES20.glGetShaderInfoLog(shaderObject);
            deleteShaderObject(shaderObject);
            doThrow("Shader object compilation error: " + errorMessage);
        }
        return shaderObject;
    }

    private int linkShaderProgram(int vertexShaderObject, int fragmentShaderObject) throws Exception {
        final int shaderProgram = GLES20.glCreateProgram();
        if(shaderProgram == 0) {
            doThrow("Unable to create shader program");
        }

        GLES20.glAttachShader(shaderProgram, vertexShaderObject);
        GLES20.glAttachShader(shaderProgram, fragmentShaderObject);
        GLES20.glLinkProgram(shaderProgram);

        // Link the program
        GLES20.glLinkProgram(shaderProgram);

        /* check link status */
        final int[] status = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, status, 0);

        if(status[0] == GLES20.GL_FALSE) {
            String errorMessage = GLES20.glGetProgramInfoLog(shaderProgram);
            deleteShaderProgram(shaderProgram);
            doThrow("Link shader error: " + errorMessage);
        }

        /* validate program */
        GLES20.glValidateProgram(shaderProgram);
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_VALIDATE_STATUS, status, 0);
        if(status[0] == GLES20.GL_FALSE) {
            deleteShaderProgram(shaderProgram);
            doThrow("Validate shader program error." );
        }
        /* detach shader objects */
        GLES20.glDetachShader(shaderProgram, vertexShaderObject);
        GLES20.glDetachShader(shaderProgram, fragmentShaderObject);
        return shaderProgram;
    }

    private void deleteShaderObject(int shaderObject) {
        GLES20.glDeleteShader(shaderObject);
    }

    private void deleteShaderProgram(int shaderProgram) {
        GLES20.glDeleteProgram(shaderProgram);
    }

    private void doThrow(String message) throws Exception {
        throw new Exception(TAG + " error:" + message);
    }
}
