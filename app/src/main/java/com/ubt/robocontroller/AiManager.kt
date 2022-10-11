package com.ubt.robocontroller

class AiManager {
    companion object {
        init {
            System.loadLibrary("robocontroller")
        }
    }

    external fun test()
}