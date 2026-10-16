package dev.grokhome;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/** Test APK only: signals readiness without recording audio or fabricating speech. */
public final class FakeRecognitionService extends RecognitionService {
    @Override protected void onStartListening(Intent intent,Callback callback){
        if(android.os.Build.VERSION.SDK_INT>=31)createContext(new android.content.ContextParams.Builder().setNextAttributionSource(callback.getCallingAttributionSource()).build());
        try{callback.readyForSpeech(new Bundle());}catch(RemoteException ignored){}
    }
    @Override protected void onCancel(Callback callback){}
    @Override protected void onStopListening(Callback callback){
        try{callback.error(SpeechRecognizer.ERROR_NO_MATCH);}catch(RemoteException ignored){}
    }
}
