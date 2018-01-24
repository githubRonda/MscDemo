// ITaskCallback.aidl
package com.kandi.systemui;

// Declare any non-default types here with import statements

interface ITaskCallback {

    void onResult(String result);

    void onError(String error);

    int getPriority();
}
