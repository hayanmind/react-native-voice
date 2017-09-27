package com.wenkesj.voice;

import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.lang.Exception;
import java.lang.Runnable;

import javax.annotation.Nullable;

public class VoiceModule extends ReactContextBaseJavaModule implements RecognitionListener {

  final ReactApplicationContext reactContext;
  private SpeechRecognizer speech = null;
  private boolean isRecognizing = false;
  private String locale = null;
  /*
    'numberOfBreakingSentenece'
    How many user stop speaking between startSpeech and stopSpeech in Android
  */
  private int numberOfBreakingSentence = 0;

  public VoiceModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  private String getLocale(String locale){
    if(locale != null && !locale.equals("")){
      return locale;
    }

    return Locale.getDefault().toString();
  }

  private void startListening() {
    if (speech != null) {
      speech.destroy();
      speech = null;
    }
    speech = SpeechRecognizer.createSpeechRecognizer(this.reactContext);
    speech.setRecognitionListener(this);

    final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocale(this.locale));
    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    // intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200000);
    // intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 100000);
    // intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 100000);

    speech.startListening(intent);
    isRecognizing = true;
  }

  @Override
  public String getName() {
    return "RCTVoice";
  }

  @ReactMethod
  public void startSpeech(final String locale, final Callback callback) {
    this.locale = locale;
    this.numberOfBreakingSentence = 0;
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(new Runnable() {

      @Override
      public void run() {
        try {
          startListening();
          callback.invoke(false);
        } catch (Exception e) {
          callback.invoke(e.getMessage());
        }
      }
    });
  }

  @ReactMethod
  public void stopSpeech(final Callback callback) {
    final VoiceModule self = this;
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          speech.stopListening();
          isRecognizing = false;
          callback.invoke(false);
        } catch(Exception e) {
          callback.invoke(e.getMessage());
        }
      }
    });
  }

  @ReactMethod
  public void cancelSpeech(final Callback callback) {
    final VoiceModule self = this;
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          speech.cancel();
          isRecognizing = false;
          callback.invoke(false);
        } catch(Exception e) {
          callback.invoke(e.getMessage());
        }
      }
    });
  }

  @ReactMethod
  public void destroySpeech(final Callback callback) {
    final VoiceModule self = this;
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          speech.destroy();
          speech = null;
          isRecognizing = false;
          callback.invoke(false);
        } catch(Exception e) {
          callback.invoke(e.getMessage());
        }
      }
    });
  }

  @ReactMethod
  public void isSpeechAvailable(final Callback callback) {
    final VoiceModule self = this;
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          Boolean isSpeechAvailable = SpeechRecognizer.isRecognitionAvailable(self.reactContext);
          callback.invoke(isSpeechAvailable, false);
        } catch(Exception e) {
          callback.invoke(false, e.getMessage());
        }
      }
    });
  }

  @ReactMethod
  public void isRecognizing(Callback callback) {
    final VoiceModule self = this;
    callback.invoke(isRecognizing);
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    this.reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  @Override
  public void onBeginningOfSpeech() {
    // The beginning of automatic detected speech
    // WritableMap event = Arguments.createMap();
    // event.putBoolean("error", false);
    // sendEvent("onSpeechStart", event);
    Log.d("ASR", "onBeginningOfSpeech()");
  }

  @Override
  public void onBufferReceived(byte[] buffer) {
    WritableMap event = Arguments.createMap();
    event.putBoolean("error", false);
    sendEvent("onSpeechRecognized", event);
    Log.d("ASR", "onBufferReceived()");
  }

  @Override
  public void onEndOfSpeech() {
    if (isRecognizing) {
      final VoiceModule self = this;
      Handler mainHandler = new Handler(this.reactContext.getMainLooper());
      mainHandler.post(new Runnable() {
        @Override
        public void run() {
          speech.cancel();
          startListening();
          self.numberOfBreakingSentence += 1;
        }
      });
    } else {
      WritableMap event = Arguments.createMap();
      event.putBoolean("error", false);
      sendEvent("onSpeechEnd", event);
      Log.d("ASR", "onEndOfSpeech()");
    }
  }

  @Override
  public void onError(int errorCode) {
    String errorMessage = String.format("%d/%s", errorCode, getErrorText(errorCode));
    WritableMap error = Arguments.createMap();
    error.putString("message", errorMessage);
    WritableMap event = Arguments.createMap();
    event.putMap("error", error);
    sendEvent("onSpeechError", event);
    Log.d("ASR", "onError() - " + errorMessage);

  }

  @Override
  public void onEvent(int arg0, Bundle arg1) { }

  @Override
  public void onPartialResults(Bundle results) {
    WritableArray arr = Arguments.createArray();

    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    for (String result : matches) {
      arr.pushString(result);
    }

    WritableMap event = Arguments.createMap();
    event.putArray("value", arr);
    event.putInt("numberOfBreakingSentence", this.numberOfBreakingSentence);
    sendEvent("onSpeechPartialResults", event);
    Log.d("ASR", "onPartialResults()");

  }

  @Override
  public void onReadyForSpeech(Bundle arg0) {
    WritableMap event = Arguments.createMap();
    event.putBoolean("error", false);
    sendEvent("onSpeechStart", event);
    Log.d("ASR", "onReadyForSpeech()");

  }

  @Override
  public void onResults(Bundle results) {
    WritableArray arr = Arguments.createArray();

    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    for (String result : matches) {
      arr.pushString(result);
    }

    WritableMap event = Arguments.createMap();
    event.putArray("value", arr);
    sendEvent("onSpeechResults", event);
    Log.d("ASR", "onResults()");
  }

  @Override
  public void onRmsChanged(float rmsdB) {
    WritableMap event = Arguments.createMap();
    event.putDouble("value", (double) rmsdB);
    sendEvent("onSpeechVolumeChanged", event);
  }

  public static String getErrorText(int errorCode) {
    String message;
    switch (errorCode) {
      case SpeechRecognizer.ERROR_AUDIO:
        message = "Audio recording error";
        break;
      case SpeechRecognizer.ERROR_CLIENT:
        message = "Client side error";
        break;
      case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
        message = "Insufficient permissions";
        break;
      case SpeechRecognizer.ERROR_NETWORK:
        message = "Network error";
        break;
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
        message = "Network timeout";
        break;
      case SpeechRecognizer.ERROR_NO_MATCH:
        message = "No match";
        break;
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
        message = "RecognitionService busy";
        break;
      case SpeechRecognizer.ERROR_SERVER:
        message = "error from server";
        break;
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
        message = "No speech input";
        break;
      default:
        message = "Didn't understand, please try again.";
        break;
    }
    return message;
  }
}
